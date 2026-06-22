package com.example.tonbo_app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import io.agora.rtc2.ChannelMediaOptions;
import io.agora.rtc2.Constants;
import io.agora.rtc2.IRtcEngineEventHandler;
import io.agora.rtc2.RtcEngine;
import io.agora.rtc2.RtcEngineConfig;
import io.agora.rtc2.video.VideoCanvas;

public class VideoCallActivity extends BaseAccessibleActivity {
    public static final String EXTRA_AGORA_CHANNEL = "agora_channel";
    public static final String EXTRA_USER_ROLE = "user_role";

    private static final String TAG = "VideoCallActivity";
    private static final String APP_ID = "27576174aeaa4b2d88ea23cd63aae2a8";
    /** 隨機房間池：每次加入從中抽一間，志願者端用相同規則即可與用戶有概率相遇 */
    private static final int RANDOM_CHANNEL_POOL = 96;
    private static final String CHANNEL_PREFIX = "tonbo_assist_";

    private RtcEngine mRtcEngine;
    private FrameLayout localVideoContainer, remoteVideoContainer;
    private TextView connectionStatus;
    private TextView pageTitle;
    private TextView statusText;
    private Button joinButton, leaveButton, switchCameraButton;
    private Button muteAudioButton, muteVideoButton;
    private ImageButton backButton;

    private boolean isJoined = false;
    private boolean localAudioMuted = false;
    private boolean localVideoMuted = false;

    /** 當前這次加入隨機選中的聲網頻道名 */
    private String activeChannelName;
    private int currentAgoraUid;
    /** 從即時協助頁傳入時與對方固定同一頻道；未傳則仍用隨機房 */
    private String pairingChannelFromIntent;
    private boolean autoJoinAttemptedForPairing;
    private boolean rtcEngineReady;
    private String userRole = "blind_user";
    private boolean hasRetriedJoinAfterTimeout = false;
    private int joinAttemptCount = 0;
    private static final int MAX_JOIN_ATTEMPTS = 3;
    private int lastJoinResultCode = 0;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable joinTimeoutRunnable = () -> {
        if (!isJoined) {
            if (statusText != null) {
                statusText.setText("入房超时，正在重试(" + joinAttemptCount + "/" + MAX_JOIN_ATTEMPTS + ")");
            }
            if (connectionStatus != null) {
                connectionStatus.setText("尚未成功加入频道");
            }
            if (joinAttemptCount < MAX_JOIN_ATTEMPTS) {
                retryJoinAfterTimeout();
            } else if (joinButton != null) {
                joinButton.setVisibility(View.VISIBLE);
                if (statusText != null) {
                    if (lastJoinResultCode != 0) {
                        statusText.setText("入房失败，代码: " + lastJoinResultCode + "（请重试或检查网络/证书）");
                    } else {
                        statusText.setText("入房失败，请重试或检查网络");
                    }
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_call);

        String ch = getIntent().getStringExtra(EXTRA_AGORA_CHANNEL);
        if (ch != null) {
            pairingChannelFromIntent = ch.trim();
            if (pairingChannelFromIntent.isEmpty()) {
                pairingChannelFromIntent = null;
            }
        }
        String role = getIntent().getStringExtra(EXTRA_USER_ROLE);
        if (role != null && !role.trim().isEmpty()) {
            userRole = role.trim();
        }

        initViews();
        checkPermissions();
    }

    @Override
    protected void announcePageTitle() {
        if (ttsManager == null) {
            return;
        }
        if (pairingChannelFromIntent != null) {
            if ("english".equals(currentLanguage)) {
                ttsManager.speak("Video assistance. Joining the channel automatically.", null, true);
            } else if ("mandarin".equals(currentLanguage)) {
                ttsManager.speak("视频协助。正在自动加入频道。", null, true);
            } else {
                ttsManager.speak("視像協助。正在自動加入頻道。", null, true);
            }
            return;
        }
        if ("english".equals(currentLanguage)) {
            ttsManager.speak("Video call page. Join to connect to a random assistance room.", null, true);
        } else if ("mandarin".equals(currentLanguage)) {
            ttsManager.speak("视频通话。点击加入频道可随机进入协助房间。", null, true);
        } else {
            ttsManager.speak("視訊通話。按下加入頻道可隨機進入協助房間。", null, true);
        }
    }

    private void initViews() {
        backButton = findViewById(R.id.backButton);
        localVideoContainer = findViewById(R.id.localVideoContainer);
        remoteVideoContainer = findViewById(R.id.remoteVideoContainer);
        connectionStatus = findViewById(R.id.connectionStatus);
        pageTitle = findViewById(R.id.pageTitle);
        statusText = findViewById(R.id.statusText);
        joinButton = findViewById(R.id.joinButton);
        leaveButton = findViewById(R.id.leaveButton);
        switchCameraButton = findViewById(R.id.switchCameraButton);
        muteAudioButton = findViewById(R.id.muteAudioButton);
        muteVideoButton = findViewById(R.id.muteVideoButton);

        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        if (pairingChannelFromIntent != null && joinButton != null) {
            joinButton.setVisibility(View.GONE);
        }
        if (joinButton != null) {
            joinButton.setOnClickListener(v -> joinChannel());
        }
        leaveButton.setOnClickListener(v -> leaveChannel());

        updateLocalPreviewVisibility();
        switchCameraButton.setOnClickListener(v -> {
            if (mRtcEngine != null) {
                mRtcEngine.switchCamera();
                if (ttsManager != null) {
                    if ("english".equals(currentLanguage)) {
                        ttsManager.speak("Camera switched", null, true);
                    } else if ("mandarin".equals(currentLanguage)) {
                        ttsManager.speak("已切换摄像头", null, true);
                    } else {
                        ttsManager.speak("已切換鏡頭", null, true);
                    }
                }
            }
        });

        if (muteAudioButton != null) {
            muteAudioButton.setOnClickListener(v -> {
                if (mRtcEngine == null) {
                    Toast.makeText(this, "引擎未就绪", Toast.LENGTH_SHORT).show();
                    return;
                }
                localAudioMuted = !localAudioMuted;
                mRtcEngine.muteLocalAudioStream(localAudioMuted);
                muteAudioButton.setText(localAudioMuted ? "🔊 取消靜音" : "🔇 靜音");
                if (ttsManager != null) {
                    ttsManager.speak(localAudioMuted ? "已靜音" : "已取消靜音", null, true);
                }
            });
        }

        if (muteVideoButton != null) {
            muteVideoButton.setOnClickListener(v -> {
                if (mRtcEngine == null) {
                    Toast.makeText(this, "引擎未就绪", Toast.LENGTH_SHORT).show();
                    return;
                }
                localVideoMuted = !localVideoMuted;
                mRtcEngine.muteLocalVideoStream(localVideoMuted);
                muteVideoButton.setText(localVideoMuted ? "📹 開啟視頻" : "📹 關閉視頻");
                if (ttsManager != null) {
                    ttsManager.speak(localVideoMuted ? "已關閉本地畫面" : "已開啟本地畫面", null, true);
                }
            });
        }
    }

    /** 從房間池隨機選一個頻道名，無需登錄與後台 */
    private String pickRandomChannelName() {
        int room = 1 + (int) (Math.random() * RANDOM_CHANNEL_POOL);
        return CHANNEL_PREFIX + room;
    }

    /** 使用 0 交給聲網自動分配 UID，避免客戶端自算 UID 衝突 */
    private int pickSessionAgoraUid() {
        return 0;
    }

    private void initializeAgoraEngine() {
        try {
            RtcEngineConfig config = new RtcEngineConfig();
            config.mContext = getApplicationContext();
            config.mAppId = APP_ID;
            config.mEventHandler = mRtcEventHandler;
            mRtcEngine = RtcEngine.create(config);
            mRtcEngine.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION);
            mRtcEngine.setClientRole(Constants.CLIENT_ROLE_BROADCASTER);
            mRtcEngine.enableVideo();
            rtcEngineReady = true;
            scheduleAutoJoinIfPairing();
        } catch (Exception e) {
            Log.e(TAG, "声网引擎初始化失败: " + e.getMessage());
            rtcEngineReady = false;
            if (statusText != null) {
                statusText.setText("聲網引擎初始化失敗");
            }
            if (pairingChannelFromIntent != null && joinButton != null) {
                joinButton.setVisibility(View.VISIBLE);
            }
        }
    }

    /** 協助配對模式：選完身份後無需再按加入，引擎就緒即進房 */
    private void scheduleAutoJoinIfPairing() {
        if (pairingChannelFromIntent == null || autoJoinAttemptedForPairing || mRtcEngine == null || !rtcEngineReady) {
            return;
        }
        autoJoinAttemptedForPairing = true;
        getWindow().getDecorView().post(() -> {
            if (isFinishing() || mRtcEngine == null) {
                return;
            }
            joinChannel();
        });
    }

    private void joinChannel() {
        if (mRtcEngine == null) {
            if (statusText != null) {
                statusText.setText("引擎未就緒，請稍候");
            }
            return;
        }
        if (isJoined || localVideoContainer.getChildCount() > 0) {
            Toast.makeText(this, "已在頻道或正在加入，請先離開後再試", Toast.LENGTH_SHORT).show();
            return;
        }

        joinAttemptCount++;
        if (pairingChannelFromIntent != null) {
            activeChannelName = pairingChannelFromIntent;
        } else {
            activeChannelName = pickRandomChannelName();
        }
        currentAgoraUid = pickSessionAgoraUid();

        if (statusText != null) {
            if (pairingChannelFromIntent != null) {
                if ("english".equals(currentLanguage)) {
                    statusText.setText("Shared room: " + activeChannelName + " (attempt " + joinAttemptCount + ")");
                } else if ("mandarin".equals(currentLanguage)) {
                    statusText.setText("共用房间：" + activeChannelName + "（第" + joinAttemptCount + "次）");
                } else {
                    statusText.setText("共用房間：" + activeChannelName + "（第" + joinAttemptCount + "次）");
                }
            } else if ("english".equals(currentLanguage)) {
                statusText.setText("Joining random room: " + activeChannelName);
            } else if ("mandarin".equals(currentLanguage)) {
                statusText.setText("正在随机进入房间：" + activeChannelName);
            } else {
                statusText.setText("正在隨機進入房間：" + activeChannelName);
            }
        }
        if (pageTitle != null) {
            pageTitle.setText(activeChannelName);
        }
        connectionStatus.setText(activeChannelName);

        localVideoContainer.removeAllViews();

        SurfaceView surfaceView = new SurfaceView(getBaseContext());
        if (localVideoContainer != null) {
            localVideoContainer.addView(surfaceView);
        }
        mRtcEngine.setupLocalVideo(new VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, currentAgoraUid));
        updateLocalPreviewVisibility();

        mRtcEngine.startPreview();

        ChannelMediaOptions options = new ChannelMediaOptions();
        options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER;
        options.autoSubscribeAudio = true;
        options.autoSubscribeVideo = true;

        int joinResult = mRtcEngine.joinChannel(null, activeChannelName, currentAgoraUid, options);
        lastJoinResultCode = joinResult;
        if (joinResult != 0) {
            if (statusText != null) {
                statusText.setText("加入失敗，錯誤碼: " + joinResult);
            }
            if (joinButton != null) {
                joinButton.setVisibility(View.VISIBLE);
            }
            if (joinAttemptCount < MAX_JOIN_ATTEMPTS) {
                retryJoinAfterTimeout();
            }
        } else {
            uiHandler.removeCallbacks(joinTimeoutRunnable);
            uiHandler.postDelayed(joinTimeoutRunnable, 8000);
        }
    }

    private void leaveChannel() {
        if (mRtcEngine != null) {
            mRtcEngine.leaveChannel();
            mRtcEngine.stopPreview();
            localAudioMuted = false;
            localVideoMuted = false;
            if (muteAudioButton != null) {
                muteAudioButton.setText("🔇 靜音");
            }
            if (muteVideoButton != null) {
                muteVideoButton.setText("📹 關閉視頻");
            }
            isJoined = false;
            hasRetriedJoinAfterTimeout = false;
            joinAttemptCount = 0;
            uiHandler.removeCallbacks(joinTimeoutRunnable);
            localVideoContainer.removeAllViews();
            remoteVideoContainer.removeAllViews();
            updateLocalPreviewVisibility();
            connectionStatus.setText("已挂断");
            if (statusText != null) {
                statusText.setText("準備加入");
            }
            if (pageTitle != null) {
                pageTitle.setText("視訊通話");
            }
            if (pairingChannelFromIntent != null) {
                autoJoinAttemptedForPairing = false;
                getWindow().getDecorView().postDelayed(() -> scheduleAutoJoinIfPairing(), 500);
            }
            if (ttsManager != null) {
                ttsManager.speak("通话已挂断", null, true);
            }
        }
    }

    private final IRtcEngineEventHandler mRtcEventHandler = new IRtcEngineEventHandler() {
        @Override
        public void onJoinChannelSuccess(String channel, int uid, int elapsed) {
            runOnUiThread(() -> {
                isJoined = true;
                hasRetriedJoinAfterTimeout = false;
                joinAttemptCount = 0;
                lastJoinResultCode = 0;
                uiHandler.removeCallbacks(joinTimeoutRunnable);
                String ch = channel != null ? channel : activeChannelName;
                connectionStatus.setText("已連接 " + ch + " (UID " + uid + ")");
                if (statusText != null) {
                    if ("english".equals(currentLanguage)) {
                        statusText.setText("In room " + ch + ". Waiting for others…");
                    } else if ("mandarin".equals(currentLanguage)) {
                        statusText.setText("已在房间 " + ch + "，等待其他人加入…");
                    } else {
                        statusText.setText("已在房間 " + ch + "，等待其他人加入…");
                    }
                }
            });
        }

        @Override
        public void onUserJoined(int uid, int elapsed) {
            runOnUiThread(() -> {
                SurfaceView surfaceView = new SurfaceView(getBaseContext());
                remoteVideoContainer.addView(surfaceView);
                mRtcEngine.setupRemoteVideo(new VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, uid));
                updateLocalPreviewVisibility();

                connectionStatus.setText("对方已加入 (" + uid + ")");
                if (statusText != null) {
                    if ("english".equals(currentLanguage)) {
                        statusText.setText("Peer connected: " + uid);
                    } else if ("mandarin".equals(currentLanguage)) {
                        statusText.setText("已与对方接通，UID " + uid);
                    } else {
                        statusText.setText("已與對方接通，UID " + uid);
                    }
                }
                if (ttsManager != null) {
                    ttsManager.speak("对方已接通", null, true);
                }
            });
        }

        @Override
        public void onUserOffline(int uid, int reason) {
            runOnUiThread(() -> {
                remoteVideoContainer.removeAllViews();
                updateLocalPreviewVisibility();
                connectionStatus.setText("对方已离开");
                if (ttsManager != null) {
                    ttsManager.speak("对方已挂断", null, true);
                }
            });
        }

        @Override
        public void onError(int err) {
            runOnUiThread(() -> {
                Log.e(TAG, "Rtc onError: " + err);
                isJoined = false;
                uiHandler.removeCallbacks(joinTimeoutRunnable);
                if (mRtcEngine != null) {
                    try {
                        mRtcEngine.leaveChannel();
                        mRtcEngine.stopPreview();
                    } catch (Exception ignored) {
                        // ignore
                    }
                }
                localAudioMuted = false;
                localVideoMuted = false;
                autoJoinAttemptedForPairing = false;
                joinAttemptCount = 0;
                lastJoinResultCode = err;
                localVideoContainer.removeAllViews();
                remoteVideoContainer.removeAllViews();
                updateLocalPreviewVisibility();
                connectionStatus.setText("連接錯誤: " + err);
                if (statusText != null) {
                    statusText.setText("連線錯誤，代碼: " + err);
                }
                Toast.makeText(VideoCallActivity.this, "聲網錯誤碼: " + err, Toast.LENGTH_LONG).show();
                if (pairingChannelFromIntent != null && joinButton != null) {
                    joinButton.setVisibility(View.VISIBLE);
                }
            });
        }
    };

    private void retryJoinAfterTimeout() {
        if (statusText != null) {
            statusText.setText("正在重试加入频道…");
        }
        try {
            if (mRtcEngine != null) {
                mRtcEngine.leaveChannel();
                mRtcEngine.stopPreview();
            }
        } catch (Exception ignored) {
            // ignore
        }
        localVideoContainer.removeAllViews();
        remoteVideoContainer.removeAllViews();
        if (mRtcEngine == null || !rtcEngineReady) {
            initializeAgoraEngine();
        } else {
            joinChannel();
        }
    }

    /**
     * 视障端：未接通前保留本地小窗，确认摄像头正常；
     * 接通后自动隐藏本地小窗，让对方画面最大化。
     */
    private void updateLocalPreviewVisibility() {
        if (localVideoContainer == null) {
            return;
        }
        if ("blind_user".equals(userRole) && remoteVideoContainer != null && remoteVideoContainer.getChildCount() > 0) {
            localVideoContainer.setVisibility(View.GONE);
        } else {
            localVideoContainer.setVisibility(View.VISIBLE);
        }
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            initializeAgoraEngine();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA}, 22);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 22) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                initializeAgoraEngine();
            } else {
                Toast.makeText(this, "需要相机和麦克风权限才能通话", Toast.LENGTH_LONG).show();
                if (ttsManager != null) {
                    ttsManager.speak("需要相机和麦克风权限", null, true);
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        uiHandler.removeCallbacks(joinTimeoutRunnable);
        if (mRtcEngine != null) {
            mRtcEngine.leaveChannel();
            RtcEngine.destroy();
            mRtcEngine = null;
        }
        rtcEngineReady = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 某些机型权限回调后页面恢复时机晚于引擎初始化，这里再兜底一次自动加入
        if (pairingChannelFromIntent != null) {
            scheduleAutoJoinIfPairing();
        }
    }
}
