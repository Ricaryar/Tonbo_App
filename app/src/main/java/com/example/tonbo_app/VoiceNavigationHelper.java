package com.example.tonbo_app;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

/**
 * 從任意頁面執行語音導航命令。
 */
public final class VoiceNavigationHelper {

    private static final String TAG = "VoiceNavigationHelper";

    private VoiceNavigationHelper() {
    }

    public static void execute(Activity activity, String command, String language) {
        if (activity == null || command == null || activity.isFinishing()) {
            return;
        }
        Log.d(TAG, "execute: " + command);

        BaseAccessibleActivity base = activity instanceof BaseAccessibleActivity
                ? (BaseAccessibleActivity) activity : null;

        switch (command) {
            case "open_environment":
                speakNav(base, language, "正在打開環境識別", "正在打开环境识别", "Opening environment recognition");
                launch(activity, RealAIDetectionActivity.class, language);
                break;
            case "open_document":
                speakNav(base, language, "正在打開閱讀助手", "正在打开阅读助手", "Opening document assistant");
                launch(activity, DocumentCurrencyActivity.class, language);
                break;
            case "open_find":
                speakNav(base, language, "正在打開尋找物品", "正在打开寻找物品", "Opening find items");
                launch(activity, FindItemsActivity.class, language);
                break;
            case "open_assistance":
                speakNav(base, language, "正在打開即時協助", "正在打开即时协助", "Opening live assistance");
                launch(activity, InstantAssistanceActivity.class, language);
                break;
            case "open_settings":
                speakNav(base, language, "正在打開系統設定", "正在打开系统设置", "Opening system settings");
                launch(activity, SettingsActivity.class, language);
                break;
            case "open_gesture":
                speakNav(base, language, "正在打開手勢管理", "正在打开手势管理", "Opening gesture management");
                launch(activity, GestureManagementActivity.class, language);
                break;
            case "open_voice":
                speakNav(base, language, "正在打開語音助手", "正在打开语音助手", "Opening voice assistant");
                launch(activity, VoiceCommandActivity.class, language);
                break;
            case "go_home":
                speakNav(base, language, "返回主頁", "返回主页", "Returning to home");
                Intent home = new Intent(activity, MainActivity.class);
                home.putExtra("language", language);
                home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                activity.startActivity(home);
                if (!(activity instanceof MainActivity)) {
                    activity.finish();
                }
                break;
            case "go_back":
                speakInfo(base, language, "返回上一頁", "返回上一页", "Going back");
                activity.finish();
                break;
            case "emergency":
                speakInfo(base, language, "觸發緊急求助", "触发紧急求助", "Triggering emergency alert");
                EmergencyManager.getInstance(activity).triggerEmergencyAlert();
                break;
            case "tell_time":
                if (base != null) {
                    base.announceCurrentTime();
                }
                break;
            case "start_detection":
            case "stop_detection":
            case "describe_environment":
            case "pause_detection":
            case "resume_detection":
            case "toggle_detection":
                if (activity instanceof RealAIDetectionActivity) {
                    Intent broadcast = new Intent("com.example.tonbo_app.VOICE_COMMAND");
                    broadcast.putExtra("action", command);
                    activity.sendBroadcast(broadcast);
                    speakInfo(base, language, "已執行環境識別指令", "已执行环境识别指令", "Environment command sent");
                } else {
                    speakInfo(base, language, "請先打開環境識別", "请先打开环境识别", "Please open environment recognition first");
                }
                break;
            case "where_am_i":
            case "what_page":
            case "current_function":
                announceCurrentPage(activity, base, language);
                break;
            default:
                if (base != null) {
                    base.announceError("english".equals(language) ? "Unknown command" : "未知命令");
                }
                break;
        }
    }

    private static void launch(Activity activity, Class<?> cls, String language) {
        Intent intent = new Intent(activity, cls);
        intent.putExtra("language", language);
        activity.startActivity(intent);
    }

    private static void speakNav(BaseAccessibleActivity base, String language,
                                 String cantonese, String mandarin, String english) {
        if (base != null) {
            base.announceNavigation(localize(language, cantonese, mandarin, english));
        }
    }

    private static void speakInfo(BaseAccessibleActivity base, String language,
                                  String cantonese, String mandarin, String english) {
        if (base != null) {
            base.announceInfo(localize(language, cantonese, mandarin, english));
        }
    }

    private static String localize(String language, String cantonese, String mandarin, String english) {
        if ("english".equals(language)) {
            return english;
        }
        if ("mandarin".equals(language)) {
            return mandarin;
        }
        return cantonese;
    }

    private static void announceCurrentPage(Activity activity, BaseAccessibleActivity base, String language) {
        String pageName = activity.getClass().getSimpleName();
        String message;
        if ("english".equals(language)) {
            message = "Current page: " + pageName;
        } else if ("mandarin".equals(language)) {
            message = "当前页面：" + pageName;
        } else {
            message = "而家喺：" + pageName;
        }
        if (base != null) {
            base.announceInfo(message);
        }
    }
}
