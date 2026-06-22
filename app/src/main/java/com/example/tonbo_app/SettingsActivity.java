package com.example.tonbo_app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

public class SettingsActivity extends BaseAccessibleActivity {
    private static final String TAG = "SettingsActivity";
    private static final String PREFS_NAME = "TonboSettings";
    
    // 語音設定相關
    private SeekBar speechRateSeekBar;
    private SeekBar speechPitchSeekBar;
    private SeekBar speechVolumeSeekBar;
    private TextView speechRateText;
    private TextView speechPitchText;
    private TextView speechVolumeText;
    
    // 無障礙設定相關
    private Button vibrationToggleButton;
    private Button screenReaderToggleButton;
    private Button gestureToggleButton;
    
    // 無障礙設定標籤
    private TextView vibrationFeedbackLabel;
    private TextView screenReaderLabel;
    private TextView gestureOperationsLabel;
    
    // 其他設定
    private Button resetSettingsButton;
    private Button testVoiceButton;
    
    private SharedPreferences preferences;
    private TTSManager ttsManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        // 初始化SharedPreferences
        preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        // 初始化TTSManager
        ttsManager = TTSManager.getInstance(this);
        
        initViews();
        loadSettings();
        setupListeners();
        updateLanguageUI();
        
        // 頁面標題播報
        announcePageTitle();
    }
    
    private void initViews() {
        // 語音設定
        speechRateSeekBar = findViewById(R.id.speechRateSeekBar);
        speechPitchSeekBar = findViewById(R.id.speechPitchSeekBar);
        speechVolumeSeekBar = findViewById(R.id.speechVolumeSeekBar);
        speechRateText = findViewById(R.id.speechRateText);
        speechPitchText = findViewById(R.id.speechPitchText);
        speechVolumeText = findViewById(R.id.speechVolumeText);
        
        // 無障礙設定
        vibrationToggleButton = findViewById(R.id.vibrationToggleButton);
        screenReaderToggleButton = findViewById(R.id.screenReaderToggleButton);
        gestureToggleButton = findViewById(R.id.gestureToggleButton);
        
        // 無障礙設定標籤
        vibrationFeedbackLabel = findViewById(R.id.vibrationFeedbackLabel);
        screenReaderLabel = findViewById(R.id.screenReaderLabel);
        gestureOperationsLabel = findViewById(R.id.gestureOperationsLabel);
        
        // 其他設定
        resetSettingsButton = findViewById(R.id.resetSettingsButton);
        testVoiceButton = findViewById(R.id.testVoiceButton);
        
        // 返回按鈕（ImageButton，而非一般 Button）
        android.widget.ImageButton backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> {
                vibrationManager.vibrateClick();
                finish();
            });
        }

    }
    
    private void loadSettings() {
        // 載入語音設定
        float speechRate = preferences.getFloat("speech_rate", 1.0f);
        float speechPitch = preferences.getFloat("speech_pitch", 1.0f);
        float speechVolume = preferences.getFloat("speech_volume", 1.0f);
        
        // 設定SeekBar值（0-200範圍，默認100）
        speechRateSeekBar.setProgress((int) (speechRate * 100));
        speechPitchSeekBar.setProgress((int) (speechPitch * 100));
        speechVolumeSeekBar.setProgress((int) (speechVolume * 100));
        
        updateSpeechTexts();
        
        // 載入無障礙設定
        boolean vibrationEnabled = preferences.getBoolean("vibration_enabled", true);
        boolean screenReaderEnabled = preferences.getBoolean("screen_reader_enabled", true);
        // 從GestureManagementActivity載入手勢登入設定
        boolean gestureLoginEnabled = GestureManagementActivity.isGestureLoginEnabled(this);
        
        updateToggleButton(vibrationToggleButton, vibrationEnabled, getLocalizedString("vibration_feedback"));
        updateToggleButton(screenReaderToggleButton, screenReaderEnabled, getLocalizedString("screen_reader_support"));
        updateToggleButton(gestureToggleButton, gestureLoginEnabled, getLocalizedString("gesture_login"));
        
        Log.d(TAG, "設定已載入 - 語速:" + speechRate + " 音調:" + speechPitch + " 音量:" + speechVolume);
    }
    
    private void setupListeners() {
        // 語速調整
        speechRateSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    float rate = progress / 100.0f;
                    ttsManager.setSpeechRate(rate);
                    updateSpeechTexts();
                    // 不在這裡播報，避免連續播報
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                vibrationManager.vibrateClick();
                ttsManager.stop(); // 停止當前播報
            }
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // 停止調整時才播報和保存
                int progress = seekBar.getProgress();
                float rate = progress / 100.0f;
                preferences.edit().putFloat("speech_rate", rate).apply();
                
                String message;
                if ("english".equals(currentLanguage)) {
                    message = "Speech rate adjusted to " + progress + "%";
                } else if ("mandarin".equals(currentLanguage)) {
                    message = "语音速度已调整为 " + progress + "%";
                } else {
                    message = "語速已調整為 " + progress + "%";
                }
                announceSettingChange(message);
            }
        });
        
        // 音調調整
        speechPitchSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    float pitch = progress / 100.0f;
                    ttsManager.setSpeechPitch(pitch);
                    updateSpeechTexts();
                    // 不在這裡播報，避免連續播報
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                vibrationManager.vibrateClick();
                ttsManager.stop(); // 停止當前播報
            }
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // 停止調整時才播報和保存
                int progress = seekBar.getProgress();
                float pitch = progress / 100.0f;
                preferences.edit().putFloat("speech_pitch", pitch).apply();
                
                String message;
                if ("english".equals(currentLanguage)) {
                    message = "Pitch adjusted to " + progress + "%";
                } else if ("mandarin".equals(currentLanguage)) {
                    message = "音调已调整为 " + progress + "%";
                } else {
                    message = "音調已調整為 " + progress + "%";
                }
                announceSettingChange(message);
            }
        });
        
        // 音量調整
        speechVolumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    float volume = progress / 100.0f;
                    ttsManager.setSpeechVolume(volume);
                    updateSpeechTexts();
                    // 不在這裡播報，避免連續播報
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                vibrationManager.vibrateClick();
                ttsManager.stop(); // 停止當前播報
            }
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // 停止調整時才播報和保存
                int progress = seekBar.getProgress();
                float volume = progress / 100.0f;
                preferences.edit().putFloat("speech_volume", volume).apply();
                
                String message;
                if ("english".equals(currentLanguage)) {
                    message = "Volume adjusted to " + progress + "%";
                } else if ("mandarin".equals(currentLanguage)) {
                    message = "音量已调整为 " + progress + "%";
                } else {
                    message = "音量已調整為 " + progress + "%";
                }
                announceSettingChange(message);
            }
        });
        
        // 震動反饋切換
        vibrationToggleButton.setOnClickListener(v -> {
            vibrationManager.vibrateClick();
            toggleVibration();
        });
        
        // 讀屏支援切換
        screenReaderToggleButton.setOnClickListener(v -> {
            vibrationManager.vibrateClick();
            toggleScreenReader();
        });
        
        // 手勢操作切換
        gestureToggleButton.setOnClickListener(v -> {
            vibrationManager.vibrateClick();
            toggleGesture();
        });
        
        // 測試語音
        testVoiceButton.setOnClickListener(v -> {
            vibrationManager.vibrateClick();
            testVoice();
        });
        
        // 重置設定
        resetSettingsButton.setOnClickListener(v -> {
            vibrationManager.vibrateClick();
            showResetDialog();
        });
    }
    
    private void updateSpeechTexts() {
        int rate = speechRateSeekBar.getProgress();
        int pitch = speechPitchSeekBar.getProgress();
        int volume = speechVolumeSeekBar.getProgress();
        
        // 使用本地化字符串
        if (speechRateText != null) {
            String text;
            if ("english".equals(currentLanguage)) {
                text = "Speech Rate: " + rate + "%";
            } else if ("mandarin".equals(currentLanguage)) {
                text = "语音速度：" + rate + "%";
            } else {
                text = "語音速度：" + rate + "%";
            }
            speechRateText.setText(text);
        }
        
        if (speechPitchText != null) {
            String text;
            if ("english".equals(currentLanguage)) {
                text = "Pitch: " + pitch + "%";
            } else if ("mandarin".equals(currentLanguage)) {
                text = "音调：" + pitch + "%";
            } else {
                text = "音調：" + pitch + "%";
            }
            speechPitchText.setText(text);
        }
        
        if (speechVolumeText != null) {
            String text;
            if ("english".equals(currentLanguage)) {
                text = "Volume: " + volume + "%";
            } else if ("mandarin".equals(currentLanguage)) {
                text = "音量：" + volume + "%";
            } else {
                text = "音量：" + volume + "%";
            }
            speechVolumeText.setText(text);
        }
    }
    
    private void updateToggleButton(Button button, boolean enabled, String settingName) {
        String statusText;
        String contentDescription;
        
        if (enabled) {
            if ("english".equals(currentLanguage)) {
                statusText = "On";
                contentDescription = settingName + " On, tap to turn off";
            } else if ("mandarin".equals(currentLanguage)) {
                statusText = "开启";
                contentDescription = settingName + " 已开启，点击关闭";
            } else {
                statusText = "開啟";
                contentDescription = settingName + " 已開啟，點擊關閉";
            }
        } else {
            if ("english".equals(currentLanguage)) {
                statusText = "Off";
                contentDescription = settingName + " Off, tap to turn on";
            } else if ("mandarin".equals(currentLanguage)) {
                statusText = "关闭";
                contentDescription = settingName + " 已关闭，点击开启";
            } else {
                statusText = "關閉";
                contentDescription = settingName + " 已關閉，點擊開啟";
            }
        }
        
        button.setText(statusText);
        button.setContentDescription(contentDescription);
    }
    
    private void toggleVibration() {
        boolean currentState = preferences.getBoolean("vibration_enabled", true);
        boolean newState = !currentState;
        
        preferences.edit().putBoolean("vibration_enabled", newState).apply();
        updateToggleButton(vibrationToggleButton, newState, getLocalizedString("vibration_feedback"));
        
        // 更新VibrationManager狀態
        vibrationManager.setEnabled(newState);
        
        String message;
        if ("english".equals(currentLanguage)) {
            message = newState ? "Vibration feedback enabled" : "Vibration feedback disabled";
        } else if ("mandarin".equals(currentLanguage)) {
            message = newState ? "震动反馈已开启" : "震动反馈已关闭";
        } else {
            message = newState ? "震動反饋已開啟" : "震動反饋已關閉";
        }
        announceSettingChange(message);
        
        if (newState) {
            vibrationManager.vibrateClick();
        }
    }
    
    private void toggleScreenReader() {
        boolean currentState = preferences.getBoolean("screen_reader_enabled", true);
        boolean newState = !currentState;
        
        preferences.edit().putBoolean("screen_reader_enabled", newState).apply();
        updateToggleButton(screenReaderToggleButton, newState, getLocalizedString("screen_reader_support"));
        
        String message;
        if ("english".equals(currentLanguage)) {
            message = newState ? "Screen reader support enabled" : "Screen reader support disabled";
        } else if ("mandarin".equals(currentLanguage)) {
            message = newState ? "屏幕阅读器支持已开启" : "屏幕阅读器支持已关闭";
        } else {
            message = newState ? "螢幕閱讀器支援已開啟" : "螢幕閱讀器支援已關閉";
        }
        announceSettingChange(message);
        
        // 這裡可以添加讀屏相關的設定邏輯
        if (newState) {
            announceInfo(message);
        }
    }
    
    private void toggleGesture() {
        // 使用GestureManagementActivity中的手势登入设置
        boolean currentState = GestureManagementActivity.isGestureLoginEnabled(this);
        boolean newState = !currentState;
        
        // 保存到GestureManagementActivity使用的SharedPreferences
        android.content.SharedPreferences gesturePrefs = getSharedPreferences("GestureSettings", Context.MODE_PRIVATE);
        gesturePrefs.edit().putBoolean("gesture_login_enabled", newState).apply();
        
        updateToggleButton(gestureToggleButton, newState, getLocalizedString("gesture_login"));
        
        String message;
        if ("english".equals(currentLanguage)) {
            message = newState ? "Gesture login enabled" : "Gesture login disabled";
        } else if ("mandarin".equals(currentLanguage)) {
            message = newState ? "手势登录已开启" : "手势登录已关闭";
        } else {
            message = newState ? "手勢登入已開啟" : "手勢登入已關閉";
        }
        announceSettingChange(message);
        
        if (newState) {
            announceInfo(message);
        }
    }
    
    private void testVoice() {
        announceInfo(getString(R.string.testing_voice));
        
        // 根據當前語言生成測試文字 - 使用SettingsActivity的currentLanguage而不是TTSManager的
        String testText;
        
        if ("english".equals(currentLanguage)) {
            testText = "This is voice test. Speech rate: " + speechRateSeekBar.getProgress() + "%, pitch: " + 
                      speechPitchSeekBar.getProgress() + "%, volume: " + speechVolumeSeekBar.getProgress() + "%.";
        } else if ("mandarin".equals(currentLanguage)) {
            testText = "这是语音测试。语速：" + speechRateSeekBar.getProgress() + "%，音调：" + 
                      speechPitchSeekBar.getProgress() + "%，音量：" + speechVolumeSeekBar.getProgress() + "%。";
        } else { // cantonese (default)
            testText = "這是語音測試。語速：" + speechRateSeekBar.getProgress() + "%，音調：" + 
                      speechPitchSeekBar.getProgress() + "%，音量：" + speechVolumeSeekBar.getProgress() + "%。";
        }
        
        new android.os.Handler().postDelayed(() -> {
            ttsManager.speak(testText, null, true);
            
            // 再延遲播報詳細設定狀態
            new android.os.Handler().postDelayed(() -> {
                announceCurrentSettings();
            }, 3000);
        }, 1000);
    }
    
    private void announceCurrentSettings() {
        // 播報當前所有設定狀態
        String currentSettings = String.format(getString(R.string.current_speech_rate), speechRateSeekBar.getProgress()) + "。";
        currentSettings += String.format(getString(R.string.current_speech_pitch), speechPitchSeekBar.getProgress()) + "。";
        currentSettings += String.format(getString(R.string.current_speech_volume), speechVolumeSeekBar.getProgress()) + "。";
        
        boolean vibrationEnabled = preferences.getBoolean("vibration_enabled", true);
        boolean screenReaderEnabled = preferences.getBoolean("screen_reader_enabled", true);
        boolean gestureLoginEnabled = GestureManagementActivity.isGestureLoginEnabled(this);
        
        currentSettings += String.format(getString(R.string.current_vibration_status), 
            vibrationEnabled ? getString(R.string.status_enabled) : getString(R.string.status_disabled)) + "。";
        currentSettings += String.format(getString(R.string.current_screen_reader_status), 
            screenReaderEnabled ? getString(R.string.status_enabled) : getString(R.string.status_disabled)) + "。";
        currentSettings += String.format(getString(R.string.current_gesture_status), 
            gestureLoginEnabled ? getString(R.string.status_enabled) : getString(R.string.status_disabled)) + "。";
        
        ttsManager.speak(currentSettings, null, true);
    }
    
    private void showResetDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.reset_settings_title))
                .setMessage(getString(R.string.reset_settings_message))
                .setPositiveButton(getString(R.string.confirm), (dialog, which) -> {
                    resetAllSettings();
                    announceInfo(getString(R.string.settings_reset));
                })
                .setNegativeButton(getString(R.string.back), (dialog, which) -> {
                    announceInfo(getString(R.string.reset_cancelled));
                })
                .show();
    }
    
    private void resetAllSettings() {
        // 重置語音設定
        preferences.edit()
                .putFloat("speech_rate", 1.0f)
                .putFloat("speech_pitch", 1.0f)
                .putFloat("speech_volume", 1.0f)
                .putBoolean("vibration_enabled", true)
                .putBoolean("screen_reader_enabled", true)
                .apply();
        
        // 重置手勢登入設定
        android.content.SharedPreferences gesturePrefs = getSharedPreferences("GestureSettings", Context.MODE_PRIVATE);
        gesturePrefs.edit().putBoolean("gesture_login_enabled", false).apply();
        
        // 重置UI
        speechRateSeekBar.setProgress(100);
        speechPitchSeekBar.setProgress(100);
        speechVolumeSeekBar.setProgress(100);
        updateSpeechTexts();
        
        updateToggleButton(vibrationToggleButton, true, getLocalizedString("vibration_feedback"));
        updateToggleButton(screenReaderToggleButton, true, getLocalizedString("screen_reader_support"));
        updateToggleButton(gestureToggleButton, false, getLocalizedString("gesture_login"));
        
        // 重置TTS設定
        ttsManager.setSpeechRate(1.0f);
        ttsManager.setSpeechPitch(1.0f);
        ttsManager.setSpeechVolume(1.0f);
        
        // 重置震動設定
        vibrationManager.setEnabled(true);
    }
    
    private void announceSettingChange(String message) {
        // 直接使用傳入的詳細訊息，不需要額外的英文訊息
        ttsManager.speak(message, null, false);
    }
    
    /**
     * 獲取本地化字符串
     */
    private String getLocalizedString(String key) {
        switch (key) {
            case "vibration_feedback":
                if ("english".equals(currentLanguage)) {
                    return "Vibration Feedback";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "震动反馈";
                } else {
                    return "震動反饋";
                }
            case "screen_reader_support":
                if ("english".equals(currentLanguage)) {
                    return "Screen Reader Support";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "屏幕阅读器支持";
                } else {
                    return "螢幕閱讀器支援";
                }
            case "gesture_login":
                if ("english".equals(currentLanguage)) {
                    return "Gesture Login";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "手势登录";
                } else {
                    return "手勢登入";
                }
            case "gesture_operations":
                if ("english".equals(currentLanguage)) {
                    return "Gesture Operations";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "手势操作";
                } else {
                    return "手勢操作";
                }
            default:
                return getString(R.string.app_name); // fallback
        }
    }
    
    @Override
    protected void announcePageTitle() {
        new android.os.Handler().postDelayed(() -> {
            String pageDescription;
            if ("english".equals(currentLanguage)) {
                pageDescription = "System Settings. You can adjust voice settings, vibration feedback, screen reader support and more.";
            } else if ("mandarin".equals(currentLanguage)) {
                pageDescription = "系统设置页面。可以调整语音设置、震动反馈、读屏支持等选项。";
            } else {
                pageDescription = "系統設定頁面。可以調整語音設定、震動反饋、讀屏支援等選項。";
            }
            ttsManager.speak(pageDescription, null, true);
        }, 500);
    }
    
    /**
     * 更新語言UI
     */
    private void updateLanguageUI() {
        // 更新頁面標題
        TextView titleText = findViewById(R.id.systemSettingsTitle);
        if (titleText != null) {
            String title;
            if ("english".equals(currentLanguage)) {
                title = "System Settings";
            } else if ("mandarin".equals(currentLanguage)) {
                title = "系统设置";
            } else {
                title = "系統設定";
            }
            titleText.setText(title);
        }
        
        // 更新語音設定標題
        TextView voiceSettingsTitle = findViewById(R.id.voiceSettingsTitle);
        if (voiceSettingsTitle != null) {
            String title;
            if ("english".equals(currentLanguage)) {
                title = "Voice Settings";
            } else if ("mandarin".equals(currentLanguage)) {
                title = "语音设置";
            } else {
                title = "語音設定";
            }
            voiceSettingsTitle.setText(title);
        }
        
        // 更新無障礙設定標題
        TextView accessibilitySettingsTitle = findViewById(R.id.accessibilitySettingsTitle);
        if (accessibilitySettingsTitle != null) {
            String title;
            if ("english".equals(currentLanguage)) {
                title = "Accessibility Settings";
            } else if ("mandarin".equals(currentLanguage)) {
                title = "无障碍设置";
            } else {
                title = "無障礙設定";
            }
            accessibilitySettingsTitle.setText(title);
        }
        
        // 更新測試和重置標題
        TextView testResetTitle = findViewById(R.id.testResetTitle);
        if (testResetTitle != null) {
            String title;
            if ("english".equals(currentLanguage)) {
                title = "Test and Reset";
            } else if ("mandarin".equals(currentLanguage)) {
                title = "测试和重置";
            } else {
                title = "測試和重置";
            }
            testResetTitle.setText(title);
        }
        
        // 更新按鈕文字
        updateButtonTexts();
        
        // 更新詳細設置項文字
        updateDetailedSettingsTexts();
        
        // 更新TTS語言設置
        updateTTSSettings();
    }
    
    /**
     * 更新TTS語言設置
     */
    private void updateTTSSettings() {
        // 確保TTS語言與當前界面語言一致
        Log.d(TAG, "更新TTS語言設置，當前語言: " + currentLanguage);
        
        // 使用setLanguageSilently避免中斷當前播報
        if ("english".equals(currentLanguage)) {
            ttsManager.setLanguageSilently("english");
        } else if ("mandarin".equals(currentLanguage)) {
            ttsManager.setLanguageSilently("mandarin");
        } else {
            ttsManager.setLanguageSilently("cantonese");
        }
        
        Log.d(TAG, "TTS語言已更新為: " + currentLanguage);
    }
    
    /**
     * 更新按鈕文字
     */
    private void updateButtonTexts() {
        // 更新測試語音按鈕
        Button testVoiceButton = findViewById(R.id.testVoiceButton);
        if (testVoiceButton != null) {
            String text;
            if ("english".equals(currentLanguage)) {
                text = "Test Voice";
            } else if ("mandarin".equals(currentLanguage)) {
                text = "测试语音";
            } else {
                text = "測試語音";
            }
            testVoiceButton.setText(text);
        }
        
        // 更新重置設定按鈕
        Button resetSettingsButton = findViewById(R.id.resetSettingsButton);
        if (resetSettingsButton != null) {
            String text;
            if ("english".equals(currentLanguage)) {
                text = "Reset Settings";
            } else if ("mandarin".equals(currentLanguage)) {
                text = "重置设置";
            } else {
                text = "重置設定";
            }
            resetSettingsButton.setText(text);
        }
    }
    
    /**
     * 更新詳細設置項文字
     */
    private void updateDetailedSettingsTexts() {
        // 更新語音設定詳細文字
        if (speechRateText != null) {
            String text;
            if ("english".equals(currentLanguage)) {
                text = "Speech Rate: " + speechRateSeekBar.getProgress() + "%";
            } else if ("mandarin".equals(currentLanguage)) {
                text = "语音速度：" + speechRateSeekBar.getProgress() + "%";
            } else {
                text = "語音速度：" + speechRateSeekBar.getProgress() + "%";
            }
            speechRateText.setText(text);
        }
        
        if (speechPitchText != null) {
            String text;
            if ("english".equals(currentLanguage)) {
                text = "Pitch: " + speechPitchSeekBar.getProgress() + "%";
            } else if ("mandarin".equals(currentLanguage)) {
                text = "音调：" + speechPitchSeekBar.getProgress() + "%";
            } else {
                text = "音調：" + speechPitchSeekBar.getProgress() + "%";
            }
            speechPitchText.setText(text);
        }
        
        if (speechVolumeText != null) {
            String text;
            if ("english".equals(currentLanguage)) {
                text = "Volume: " + speechVolumeSeekBar.getProgress() + "%";
            } else if ("mandarin".equals(currentLanguage)) {
                text = "音量：" + speechVolumeSeekBar.getProgress() + "%";
            } else {
                text = "音量：" + speechVolumeSeekBar.getProgress() + "%";
            }
            speechVolumeText.setText(text);
        }
        
        // 更新無障礙設定詳細文字
        updateAccessibilitySettingsTexts();
    }
    
    /**
     * 更新無障礙設定文字
     */
    private void updateAccessibilitySettingsTexts() {
        // 震動反饋標籤
        if (vibrationFeedbackLabel != null) {
            String text;
            if ("english".equals(currentLanguage)) {
                text = "Vibration Feedback";
            } else if ("mandarin".equals(currentLanguage)) {
                text = "震动反馈";
            } else {
                text = "震動反饋";
            }
            vibrationFeedbackLabel.setText(text);
        }
        
        // 讀屏支援標籤
        if (screenReaderLabel != null) {
            String text;
            if ("english".equals(currentLanguage)) {
                text = "Screen Reader Support";
            } else if ("mandarin".equals(currentLanguage)) {
                text = "屏幕阅读器支持";
            } else {
                text = "螢幕閱讀器支援";
            }
            screenReaderLabel.setText(text);
        }
        
        // 手勢登入標籤
        if (gestureOperationsLabel != null) {
            String text;
            if ("english".equals(currentLanguage)) {
                text = "Gesture Login";
            } else if ("mandarin".equals(currentLanguage)) {
                text = "手势登录";
            } else {
                text = "手勢登入";
            }
            gestureOperationsLabel.setText(text);
        }
        
        // 無障礙設定按鈕應該顯示狀態，而不是標籤
        // 這些按鈕的狀態由 updateToggleButton() 方法控制
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 保存當前設定
        saveSettings();
    }
    
    private void saveSettings() {
        // 確保所有設定都已保存
        Log.d(TAG, "設定已保存");
    }
}
