package com.example.tonbo_app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.List;

/**
 * 全局浮動語音助手：可拖動氣泡 + 底部快捷面板，適用於各功能頁面。
 */
public class FloatingVoiceAssistantOverlay {

    private static final String TAG = "FloatingVoiceOverlay";
    private static final int PERMISSION_REQUEST_RECORD_AUDIO = 3010;

    private final BaseAccessibleActivity activity;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private View rootOverlay;
    private View panel;
    private ImageButton bubble;
    private ImageButton expandButton;
    private ImageButton closePanelButton;
    private Button listenButton;
    private TextView statusText;
    private TextView resultText;

    private VoiceCommandManager voiceCommandManager;
    private VoiceAIAssistant aiAssistant;
    private boolean expanded;
    private boolean listening;
    private boolean attached;

    private float dragOffsetX;
    private float dragOffsetY;
    private float dragStartRawX;
    private float dragStartRawY;
    private boolean dragging;
    private static final float DRAG_THRESHOLD_PX = 12f;
    private Runnable pendingIntroRunnable;

    public FloatingVoiceAssistantOverlay(BaseAccessibleActivity activity) {
        this.activity = activity;
    }

    public void attach() {
        if (attached || activity.isFinishing()) {
            return;
        }

        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        LayoutInflater.from(activity).inflate(R.layout.layout_floating_voice_assistant, decor, true);

        rootOverlay = decor.findViewById(R.id.floatingVoiceRoot);
        panel = decor.findViewById(R.id.floatingVoicePanel);
        bubble = decor.findViewById(R.id.floatingVoiceBubble);
        expandButton = decor.findViewById(R.id.floatingExpandButton);
        closePanelButton = decor.findViewById(R.id.floatingClosePanelButton);
        listenButton = decor.findViewById(R.id.floatingListenButton);
        statusText = decor.findViewById(R.id.floatingStatusText);
        resultText = decor.findViewById(R.id.floatingResultText);

        voiceCommandManager = VoiceCommandManager.getInstance(activity);
        aiAssistant = new VoiceAIAssistant(activity);
        aiAssistant.setLanguage(activity.getCurrentLanguage());

        setupInteractions();
        applyWindowInsets();
        updateAccessibilityTexts();
        scheduleBubbleIntroAnnouncement();
        attached = true;
        Log.d(TAG, "attached to " + activity.getClass().getSimpleName());
    }

    /** 進入頁面後提示浮動掣位置（等頁面標題播完） */
    private void scheduleBubbleIntroAnnouncement() {
        if (rootOverlay == null) {
            return;
        }
        cancelPendingIntro();
        pendingIntroRunnable = () -> {
            if (attached && bubble != null && bubble.getVisibility() == View.VISIBLE && !listening) {
                speakAnnouncement(R.string.floating_voice_bubble_intro, false);
            }
            pendingIntroRunnable = null;
        };
        rootOverlay.postDelayed(pendingIntroRunnable, 2800);
    }

    private void cancelPendingIntro() {
        if (rootOverlay != null && pendingIntroRunnable != null) {
            rootOverlay.removeCallbacks(pendingIntroRunnable);
            pendingIntroRunnable = null;
        }
    }

    /** 停止語音播報，但保留排隊中的面板指引（若需要） */
    private void stopAllPlayback() {
        cancelPendingIntro();
        activity.ttsManager.stopSpeaking();
    }

    /** 打開面板時播報：開始掣同收起掣位置 */
    private void announcePanelGuide() {
        activity.ttsManager.forceInitialize();
        speakAnnouncement(R.string.floating_voice_panel_open_announce, true);
    }

    private void speakAnnouncement(int stringRes, boolean priority) {
        String text = activity.getString(stringRes);
        activity.ttsManager.speak(text, text, priority);
    }

    private void speakAnnouncementThen(int stringRes, Runnable after) {
        String text = activity.getString(stringRes);
        activity.ttsManager.speakThenRun(text, text, true, after);
    }

    private void updateAccessibilityTexts() {
        if (bubble != null) {
            bubble.setContentDescription(activity.getString(R.string.floating_voice_bubble_desc));
        }
        if (listenButton != null) {
            listenButton.setContentDescription(activity.getString(R.string.floating_voice_start_button_desc));
            listenButton.setText(activity.getString(R.string.floating_voice_start_button));
        }
        if (expandButton != null) {
            expandButton.setContentDescription(activity.getString(R.string.floating_voice_expand));
        }
        if (closePanelButton != null) {
            closePanelButton.setContentDescription(activity.getString(R.string.floating_voice_close_desc));
        }
        if (statusText != null && !listening) {
            statusText.setText(activity.getString(R.string.floating_voice_tap_to_speak));
        }
        TextView title = panel != null ? panel.findViewById(R.id.floatingPanelTitle) : null;
        if (title != null) {
            title.setText(activity.getString(R.string.floating_voice_title));
        }
    }

    private void applyWindowInsets() {
        if (panel == null || bubble == null) {
            return;
        }
        final int basePanelPadding = dp(16);
        final int baseMargin = dp(20);
        final int bubbleBaseMargin = dp(96);
        final boolean onHomePage = activity instanceof MainActivity;

        ViewCompat.setOnApplyWindowInsetsListener(rootOverlay, (view, insets) -> {
            int safeBottom = resolveSafeBottomInset(insets);
            if (onHomePage) {
                // 主頁底部有緊急求助條（約 110dp），整個面板再抬高
                safeBottom += dp(112);
            }

            panel.setPadding(
                    basePanelPadding,
                    basePanelPadding,
                    basePanelPadding,
                    basePanelPadding
            );

            ViewGroup.MarginLayoutParams panelLp = (ViewGroup.MarginLayoutParams) panel.getLayoutParams();
            panelLp.bottomMargin = baseMargin + safeBottom;
            panel.setLayoutParams(panelLp);

            ViewGroup.MarginLayoutParams bubbleLp = (ViewGroup.MarginLayoutParams) bubble.getLayoutParams();
            bubbleLp.bottomMargin = bubbleBaseMargin + safeBottom + dp(16);
            bubble.setLayoutParams(bubbleLp);

            panel.bringToFront();
            bubble.bringToFront();
            return insets;
        });

        rootOverlay.post(() -> ViewCompat.requestApplyInsets(rootOverlay));
        // Samsung One UI 有时稍晚才返回正确 inset，再补一次
        rootOverlay.postDelayed(() -> ViewCompat.requestApplyInsets(rootOverlay), 120);
    }

    /**
     * Samsung S20 / One UI 经常在 decor overlay 上回报 0，需结合系统 dimen 兜底。
     */
    private int resolveSafeBottomInset(WindowInsetsCompat insets) {
        int nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
        int gesture = insets.getInsets(WindowInsetsCompat.Type.systemGestures()).bottom;
        int mandatory = insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures()).bottom;
        int tappable = insets.getInsets(WindowInsetsCompat.Type.tappableElement()).bottom;

        int safe = Math.max(Math.max(nav, gesture), Math.max(mandatory, tappable));
        safe = Math.max(safe, getSystemDimenPx("navigation_bar_height", dp(48)));
        safe = Math.max(safe, getSystemDimenPx("navigation_bar_gesture_height", 0));

        if (isSamsungOneUi()) {
            // S20 全面屏手势条 + One UI 额外触控安全区
            safe += dp(28);
        }

        Log.d(TAG, "safeBottomInset=" + safe + " px, model=" + Build.MODEL);
        return safe;
    }

    private boolean isSamsungOneUi() {
        return Build.MANUFACTURER != null
                && Build.MANUFACTURER.toLowerCase(java.util.Locale.ROOT).contains("samsung");
    }

    private int getSystemDimenPx(String name, int fallbackPx) {
        int id = activity.getResources().getIdentifier(name, "dimen", "android");
        if (id > 0) {
            return activity.getResources().getDimensionPixelSize(id);
        }
        return fallbackPx;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    public void detach() {
        if (!attached) {
            return;
        }
        stopAllPlayback();
        stopListening();
        voiceCommandManager.setCommandListener(null);

        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        if (rootOverlay != null && rootOverlay.getParent() == decor) {
            decor.removeView(rootOverlay);
        }

        rootOverlay = null;
        attached = false;
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != PERMISSION_REQUEST_RECORD_AUDIO) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListening();
        } else {
            activity.announceError(localize("需要麥克風權限", "需要麦克风权限", "Microphone permission needed"));
            updateListeningUi(false);
        }
    }

    private void setupInteractions() {
        bubble.setOnClickListener(v -> {
            if (dragging) {
                return;
            }
            activity.vibrationManager.vibrateClick();
            if (expanded) {
                speakAnnouncement(R.string.floating_voice_close_announce, true);
                collapsePanel();
            } else {
                cancelPendingIntro();
                expandPanel(true);
            }
        });

        bubble.setOnLongClickListener(v -> {
            activity.vibrationManager.vibrateLongPress();
            stopAllPlayback();
            startListening();
            return true;
        });

        bubble.setOnTouchListener(this::handleBubbleDrag);

        closePanelButton.setOnClickListener(v -> {
            activity.vibrationManager.vibrateClick();
            speakAnnouncement(R.string.floating_voice_close_announce, true);
            collapsePanel();
        });

        expandButton.setOnClickListener(v -> {
            speakAnnouncement(R.string.floating_voice_expand_announce, true);
            openFullVoiceAssistant();
        });

        listenButton.setOnClickListener(v -> {
            activity.vibrationManager.vibrateClick();
            if (listening) {
                stopAllPlayback();
                stopListening();
            } else {
                stopAllPlayback();
                startListening();
            }
        });

        listenButton.setOnLongClickListener(v -> {
            activity.vibrationManager.vibrateLongPress();
            speakAnnouncement(R.string.floating_voice_expand_announce, true);
            openFullVoiceAssistant();
            return true;
        });
    }

    private boolean handleBubbleDrag(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragOffsetX = view.getX() - event.getRawX();
                dragOffsetY = view.getY() - event.getRawY();
                dragStartRawX = event.getRawX();
                dragStartRawY = event.getRawY();
                dragging = false;
                return false;
            case MotionEvent.ACTION_MOVE:
                float movedX = Math.abs(event.getRawX() - dragStartRawX);
                float movedY = Math.abs(event.getRawY() - dragStartRawY);
                if (movedX > DRAG_THRESHOLD_PX || movedY > DRAG_THRESHOLD_PX) {
                    dragging = true;
                    View parent = (View) view.getParent();
                    float newX = event.getRawX() + dragOffsetX;
                    float newY = event.getRawY() + dragOffsetY;
                    newX = Math.max(0, Math.min(newX, parent.getWidth() - view.getWidth()));
                    newY = Math.max(0, Math.min(newY, parent.getHeight() - view.getHeight()));
                    view.setX(newX);
                    view.setY(newY);
                    return true;
                }
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                boolean wasDragging = dragging;
                dragging = false;
                return wasDragging;
            default:
                return false;
        }
    }

    private void expandPanel() {
        expandPanel(true);
    }

    private void expandPanel(boolean announceGuide) {
        expanded = true;
        panel.setVisibility(View.VISIBLE);
        bubble.setVisibility(View.GONE);
        updateAccessibilityTexts();
        if (announceGuide && !listening && rootOverlay != null) {
            rootOverlay.postDelayed(this::announcePanelGuide, 250);
        }
    }

    private void collapsePanel() {
        expanded = false;
        panel.setVisibility(View.GONE);
        bubble.setVisibility(View.VISIBLE);
        stopListening();
    }

    private void openFullVoiceAssistant() {
        activity.vibrationManager.vibrateClick();
        Intent intent = new Intent(activity, VoiceCommandActivity.class);
        intent.putExtra("language", activity.getCurrentLanguage());
        activity.startActivity(intent);
    }

    private void startListening() {
        if (listening) {
            return;
        }
        stopAllPlayback();
        if (!hasRecordPermission()) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    PERMISSION_REQUEST_RECORD_AUDIO);
            return;
        }

        expandPanel(false);
        voiceCommandManager.setLanguage(activity.getCurrentLanguage());
        voiceCommandManager.setCommandListener(createListener());
        listening = true;
        updateListeningUi(true);
        voiceCommandManager.startListening();
    }

    private void stopListening() {
        if (!listening) {
            return;
        }
        listening = false;
        voiceCommandManager.stopListening();
        updateListeningUi(false);
    }

    private boolean hasRecordPermission() {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private VoiceCommandManager.ExtendedVoiceCommandListener createListener() {
        return new VoiceCommandManager.ExtendedVoiceCommandListener() {
            @Override
            public void onTravelDestinationRecognized(TravelParseResult parseResult) {
                mainHandler.post(() -> {
                    if (parseResult == null || !parseResult.hasDestination()) {
                        return;
                    }
                    activity.vibrationManager.vibrateSuccess();
                    showResult(localize("目的地：", "目的地：", "Destination: ") + parseResult.destination);
                    Intent intent = new Intent(activity, StartTravelActivity.class);
                    intent.putExtra("language", activity.getCurrentLanguage());
                    intent.putExtra("destination", parseResult.destination);
                    activity.startActivity(intent);
                });
            }

            @Override
            public void onCommandRecognized(String command, String originalText) {
                mainHandler.post(() -> {
                    if ("continuous_commands".equals(command)) {
                        return;
                    }
                    activity.vibrationManager.vibrateSuccess();
                    showResult(localize("識別到：", "识别到：", "Recognized: ") + originalText);
                    VoiceNavigationHelper.execute(activity, command, activity.getCurrentLanguage());
                    stopListening();
                });
            }

            @Override
            public void onContinuousCommandsRecognized(List<VoiceCommandManager.CommandPair> commands, String originalText) {
                mainHandler.post(() -> {
                    if (commands == null || commands.isEmpty()) {
                        return;
                    }
                    activity.vibrationManager.vibrateSuccess();
                    showResult(localize("識別到：", "识别到：", "Recognized: ") + originalText);
                    VoiceCommandManager.CommandPair first = commands.get(0);
                    VoiceNavigationHelper.execute(activity, first.command, activity.getCurrentLanguage());
                    stopListening();
                });
            }

            @Override
            public void onListeningStarted() {
                mainHandler.post(() -> updateListeningUi(true));
            }

            @Override
            public void onListeningStopped() {
                mainHandler.post(() -> updateListeningUi(false));
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    activity.vibrationManager.vibrateError();
                    statusText.setText(error);
                    updateListeningUi(false);
                });
            }

            @Override
            public void onPartialResult(String partialText) {
                mainHandler.post(() -> {
                    statusText.setText(localize("識別中：", "识别中：", "Recognizing: ") + partialText);
                });
            }

            @Override
            public void onTextRecognized(String text) {
                mainHandler.post(() -> {
                    activity.vibrationManager.vibrateSuccess();
                    aiAssistant.setLanguage(activity.getCurrentLanguage());

                    String command = voiceCommandManager.checkIfCommand(text);
                    if (command != null) {
                        VoiceNavigationHelper.execute(activity, command, activity.getCurrentLanguage());
                        showResult(localize("識別到：", "识别到：", "Recognized: ") + text);
                        stopListening();
                        return;
                    }

                    showResult(localize("您：", "您：", "You: ") + text);
                    aiAssistant.processInputAsync(text, response -> mainHandler.post(() -> {
                        if (response != null && response.response != null && !response.response.isEmpty()) {
                            resultText.setText(localize("助手：", "助手：", "Assistant: ") + response.response);
                            activity.announceInfo(response.response);
                        }
                        stopListening();
                    }));
                });
            }
        };
    }

    private void updateListeningUi(boolean active) {
        listening = active;
        if (statusText == null || listenButton == null) {
            return;
        }
        if (active) {
            statusText.setText(localize("正在聆聽…", "正在聆听…", "Listening…"));
            listenButton.setText(activity.getString(R.string.floating_voice_stop_button));
            listenButton.setContentDescription(activity.getString(R.string.floating_voice_stop_button));
        } else {
            statusText.setText(activity.getString(R.string.floating_voice_tap_to_speak));
            listenButton.setText(activity.getString(R.string.floating_voice_start_button));
            listenButton.setContentDescription(activity.getString(R.string.floating_voice_start_button_desc));
        }
    }

    private void showResult(String text) {
        if (resultText != null) {
            resultText.setText(text);
        }
    }

    /** 語言切換後刷新浮動助手文案（若頁面內切換語言時調用） */
    public void refreshLanguage() {
        if (!attached) {
            return;
        }
        aiAssistant.setLanguage(activity.getCurrentLanguage());
        updateAccessibilityTexts();
        updateListeningUi(listening);
    }

    private String localize(String cantonese, String mandarin, String english) {
        String language = activity.getCurrentLanguage();
        if ("english".equals(language)) {
            return english;
        }
        if ("mandarin".equals(language)) {
            return mandarin;
        }
        return cantonese;
    }
}
