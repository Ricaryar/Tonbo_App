package com.example.tonbo_app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * LLM configuration manager
 * Manages LLM API keys and settings
 */
public class LLMConfig {
    private static final String TAG = "LLMConfig";
    private static final String PREFS_NAME = "LLMConfig";
    
    // Configuration keys
    private static final String KEY_PROVIDER = "llm_provider";
    private static final String KEY_API_KEY = "llm_api_key";
    private static final String KEY_ENABLED = "llm_enabled";
    
    // Default values
    private static final String DEFAULT_PROVIDER = "zhipu";  // Use GLM-4-Flash as default (free)
    private static final boolean DEFAULT_ENABLED = true;  // Enable by default
    
    // API keys：在 LLMConfigLocal.java 中配置 ZHIPU_API_KEY（不提交 Git）
    private static String ZHIPU_API_KEY = getLocalApiKey("ZHIPU_API_KEY", "");
    
    /**
     * Get API key from local config class (if exists)
     */
    private static String getLocalApiKey(String keyName, String defaultValue) {
        try {
            // Try to load from LLMConfigLocal class (not in Git)
            Class<?> localConfigClass = Class.forName("com.example.tonbo_app.LLMConfigLocal");
            java.lang.reflect.Field field = localConfigClass.getField(keyName);
            return (String) field.get(null);
        } catch (Exception e) {
            // LLMConfigLocal not found, return default
            return defaultValue;
        }
    }
    
    private Context context;
    private SharedPreferences prefs;
    
    public LLMConfig(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        // Initialize with default API keys if not set
        initializeDefaultKeys();
    }
    
    /**
     * Initialize default API keys
     */
    private void initializeDefaultKeys() {
        String provider = prefs.getString(KEY_PROVIDER, null);
        String apiKey = prefs.getString(KEY_API_KEY, null);
        
        // 僅使用智譜 GLM；舊版若存成 deepseek 則遷移到 zhipu
        if (provider == null || apiKey == null || "deepseek".equals(provider)) {
            setProvider("zhipu");
            setApiKey(ZHIPU_API_KEY);
            boolean hasKey = ZHIPU_API_KEY != null && !ZHIPU_API_KEY.isEmpty();
            setEnabled(hasKey);
            Log.d(TAG, "LLM provider=zhipu, enabled=" + hasKey);
        }
    }
    
    /**
     * Get current provider
     */
    public String getProvider() {
        return prefs.getString(KEY_PROVIDER, DEFAULT_PROVIDER);
    }
    
    /**
     * Set provider（僅支持 zhipu，其它值一律寫入 zhipu）
     */
    public void setProvider(String provider) {
        if (provider != null && !"zhipu".equals(provider)) {
            Log.w(TAG, "Ignoring provider " + provider + ", using zhipu only");
        }
        prefs.edit().putString(KEY_PROVIDER, "zhipu").apply();
        if (ZHIPU_API_KEY != null && !ZHIPU_API_KEY.isEmpty()) {
            setApiKey(ZHIPU_API_KEY);
        } else {
            Log.w(TAG, "Zhipu API key not configured in LLMConfigLocal.java");
        }
        Log.d(TAG, "Provider set to: zhipu");
    }
    
    /**
     * Get API key
     */
    public String getApiKey() {
        return prefs.getString(KEY_API_KEY, "");
    }
    
    /**
     * Set API key
     */
    public void setApiKey(String apiKey) {
        prefs.edit().putString(KEY_API_KEY, apiKey).apply();
        Log.d(TAG, "API key updated");
    }
    
    /**
     * Check if LLM is enabled
     */
    public boolean isEnabled() {
        return prefs.getBoolean(KEY_ENABLED, DEFAULT_ENABLED);
    }
    
    /**
     * Enable or disable LLM
     */
    public void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
        Log.d(TAG, "LLM enabled: " + enabled);
    }
    
    /**
     * Switch to GLM-4-Flash (Zhipu)
     */
    public void useGLM4Flash() {
        setProvider("zhipu");
        if (ZHIPU_API_KEY != null && !ZHIPU_API_KEY.isEmpty()) {
            setApiKey(ZHIPU_API_KEY);
            setEnabled(true);
            Log.d(TAG, "Switched to GLM-4-Flash");
        } else {
            Log.w(TAG, "Zhipu API key not configured, LLM will be disabled");
            setEnabled(false);
        }
    }

    /**
     * Get configuration summary
     */
    public String getConfigSummary() {
        return String.format(
            "LLM Config:\n" +
            "  Enabled: %s\n" +
            "  Provider: %s\n" +
            "  API Key: %s...",
            isEnabled(),
            getProvider(),
            getApiKey().length() > 10 ? getApiKey().substring(0, 10) : "Not set"
        );
    }
}

