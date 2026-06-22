package com.example.tonbo_app;

import android.content.Context;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.os.Build;
import android.util.Log;

public class VibrationManager {
    private static final String TAG = "VibrationManager";
    private static VibrationManager instance;
    
    private Vibrator vibrator;
    private Context context;
    private boolean isEnabled = true;
    
    // 震動模式定義
    public static final int VIBRATION_CLICK = 50;          // 點擊反饋
    public static final int VIBRATION_FOCUS = 30;          // 焦點移動
    public static final int VIBRATION_LONG_PRESS = 200;    // 長按
    public static final int VIBRATION_EMERGENCY = 500;     // 緊急情況
    public static final int VIBRATION_SUCCESS = 150;       // 成功操作
    public static final int VIBRATION_ERROR = 300;         // 錯誤提示
    public static final int VIBRATION_NOTIFICATION = 100;  // 通知
    
    private VibrationManager(Context context) {
        this.context = context.getApplicationContext();
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }
    
    public static synchronized VibrationManager getInstance(Context context) {
        if (instance == null) {
            instance = new VibrationManager(context);
        }
        return instance;
    }
    
    public void vibrate(int duration) {
        if (!isEnabled) {
            Log.d(TAG, "震動已禁用");
            return;
        }
        
        if (vibrator == null || !vibrator.hasVibrator()) {
            Log.w(TAG, "設備不支持震動功能");
            return;
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8.0 及以上使用新的震動API
                VibrationEffect effect = VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE);
                vibrator.vibrate(effect);
            } else {
                // 舊版本API
                vibrator.vibrate(duration);
            }
            Log.d(TAG, "震動觸發: " + duration + "ms");
        } catch (Exception e) {
            Log.e(TAG, "震動失敗: " + e.getMessage());
        }
    }
    
    public void vibratePattern(long[] pattern, int repeat) {
        if (!isEnabled) {
            Log.d(TAG, "震動已禁用");
            return;
        }
        
        if (vibrator == null || !vibrator.hasVibrator()) {
            Log.w(TAG, "設備不支持震動功能");
            return;
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VibrationEffect effect = VibrationEffect.createWaveform(pattern, repeat);
                vibrator.vibrate(effect);
            } else {
                vibrator.vibrate(pattern, repeat);
            }
            Log.d(TAG, "模式震動觸發");
        } catch (Exception e) {
            Log.e(TAG, "模式震動失敗: " + e.getMessage());
        }
    }
    
    // 特定場景的震動方法
    public void vibrateClick() {
        vibrate(VIBRATION_CLICK);
    }
    
    public void vibrateFocus() {
        vibrate(VIBRATION_FOCUS);
    }
    
    public void vibrateLongPress() {
        vibrate(VIBRATION_LONG_PRESS);
    }
    
    public void vibrateEmergency() {
        vibrate(VIBRATION_EMERGENCY);
    }
    
    public void vibrateSuccess() {
        vibrate(VIBRATION_SUCCESS);
    }
    
    public void vibrateError() {
        vibrate(VIBRATION_ERROR);
    }
    
    public void vibrateNotification() {
        vibrate(VIBRATION_NOTIFICATION);
    }
    
    // 緊急求助的特殊震動模式
    public void vibrateEmergencyPattern() {
        long[] pattern = {0, 200, 100, 200, 100, 200}; // 三下長震動
        vibratePattern(pattern, -1);
    }
    
    // 成功操作的特殊震動模式
    public void vibrateSuccessPattern() {
        long[] pattern = {0, 100, 50, 100}; // 兩下短震動
        vibratePattern(pattern, -1);
    }
    
    // 錯誤提示的特殊震動模式
    public void vibrateErrorPattern() {
        long[] pattern = {0, 300, 100, 300}; // 兩下長震動
        vibratePattern(pattern, -1);
    }
    
    public void cancel() {
        if (vibrator != null) {
            vibrator.cancel();
        }
    }
    
    /**
     * 設置震動是否啟用
     */
    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
        Log.d(TAG, "震動功能已" + (enabled ? "啟用" : "禁用"));
    }
    
    /**
     * 檢查震動是否啟用
     */
    public boolean isEnabled() {
        return isEnabled;
    }
}
