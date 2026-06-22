package com.example.tonbo_app;

import android.graphics.RectF;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 物體搜索管理器
 * 負責搜索特定物體、匹配物體名稱、生成位置描述
 * 
 * @author LUO Feiyang
 */
public class ObjectSearchManager {
    private static final String TAG = "ObjectSearchManager";
    
    // 物體名稱映射表（支持多語言和同義詞）
    private Map<String, List<String>> objectNameMap;
    
    // 當前搜索的物體
    private String currentSearchTarget = null;
    
    // 上次找到的位置
    private RectF lastFoundPosition = null;
    
    // 搜索狀態
    private boolean isSearching = false;
    
    // 搜索結果回調
    public interface SearchResultListener {
        void onObjectFound(ObjectDetectorHelper.DetectionResult result, String positionDescription);
        void onObjectNotFound(String guidance);
        void onSearchStarted(String objectName);
        void onSearchStopped();
    }
    
    private SearchResultListener searchListener;
    
    public ObjectSearchManager() {
        initializeObjectNameMap();
    }
    
    /**
     * 初始化物體名稱映射表
     * 支持多語言和同義詞匹配
     */
    private void initializeObjectNameMap() {
        objectNameMap = new HashMap<>();
        
        // 鑰匙相關
        addObjectNames("key", "鑰匙", "钥匙", "key", "keys", "鑰", "鎖匙");
        
        // 手機相關
        addObjectNames("cell phone", "手機", "手机", "phone", "mobile", "電話", "电话", "智能手機");
        
        // 錢包相關
        addObjectNames("handbag", "錢包", "钱包", "wallet", "purse", "手袋", "手提包");
        
        // 眼鏡相關
        addObjectNames("eyeglasses", "眼鏡", "眼镜", "glasses", "眼鏡框");
        
        // 遙控器相關
        addObjectNames("remote", "遙控器", "遥控器", "remote control", "遙控");
        
        // 杯子相關
        addObjectNames("cup", "杯子", "杯", "mug", "茶杯", "水杯");
        
        // 書本相關
        addObjectNames("book", "書", "书", "books", "書本", "书本");
        
        // 筆相關
        addObjectNames("pen", "筆", "笔", "pens", "原子筆", "圆珠笔");
        
        // 錢包/手提包
        addObjectNames("handbag", "手提包", "手袋", "handbag", "bag", "包包");
        
        // 筆記本電腦
        addObjectNames("laptop", "筆記本", "笔记本", "laptop", "電腦", "电脑");
        
        // 鼠標
        addObjectNames("mouse", "鼠標", "鼠标", "mouse", "滑鼠");
        
        // 鍵盤
        addObjectNames("keyboard", "鍵盤", "键盘", "keyboard");
        
        // 瓶子
        addObjectNames("bottle", "瓶子", "瓶", "bottle", "水瓶");
        
        // 遙控器
        addObjectNames("remote", "遙控器", "遥控器", "remote", "遙控");
        
        Log.d(TAG, "物體名稱映射表初始化完成，共 " + objectNameMap.size() + " 個物體類別");
    }
    
    /**
     * 添加物體名稱（支持多個同義詞）
     */
    private void addObjectNames(String key, String... names) {
        List<String> nameList = new ArrayList<>();
        for (String name : names) {
            nameList.add(name.toLowerCase());
        }
        objectNameMap.put(key.toLowerCase(), nameList);
    }
    
    /**
     * 設置搜索結果監聽器
     */
    public void setSearchResultListener(SearchResultListener listener) {
        this.searchListener = listener;
    }
    
    /**
     * 開始搜索物體
     * @param objectName 用戶說出的物體名稱（可能是中文、英文或同義詞）
     */
    public void startSearch(String objectName) {
        Log.d(TAG, "開始搜索物體: " + objectName);
        
        // 提取關鍵詞（移除常見的問句詞）
        String keyword = extractKeyword(objectName);
        Log.d(TAG, "提取的關鍵詞: " + keyword);
        
        // 匹配標準物體名稱
        String matchedObject = matchObjectName(keyword);
        
        if (matchedObject != null) {
            currentSearchTarget = matchedObject;
            isSearching = true;
            lastFoundPosition = null;
            
            if (searchListener != null) {
                searchListener.onSearchStarted(matchedObject);
            }
            
            Log.d(TAG, "匹配到物體: " + matchedObject);
        } else {
            Log.w(TAG, "無法匹配物體名稱: " + keyword);
            if (searchListener != null) {
                searchListener.onObjectNotFound("無法識別「" + keyword + "」，請嘗試說出其他名稱");
            }
        }
    }
    
    /**
     * 停止搜索
     */
    public void stopSearch() {
        isSearching = false;
        currentSearchTarget = null;
        lastFoundPosition = null;
        
        if (searchListener != null) {
            searchListener.onSearchStopped();
        }
        
        Log.d(TAG, "停止搜索");
    }
    
    /**
     * 在檢測結果中搜索目標物體
     * @param detections 當前檢測到的所有物體
     * @return 找到的物體，如果沒找到返回null
     */
    public ObjectDetectorHelper.DetectionResult searchInDetections(
            List<ObjectDetectorHelper.DetectionResult> detections) {
        
        if (!isSearching || currentSearchTarget == null || detections == null) {
            return null;
        }
        
        // 獲取目標物體的所有可能名稱
        List<String> targetNames = objectNameMap.get(currentSearchTarget);
        if (targetNames == null) {
            return null;
        }
        
        // 在檢測結果中查找匹配的物體
        for (ObjectDetectorHelper.DetectionResult detection : detections) {
            String label = detection.getLabel().toLowerCase();
            String labelZh = detection.getLabelZh() != null ? 
                    detection.getLabelZh().toLowerCase() : "";
            
            // 檢查是否匹配
            for (String targetName : targetNames) {
                if (label.contains(targetName) || labelZh.contains(targetName)) {
                    Log.d(TAG, "找到目標物體: " + detection.getLabelZh() + 
                          " (匹配: " + targetName + ")");
                    lastFoundPosition = detection.getBoundingBox();
                    return detection;
                }
            }
        }
        
        return null;
    }
    
    /**
     * 提取關鍵詞（移除問句詞和常見詞）
     */
    private String extractKeyword(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        String lowerText = text.toLowerCase().trim();
        
        // 移除常見的問句詞
        lowerText = lowerText.replaceAll("(我的|我的|where is|where's|在哪|在哪裡|在哪里|找|尋找|寻找)", "");
        lowerText = lowerText.replaceAll("(的|了|呢|嗎|吗|？|\\?)", "");
        
        return lowerText.trim();
    }
    
    /**
     * 匹配物體名稱（支持模糊匹配）
     */
    private String matchObjectName(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return null;
        }
        
        keyword = keyword.toLowerCase().trim();
        
        // 1. 精確匹配
        for (Map.Entry<String, List<String>> entry : objectNameMap.entrySet()) {
            List<String> names = entry.getValue();
            for (String name : names) {
                if (name.equals(keyword)) {
                    return entry.getKey();
                }
            }
        }
        
        // 2. 包含匹配
        for (Map.Entry<String, List<String>> entry : objectNameMap.entrySet()) {
            List<String> names = entry.getValue();
            for (String name : names) {
                if (keyword.contains(name) || name.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }
        
        // 3. 模糊匹配（使用簡單的相似度計算）
        String bestMatch = null;
        double bestScore = 0.0;
        double threshold = 0.6;
        
        for (Map.Entry<String, List<String>> entry : objectNameMap.entrySet()) {
            List<String> names = entry.getValue();
            for (String name : names) {
                double similarity = calculateSimilarity(keyword, name);
                if (similarity > bestScore && similarity >= threshold) {
                    bestScore = similarity;
                    bestMatch = entry.getKey();
                }
            }
        }
        
        return bestMatch;
    }
    
    /**
     * 計算字符串相似度（簡單的編輯距離）
     */
    private double calculateSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        if (s1.equals(s2)) return 1.0;
        
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) return 1.0;
        
        int distance = levenshteinDistance(s1, s2);
        return 1.0 - (double) distance / maxLen;
    }
    
    /**
     * 計算 Levenshtein 距離
     */
    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        
        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = Math.min(Math.min(dp[i - 1][j], dp[i][j - 1]), dp[i - 1][j - 1]) + 1;
                }
            }
        }
        
        return dp[s1.length()][s2.length()];
    }
    
    /**
     * 檢查是否正在搜索
     */
    public boolean isSearching() {
        return isSearching;
    }
    
    /**
     * 獲取當前搜索目標
     */
    public String getCurrentSearchTarget() {
        return currentSearchTarget;
    }
    
    /**
     * 獲取上次找到的位置
     */
    public RectF getLastFoundPosition() {
        return lastFoundPosition;
    }
}

