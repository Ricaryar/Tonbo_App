package com.example.tonbo_app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.telephony.SmsManager;
import android.util.Log;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

public class EmergencyManager {
    private static final String TAG = "EmergencyManager";
    private static EmergencyManager instance;
    
    private Context context;
    private TTSManager ttsManager;
    private VibrationManager vibrationManager;
    
    // 緊急服務電話號碼（固定為999）
    private static final String EMERGENCY_NUMBER = "999";
    private String emergencyMessage = "緊急求助！我在使用瞳伴應用時遇到緊急情況，需要立即協助。請盡快聯繫我。";
    private String emergencyMessageEn = "Emergency! I'm using Tonbo app and need immediate assistance. Please contact me as soon as possible.";
    
    private EmergencyManager(Context context) {
        this.context = context.getApplicationContext();
        ttsManager = TTSManager.getInstance(context);
        vibrationManager = VibrationManager.getInstance(context);
    }
    
    public static synchronized EmergencyManager getInstance(Context context) {
        if (instance == null) {
            instance = new EmergencyManager(context);
        }
        return instance;
    }
    
    /**
     * 觸發緊急求助（單一功能：直接撥打999）
     */
    public void triggerEmergencyAlert() {
        Log.d(TAG, "緊急求助觸發，撥打999");
        
        // 播放緊急提示音
        String cantoneseText = "正在撥打緊急服務電話999，請保持冷靜";
        String englishText = "Calling emergency service 999, please stay calm";
        ttsManager.speak(cantoneseText, englishText, true);
        
        // 強烈震動提醒
        vibrationManager.vibrateEmergencyPattern();
        
        // 發送緊急短信給999（如果支持）
        sendEmergencySMS();
        
        // 撥打緊急電話999
        callEmergencyService();
        
        // 記錄緊急事件
        logEmergencyEvent();
    }
    
    /**
     * 發送緊急短信給999（如果支持短信報警）
     * 注意：999通常不支持短信，此功能主要用於記錄
     */
    private void sendEmergencySMS() {
        try {
            // 注意：999緊急服務通常不支持短信，此處僅作為記錄功能
            // 實際應用中，可以考慮發送給其他緊急聯絡人（如果未來需要）
            Log.d(TAG, "緊急短信功能：999緊急服務通常不支持短信報警");
            
            // 如果需要發送短信給其他號碼，可以在這裡實現
            // 目前簡化為單一功能，只撥打999
        } catch (Exception e) {
            Log.e(TAG, "緊急短信處理失敗: " + e.getMessage());
        }
    }
    
    /**
     * 撥打緊急電話999
     */
    private void callEmergencyService() {
        try {
            String phoneNumber = EMERGENCY_NUMBER; // 固定撥打999
            
            String phoneUri = "tel:" + phoneNumber;
            Log.d(TAG, "準備直接撥打緊急電話: " + phoneNumber);
            
            // 檢查是否有撥打電話的權限
            boolean hasCallPermission = ContextCompat.checkSelfPermission(context, 
                android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED;
            
            // 優先直接撥打（適合視障用戶，無需確認）
            if (hasCallPermission) {
            try {
                Intent callIntent = new Intent(Intent.ACTION_CALL);
                callIntent.setData(Uri.parse(phoneUri));
                callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(callIntent);
                    Log.d(TAG, "緊急電話已直接撥打: " + phoneNumber);
                
                // 播報撥打信息
                    String cantoneseText = "正在撥打緊急電話：" + phoneNumber;
                    String englishText = "Calling emergency number: " + phoneNumber;
                    ttsManager.speak(cantoneseText, englishText, false);
                    return; // 成功撥打，直接返回
                    
                } catch (SecurityException e) {
                    Log.e(TAG, "直接撥打失敗（權限問題）: " + e.getMessage());
                } catch (Exception e) {
                    Log.e(TAG, "直接撥打失敗: " + e.getMessage());
                }
            }
            
            // 如果沒有權限或直接撥打失敗，嘗試使用 ACTION_DIAL（後備方案）
            // 注意：對於緊急號碼999，某些系統可能允許直接撥打
            try {
                // 即使沒有權限，也嘗試直接撥打（緊急號碼可能被允許）
                Intent callIntent = new Intent(Intent.ACTION_CALL);
                callIntent.setData(Uri.parse(phoneUri));
                callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(callIntent);
                Log.d(TAG, "緊急電話已撥打（無權限但系統允許）: " + phoneNumber);
                
                String cantoneseText = "正在撥打緊急電話：" + phoneNumber;
                String englishText = "Calling emergency number: " + phoneNumber;
                ttsManager.speak(cantoneseText, englishText, false);
                
            } catch (SecurityException e) {
                Log.e(TAG, "無法直接撥打，使用撥號界面: " + e.getMessage());
                // 最後的後備方案：打開撥號界面
                try {
                    Intent dialIntent = new Intent(Intent.ACTION_DIAL);
                    dialIntent.setData(Uri.parse(phoneUri));
                    dialIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(dialIntent);
                    
                    String cantoneseText = "已打開撥號界面，號碼已填入：" + phoneNumber + "，請按撥打按鈕";
                    String englishText = "Dialer opened, number " + phoneNumber + " is ready, please press call button";
                    ttsManager.speak(cantoneseText, englishText, false);
                    Log.d(TAG, "已打開撥號界面: " + phoneNumber);
                } catch (Exception ex) {
                    Log.e(TAG, "打開撥號界面失敗: " + ex.getMessage());
                    ttsManager.speakError("無法撥打緊急電話，請手動撥打" + phoneNumber);
                }
            } catch (Exception e) {
                Log.e(TAG, "撥打緊急電話失敗: " + e.getMessage());
                ttsManager.speakError("撥打緊急電話失敗，請手動撥打" + phoneNumber);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "撥打緊急電話失敗: " + e.getMessage());
            ttsManager.speakError("撥打緊急電話失敗，請手動撥打");
        }
    }
    
    private void logEmergencyEvent() {
        // 記錄緊急事件到日誌或本地數據庫
        Log.i(TAG, "緊急事件已記錄: " + System.currentTimeMillis());
        
        // 可以在這裡添加更多功能：
        // - 記錄GPS位置
        // - 保存到本地數據庫
        // - 上傳到雲端服務
        // - 發送給家人朋友
    }
    
    // 設置緊急訊息（可選功能，用於未來擴展）
    public void setEmergencyMessage(String message, String messageEn) {
        this.emergencyMessage = message;
        this.emergencyMessageEn = messageEn;
    }
    
    // 測試緊急功能（用於測試，不會真正發送）
    public void testEmergencyAlert() {
        String cantoneseText = "這是緊急功能測試，沒有發送實際求助信息";
        String englishText = "This is an emergency function test, no actual help request was sent";
        ttsManager.speak(cantoneseText, englishText, true);
        vibrationManager.vibrateSuccessPattern();
    }
}
