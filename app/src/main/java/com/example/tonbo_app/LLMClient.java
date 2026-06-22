package com.example.tonbo_app;

import android.content.Context;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * LLM Client for intelligent conversation（僅智譜 GLM-4-Flash 文本對話 API）
 */
public class LLMClient {
    private static final String TAG = "LLMClient";
    
    private static LLMClient instance;
    private OkHttpClient httpClient;
    private Gson gson;
    private LLMConfig config;
    private Context context;
    
    private static final String ZHIPU_API_URL = "https://open.bigmodel.cn/api/paas/v4/chat/completions";
    
    /**
     * Get singleton instance
     */
    public static synchronized LLMClient getInstance(Context context) {
        if (instance == null) {
            instance = new LLMClient(context);
        }
        return instance;
    }
    
    private LLMClient(Context context) {
        this.context = context.getApplicationContext();
        this.gson = new Gson();
        this.config = new LLMConfig(context);
        
        // Initialize HTTP client with timeout
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    }
    
    /**
     * Check if LLM is enabled
     */
    public boolean isEnabled() {
        return config.isEnabled();
    }
    
    /** 是否已配置非空 API Key（用於判斷是否走網絡 LLM） */
    public boolean hasConfiguredApiKey() {
        String key = config.getApiKey();
        return key != null && !key.trim().isEmpty();
    }
    
    /**
     * Get current provider
     */
    public String getProvider() {
        return config.getProvider();
    }
    
    /**
     * Send chat message to LLM
     * @param message User input text
     * @param history Conversation history (can be null)
     * @param callback Response callback
     */
    public void sendChatMessage(String message, List<ConversationManager.ConversationTurn> history, ChatCallback callback) {
        if (!config.isEnabled()) {
            callback.onError("LLM is disabled");
            return;
        }
        
        String apiKey = config.getApiKey();
        
        if (apiKey == null || apiKey.isEmpty()) {
            callback.onError("API key not configured");
            return;
        }
        
        Log.i(TAG, "LLM invoked → Zhipu GLM-4-Flash, userTextLen=" + (message != null ? message.length() : 0));
        sendZhipuRequest(message, history, apiKey, callback);
    }
    
    /**
     * Send request to Zhipu (GLM-4-Flash) API
     */
    private void sendZhipuRequest(String message, List<ConversationManager.ConversationTurn> history,
                                  String apiKey, ChatCallback callback) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", "glm-4-flash");
        requestBody.addProperty("temperature", 0.7);
        requestBody.addProperty("max_tokens", 1000);
        
        // Build messages array
        JsonArray messages = new JsonArray();
        
        // Add system message
        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        String systemPrompt = buildSystemPrompt();
        systemMsg.addProperty("content", systemPrompt);
        messages.add(systemMsg);
        
        // Add conversation history
        if (history != null && !history.isEmpty()) {
            for (ConversationManager.ConversationTurn turn : history) {
                JsonObject userMsg = new JsonObject();
                userMsg.addProperty("role", "user");
                userMsg.addProperty("content", turn.userInput);
                messages.add(userMsg);
                
                if (turn.assistantResponse != null && !turn.assistantResponse.isEmpty()) {
                    JsonObject assistantMsg = new JsonObject();
                    assistantMsg.addProperty("role", "assistant");
                    assistantMsg.addProperty("content", turn.assistantResponse);
                    messages.add(assistantMsg);
                }
            }
        }
        
        // Add current user message
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", message);
        messages.add(userMsg);
        
        requestBody.add("messages", messages);
        
        RequestBody body = RequestBody.create(
            requestBody.toString(),
            MediaType.parse("application/json; charset=utf-8")
        );
        
        // Zhipu API uses different auth format
        Request request = new Request.Builder()
            .url(ZHIPU_API_URL)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer " + apiKey)
            .build();
        
        sendRequest(request, message, history, callback);
    }

    /**
     * Build system prompt based on current language
     */
    private String buildSystemPrompt() {
        // Get current language
        android.content.SharedPreferences prefs = context.getSharedPreferences(
            "AppSettings", Context.MODE_PRIVATE);
        String language = prefs.getString("current_language", "cantonese");
        
        if ("cantonese".equals(language)) {
            return "你是一個友善的語音助手，專為視障人士設計。請用廣東話回應，回答要簡潔自然，適合語音播報。";
        } else if ("mandarin".equals(language)) {
            return "你是一個友善的語音助手，專為視障人士設計。請用普通話回應，回答要簡潔自然，適合語音播報。";
        } else {
            return "You are a friendly voice assistant designed for visually impaired users. Please respond in English, keep answers concise and natural, suitable for voice broadcast.";
        }
    }
    
    /**
     * Send HTTP request
     */
    private void sendRequest(Request request, String message,
                             List<ConversationManager.ConversationTurn> history,
                             ChatCallback callback) {
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "LLM API request failed", e);
                callback.onError("Network error: " + e.getMessage());
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    String errorMsg = "HTTP " + response.code() + ": " + response.message();
                    String errorBody = response.body() != null ? response.body().string() : "";
                    Log.e(TAG, errorMsg + " - " + errorBody);

                    callback.onError(errorMsg);
                    return;
                }
                try {
                    String responseBody = response.body().string();
                    JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                    
                    // Parse response (both APIs use similar format)
                    String aiResponse = null;
                    if (jsonResponse.has("choices")) {
                        JsonArray choices = jsonResponse.getAsJsonArray("choices");
                        if (choices.size() > 0) {
                            JsonObject choice = choices.get(0).getAsJsonObject();
                            if (choice.has("message")) {
                                JsonObject message = choice.getAsJsonObject("message");
                                if (message.has("content")) {
                                    aiResponse = message.get("content").getAsString();
                                }
                            }
                        }
                    }
                    
                    if (aiResponse != null && !aiResponse.isEmpty()) {
                        Log.i(TAG, "LLM HTTP success, replyLen=" + aiResponse.length());
                        callback.onResponse(aiResponse);
                    } else {
                        Log.w(TAG, "Empty response from LLM");
                        callback.onError("Empty response from server");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse LLM response", e);
                    callback.onError("Failed to parse response: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Test connection to LLM API
     */
    public void testConnection(ConnectionCallback callback) {
        if (!config.isEnabled()) {
            callback.onResult(false, "LLM is disabled");
            return;
        }
        
        sendChatMessage("你好", null, new ChatCallback() {
            @Override
            public void onResponse(String response) {
                callback.onResult(true, "Connection successful");
            }
            
            @Override
            public void onError(String error) {
                callback.onResult(false, "Connection failed: " + error);
            }
        });
    }
    
    /**
     * Chat callback interface
     */
    public interface ChatCallback {
        void onResponse(String response);
        void onError(String error);
    }
    
    /**
     * Connection test callback interface
     */
    public interface ConnectionCallback {
        void onResult(boolean success, String message);
    }
    
    /**
     * Release resources
     */
    public void release() {
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
        }
    }
}

