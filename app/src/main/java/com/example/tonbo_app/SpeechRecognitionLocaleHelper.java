package com.example.tonbo_app;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Android SpeechRecognizer 語言選擇。
 * 離線時 Google 語音包通常不支援粵語 zh-HK，需回退到繁中/普通話。
 */
public final class SpeechRecognitionLocaleHelper {
    private static final String TAG = "SpeechLocaleHelper";

    private SpeechRecognitionLocaleHelper() {}

    public static List<Locale> buildLocaleChain(String appLanguage, boolean preferOffline) {
        List<Locale> chain = new ArrayList<>();
        if ("english".equals(appLanguage)) {
            chain.add(Locale.US);
            chain.add(Locale.UK);
            chain.add(Locale.ENGLISH);
        } else if ("mandarin".equals(appLanguage)) {
            chain.add(Locale.SIMPLIFIED_CHINESE);
            chain.add(Locale.CHINA);
            if (preferOffline) {
                chain.add(Locale.TRADITIONAL_CHINESE);
            }
        } else {
            if (preferOffline) {
                chain.add(Locale.TRADITIONAL_CHINESE);
                chain.add(Locale.SIMPLIFIED_CHINESE);
                chain.add(new Locale("zh", "HK"));
                Log.i(TAG, "離線模式：粵語語音識別改用繁體中文/普通話");
            } else {
                chain.add(new Locale("zh", "HK"));
                chain.add(new Locale("yue", "HK"));
                chain.add(Locale.TRADITIONAL_CHINESE);
                chain.add(Locale.SIMPLIFIED_CHINESE);
            }
        }
        return chain;
    }

    public static boolean isLanguageError(int errorCode) {
        return errorCode == 12 || errorCode == 13 || errorCode == 14;
    }
}
