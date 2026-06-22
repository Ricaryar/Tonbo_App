package com.example.tonbo_app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.util.Log;

import java.util.Locale;

/**
 * 語言管理器
 * 負責應用程序的語言切換和持久化
 */
public class LocaleManager {
    private static final String TAG = "LocaleManager";
    private static final String PREF_NAME = "TonboLanguage";
    private static final String KEY_LANGUAGE = "selected_language";
    
    private static LocaleManager instance;
    private Context context;
    private String currentLanguage;
    
    private LocaleManager(Context context) {
        this.context = context.getApplicationContext();
        loadSavedLanguage();
    }
    
    public static synchronized LocaleManager getInstance(Context context) {
        if (instance == null) {
            instance = new LocaleManager(context);
        }
        return instance;
    }
    
    /**
     * 載入保存的語言設置
     */
    private void loadSavedLanguage() {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        currentLanguage = prefs.getString(KEY_LANGUAGE, "english"); // 預設為英文
        Log.d(TAG, "載入語言設置: " + currentLanguage);
    }
    
    /**
     * 保存語言設置
     */
    private void saveLanguage(String language) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LANGUAGE, language).apply();
        currentLanguage = language;
        Log.d(TAG, "保存語言設置: " + language);
    }
    
    /**
     * 獲取當前語言
     */
    public String getCurrentLanguage() {
        return currentLanguage;
    }
    
    /**
     * 設置應用語言
     */
    public void setLanguage(Context context, String language) {
        saveLanguage(language);
        updateResources(context, language);
    }
    
    /**
     * 更新資源配置
     */
    public Context updateResources(Context context, String language) {
        Locale locale = getLocaleFromLanguage(language);
        Locale.setDefault(locale);
        
        Resources resources = context.getResources();
        Configuration configuration = new Configuration(resources.getConfiguration());
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocale(locale);
            context = context.createConfigurationContext(configuration);
        } else {
            configuration.locale = locale;
            resources.updateConfiguration(configuration, resources.getDisplayMetrics());
        }
        
        Log.d(TAG, "更新資源配置: " + language + " -> " + locale.toString());
        return context;
    }
    
    /**
     * 應用到Activity
     */
    public void applyLanguageToActivity(Activity activity) {
        updateResources(activity, currentLanguage);
    }
    
    /**
     * 根據語言代碼獲取Locale
     */
    private Locale getLocaleFromLanguage(String language) {
        switch (language) {
            case "cantonese":
                return new Locale("zh", "HK"); // 繁體中文（香港）
            case "english":
                return Locale.ENGLISH; // 英文
            case "mandarin":
                return Locale.SIMPLIFIED_CHINESE; // 簡體中文
            default:
                return new Locale("zh", "HK");
        }
    }
    
    /**
     * 獲取語言對應的Locale標籤
     */
    public String getLocaleTag(String language) {
        switch (language) {
            case "cantonese":
                return "zh-HK";
            case "english":
                return "en";
            case "mandarin":
                return "zh-CN";
            default:
                return "zh-HK";
        }
    }
    
    /**
     * 獲取語言顯示名稱
     */
    public String getLanguageDisplayName(String language) {
        switch (language) {
            case "cantonese":
                return "繁體中文";
            case "english":
                return "English";
            case "mandarin":
                return "简体中文";
            default:
                return "繁體中文";
        }
    }
    
    /**
     * 獲取語言按鈕文字
     */
    public String getLanguageButtonText(String language) {
        switch (language) {
            case "cantonese":
                return "廣";
            case "english":
                return "EN";
            case "mandarin":
                return "普";
            default:
                return "廣";
        }
    }
}

