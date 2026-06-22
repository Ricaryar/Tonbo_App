package com.example.tonbo_app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 開始出行頁面：語音目的地輸入 → 周邊 POI 前三候選 → 語音/按鈕選擇 → 導航
 */
public class StartTravelActivity extends BaseAccessibleActivity {
    private static final String TAG = "StartTravel";
    private static final int PERMISSION_REQUEST_RECORD_AUDIO = 100;
    private static final int PERMISSION_REQUEST_LOCATION = 101;
    private static final long SELECTION_TIMEOUT_MS = 8000L;
    private static final double INVALID_MATCH_THRESHOLD = 0.35d;
    private static final int MIN_CANDIDATE_COUNT = 1;

    /** 英文語音選「第幾個」：first / number 1 / option one 等 */
    private static final Pattern PAT_EN_SELECT_FIRST = Pattern.compile(
            "\\b(first|1st)\\b|\\b(number|option)\\s*1\\b|#\\s*1\\b|\\bnumber\\s+one\\b|\\boption\\s+one\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_EN_SELECT_SECOND = Pattern.compile(
            "\\b(second|2nd)\\b|\\b(number|option)\\s*2\\b|#\\s*2\\b|\\bnumber\\s+two\\b|\\boption\\s+two\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_EN_SELECT_THIRD = Pattern.compile(
            "\\b(third|3rd)\\b|\\b(number|option)\\s*3\\b|#\\s*3\\b|\\bnumber\\s+three\\b|\\boption\\s+three\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_EN_USE_TEXT_NAV = Pattern.compile(
            "\\b(text|input|manual|typed)\\b|use\\s+(the\\s+)?typed\\s+text|typed\\s+text|type\\s+above|what\\s+i\\s+typed",
            Pattern.CASE_INSENSITIVE);

    private TextView promptText;
    private TextView statusText;
    private EditText recognitionEdit;
    private Button confirmButton;
    private Button micButton;
    private LinearLayout poiChoiceContainer;
    private TextView poiChoiceTitle;
    private Button btnPoi1;
    private Button btnPoi2;
    private Button btnPoi3;
    private Button btnPoiUseText;

    private VoiceCommandManager voiceCommandManager;
    private LocationService locationService;
    private String pendingDestination = null;
    private boolean isListening = false;
    private boolean hasAnnouncedPageTitle = false;
    private boolean awaitingCandidateSelection = false;
    private boolean isNavigating = false;
    private boolean pendingResolveCandidates = false;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable selectionTimeoutRunnable;
    private final List<CandidateOption> currentCandidates = new ArrayList<>();
    private String currentKeyword = "";
    private String lastLocationText = "";

    private static class CandidateOption {
        final LocationService.POICandidate poi;
        final boolean likelyInvalid;

        CandidateOption(LocationService.POICandidate poi, boolean likelyInvalid) {
            this.poi = poi;
            this.likelyInvalid = likelyInvalid;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_travel);

        promptText = findViewById(R.id.prompt_text);
        statusText = findViewById(R.id.status_text);
        recognitionEdit = findViewById(R.id.recognition_edit);
        confirmButton = findViewById(R.id.confirm_button);
        micButton = findViewById(R.id.mic_button);
        poiChoiceContainer = findViewById(R.id.poi_choice_container);
        poiChoiceTitle = findViewById(R.id.poi_choice_title);
        btnPoi1 = findViewById(R.id.btn_poi_1);
        btnPoi2 = findViewById(R.id.btn_poi_2);
        btnPoi3 = findViewById(R.id.btn_poi_3);
        btnPoiUseText = findViewById(R.id.btn_poi_use_text);

        android.widget.ImageButton backButton = findViewById(R.id.back_button);
        if (backButton != null) {
            backButton.setOnClickListener(v -> {
                vibrationManager.vibrateClick();
                if (ttsManager != null) ttsManager.stopSpeaking();
                if (isListening) {
                    voiceCommandManager.stopListening();
                    isListening = false;
                }
                clearSelectionTimeout();
                finish();
            });
        }

        voiceCommandManager = VoiceCommandManager.getInstance(this);
        voiceCommandManager.setLanguage(currentLanguage);
        locationService = new LocationService(this);
        setupVoiceListener();
        updateLanguageUI();

        String destination = getIntent() != null ? getIntent().getStringExtra("destination") : null;
        if (!TextUtils.isEmpty(destination)) {
            recognitionEdit.setText(destination);
            recognitionEdit.setSelection(destination.length());
            statusText.setText(getLocalizedString("start_travel_recognition_ok"));
        }

        micButton.setOnClickListener(v -> {
            vibrationManager.vibrateClick();
            if (isListening) {
                voiceCommandManager.stopListening();
                isListening = false;
                updateMicUI(false);
            } else {
                startListening();
            }
        });

        confirmButton.setOnClickListener(v -> {
            vibrationManager.vibrateClick();
            if (isNavigating) return;
            String dest = recognitionEdit != null ? recognitionEdit.getText().toString().trim() : "";
            if (TextUtils.isEmpty(dest)) {
                announceInfo(getLocalizedString("please_enter_destination"));
                vibrationManager.vibrateError();
                return;
            }
            resolveTopCandidates(dest);
        });

        setupCandidateButtons();
        hideCandidateButtons();
    }

    @Override
    protected void onDestroy() {
        if (voiceCommandManager != null) voiceCommandManager.stopListening();
        clearSelectionTimeout();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (ttsManager != null) ttsManager.stopSpeaking();
        if (isListening) {
            voiceCommandManager.stopListening();
            isListening = false;
        }
        clearSelectionTimeout();
    }

    @Override
    protected void announcePageTitle() {
        if (!hasAnnouncedPageTitle && ttsManager != null) {
            hasAnnouncedPageTitle = true;
            String intro = getLocalizedString("start_travel_tts_intro");
            ttsManager.speakThenRun(intro, intro, true, () -> {
                if (isFinishing()) return;
                startListening();
            });
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void setupVoiceListener() {
        voiceCommandManager.setCommandListener(new VoiceCommandManager.ExtendedVoiceCommandListener() {
            @Override
            public void onTravelDestinationRecognized(TravelParseResult parseResult) {
                runOnUiThread(() -> {
                    if (parseResult == null || !parseResult.hasDestination()) return;
                    vibrationManager.vibrateSuccess();
                    isListening = false;
                    updateMicUI(false);
                    String raw = parseResult.destination;
                    statusText.setText(getLocalizedString("start_travel_recognition_ok"));
                    recognitionEdit.setText(raw);
                    recognitionEdit.setSelection(raw.length());
                    resolveTopCandidates(raw);
                });
            }

            @Override
            public void onCommandRecognized(String command, String originalText) {
                runOnUiThread(() -> {
                    if (isNavigating) return;
                    if (awaitingCandidateSelection) {
                        trySelectCandidateFromText(originalText);
                        return;
                    }
                    isListening = false;
                    updateMicUI(false);
                    ttsSpeak(getLocalizedString("start_travel_retry_dest"));
                });
            }

            @Override
            public void onTextRecognized(String text) {
                runOnUiThread(() -> {
                    if (isNavigating) return;
                    if (awaitingCandidateSelection) {
                        trySelectCandidateFromText(text);
                        return;
                    }
                    TravelParseResult parsed = voiceCommandManager.parseDestinationFromTravelPhrase(text);
                    if (parsed != null && parsed.hasDestination()) {
                        onTravelDestinationRecognized(parsed);
                        return;
                    }
                    // 與手動輸入一致：未帶「我要去/去…」等前綴時，仍將整句識別結果作為目的地關鍵詞（否則會誤報「沒聽清」）
                    String trimmed = text != null ? text.trim() : "";
                    if (!TextUtils.isEmpty(trimmed)) {
                        vibrationManager.vibrateSuccess();
                        isListening = false;
                        updateMicUI(false);
                        statusText.setText(getLocalizedString("start_travel_recognition_ok"));
                        recognitionEdit.setText(trimmed);
                        recognitionEdit.setSelection(trimmed.length());
                        resolveTopCandidates(trimmed);
                        return;
                    }
                    isListening = false;
                    updateMicUI(false);
                    ttsSpeak(getLocalizedString("start_travel_retry_dest"));
                });
            }

            @Override
            public void onListeningStarted() {
                runOnUiThread(() -> {
                    if (isNavigating) return;
                    isListening = true;
                    updateMicUI(true);
                    statusText.setText(getLocalizedString("voice_status_listening"));
                });
            }

            @Override
            public void onListeningStopped() {
                runOnUiThread(() -> {
                    if (isNavigating) return;
                    isListening = false;
                    updateMicUI(false);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    if (isNavigating) return;
                    isListening = false;
                    updateMicUI(false);
                    ttsSpeak(getLocalizedString("start_travel_retry_dest"));
                });
            }

            @Override
            public void onPartialResult(String partialText) {
                runOnUiThread(() -> {
                    if (isNavigating) return;
                    if (!TextUtils.isEmpty(partialText) && statusText != null) {
                        statusText.setText(String.format(getLocalizedString("recognizing_partial_fmt"), partialText));
                    }
                });
            }

            @Override
            public void onContinuousCommandsRecognized(List<VoiceCommandManager.CommandPair> commands, String originalText) {
                runOnUiThread(() -> {
                    if (isNavigating) return;
                    isListening = false;
                    updateMicUI(false);
                    ttsSpeak(getLocalizedString("start_travel_retry_dest"));
                });
            }
        });
    }

    private void startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ttsSpeak(getLocalizedString("start_travel_need_mic_permission"));
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_REQUEST_RECORD_AUDIO);
            return;
        }
        if (ttsManager != null) ttsManager.stopSpeaking();
        voiceCommandManager.startListening();
    }

    private void updateMicUI(boolean listening) {
        if (micButton == null) return;
        if (listening) {
            micButton.setText("⏸️");
            micButton.setContentDescription(getLocalizedString("mic_stop_listening"));
        } else {
            micButton.setText("🎤");
            micButton.setContentDescription(getLocalizedString("mic_start_speaking"));
        }
    }

    private void ttsSpeak(String text) {
        if (ttsManager != null) ttsManager.speak(text, text, true);
    }

    private void setupCandidateButtons() {
        if (btnPoi1 != null) {
            btnPoi1.setOnClickListener(v -> {
                vibrationManager.vibrateClick();
                onCandidateSelected(0);
            });
        }
        if (btnPoi2 != null) {
            btnPoi2.setOnClickListener(v -> {
                vibrationManager.vibrateClick();
                onCandidateSelected(1);
            });
        }
        if (btnPoi3 != null) {
            btnPoi3.setOnClickListener(v -> {
                vibrationManager.vibrateClick();
                onCandidateSelected(2);
            });
        }
    }

    private void resolveTopCandidates(String keyword) {
        if (TextUtils.isEmpty(keyword) || isNavigating) return;
        hideCandidateButtons();
        currentKeyword = keyword.trim();
        if (!isSystemLocationEnabled()) {
            handleCandidateResolveFailed(getLocalizedString("candidate_fail_location_off"));
            return;
        }
        if (!hasLocationPermission()) {
            pendingDestination = currentKeyword;
            pendingResolveCandidates = true;
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    PERMISSION_REQUEST_LOCATION);
            return;
        }

        updateStatus(getLocalizedString("voice_status_processing"));
        if (locationService == null) {
            performNavigationWithGuards(currentKeyword);
            return;
        }

        locationService.getCurrentLocationShortText((shortText, locError) -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed() || isNavigating) return;
            if (TextUtils.isEmpty(shortText)) {
                lastLocationText = getLocalizedString("candidate_nearby_fallback");
            } else {
                lastLocationText = shortText;
            }

            locationService.searchNearbyPOIs(currentKeyword, (pois, poiError) -> runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || isNavigating) return;
                if (poiError != null || pois == null || pois.isEmpty()) {
                    handleCandidateResolveFailed(poiError);
                    return;
                }
                currentCandidates.clear();
                int limit = Math.min(3, pois.size());
                for (int i = 0; i < limit; i++) {
                    LocationService.POICandidate candidate = pois.get(i);
                    if (candidate != null && !TextUtils.isEmpty(candidate.name)) {
                        boolean likelyInvalid = isLikelyInvalidCandidate(currentKeyword, candidate.name);
                        currentCandidates.add(new CandidateOption(candidate, likelyInvalid));
                    }
                }
                if (currentCandidates.size() < MIN_CANDIDATE_COUNT) {
                    handleCandidateResolveFailed(getLocalizedString("candidate_fail_not_enough"));
                    return;
                }
                showCandidateButtonsAndPrompt(currentKeyword);
            }));
        }));
    }

    private void handleCandidateResolveFailed(String reason) {
        if (isFinishing() || isDestroyed()) return;
        Log.w(TAG, "候選匹配失敗: " + (reason == null ? "unknown" : reason));
        awaitingCandidateSelection = false;
        clearSelectionTimeout();
        if (poiChoiceContainer != null) {
            poiChoiceContainer.setVisibility(View.GONE);
        }
        if (voiceCommandManager != null) {
            voiceCommandManager.stopListening();
        }
        isListening = false;
        updateMicUI(false);
        updateStatus(getLocalizedString("candidate_resolve_failed_status"));
        ttsSpeak(getLocalizedString("candidate_resolve_failed_tts"));
    }

    private boolean isSystemLocationEnabled() {
        try {
            LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (locationManager == null) return false;
            return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception e) {
            Log.w(TAG, "檢查定位開關失敗: " + e.getMessage());
            return false;
        }
    }

    private void showCandidateButtonsAndPrompt(String keyword) {
        if (poiChoiceContainer == null) return;
        awaitingCandidateSelection = true;
        updateStatus(getLocalizedString("start_travel_recognition_ok"));
        if (poiChoiceTitle != null) {
            poiChoiceTitle.setText(getLocalizedString("poi_pick_title"));
        }
        bindCandidateButtonText();
        poiChoiceContainer.setVisibility(View.VISIBLE);
        if (btnPoiUseText != null) {
            btnPoiUseText.setVisibility(View.VISIBLE);
            btnPoiUseText.setText(getLocalizedString("poi_use_text_nav"));
            btnPoiUseText.setOnClickListener(v -> {
                vibrationManager.vibrateClick();
                onUseTextNavigation();
            });
        }
        if (ttsManager != null) ttsManager.stopSpeaking();
        String prompt = buildCandidatePrompt(keyword);
        if (ttsManager != null) {
            ttsManager.speakThenRun(prompt, prompt, true, this::restartSelectionListeningCycle);
        } else {
            restartSelectionListeningCycle();
        }
    }

    private void bindCandidateButtonText() {
        setCandidateButton(btnPoi1, 0, getLocalizedString("candidate_first"));
        setCandidateButton(btnPoi2, 1, getLocalizedString("candidate_second"));
        setCandidateButton(btnPoi3, 2, getLocalizedString("candidate_third"));
    }

    private void setCandidateButton(Button button, int index, String fallbackTitle) {
        if (button == null) return;
        if (index < currentCandidates.size() && currentCandidates.get(index) != null && currentCandidates.get(index).poi != null) {
            CandidateOption option = currentCandidates.get(index);
            LocationService.POICandidate c = option.poi;
            String address = safeAddress(c.address);
            String invalidSuffix = option.likelyInvalid ? getLocalizedString("candidate_likely_invalid_suffix") : "";
            String label = String.format(getLocalizedString("candidate_item_label_fmt"),
                    fallbackTitle, safeText(c.name), address, invalidSuffix);
            button.setText(label);
            button.setVisibility(View.VISIBLE);
        } else {
            button.setVisibility(View.GONE);
        }
    }

    /** 播報用：盡量短，只念序號與名稱 */
    private String buildCandidatePrompt(String keyword) {
        StringBuilder builder = new StringBuilder();
        builder.append(String.format(getLocalizedString("candidate_prompt_header_fmt"), lastLocationText));
        for (int i = 0; i < currentCandidates.size(); i++) {
            CandidateOption option = currentCandidates.get(i);
            LocationService.POICandidate c = option != null ? option.poi : null;
            String invalidSuffix = (option != null && option.likelyInvalid)
                    ? getLocalizedString("candidate_likely_invalid_suffix_tts")
                    : "";
            builder.append(String.format(getLocalizedString("candidate_prompt_item_tts_fmt"),
                    getOrdinalText(i),
                    safeText(c != null ? c.name : keyword),
                    invalidSuffix));
        }
        builder.append(getLocalizedString("candidate_prompt_action_tts"));
        return builder.toString();
    }

    private void restartSelectionListeningCycle() {
        if (!awaitingCandidateSelection || isNavigating) return;
        clearSelectionTimeout();
        if (isListening && voiceCommandManager != null) {
            voiceCommandManager.stopListening();
            isListening = false;
            updateMicUI(false);
        }
        startListening();
        selectionTimeoutRunnable = () -> {
            if (!awaitingCandidateSelection || isNavigating || isFinishing() || isDestroyed()) return;
            if (voiceCommandManager != null) voiceCommandManager.stopListening();
            isListening = false;
            updateMicUI(false);
            ttsSpeak(getLocalizedString("candidate_timeout_retry"));
            uiHandler.postDelayed(this::restartSelectionListeningCycle, 1200);
        };
        uiHandler.postDelayed(selectionTimeoutRunnable, SELECTION_TIMEOUT_MS);
    }

    private void clearSelectionTimeout() {
        if (selectionTimeoutRunnable != null) {
            uiHandler.removeCallbacks(selectionTimeoutRunnable);
            selectionTimeoutRunnable = null;
        }
    }

    private void hideCandidateButtons() {
        awaitingCandidateSelection = false;
        clearSelectionTimeout();
        if (poiChoiceContainer != null) {
            poiChoiceContainer.setVisibility(View.GONE);
        }
        for (Button b : new Button[]{btnPoi1, btnPoi2, btnPoi3, btnPoiUseText}) {
            if (b != null) {
                b.setVisibility(View.GONE);
            }
        }
    }

    private void trySelectCandidateFromText(String text) {
        if (wantsTextNavigation(text)) {
            onUseTextNavigation();
            return;
        }
        int index = parseSelectionIndex(text);
        if (index >= 0) {
            onCandidateSelected(index);
        }
    }

    private boolean wantsTextNavigation(String text) {
        if (TextUtils.isEmpty(text)) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        if (PAT_EN_USE_TEXT_NAV.matcher(lower).find()) return true;
        String t = text.replace(" ", "");
        return t.contains("文字") || t.contains("輸入") || t.contains("输入") || t.contains("上面")
                || t.contains("手動") || t.contains("手动");
    }

    private int parseSelectionIndex(String text) {
        if (TextUtils.isEmpty(text)) return -1;
        String normalized = text.replace(" ", "");
        if (normalized.contains("第一") || normalized.contains("第1") || normalized.contains("一个") || normalized.contains("一個"))
            return 0;
        if (normalized.contains("第二") || normalized.contains("第2") || normalized.contains("二个") || normalized.contains("二個"))
            return 1;
        if (normalized.contains("第三") || normalized.contains("第3") || normalized.contains("三个") || normalized.contains("三個"))
            return 2;
        String lower = text.toLowerCase(Locale.ROOT);
        if (PAT_EN_SELECT_FIRST.matcher(lower).find()) return 0;
        if (PAT_EN_SELECT_SECOND.matcher(lower).find()) return 1;
        if (PAT_EN_SELECT_THIRD.matcher(lower).find()) return 2;
        return -1;
    }

    private void onUseTextNavigation() {
        if (isNavigating || !awaitingCandidateSelection) return;
        String keyword = !TextUtils.isEmpty(currentKeyword) ? currentKeyword
                : (recognitionEdit != null ? recognitionEdit.getText().toString().trim() : "");
        if (TextUtils.isEmpty(keyword)) return;

        isNavigating = true;
        awaitingCandidateSelection = false;
        clearSelectionTimeout();
        if (voiceCommandManager != null) voiceCommandManager.stopListening();
        isListening = false;
        updateMicUI(false);
        if (ttsManager != null) ttsManager.stopSpeaking();
        if (poiChoiceContainer != null) poiChoiceContainer.setVisibility(View.GONE);
        updateStatus(getLocalizedString("voice_status_processing"));
        performNavigationWithGuards(keyword);
    }

    private void onCandidateSelected(int index) {
        if (isNavigating || !awaitingCandidateSelection) return;
        if (index < 0 || index >= currentCandidates.size()) return;
        CandidateOption option = currentCandidates.get(index);
        LocationService.POICandidate selected = option != null ? option.poi : null;
        if (selected == null || TextUtils.isEmpty(selected.name)) return;

        isNavigating = true;
        awaitingCandidateSelection = false;
        clearSelectionTimeout();
        if (voiceCommandManager != null) {
            voiceCommandManager.stopListening();
        }
        isListening = false;
        updateMicUI(false);
        if (ttsManager != null) ttsManager.stopSpeaking();
        if (poiChoiceContainer != null) poiChoiceContainer.setVisibility(View.GONE);
        updateStatus(getLocalizedString("voice_status_processing"));

        String resolved = safeText(selected.name) + " " + safeAddress(selected.address);
        performNavigationWithGuards(resolved.trim());
    }

    private String safeAddress(String value) {
        if (TextUtils.isEmpty(value)) return getLocalizedString("candidate_unknown_address");
        return value;
    }

    private String safeText(String value) {
        return TextUtils.isEmpty(value) ? "" : value;
    }

    private String getOrdinalText(int index) {
        if (index == 0) return getLocalizedString("candidate_first");
        if (index == 1) return getLocalizedString("candidate_second");
        return getLocalizedString("candidate_third");
    }

    private boolean isLikelyInvalidCandidate(String keyword, String candidateName) {
        if (TextUtils.isEmpty(keyword) || TextUtils.isEmpty(candidateName)) return true;
        String k = normalizeForMatch(keyword);
        String n = normalizeForMatch(candidateName);
        if (TextUtils.isEmpty(k) || TextUtils.isEmpty(n)) return true;
        if (n.contains(k) || k.contains(n)) {
            return false;
        }
        int lcs = longestCommonSubsequenceLength(k, n);
        double score = (double) lcs / (double) Math.max(k.length(), n.length());
        return score < INVALID_MATCH_THRESHOLD;
    }

    private String normalizeForMatch(String text) {
        if (text == null) return "";
        String normalized = text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        normalized = normalized.replace("（", "").replace("）", "")
                .replace("(", "").replace(")", "")
                .replace("·", "");
        return normalized;
    }

    private int longestCommonSubsequenceLength(String a, String b) {
        if (TextUtils.isEmpty(a) || TextUtils.isEmpty(b)) return 0;
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }
        return dp[a.length()][b.length()];
    }

    private void performNavigationWithGuards(String destination) {
        hideCandidateButtons();
        if (!hasLocationPermission()) {
            pendingDestination = destination;
            pendingResolveCandidates = false;
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    PERMISSION_REQUEST_LOCATION);
            return;
        }

        locationService.checkCrossRegion(destination, (isCrossRegion, error) -> {
            runOnUiThread(() -> {
                if (error != null) {
                    doStartNavigation(destination);
                    return;
                }
                if (isCrossRegion) {
                    ttsSpeak(getLocalizedString("start_travel_cross_region"));
                    statusText.setText(getLocalizedString("start_travel_cross_region_status"));
                    isNavigating = false;
                    return;
                }

                locationService.searchNearbyPOIs(destination, (pois, poiError) -> {
                    runOnUiThread(() -> {
                        if (poiError != null || pois == null || pois.isEmpty()) {
                            doStartNavigation(destination);
                            return;
                        }
                        LocationService.POICandidate nearest = pois.get(0);
                        String resolved = nearest.name + (nearest.address != null && !nearest.address.isEmpty() ? " " + nearest.address : "");
                        if (pois.size() > 1) {
                            ttsSpeak(String.format(getLocalizedString("start_travel_nav_nearest_fmt"), destination));
                        }
                        doStartNavigation(resolved);
                    });
                });
            });
        });
    }

    private void doStartNavigation(String destination) {
        if (isFinishing() || isDestroyed()) return;
        Intent intent = new Intent(this, NavigationActivity.class);
        intent.putExtra("destination", destination);
        intent.putExtra("language", currentLanguage != null ? currentLanguage : LocaleManager.getInstance(this).getCurrentLanguage());
        startActivity(intent);
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_RECORD_AUDIO && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListening();
        }
        if (requestCode == PERMISSION_REQUEST_LOCATION && grantResults.length > 0) {
            boolean granted = (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    || (grantResults.length > 1 && grantResults[1] == PackageManager.PERMISSION_GRANTED);
            if (granted && pendingDestination != null) {
                if (pendingResolveCandidates) {
                    resolveTopCandidates(pendingDestination);
                } else {
                    performNavigationWithGuards(pendingDestination);
                }
                pendingResolveCandidates = false;
                pendingDestination = null;
            } else if (!granted) {
                pendingResolveCandidates = false;
                pendingDestination = null;
                isNavigating = false;
                announceInfo(getLocalizedString("location_permission_denied_nav"));
                vibrationManager.vibrateError();
            }
        }
    }

    private void updateStatus(String status) {
        if (statusText != null) statusText.setText(status);
    }

    private void updateLanguageUI() {
        promptText.setText(getLocalizedString("say_destination_prompt"));
        statusText.setText(getLocalizedString("voice_status_ready"));
        TextView pageTitle = findViewById(R.id.page_title);
        if (pageTitle != null) {
            pageTitle.setText(getLocalizedString("start_travel_page_title"));
        }
        if (confirmButton != null) {
            confirmButton.setText(getLocalizedString("confirm_depart"));
        }
        updateMicUI(isListening);
    }

    private String getLocalizedString(String key) {
        String lang = LocaleManager.getInstance(this).getCurrentLanguage();
        boolean en = "english".equals(lang);
        boolean mandarin = "mandarin".equals(lang);
        switch (key) {
            case "start_travel_page_title":
                if (en) return "Start trip";
                return mandarin ? "开始出行" : "開始出行";
            case "start_travel_tts_intro":
                if (en) return "Start trip. Say your destination.";
                return mandarin ? "开始出行，请说出目的地。" : "開始出行，請講出目的地。";
            case "start_travel_recognition_ok":
                if (en) return "Recognized";
                return mandarin ? "识别成功" : "識別成功";
            case "start_travel_retry_dest":
                if (en) return "Could not hear the destination. Please say it again.";
                return mandarin ? "没听清目的地，请再说一次。" : "未聽清目的地，請再講一次。";
            case "start_travel_need_mic_permission":
                if (en) return "Please allow microphone access.";
                return mandarin ? "请先允许使用麦克风。" : "請先允許使用咪高峰。";
            case "mic_stop_listening":
                if (en) return "Stop listening";
                return mandarin ? "停止聆听" : "停止聆聽";
            case "mic_start_speaking":
                if (en) return "Start speaking";
                return mandarin ? "开始说话" : "開始說話";
            case "recognizing_partial_fmt":
                if (en) return "Recognizing: %s";
                return mandarin ? "识别中：%s" : "識別中：%s";
            case "candidate_nearby_fallback":
                if (en) return "nearby";
                return mandarin ? "附近" : "附近";
            case "candidate_fail_location_off":
                if (en) return "Location off";
                return mandarin ? "定位未开" : "定位未開";
            case "candidate_fail_not_enough":
                if (en) return "Not enough results";
                return mandarin ? "候选不足" : "候選不足";
            case "candidate_resolve_failed_status":
                if (en) return "No nearby place found. Try another phrase or check location.";
                return mandarin ? "未找到合适地点，请换个说法或检查定位" : "未找到合適地點，請換個說法或檢查定位";
            case "candidate_resolve_failed_tts":
                if (en) return "No nearby place found. Say it again or tap confirm to retry.";
                return mandarin ? "没找到附近地点，请再说一次或点击确认重试。" : "未搵到附近地點，請再講一次或撳確認重試。";
            case "candidate_first":
                if (en) return "First";
                return mandarin ? "第一个" : "第一個";
            case "candidate_second":
                if (en) return "Second";
                return mandarin ? "第二个" : "第二個";
            case "candidate_third":
                if (en) return "Third";
                return mandarin ? "第三个" : "第三個";
            case "candidate_likely_invalid_suffix":
                if (en) return " (may be inaccurate)";
                return mandarin ? "（可能不准）" : "（可能唔準）";
            case "candidate_likely_invalid_suffix_tts":
                if (en) return ", may be inaccurate";
                return mandarin ? "，可能不准" : "，可能唔準";
            case "candidate_item_label_fmt":
                if (en) return "%1$s: %2$s, %3$s%4$s";
                return mandarin ? "%1$s：%2$s，%3$s%4$s" : "%1$s：%2$s，%3$s%4$s";
            case "candidate_prompt_header_fmt":
                if (en) return "Near %1$s, ";
                return mandarin ? "在%1$s，" : "喺%1$s，";
            case "candidate_prompt_item_tts_fmt":
                if (en) return "%1$s, %2$s%3$s. ";
                return mandarin ? "%1$s，%2$s%3$s。" : "%1$s，%2$s%3$s。";
            case "candidate_prompt_action_tts":
                if (en) return "Say first, second, or third, or say use typed text.";
                return mandarin ? "请说第一个、第二个或第三个，也可以说用输入文字。" : "請講第一個、第二個或第三個，亦可以講用輸入文字。";
            case "candidate_timeout_retry":
                if (en) return "Timed out. Say which option.";
                return mandarin ? "超时了，请说第几个。" : "超時喇，請講第幾個。";
            case "candidate_unknown_address":
                if (en) return "Address unknown";
                return mandarin ? "地址未知" : "地址未知";
            case "start_travel_cross_region":
                if (en) return "Your location and destination are in different regions. Cross-border navigation is not supported.";
                return mandarin ? "当前位置与目的地不在同一地区，暂不支持跨境导航。" : "目前位置同目的地唔同地區，暫唔支援跨境導航。";
            case "start_travel_cross_region_status":
                if (en) return "Cross-border navigation not supported";
                return mandarin ? "暂不支持跨境导航" : "暫唔支援跨境導航";
            case "start_travel_nav_nearest_fmt":
                if (en) return "Multiple matches. Navigating to the nearest for: %s";
                return mandarin ? "多个结果，先按最近的为您导航：%s" : "多個結果，先按最近嘅為您導航：%s";
            case "location_permission_denied_nav":
                if (en) return "Location permission denied. Cannot start navigation.";
                return mandarin ? "未授予位置权限，无法导航。" : "未授予位置權限，無法導航。";
            case "say_destination_prompt":
                if (en) return "Say where you want to go";
                return mandarin ? "请说出你要去的位置" : "請講出你要去嘅位置";
            case "voice_status_ready":
                if (en) return "Ready";
                return mandarin ? "就绪" : "就緒";
            case "voice_status_listening":
                if (en) return "Listening...";
                return mandarin ? "正在聆听..." : "正在聆聽...";
            case "voice_status_processing":
                if (en) return "Processing...";
                return mandarin ? "处理中..." : "處理中...";
            case "confirm_depart":
                if (en) return "Confirm";
                return mandarin ? "确认出发" : "確認出發";
            case "please_enter_destination":
                if (en) return "Please enter a destination";
                return mandarin ? "请输入目的地" : "請輸入目的地";
            case "voice_status_recognized":
                if (en) return "Recognized";
                return mandarin ? "已识别" : "已識別";
            case "location_permission_denied":
                if (en) return "Location permission denied. Cannot start navigation.";
                return mandarin ? "位置权限被拒绝，无法开始导航" : "位置權限被拒絕，無法開始導航";
            case "voice_status_getting_location":
                if (en) return "Getting location...";
                return mandarin ? "正在获取位置..." : "正在獲取位置...";
            case "poi_pick_title":
                if (en) return "Choose a place: first, second, or third";
                return mandarin ? "请选择目的地（第一个、第二个、第三个）" : "請選擇目的地（第一個、第二個、第三個）";
            case "poi_use_text_nav":
                if (en) return "Navigate using typed text above";
                return mandarin ? "使用上方输入文字导航" : "使用上方輸入文字導航";
            default:
                return getString(R.string.app_name);
        }
    }
}
