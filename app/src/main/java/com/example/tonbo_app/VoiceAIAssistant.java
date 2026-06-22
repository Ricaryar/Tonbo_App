package com.example.tonbo_app;

import android.content.Context;
import android.util.Log;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * 語音助手
 * 整合對話管理、回應生成和命令識別
 */
public class VoiceAIAssistant {
    private static final String TAG = "VoiceAIAssistant";
    
    private ConversationManager conversationManager;
    private ConversationResponseGenerator responseGenerator;
    private String currentLanguage;
    private Random random = new Random();
    private Context context;
    
    // 意圖識別關鍵詞
    private Map<String, String[]> greetingKeywords;
    private Map<String, String[]> questionKeywords;
    private Map<String, String[]> farewellKeywords;
    
    public VoiceAIAssistant() {
        this.currentLanguage = detectDefaultLanguage();
        conversationManager = new ConversationManager();
        responseGenerator = new ConversationResponseGenerator();
        responseGenerator.setLanguage(currentLanguage);
        initializeIntentKeywords();
    }
    
    /**
     * 帶 Context 的構造函數
     */
    public VoiceAIAssistant(Context context) {
        this.context = context;
        this.currentLanguage = detectDefaultLanguage();
        conversationManager = new ConversationManager();
        responseGenerator = new ConversationResponseGenerator(context);
        // 設置對話管理器，讓回應生成器可以獲取上下文
        responseGenerator.setConversationManager(conversationManager);
        responseGenerator.setLanguage(currentLanguage);
        initializeIntentKeywords();
    }

    /**
     * 根據系統語言選擇預設語言，避免寫死為 cantonese。
     */
    private String detectDefaultLanguage() {
        Locale locale = Locale.getDefault();
        String language = locale != null ? locale.getLanguage() : "";
        if ("en".equalsIgnoreCase(language)) {
            return "english";
        }
        if ("zh".equalsIgnoreCase(language)) {
            String region = locale.getCountry();
            if ("HK".equalsIgnoreCase(region) || "MO".equalsIgnoreCase(region)) {
                return "cantonese";
            }
            return "mandarin";
        }
        return "mandarin";
    }
    
    /**
     * 初始化意圖關鍵詞
     */
    private void initializeIntentKeywords() {
        greetingKeywords = new HashMap<>();
        questionKeywords = new HashMap<>();
        farewellKeywords = new HashMap<>();
        
        // 問候語
        greetingKeywords.put("cantonese", new String[]{"你好", "早晨", "午安", "晚安", "嗨", "哈囉"});
        greetingKeywords.put("mandarin", new String[]{"你好", "早上好", "中午好", "晚上好", "嗨", "哈囉"});
        greetingKeywords.put("english", new String[]{"hello", "hi", "good morning", "good afternoon", "good evening", "hey"});
        
        // 問題關鍵詞
        questionKeywords.put("cantonese", new String[]{"點解", "點樣", "點", "為什麼", "什麼", "幾時", "邊個", "邊度"});
        questionKeywords.put("mandarin", new String[]{"為什麼", "怎麼", "什麼", "什麼時候", "誰", "哪裡", "如何"});
        questionKeywords.put("english", new String[]{"why", "how", "what", "when", "who", "where", "which"});
        
        // 告別語
        farewellKeywords.put("cantonese", new String[]{"再見", "拜拜", "下次見", "再會"});
        farewellKeywords.put("mandarin", new String[]{"再見", "拜拜", "下次見", "再會"});
        farewellKeywords.put("english", new String[]{"goodbye", "bye", "see you", "farewell"});
    }
    
    /**
     * 設置語言
     */
    public void setLanguage(String language) {
        this.currentLanguage = language;
        responseGenerator.setLanguage(language);
    }
    
    /**
     * 處理用戶輸入（同步版本）
     * @param userInput 用戶輸入的文本
     * @return 處理結果
     */
    public AssistantResponse processInput(String userInput) {
        if (userInput == null || userInput.trim().isEmpty()) {
            return new AssistantResponse("", false, null, "empty");
        }
        
        conversationManager.setCurrentState(ConversationManager.ConversationState.PROCESSING);
        
        // 1. 檢查是否為命令
        String command = checkForCommand(userInput);
        if (command != null) {
            // 是命令
            String response = generateCommandResponse(command, userInput);
            conversationManager.addTurn(userInput, response, true, command);
            conversationManager.setCurrentState(ConversationManager.ConversationState.IDLE);
            return new AssistantResponse(response, true, command, "command");
        }
        
        // 2. 識別意圖
        String intent = identifyIntent(userInput);
        
        // 3. 生成回應（同步，使用關鍵詞匹配）
        String response = generateChatResponse(userInput, intent);
        
        // 4. 記錄對話
        conversationManager.addTurn(userInput, response, false, intent);
        conversationManager.setCurrentState(ConversationManager.ConversationState.IDLE);
        
        return new AssistantResponse(response, false, null, intent);
    }
    
    /**
     * 處理用戶輸入（異步版本）
     * @param userInput 用戶輸入的文本
     * @param callback 回調接口
     */
    public void processInputAsync(String userInput, AssistantResponseCallback callback) {
        if (userInput == null || userInput.trim().isEmpty()) {
            callback.onResponse(new AssistantResponse("", false, null, "empty"));
            return;
        }
        
        conversationManager.setCurrentState(ConversationManager.ConversationState.PROCESSING);
        
        // 1. 檢查是否為命令
        String command = checkForCommand(userInput);
        if (command != null) {
            // 是命令
            String response = generateCommandResponse(command, userInput);
            conversationManager.addTurn(userInput, response, true, command);
            conversationManager.setCurrentState(ConversationManager.ConversationState.IDLE);
            callback.onResponse(new AssistantResponse(response, true, command, "command"));
            return;
        }
        
        // 2. 識別意圖
        String intent = identifyIntent(userInput);
        
        // 3. 生成回應（異步，使用關鍵詞匹配）
        generateChatResponseAsync(userInput, intent, new ResponseCallback() {
            @Override
            public void onResponse(String response) {
                // 4. 記錄對話
                conversationManager.addTurn(userInput, response, false, intent);
                conversationManager.setCurrentState(ConversationManager.ConversationState.IDLE);
                
                callback.onResponse(new AssistantResponse(response, false, null, intent));
            }
        });
    }
    
    /**
     * 助手回應回調接口
     */
    public interface AssistantResponseCallback {
        void onResponse(AssistantResponse response);
    }
    
    /**
     * 檢查是否為命令
     */
    private String checkForCommand(String userInput) {
        // 這裡可以調用 VoiceCommandManager 的 matchCommand 方法
        // 暫時返回 null，由外部處理命令識別
        return null;
    }
    
    /**
     * 識別用戶意圖
     */
    private String identifyIntent(String userInput) {
        // 若已啟用 LLM，意圖由模型理解，避免依賴本地硬編碼關鍵詞。
        if (responseGenerator != null && responseGenerator.isUsingLLM()) {
            return "chat";
        }

        String lowerInput = userInput.toLowerCase();
        
        // 檢查問候
        String[] greetings = greetingKeywords.get(currentLanguage);
        if (greetings != null) {
            for (String greeting : greetings) {
                if (lowerInput.contains(greeting.toLowerCase())) {
                    return "greeting";
                }
            }
        }
        
        // 檢查問題
        String[] questions = questionKeywords.get(currentLanguage);
        if (questions != null) {
            for (String question : questions) {
                if (lowerInput.contains(question.toLowerCase())) {
                    return "question";
                }
            }
        }
        
        // 檢查告別
        String[] farewells = farewellKeywords.get(currentLanguage);
        if (farewells != null) {
            for (String farewell : farewells) {
                if (lowerInput.contains(farewell.toLowerCase())) {
                    return "farewell";
                }
            }
        }
        
        // 檢查天氣相關關鍵詞
        if (lowerInput.contains("天氣") || lowerInput.contains("天气") || lowerInput.contains("weather") ||
            lowerInput.contains("幾多度") || lowerInput.contains("多少度") || lowerInput.contains("temperature") ||
            lowerInput.contains("溫度") || lowerInput.contains("温度") || lowerInput.contains("熱") ||
            lowerInput.contains("热") || lowerInput.contains("冷") || lowerInput.contains("hot") ||
            lowerInput.contains("cold")) {
            return "weather_topic";
        }
        
        // 檢查是否在討論某個話題
        if (conversationManager.isDiscussingTopic("天氣")) {
            return "weather_topic";
        }
        
        return "chat";
    }
    
    /**
     * 生成聊天回應（同步版本）
     */
    private String generateChatResponse(String userInput, String intent) {
        if (responseGenerator != null && responseGenerator.isUsingLLM()) {
            // 同步流程無法直接等待 LLM 異步結果，返回空以便走通用回應分支，
            // 真正對話請優先使用 processInputAsync。
            return enhanceResponseWithContext("", userInput);
        }

        // 使用回應生成器
        String response = responseGenerator.generateResponse(userInput);
        
        // 如果回應生成器沒有生成回應，根據意圖生成
        if (response == null || response.isEmpty()) {
            response = generateResponseByIntent(userInput, intent);
        }
        
        // 增強回應（添加上下文）
        response = enhanceResponseWithContext(response, userInput);
        
        return response;
    }
    
    /**
     * 生成聊天回應（異步版本）
     */
    private void generateChatResponseAsync(String userInput, String intent, ResponseCallback callback) {
        // 檢查是否需要獲取天氣信息
        if ("weather_topic".equals(intent) && context != null) {
            // 檢查是否為天氣查詢（包含溫度相關關鍵詞）
            String lowerInput = userInput.toLowerCase();
            if (lowerInput.contains("幾多度") || lowerInput.contains("多少度") || 
                lowerInput.contains("temperature") || lowerInput.contains("溫度") || 
                lowerInput.contains("温度")) {
                // 這是天氣查詢，應該獲取實際天氣信息
                // 注意：這裡需要 WeatherInfoManager，但目前代碼中沒有
                // 先使用關鍵詞匹配回應，後續可以集成天氣API
                Log.d(TAG, "檢測到天氣查詢意圖，但天氣API未集成，使用關鍵詞匹配");
            }
        }
        
        responseGenerator.generateResponseAsync(userInput, new ConversationResponseGenerator.ResponseCallback() {
            @Override
            public void onResponse(String response) {
                // 如果回應生成器沒有生成回應，根據意圖生成
                if (response == null || response.isEmpty()) {
                    response = generateResponseByIntent(userInput, intent);
                }
                
                // 增強回應（添加上下文）
                response = enhanceResponseWithContext(response, userInput);
                
                callback.onResponse(response);
            }
        });
    }
    
    /**
     * 回應回調接口
     */
    private interface ResponseCallback {
        void onResponse(String response);
    }
    
    /**
     * 根據意圖生成回應
     */
    private String generateResponseByIntent(String userInput, String intent) {
        // 當 LLM 可用時，避免使用硬編碼意圖模板。
        if (responseGenerator != null && responseGenerator.isUsingLLM()) {
            return "";
        }

        switch (intent) {
            case "greeting":
                return generateGreetingResponse();
            case "question":
                return generateQuestionResponse(userInput);
            case "farewell":
                return generateFarewellResponse();
            case "weather_topic":
                return generateWeatherResponse(userInput);
            default:
                return generateGenericResponse(userInput);
        }
    }
    
    /**
     * 生成問候回應
     */
    private String generateGreetingResponse() {
        String userName = conversationManager.getUserName();
        
        if (currentLanguage.equals("cantonese")) {
            String[] greetings = {
                "你好，" + userName + "，有咩可以幫到你？",
                "你好，" + userName + "，很高興為你服務",
                "你好，" + userName + "，我係你的語音助手"
            };
            return greetings[random.nextInt(greetings.length)];
        } else if (currentLanguage.equals("mandarin")) {
            String[] greetings = {
                "你好，" + userName + "，有什麼可以幫你的？",
                "你好，" + userName + "，很高興為你服務",
                "你好，" + userName + "，我是你的語音助手"
            };
            return greetings[random.nextInt(greetings.length)];
        } else {
            String[] greetings = {
                "Hello " + userName + ", how can I help you?",
                "Hi " + userName + ", nice to serve you",
                "Hello " + userName + ", I'm your voice assistant"
            };
            return greetings[random.nextInt(greetings.length)];
        }
    }
    
    /**
     * 生成問題回應
     */
    private String generateQuestionResponse(String userInput) {
        if (currentLanguage.equals("cantonese")) {
            return "呢個問題幾有趣，讓我諗下點答你";
        } else if (currentLanguage.equals("mandarin")) {
            return "這個問題很有趣，讓我想想怎麼回答你";
        } else {
            return "That's an interesting question, let me think about it";
        }
    }
    
    /**
     * 生成告別回應
     */
    private String generateFarewellResponse() {
        if (currentLanguage.equals("cantonese")) {
            String[] farewells = {
                "再見，有需要隨時叫我",
                "再見，保重",
                "再見，祝你一切順利"
            };
            return farewells[random.nextInt(farewells.length)];
        } else if (currentLanguage.equals("mandarin")) {
            String[] farewells = {
                "再見，有需要隨時叫我",
                "再見，保重",
                "再見，祝你一切順利"
            };
            return farewells[random.nextInt(farewells.length)];
        } else {
            String[] farewells = {
                "Goodbye, call me anytime you need",
                "Goodbye, take care",
                "Goodbye, all the best"
            };
            return farewells[random.nextInt(farewells.length)];
        }
    }
    
    /**
     * 生成天氣相關回應
     */
    private String generateWeatherResponse(String userInput) {
        // 使用回應生成器
        return responseGenerator.generateResponse(userInput);
    }
    
    /**
     * 生成通用回應
     */
    private String generateGenericResponse(String userInput) {
        // 使用回應生成器
        String response = responseGenerator.generateResponse(userInput);
        if (response == null || response.isEmpty()) {
            // 如果回應生成器無法生成，使用上下文增強
            response = enhanceResponseWithContext("", userInput);
        }
        return response;
    }
    
    /**
     * 生成命令回應
     */
    private String generateCommandResponse(String command, String userInput) {
        if (currentLanguage.equals("cantonese")) {
            return "好的，我立即為你執行";
        } else if (currentLanguage.equals("mandarin")) {
            return "好的，我立即為你執行";
        } else {
            return "Okay, I'll do that for you";
        }
    }
    
    /**
     * 使用上下文增強回應
     */
    private String enhanceResponseWithContext(String baseResponse, String userInput) {
        // 如果有基礎回應，直接返回，不要重複用戶的話
        if (baseResponse != null && !baseResponse.isEmpty()) {
            return baseResponse;
        }
        
        // 如果沒有基礎回應，生成真正的對話回應，不要重複用戶的話
        if (currentLanguage.equals("cantonese")) {
            String[] responses = {
                "我明白，繼續講", "係啊，我聽到", "嗯，我理解", "好，繼續",
                "有咩可以幫你？", "我聽到，然後呢？", "明白，繼續", "好，我明白"
            };
            return responses[random.nextInt(responses.length)];
        } else if (currentLanguage.equals("mandarin")) {
            String[] responses = {
                "我明白，繼續說", "是啊，我聽到", "嗯，我理解", "好，繼續",
                "有什麼可以幫你？", "我聽到，然後呢？", "明白，繼續", "好，我明白"
            };
            return responses[random.nextInt(responses.length)];
        } else {
            String[] responses = {
                "I understand, continue", "Yes, I hear you", "Hmm, I see", "Okay, go on",
                "How can I help you?", "I hear you, what's next?", "Got it, continue", "Okay, I understand"
            };
            return responses[random.nextInt(responses.length)];
        }
    }
    
    /**
     * 獲取對話管理器
     */
    public ConversationManager getConversationManager() {
        return conversationManager;
    }
    
    /**
     * 助手回應結果
     */
    public static class AssistantResponse {
        public String response;        // 回應文本
        public boolean isCommand;      // 是否為命令
        public String commandType;     // 命令類型
        public String intent;          // 識別的意圖
        
        public AssistantResponse(String response, boolean isCommand, String commandType, String intent) {
            this.response = response;
            this.isCommand = isCommand;
            this.commandType = commandType;
            this.intent = intent;
        }
    }
}

