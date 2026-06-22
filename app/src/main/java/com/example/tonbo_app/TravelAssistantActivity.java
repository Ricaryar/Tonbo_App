package com.example.tonbo_app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

/**
 * 出行協助Activity
 * 提供出行導航、路線規劃、交通信息等功能
 */
public class TravelAssistantActivity extends BaseAccessibleActivity {
    private static final String TAG = "TravelAssistant";
    
    private TextView pageTitle;
    private TextView voiceStatusTitle;
    private TextView voiceStatusText;
    private Button startTravelVoiceButton;
    private Button emergencyButton;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_travel_assistant);
        
        initViews();
        setupButtons();
        announcePageTitle();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
    
    private void initViews() {
        pageTitle = findViewById(R.id.page_title);
        voiceStatusTitle = findViewById(R.id.voice_status_title);
        voiceStatusText = findViewById(R.id.voice_status_text);
        startTravelVoiceButton = findViewById(R.id.start_travel_voice_button);
        emergencyButton = findViewById(R.id.emergency_button);
        
        // 設置返回按鈕
        android.widget.ImageButton backButton = findViewById(R.id.back_button);
        if (backButton != null) {
            backButton.setOnClickListener(v -> {
                handleBackPressed();
            });
        }
        
        // 根據當前語言更新界面文字
        updateLanguageUI();
    }
    
    private void setupButtons() {
        // 開始出行：直接跳轉 StartTravelActivity
        startTravelVoiceButton.setOnClickListener(v -> {
            vibrationManager.vibrateClick();
            Intent intent = new Intent(this, StartTravelActivity.class);
            intent.putExtra("language", currentLanguage);
            startActivity(intent);
        });
        
        // 聯繫志願者
        emergencyButton.setOnClickListener(v -> {
            vibrationManager.vibrateClick();
            updateVoiceStatus(getLocalizedString("voice_status_emergency"));
            EmergencyManager.getInstance(this).triggerEmergencyAlert();
        });
    }
    
    /**
     * 更新語音狀態提示
     */
    private void updateVoiceStatus(String status) {
        if (voiceStatusText != null) {
            voiceStatusText.setText(status);
            // 為視障用戶播報狀態（避免重複播報，只在關鍵狀態變化時播報）
        }
    }
    
    /**
     * 更新語言UI
     */
    private void updateLanguageUI() {
        if (pageTitle != null) {
            pageTitle.setText(getLocalizedString("travel_assistant_title"));
        }
        
        if (voiceStatusTitle != null) {
            voiceStatusTitle.setText(getLocalizedString("voice_status_title"));
        }
        
        if (voiceStatusText != null) {
            voiceStatusText.setText(getLocalizedString("voice_status_ready"));
        }
        
        if (startTravelVoiceButton != null) {
            startTravelVoiceButton.setText(getLocalizedString("start_travel_voice"));
        }
        
        if (emergencyButton != null) {
            emergencyButton.setText(getLocalizedString("contact_volunteer"));
        }
    }
    
    /**
     * 根據當前語言獲取本地化字符串
     */
    private String getLocalizedString(String key) {
        String currentLang = LocaleManager.getInstance(this).getCurrentLanguage();
        
        switch (key) {
            case "travel_assistant_title":
                if ("english".equals(currentLang)) {
                    return "Travel Assistant";
                } else if ("mandarin".equals(currentLang)) {
                    return "出行协助";
                } else {
                    return "出行協助";
                }
            case "travel_assistant_status":
                if ("english".equals(currentLang)) {
                    return "Travel assistance functions are ready";
                } else if ("mandarin".equals(currentLang)) {
                    return "出行协助功能准备就绪";
                } else {
                    return "出行協助功能準備就緒";
                }
            case "navigation":
                if ("english".equals(currentLang)) {
                    return "Navigation";
                } else if ("mandarin".equals(currentLang)) {
                    return "导航";
                } else {
                    return "導航";
                }
            case "route_planning":
                if ("english".equals(currentLang)) {
                    return "Route Planning";
                } else if ("mandarin".equals(currentLang)) {
                    return "路线规划";
                } else {
                    return "路線規劃";
                }
            case "traffic_info":
                if ("english".equals(currentLang)) {
                    return "Traffic Info";
                } else if ("mandarin".equals(currentLang)) {
                    return "交通信息";
                } else {
                    return "交通信息";
                }
            case "weather_info":
                if ("english".equals(currentLang)) {
                    return "Weather Info";
                } else if ("mandarin".equals(currentLang)) {
                    return "天气信息";
                } else {
                    return "天氣信息";
                }
            case "emergency_location":
                if ("english".equals(currentLang)) {
                    return "Emergency Location";
                } else if ("mandarin".equals(currentLang)) {
                    return "紧急位置分享";
                } else {
                    return "緊急位置分享";
                }
            case "going_back_to_home":
                if ("english".equals(currentLang)) {
                    return "Going back to home";
                } else if ("mandarin".equals(currentLang)) {
                    return "返回主页";
                } else {
                    return "返回主頁";
                }
            case "voice_status_title":
                if ("english".equals(currentLang)) {
                    return "Voice Status";
                } else if ("mandarin".equals(currentLang)) {
                    return "语音状态";
                } else {
                    return "語音狀態";
                }
            case "voice_status_ready":
                if ("english".equals(currentLang)) {
                    return "Ready";
                } else if ("mandarin".equals(currentLang)) {
                    return "就绪";
                } else {
                    return "就緒";
                }
            case "voice_status_listening":
                if ("english".equals(currentLang)) {
                    return "Listening...";
                } else if ("mandarin".equals(currentLang)) {
                    return "正在聆听...";
                } else {
                    return "正在聆聽...";
                }
            case "voice_status_processing":
                if ("english".equals(currentLang)) {
                    return "Processing...";
                } else if ("mandarin".equals(currentLang)) {
                    return "处理中...";
                } else {
                    return "處理中...";
                }
            case "voice_status_emergency":
                if ("english".equals(currentLang)) {
                    return "Emergency Mode";
                } else if ("mandarin".equals(currentLang)) {
                    return "紧急模式";
                } else {
                    return "緊急模式";
                }
            case "start_travel_voice":
                if ("english".equals(currentLang)) {
                    return "Start Travel";
                } else if ("mandarin".equals(currentLang)) {
                    return "开始出行";
                } else {
                    return "開始出行";
                }
            case "environment_recognition":
                if ("english".equals(currentLang)) {
                    return "Environment Recognition";
                } else if ("mandarin".equals(currentLang)) {
                    return "前方环境识别";
                } else {
                    return "前方環境識別";
                }
            case "emergency_assistance":
                if ("english".equals(currentLang)) {
                    return "Emergency Assistance";
                } else if ("mandarin".equals(currentLang)) {
                    return "紧急求助";
                } else {
                    return "緊急求助";
                }
            case "contact_volunteer":
                if ("english".equals(currentLang)) {
                    return "Contact Volunteer";
                } else if ("mandarin".equals(currentLang)) {
                    return "联系志愿者";
                } else {
                    return "聯繫志願者";
                }
            case "voice_status_speaking":
                if ("english".equals(currentLang)) {
                    return "Speaking...";
                } else if ("mandarin".equals(currentLang)) {
                    return "正在说话...";
                } else {
                    return "正在說話...";
                }
            case "voice_status_recognized":
                if ("english".equals(currentLang)) {
                    return "Recognized";
                } else if ("mandarin".equals(currentLang)) {
                    return "已识别";
                } else {
                    return "已識別";
                }
            case "voice_status_getting_location":
                if ("english".equals(currentLang)) {
                    return "Getting location...";
                } else if ("mandarin".equals(currentLang)) {
                    return "正在获取位置...";
                } else {
                    return "正在獲取位置...";
                }
            case "voice_status_planning_route":
                if ("english".equals(currentLang)) {
                    return "Planning route...";
                } else if ("mandarin".equals(currentLang)) {
                    return "正在规划路线...";
                } else {
                    return "正在規劃路線...";
                }
            case "voice_status_route_planned":
                if ("english".equals(currentLang)) {
                    return "Route planned";
                } else if ("mandarin".equals(currentLang)) {
                    return "路线已规划";
                } else {
                    return "路線已規劃";
                }
            case "voice_status_navigating":
                if ("english".equals(currentLang)) {
                    return "Navigating...";
                } else if ("mandarin".equals(currentLang)) {
                    return "导航中...";
                } else {
                    return "導航中...";
                }
            case "voice_status_arrived":
                if ("english".equals(currentLang)) {
                    return "Arrived";
                } else if ("mandarin".equals(currentLang)) {
                    return "已到达";
                } else {
                    return "已到達";
                }
            case "environment_recognition_starting":
                if ("english".equals(currentLang)) {
                    return "Starting environment recognition, please point camera forward";
                } else if ("mandarin".equals(currentLang)) {
                    return "正在启动环境识别，请将摄像头对准前方路段";
                } else {
                    return "正在啟動環境識別，請將攝像頭對準前方路段";
                }
            case "voice_status_error_not_available":
                if ("english".equals(currentLang)) {
                    return "Not Available";
                } else if ("mandarin".equals(currentLang)) {
                    return "不可用";
                } else {
                    return "不可用";
                }
            case "voice_status_error_permission":
                if ("english".equals(currentLang)) {
                    return "Permission Denied";
                } else if ("mandarin".equals(currentLang)) {
                    return "权限被拒绝";
                } else {
                    return "權限被拒絕";
                }
            case "voice_recognition_success":
                if ("english".equals(currentLang)) {
                    return "Recognized";
                } else if ("mandarin".equals(currentLang)) {
                    return "识别成功";
                } else {
                    return "識別成功";
                }
            case "voice_recognition_error":
                if ("english".equals(currentLang)) {
                    return "Recognition Error";
                } else if ("mandarin".equals(currentLang)) {
                    return "识别错误";
                } else {
                    return "識別錯誤";
                }
            case "voice_recognition_no_result":
                if ("english".equals(currentLang)) {
                    return "No result, please try again";
                } else if ("mandarin".equals(currentLang)) {
                    return "未识别到结果，请重试";
                } else {
                    return "未識別到結果，請重試";
                }
            case "voice_recognition_already_listening":
                if ("english".equals(currentLang)) {
                    return "Already listening";
                } else if ("mandarin".equals(currentLang)) {
                    return "正在聆听中";
                } else {
                    return "正在聆聽中";
                }
            case "voice_recognition_error_not_available":
                if ("english".equals(currentLang)) {
                    return "Speech recognition not available";
                } else if ("mandarin".equals(currentLang)) {
                    return "语音识别不可用";
                } else {
                    return "語音識別不可用";
                }
            case "voice_recognition_error_start_failed":
                if ("english".equals(currentLang)) {
                    return "Failed to start recognition";
                } else if ("mandarin".equals(currentLang)) {
                    return "启动识别失败";
                } else {
                    return "啟動識別失敗";
                }
            case "voice_recognition_permission_denied":
                if ("english".equals(currentLang)) {
                    return "Microphone permission denied";
                } else if ("mandarin".equals(currentLang)) {
                    return "麦克风权限被拒绝";
                } else {
                    return "麥克風權限被拒絕";
                }
            case "error_audio":
                if ("english".equals(currentLang)) {
                    return "Audio error";
                } else if ("mandarin".equals(currentLang)) {
                    return "音频错误";
                } else {
                    return "音頻錯誤";
                }
            case "error_client":
                if ("english".equals(currentLang)) {
                    return "Client error";
                } else if ("mandarin".equals(currentLang)) {
                    return "客户端错误";
                } else {
                    return "客戶端錯誤";
                }
            case "error_permissions":
                if ("english".equals(currentLang)) {
                    return "Permission denied";
                } else if ("mandarin".equals(currentLang)) {
                    return "权限不足";
                } else {
                    return "權限不足";
                }
            case "error_network":
                if ("english".equals(currentLang)) {
                    return "Network error";
                } else if ("mandarin".equals(currentLang)) {
                    return "网络错误";
                } else {
                    return "網絡錯誤";
                }
            case "error_network_timeout":
                if ("english".equals(currentLang)) {
                    return "Network timeout";
                } else if ("mandarin".equals(currentLang)) {
                    return "网络超时";
                } else {
                    return "網絡超時";
                }
            case "error_no_match":
                if ("english".equals(currentLang)) {
                    return "No match found";
                } else if ("mandarin".equals(currentLang)) {
                    return "未找到匹配";
                } else {
                    return "未找到匹配";
                }
            case "error_recognizer_busy":
                if ("english".equals(currentLang)) {
                    return "Recognizer busy";
                } else if ("mandarin".equals(currentLang)) {
                    return "识别器忙碌";
                } else {
                    return "識別器忙碌";
                }
            case "error_server":
                if ("english".equals(currentLang)) {
                    return "Server error";
                } else if ("mandarin".equals(currentLang)) {
                    return "服务器错误";
                } else {
                    return "服務器錯誤";
                }
            case "error_speech_timeout":
                if ("english".equals(currentLang)) {
                    return "Speech timeout";
                } else if ("mandarin".equals(currentLang)) {
                    return "语音超时";
                } else {
                    return "語音超時";
                }
            case "error_unknown":
                if ("english".equals(currentLang)) {
                    return "Unknown error";
                } else if ("mandarin".equals(currentLang)) {
                    return "未知错误";
                } else {
                    return "未知錯誤";
                }
            case "voice_status_requesting_location_permission":
                if ("english".equals(currentLang)) {
                    return "Requesting location permission...";
                } else if ("mandarin".equals(currentLang)) {
                    return "正在请求位置权限...";
                } else {
                    return "正在請求位置權限...";
                }
            case "location_permission_required":
                if ("english".equals(currentLang)) {
                    return "Location permission is required for navigation. Please grant the permission.";
                } else if ("mandarin".equals(currentLang)) {
                    return "导航需要位置权限，请授予位置权限";
                } else {
                    return "導航需要位置權限，請授予位置權限";
                }
            case "location_permission_denied":
                if ("english".equals(currentLang)) {
                    return "Location permission denied. Cannot start navigation.";
                } else if ("mandarin".equals(currentLang)) {
                    return "位置权限被拒绝，无法开始导航";
                } else {
                    return "位置權限被拒絕，無法開始導航";
                }
            default:
                return getString(R.string.app_name);
        }
    }

    @Override
    protected void announcePageTitle() {
        String currentLang = LocaleManager.getInstance(this).getCurrentLanguage();
        switch (currentLang) {
            case "english":
                ttsManager.speak(null, "Travel Assistant. Start travel, contact volunteer.", true);
                break;
            case "mandarin":
                ttsManager.speak("出行协助。开始出行、联系志愿者。", null, true);
                break;
            case "cantonese":
            default:
                ttsManager.speak("出行協助。開始出行、聯繫志願者。", "Travel Assistant. Start travel, contact volunteer.", true);
                break;
        }
    }
    
    private String getEnglishDescription() {
        return "This feature provides travel assistance including navigation, route planning, traffic information, weather updates, and emergency location sharing.";
    }
    
    private String getSimplifiedChineseDescription() {
        return "出行助手功能，提供导航、路线规划、交通信息、天气更新和紧急位置分享等服务。";
    }
    
    @Override
    protected void startEnvironmentActivity() {
        // 重寫父類方法，避免語音命令衝突
        startEnvironmentRecognition();
    }
    
    /**
     * 啟動環境識別（自動開始檢測）
     */
    private void startEnvironmentRecognition() {
        Log.d(TAG, "啟動環境識別，自動開始檢測");
        
        // 播報提示
        String announcement = getLocalizedString("environment_recognition_starting");
        announceInfo(announcement);
        
        // 啟動環境識別 Activity，並傳遞自動開始標記
        Intent intent = new Intent(this, RealAIDetectionActivity.class);
        intent.putExtra("language", currentLanguage);
        intent.putExtra("auto_start", true); // 標記自動開始檢測
        startActivity(intent);
    }
    
    @Override
    protected void startDocumentCurrencyActivity() {
        // 重寫父類方法，避免語音命令衝突
    }
    
    @Override
    protected void startFindItemsActivity() {
        // 重寫父類方法，避免語音命令衝突
    }
    
    @Override
    protected void startSettingsActivity() {
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.putExtra("language", currentLanguage);
        startActivity(intent);
    }
    
    @Override
    protected void handleEmergencyCommand() {
        announceInfo(getString(R.string.emergency_location_feature_coming_soon));
    }
    
    @Override
    protected void goToHome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("language", currentLanguage);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
    
    @Override
    protected void handleLanguageSwitch() {
        announceInfo(getString(R.string.language_switch_feature_coming_soon));
    }
    
    @Override
    protected void stopCurrentOperation() {
        announceInfo(getString(R.string.operation_stopped));
    }
}
