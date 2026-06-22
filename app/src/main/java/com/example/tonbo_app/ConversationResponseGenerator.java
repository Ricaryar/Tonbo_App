package com.example.tonbo_app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Conversation response generator
 * Generates intelligent responses based on user's voice input
 * Uses keyword matching pattern
 */
public class ConversationResponseGenerator {
    private static final String TAG = "ConversationResponseGenerator";
    
    private String currentLanguage = "cantonese";
    private Random random = new Random();
    private Context context;
    private ConversationManager conversationManager; // Used to get conversation context
    private LLMClient llmClient; // 云端 LLM 客户端
    private OfflineLLMClient offlineLLMClient; // 离线 LLM 客户端
    private boolean useLLM = true; // Whether to use LLM (default: true)
    
    // Keyword to response template mapping (Cantonese)
    private Map<String, String[]> cantoneseResponses = new HashMap<>();
    // Keyword to response template mapping (Mandarin)
    private Map<String, String[]> mandarinResponses = new HashMap<>();
    // Keyword to response template mapping (English)
    private Map<String, String[]> englishResponses = new HashMap<>();
    
    public ConversationResponseGenerator() {
        initializeResponses();
    }
    
    /**
     * Constructor with Context
     */
    public ConversationResponseGenerator(Context context) {
        this.context = context;
        initializeResponses();
        // Initialize LLM clients (offline + cloud)
        if (context != null) {
            llmClient = LLMClient.getInstance(context);
            offlineLLMClient = OfflineLLMClient.getInstance(context);
            LLMConfig config = new LLMConfig(context);
            useLLM = config.isEnabled();
        }
    }
    
    /**
     * Enable or disable LLM
     */
    public void setUseLLM(boolean use) {
        this.useLLM = use;
        if (use && context != null && llmClient == null) {
            llmClient = LLMClient.getInstance(context);
        }
    }
    
    /**
     * 离线模型是否已就绪（与网络无关）
     */
    private boolean isOfflineLLMReady() {
        return useLLM
                && offlineLLMClient != null
                && OfflineLLMClient.isOfflineModeEnabled(context)
                && offlineLLMClient.isReady();
    }

    /**
     * 是否使用离线 LLM（当前请求会走本地模型：无网或云端不可用时）
     */
    public boolean isUsingOfflineLLM() {
        return isOfflineLLMReady() && !isUsingCloudLLM();
    }

    /** 云端 LLM 是否已配置（不要求有网） */
    public boolean isCloudLLMConfigured() {
        return useLLM && llmClient != null && llmClient.isEnabled() && llmClient.hasConfiguredApiKey();
    }

    /**
     * 是否使用云端 LLM（已配置且设备已联网）
     */
    public boolean isUsingCloudLLM() {
        return isCloudLLMConfigured()
                && context != null
                && NetworkUtils.isNetworkAvailable(context);
    }

    /** @deprecated 使用 isUsingOfflineLLM() 或 isUsingCloudLLM() */
    public boolean isUsingLLM() {
        return isUsingOfflineLLM() || isUsingCloudLLM();
    }
    
    /**
     * Set language
     */
    public void setLanguage(String language) {
        this.currentLanguage = language;
    }
    
    /**
     * Set conversation manager (for getting context)
     */
    public void setConversationManager(ConversationManager manager) {
        this.conversationManager = manager;
    }
    
    /**
     * Get conversation context
     * Used for multi-turn conversation context management
     */
    private String getConversationContext() {
        if (conversationManager == null) {
            return null;
        }
        
        // Get recent 5 conversation turns as context (increase context length to improve understanding)
        List<ConversationManager.ConversationTurn> recentHistory = 
            conversationManager.getRecentHistory(5);
        
        if (recentHistory == null || recentHistory.isEmpty()) {
            return null;
        }
        
        // Build clearer context format
        StringBuilder context = new StringBuilder();
        context.append("以下是之前的對話歷史：\n\n");
        
        for (int i = 0; i < recentHistory.size(); i++) {
            ConversationManager.ConversationTurn turn = recentHistory.get(i);
            context.append("[").append(i + 1).append("] 用戶: ").append(turn.userInput).append("\n");
            if (turn.assistantResponse != null && !turn.assistantResponse.isEmpty()) {
                context.append("    助手: ").append(turn.assistantResponse).append("\n");
            }
            context.append("\n");
        }
        
        context.append("請基於以上對話歷史，理解上下文並給出合適的回應。");
        
        return context.toString().trim();
    }
    
    /**
     * Post-process response to make it more natural
     */
    private String postProcessResponse(String response) {
        if (response == null || response.isEmpty()) {
            return response;
        }
        
        // Remove possible AI markers or formatting
        response = response.trim();
        
        // Remove common AI opening words
        String[] aiPrefixes = currentLanguage.equals("english") ? 
            new String[]{"As an AI", "I'm an AI", "As a language model"} :
            new String[]{"作為AI", "我是一個AI", "作為語言模型", "我係一個AI"};
        
        for (String prefix : aiPrefixes) {
            if (response.startsWith(prefix)) {
                // Start after first period
                int periodIndex = response.indexOf("。");
                if (periodIndex < 0) periodIndex = response.indexOf(".");
                if (periodIndex >= 0 && periodIndex < response.length() - 1) {
                    response = response.substring(periodIndex + 1).trim();
                }
            }
        }
        
        // Ensure response length is appropriate (allow longer responses for voice)
        // If exceeds 200 characters, truncate at appropriate position
        if (response.length() > 200) {
            // Try to truncate at period
            int lastPeriod = response.lastIndexOf("。", 180);
            if (lastPeriod < 0) lastPeriod = response.lastIndexOf(".", 180);
            if (lastPeriod < 0) lastPeriod = response.lastIndexOf("，", 180);
            if (lastPeriod < 0) lastPeriod = response.lastIndexOf(",", 180);
            
            if (lastPeriod > 0 && lastPeriod < 190) {
                response = response.substring(0, lastPeriod + 1);
            } else {
                // If no suitable truncation point found, truncate at 180 characters
                response = response.substring(0, 177) + "...";
            }
        }
        
        return response;
    }
    
    /**
     * Initialize response templates
     */
    private void initializeResponses() {
        // Cantonese responses (more natural expressions)
        cantoneseResponses.put("天氣", new String[]{
            "係啊，今日天氣真係好好，好適合出街", "天氣真係唔錯，心情都好好", "係啊，天氣幾好，你有冇出街啊？"
        });
        cantoneseResponses.put("幾多度", new String[]{
            "等我幫你查下今日溫度", "我幫你睇下天氣", "等我查下天氣資料"
        });
        cantoneseResponses.put("溫度", new String[]{
            "等我幫你查下溫度", "我幫你睇下天氣", "等我查下天氣資料"
        });
        cantoneseResponses.put("熱", new String[]{
            "係啊，今日真係幾熱，記得多飲水", "天氣熱要小心防曬", "熱就開冷氣或者去陰涼地方"
        });
        cantoneseResponses.put("冷", new String[]{
            "係啊，今日真係幾凍，記得多著衫", "天氣凍要保暖", "凍就著多件衫，注意保暖"
        });
        String[] hkSightseeingCantonese = new String[]{
                "香港值得去嘅地方好多，例如維港夜景、太平山頂、星光大道、西九文化區、香港故宮文化博物館、中環海濱、旺角、銅鑼灣、淺水灣等，可以按你嘅體力同交通安排半日或一日行程。",
                "如果你想輕鬆行吓，可以考慮尖沙咀海旁、金紫荊廣場一帶；想購物就去銅鑼灣或旺角；想睇景就上太平山或者坐天星小輪過海，都好有特色。"
        };
        cantoneseResponses.put("好去處", hkSightseeingCantonese);
        cantoneseResponses.put("好去处", hkSightseeingCantonese);
        cantoneseResponses.put("景點", new String[]{
                "香港熱門景點包括維港、山頂、廟街夜市、女人街、赤柱、大澳等，郊野亦有麥理浩徑一段可以量力行。你講多啲偏好，我可以幫你收窄路線。",
                "市區可以行中上環、PMQ、大館；想文化體驗就去西九故宮或藝術公園；親子可考慮海洋公園或科學館，視乎你嘅興趣。"
        });
        cantoneseResponses.put("開心", new String[]{
            "聽到你開心我都好開心，希望你一直保持開心", "開心就好，保持呢個狀態", "好開心聽到你咁講"
        });
        cantoneseResponses.put("累", new String[]{
            "辛苦你啦，記得休息下，身體緊要", "要好好休息，唔好太勉強自己", "注意身體，累就休息下"
        });
        cantoneseResponses.put("謝謝", new String[]{
            "唔使客氣，應該嘅", "唔使多謝，有需要隨時搵我", "隨時可以幫你，唔使客氣"
        });
        cantoneseResponses.put("你好", new String[]{
            "你好，有咩可以幫到你？", "你好，我係你的助手，有咩可以幫手？", "你好，好高興同你傾計"
        });
        cantoneseResponses.put("再見", new String[]{
            "再見，有需要隨時叫我", "再見，保重，有咩事隨時搵我", "再見，祝你一切順利"
        });
        
        // Mandarin responses (more natural expressions)
        mandarinResponses.put("天氣", new String[]{
            "是啊，今天天氣真的很好，很適合出門", "天氣真不錯，心情也變好了", "是啊，天氣挺好的，你有出門嗎？"
        });
        mandarinResponses.put("多少度", new String[]{
            "等我幫你查一下今天溫度", "我幫你看一下天氣", "等我查一下天氣資料"
        });
        mandarinResponses.put("溫度", new String[]{
            "等我幫你查一下溫度", "我幫你看一下天氣", "等我查一下天氣資料"
        });
        mandarinResponses.put("熱", new String[]{
            "是啊，今天真的很熱，記得多喝水", "天氣熱要小心防曬", "熱就開空調或者去陰涼地方"
        });
        mandarinResponses.put("冷", new String[]{
            "是啊，今天真的很冷，記得多穿衣服", "天氣冷要保暖", "冷就多穿點衣服，注意保暖"
        });
        String[] hkSightseeingMandarin = new String[]{
                "香港值得去的地方很多，例如维港夜景、太平山顶、星光大道、西九文化区、香港故宫文化博物馆、中环海滨、旺角、铜锣湾、浅水湾等，可以按体力和交通安排半日或一日游。",
                "想轻松走走可看尖沙咀海滨、金紫荆一带；购物可选铜锣湾或旺角；看景可上太平山或坐天星小轮过海，都很有特色。"
        };
        mandarinResponses.put("好去处", hkSightseeingMandarin);
        mandarinResponses.put("景点", new String[]{
                "香港常见景点有维港、山顶、庙街夜市、女人街、赤柱、大澳等，郊区也有麦理浩径等徒步路线可按体力选择。可以说说你更喜欢逛街、看景还是文化场馆。",
                "市区可逛中环上环、大馆、PMQ；文化体验可选西九故宫或艺术公园；亲子可考虑海洋公园或科学馆，看你兴趣而定。"
        });
        mandarinResponses.put("開心", new String[]{
            "聽到你開心我也很開心，希望你一直保持開心", "開心就好，保持這個狀態", "很高興聽到你這麼說"
        });
        mandarinResponses.put("累", new String[]{
            "辛苦了，記得休息一下，身體重要", "要好好休息，別太勉強自己", "注意身體，累了就休息一下"
        });
        mandarinResponses.put("謝謝", new String[]{
            "不客氣，應該的", "不用謝，有需要隨時找我", "隨時可以幫你，不用客氣"
        });
        mandarinResponses.put("你好", new String[]{
            "你好，有什麼可以幫你的？", "你好，我是你的助手，有什麼可以幫忙的？", "你好，很高興和你聊天"
        });
        mandarinResponses.put("再見", new String[]{
            "再見，有需要隨時叫我", "再見，保重，有什麼事隨時找我", "再見，祝你一切順利"
        });
        
        // English responses (more natural expressions)
        englishResponses.put("weather", new String[]{
            "Yes, the weather is really nice today, perfect for going out", "The weather is great, it makes me feel good too", "Yes, the weather is good, did you go out?"
        });
        englishResponses.put("temperature", new String[]{
            "Let me check the temperature for you", "I'll look up the weather for you", "Let me check the weather information"
        });
        englishResponses.put("how hot", new String[]{
            "Let me check the temperature for you", "I'll look up the weather for you", "Let me check the weather information"
        });
        englishResponses.put("hot", new String[]{
            "Yes, it's really hot today, remember to drink more water", "Hot weather requires sun protection", "When it's hot, turn on the AC or go to a shady place"
        });
        englishResponses.put("cold", new String[]{
            "Yes, it's really cold today, remember to wear more clothes", "Cold weather requires keeping warm", "When it's cold, wear more clothes and stay warm"
        });
        String[] hkSightseeingEnglish = new String[]{
                "Hong Kong has plenty to offer: Victoria Harbour views, the Peak, Avenue of Stars, West Kowloon Cultural District, Hong Kong Palace Museum, Central waterfront, Mong Kok, Causeway Bay, Repulse Bay, and more. Pick based on your energy and transport for a half-day or full day.",
                "For an easy walk try the Tsim Sha Tsui promenade or Golden Bauhinia Square; for shopping try Causeway Bay or Mong Kok; for views take the Peak Tram or the Star Ferry across the harbour."
        };
        englishResponses.put("good places to visit", hkSightseeingEnglish);
        englishResponses.put("places to visit", hkSightseeingEnglish);
        englishResponses.put("things to do", hkSightseeingEnglish);
        englishResponses.put("happy", new String[]{
            "I'm glad to hear you're happy, I hope you stay happy", "That's great, keep that feeling", "So happy to hear that"
        });
        englishResponses.put("tired", new String[]{
            "You've worked hard, remember to rest, your health matters", "Take good care, don't push yourself too hard", "Take care of yourself, rest when you're tired"
        });
        englishResponses.put("thank", new String[]{
            "You're welcome, my pleasure", "No problem, feel free to ask anytime", "Anytime, don't hesitate to ask"
        });
        englishResponses.put("hello", new String[]{
            "Hello, how can I help you?", "Hello, I'm your assistant, what can I do for you?", "Hello, nice to chat with you"
        });
        englishResponses.put("bye", new String[]{
            "Goodbye, call me anytime you need", "Goodbye, take care, feel free to reach out", "Goodbye, all the best"
        });
    }
    
    /**
     * Generate response (synchronous version, using keyword matching)
     * @param userText User input text
     * @return Response text, returns null if cannot generate
     */
    public String generateResponse(String userText) {
        if (userText == null || userText.trim().isEmpty()) {
            return null;
        }
        
        String lowerText = userText.toLowerCase().trim();
        Map<String, String[]> responses = getResponsesForLanguage();
        
        // 長關鍵詞先匹配，避免「好去處」被單字「好」等誤觸
        List<Map.Entry<String, String[]>> entries = new ArrayList<>(responses.entrySet());
        Collections.sort(entries, Comparator.comparingInt(e -> -e.getKey().length()));
        
        for (Map.Entry<String, String[]> entry : entries) {
            String keyword = entry.getKey();
            if (lowerText.contains(keyword)) {
                String[] templates = entry.getValue();
                if (templates != null && templates.length > 0) {
                    // Randomly select a response template
                    String template = templates[random.nextInt(templates.length)];
                    
                    // Try to extract key information from user's words and incorporate into response
                    String response = enhanceResponse(template, userText, keyword);
                    
                    Log.d(TAG, "Generate response: " + response + " (keyword: " + keyword + ")");
                    return response;
                }
            }
        }
        
        // If no matching keywords, generate generic response
        return generateGenericResponse(userText);
    }
    
    /**
     * Generate response (asynchronous version)
     * @param userText User input text
     * @param callback Callback interface
     */
    public void generateResponseAsync(String userText, ResponseCallback callback) {
        if (userText == null || userText.trim().isEmpty()) {
            callback.onResponse(null);
            return;
        }
        
        // 有网 → 云端 LLM；无网 → 离线 LLM；均不可用 → 关键词匹配
        if (isUsingCloudLLM()) {
            Log.i(TAG, "ConversationResponseGenerator: 云端 LLM (已联网)");
            generateResponseWithCloudLLM(userText, callback);
            return;
        }
        if (isOfflineLLMReady()) {
            Log.i(TAG, "ConversationResponseGenerator: 离线 LLM 分支 (无网或云端未配置)");
            generateResponseWithOfflineLLM(userText, callback);
            return;
        }

        // Fallback to keyword matching
        Log.w(TAG, "ConversationResponseGenerator: LLM 未使用 (关键词回退). offlineReady="
                + isOfflineLLMReady()
                + ", cloudConfigured=" + isCloudLLMConfigured()
                + ", online=" + (context != null && NetworkUtils.isNetworkAvailable(context)));
        String response = generateResponse(userText);
        callback.onResponse(response);
    }
    
    private List<ConversationManager.ConversationTurn> getHistory() {
        if (conversationManager != null) {
            return conversationManager.getRecentHistory(5);
        }
        return null;
    }

    /** 使用离线 LLM 生成回复 */
    private void generateResponseWithOfflineLLM(String userText, ResponseCallback callback) {
        offlineLLMClient.sendChatMessage(userText, getHistory(), new LLMClient.ChatCallback() {
            @Override
            public void onResponse(String response) {
                String processed = postProcessResponse(response);
                Log.d(TAG, "离线 LLM 回复: " + processed);
                callback.onResponse(processed);
            }

            @Override
            public void onError(String error) {
                Log.w(TAG, "离线 LLM 失败: " + error + "，尝试云端 LLM");
                if (isUsingCloudLLM()) {
                    generateResponseWithCloudLLM(userText, callback);
                } else if (isCloudLLMConfigured() && context != null
                        && !NetworkUtils.isNetworkAvailable(context)) {
                    Log.w(TAG, "云端 LLM 已配置但当前无网络，回退关键词");
                    callback.onResponse(generateResponse(userText));
                } else {
                    callback.onResponse(generateResponse(userText));
                }
            }
        });
    }

    /** 使用云端 LLM 生成回复 */
    private void generateResponseWithCloudLLM(String userText, ResponseCallback callback) {
        if (llmClient == null || !llmClient.isEnabled()) {
            callback.onResponse(generateResponse(userText));
            return;
        }

        llmClient.sendChatMessage(userText, getHistory(), new LLMClient.ChatCallback() {
            @Override
            public void onResponse(String response) {
                String processed = postProcessResponse(response);
                Log.d(TAG, "云端 LLM 回复: " + processed);
                callback.onResponse(processed);
            }

            @Override
            public void onError(String error) {
                Log.w(TAG, "云端 LLM 失败: " + error + "，尝试离线 LLM 或关键词");
                if (isOfflineLLMReady()) {
                    generateResponseWithOfflineLLM(userText, callback);
                } else {
                    callback.onResponse(generateResponse(userText));
                }
            }
        });
    }
    
    /**
     * Response callback interface
     */
    public interface ResponseCallback {
        void onResponse(String response);
    }
    
    /**
     * Enhance response, incorporate user's words into response
     * Note: Don't repeat user's words, but give real conversation response
     */
    private String enhanceResponse(String template, String userText, String keyword) {
        // Directly return template, don't repeat user's words
        // Template already contains appropriate response, no need to modify user's words
        return template;
    }
    
    /**
     * Generate generic response (when no matching keywords)
     */
    private String generateGenericResponse(String userText) {
        Map<String, String> genericResponses = new HashMap<>();
        
        if (currentLanguage.equals("cantonese")) {
            // More natural generic responses
            String[] naturalResponses = {
                "我明白，繼續講", "係啊，我聽到", "嗯，我理解", "好，繼續"
            };
            if (userText.length() > 5) {
                // Try to incorporate user's words, but more naturally
                String lastPart = userText.length() > 15 ? 
                    userText.substring(userText.length() - 15) : userText;
                return "係啊，" + lastPart + "，我明白";
            }
            return naturalResponses[random.nextInt(naturalResponses.length)];
        } else if (currentLanguage.equals("mandarin")) {
            String[] naturalResponses = {
                "我明白，继续说吧", "是啊，我听到了", "嗯，我理解", "好，继续"
            };
            if (userText.length() > 5) {
                String lastPart = userText.length() > 15 ? 
                    userText.substring(userText.length() - 15) : userText;
                return "是啊，" + lastPart + "，我明白";
            }
            return naturalResponses[random.nextInt(naturalResponses.length)];
        } else {
            String[] naturalResponses = {
                "I understand, go on", "Yes, I heard you", "I see, continue", "Okay, go ahead"
            };
            if (userText.length() > 5) {
                String lastPart = userText.length() > 15 ? 
                    userText.substring(userText.length() - 15) : userText;
                return "Yes, " + lastPart + ", I understand";
            }
            return naturalResponses[random.nextInt(naturalResponses.length)];
        }
    }
    
    /**
     * Get response mapping for current language
     */
    private Map<String, String[]> getResponsesForLanguage() {
        switch (currentLanguage) {
            case "english":
                return englishResponses;
            case "mandarin":
                return mandarinResponses;
            case "cantonese":
            default:
                return cantoneseResponses;
        }
    }
    
    /**
     * Check if should generate response
     * Some cases may not need response (e.g., user is just thinking)
     */
    public boolean shouldRespond(String userText) {
        if (userText == null || userText.trim().isEmpty()) {
            return false;
        }
        
        // If too short (may be misrecognition)
        if (userText.trim().length() < 2) {
            return false;
        }
        
        // If just filler words
        String[] fillerWords = currentLanguage.equals("english") ? 
            new String[]{"um", "uh", "ah", "oh"} :
            new String[]{"嗯", "啊", "哦", "呃"};
        
        for (String filler : fillerWords) {
            if (userText.trim().equalsIgnoreCase(filler)) {
                return false;
            }
        }
        
        return true;
    }
}

