package com.example.tonbo_app;

import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;

/**
 * 緊急位置分享相關的輔助方法。
 * 僅供 NavigationActivity 使用，不影響現有緊急求助邏輯。
 */
public class EmergencyLocationHelper {

    /**
     * 構造緊急位置短信內容。
     *
     * @param latitude         當前緯度
     * @param longitude        當前經度
     * @param destinationName  當前導航目的地名稱（可為 null 或空）
     * @param batteryText      電量資訊（例如 \"78%\"，可為 null 或空）
     */
    public static String buildEmergencyMessage(
            double latitude,
            double longitude,
            String destinationName,
            String batteryText
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Emergency! I may need assistance.\n");
        sb.append("My current location:\n");

        String url = "https://maps.google.com/?q=" + latitude + "," + longitude;
        sb.append(url).append("\n");

        if (destinationName != null && !destinationName.trim().isEmpty()) {
            sb.append("\n");
            sb.append("If currently navigating, also include:\n");
            sb.append("Destination: ").append(destinationName.trim()).append("\n");
        }

        if (batteryText != null && !batteryText.trim().isEmpty()) {
            sb.append("\n");
            sb.append("Battery: ").append(batteryText.trim()).append("\n");
        }

        return sb.toString();
    }

    /**
     * 獲取電量百分比文本，例如 \"78%\"，獲取失敗返回 null。
     */
    public static String getBatteryLevelText(Context context) {
        try {
            Intent batteryStatus = context.registerReceiver(
                    null,
                    new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            );
            if (batteryStatus == null) return null;

            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level < 0 || scale <= 0) return null;

            int percent = (int) ((level / (float) scale) * 100);
            return percent + "%";
        } catch (Exception e) {
            return null;
        }
    }
}

