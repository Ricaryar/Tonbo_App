package com.example.tonbo_app;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 貨幣識別助手類
 * 專門用於識別港幣紙幣和硬幣
 */
public class CurrencyDetector {
    private static final String TAG = "CurrencyDetector";
    
    // 港幣面額特徵
    private static final Map<String, CurrencyInfo> CURRENCY_FEATURES = new HashMap<>();
    
    static {
        // 港幣紙幣特徵
        CURRENCY_FEATURES.put("10", new CurrencyInfo("十元港幣", "綠色", "獅子山", "紙幣"));
        CURRENCY_FEATURES.put("20", new CurrencyInfo("二十元港幣", "藍色", "獅子山", "紙幣"));
        CURRENCY_FEATURES.put("50", new CurrencyInfo("五十元港幣", "紫色", "獅子山", "紙幣"));
        CURRENCY_FEATURES.put("100", new CurrencyInfo("一百元港幣", "紅色", "獅子山", "紙幣"));
        CURRENCY_FEATURES.put("500", new CurrencyInfo("五百元港幣", "棕色", "獅子山", "紙幣"));
        CURRENCY_FEATURES.put("1000", new CurrencyInfo("一千元港幣", "金色", "獅子山", "紙幣"));
        
        // 港幣硬幣特徵
        CURRENCY_FEATURES.put("1", new CurrencyInfo("一元港幣", "銀色", "紫荊花", "硬幣"));
        CURRENCY_FEATURES.put("2", new CurrencyInfo("二元港幣", "銀色", "紫荊花", "硬幣"));
        CURRENCY_FEATURES.put("5", new CurrencyInfo("五元港幣", "銀色", "紫荊花", "硬幣"));
    }
    
    private Context context;
    private OCRHelper ocrHelper;
    private CurrencyClassifier currencyClassifier;
    
    public CurrencyDetector(Context context) {
        this.context = context;
        this.ocrHelper = new OCRHelper(context);
        this.currencyClassifier = new CurrencyClassifier(context);
    }
    
    /**
     * 檢測圖片中的貨幣
     * @param bitmap 要檢測的圖片
     * @return 貨幣檢測結果列表
     */
    public List<CurrencyResult> detectCurrency(Bitmap bitmap) {
        return detectCurrency(bitmap, 0);
    }

    /**
     * 檢測圖片中的貨幣（支持 OCR 旋轉角度）
     */
    public List<CurrencyResult> detectCurrency(Bitmap bitmap, int rotationDegrees) {
        Map<String, CurrencyResult> uniqueResults = new LinkedHashMap<>();

        try {
            // 1. 優先使用 YOLOv8-cls 圖像分類（真實視覺識別）
            CurrencyResult visionResult = classifyCurrencyFromImage(bitmap, rotationDegrees);
            if (visionResult != null) {
                String key = visionResult.getAmount() + "-" + visionResult.getType();
                uniqueResults.put(key, visionResult);
            }

            // 2. OCR 作為輔助（硬幣、文字清晰場景、或視覺模型不確定時）
            List<OCRHelper.OCRResult> ocrResults = ocrHelper.recognizeText(bitmap, rotationDegrees);
            for (OCRHelper.OCRResult ocrResult : ocrResults) {
                CurrencyResult ocrCurrency = analyzeTextForCurrency(ocrResult.getText());
                if (ocrCurrency == null || ocrCurrency.getConfidence() < 0.6f) {
                    continue;
                }

                String key = ocrCurrency.getAmount() + "-" + ocrCurrency.getType();
                CurrencyResult existing = uniqueResults.get(key);
                if (existing == null) {
                    // 視覺未識別到時，採用 OCR（尤其硬幣 1/2/5 元）
                    boolean visionConfident = visionResult != null
                            && visionResult.getConfidence() >= AppConstants.CURRENCY_CLS_CONFIDENCE_THRESHOLD;
                    if (!visionConfident) {
                        uniqueResults.put(key, ocrCurrency);
                    }
                } else if (existing.getAmount().equals(ocrCurrency.getAmount())) {
                    // 視覺與 OCR 一致，提高置信度
                    float boosted = Math.min(0.99f, Math.max(existing.getConfidence(), ocrCurrency.getConfidence()) + 0.1f);
                    uniqueResults.put(key, new CurrencyResult(
                            existing.getName(),
                            existing.getAmount(),
                            existing.getColor(),
                            existing.getDesign(),
                            existing.getType(),
                            boosted,
                            existing.getDetectionMethod() + " + OCR"
                    ));
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "貨幣檢測失敗: " + e.getMessage());
        }

        return new ArrayList<>(uniqueResults.values());
    }

    /**
     * 使用訓練好的 YOLOv8n-cls 模型進行港幣紙幣圖像識別
     */
    private CurrencyResult classifyCurrencyFromImage(Bitmap bitmap, int rotationDegrees) {
        if (currencyClassifier == null || !currencyClassifier.isReady()) {
            Log.w(TAG, "圖像分類器未就緒，跳過視覺識別");
            return null;
        }

        CurrencyClassifier.ClassificationResult result =
                currencyClassifier.classify(bitmap, rotationDegrees);
        if (result == null || !result.isConfident()) {
            if (result != null) {
                Log.d(TAG, String.format("視覺識別置信度不足: %s (%.0f%%)",
                        result.getClassName(), result.getConfidence() * 100));
            }
            return null;
        }

        CurrencyInfo info = CURRENCY_FEATURES.get(result.getAmount());
        if (info == null) {
            return null;
        }

        return new CurrencyResult(
                info.getName(),
                result.getAmount(),
                info.getColor(),
                info.getDesign(),
                info.getType(),
                result.getConfidence(),
                "圖像識別 (AI)"
        );
    }
    
    /**
     * 分析文字內容尋找貨幣信息
     */
    private CurrencyResult analyzeTextForCurrency(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        String cleanText = text.replaceAll("\\s+", "").toLowerCase();

        // 简化的货币识别：先找数字，再验证上下文
        Pattern numberPattern = Pattern.compile("\\b(\\d+)\\b");
        Matcher numberMatcher = numberPattern.matcher(text);

        while (numberMatcher.find()) {
            String potentialAmount = numberMatcher.group(1);

            // 检查这个数字是否是支持的港币面额
            CurrencyInfo info = CURRENCY_FEATURES.get(potentialAmount);
            if (info != null && isValidCurrencyContext(text, potentialAmount)) {
                return new CurrencyResult(
                    info.getName(),
                    potentialAmount,
                    info.getColor(),
                    info.getDesign(),
                    info.getType(),
                    0.75f, // 降低置信度阈值
                    "文字識別"
                );
            }
        }

        // 检查更具体的货币符号模式（如：HK$100, $50港幣等）
        Pattern currencySymbolPattern = Pattern.compile("\\b(?:hk\\$|\\$)\\s*(\\d+)\\b", Pattern.CASE_INSENSITIVE);
        Matcher symbolMatcher = currencySymbolPattern.matcher(text);

        while (symbolMatcher.find()) {
            String amount = symbolMatcher.group(1);
            CurrencyInfo info = CURRENCY_FEATURES.get(amount);

            if (info != null) {
                return new CurrencyResult(
                    info.getName(),
                    amount,
                    info.getColor(),
                    info.getDesign(),
                    info.getType(),
                    0.8f, // 降低置信度阈值
                    "貨幣符號識別"
                );
            }
        }

        return null;
    }

    /**
     * 检查货币识别的上下文是否有效
     */
    private boolean isValidCurrencyContext(String text, String amount) {
        // 对于货币符号模式（HK$100），不需要额外上下文检查
        if (text.toLowerCase().contains("hk$") || text.toLowerCase().contains("$")) {
            return true;
        }

        // 对于很短的文本（可能是货币标签），直接认为是有效的
        if (text.trim().length() < 30) {
            return true;
        }

        // 确保不是孤立的数字（比如电话号码、日期等）
        int amountIndex = text.toLowerCase().indexOf(amount.toLowerCase());
        if (amountIndex == -1) return false;

        String contextBefore = text.substring(0, amountIndex).toLowerCase();
        String contextAfter = text.substring(amountIndex + amount.length()).toLowerCase();

        // 检查前后是否有货币相关的上下文
        boolean hasCurrencyContext = contextBefore.contains("港幣") ||
                                   contextBefore.contains("hk$") ||
                                   contextBefore.contains("hkd") ||
                                   contextBefore.contains("元") ||
                                   contextBefore.contains("dollar") ||
                                   contextAfter.contains("港幣") ||
                                   contextAfter.contains("hk$") ||
                                   contextAfter.contains("hkd") ||
                                   contextAfter.contains("元") ||
                                   contextAfter.contains("dollar");

        return hasCurrencyContext;
    }

    /**
     * 分析圖像特徵尋找貨幣（簡化實現）
     */
    private List<CurrencyResult> analyzeImageForCurrency(Bitmap bitmap) {
        List<CurrencyResult> results = new ArrayList<>();
        
        // 這裡可以實現更複雜的圖像分析
        // 例如：顏色分析、形狀檢測、邊緣檢測等
        
        // 簡化實現：基於圖片大小和顏色特徵
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        
        // 分析圖片比例，港幣紙幣通常是長方形
        float aspectRatio = (float) width / height;
        
        if (aspectRatio > 1.5f && aspectRatio < 3.0f) {
            // 可能是紙幣
            results.add(new CurrencyResult(
                "港幣紙幣",
                "未知面額",
                "多色",
                "獅子山",
                "紙幣",
                0.6f,
                "圖像分析"
            ));
        } else if (aspectRatio > 0.8f && aspectRatio < 1.2f) {
            // 可能是硬幣
            results.add(new CurrencyResult(
                "港幣硬幣",
                "未知面額",
                "銀色",
                "紫荊花",
                "硬幣",
                0.6f,
                "圖像分析"
            ));
        }
        
        return results;
    }
    
    /**
     * 格式化貨幣檢測結果為語音文本
     */
    public String formatResultsForSpeech(List<CurrencyResult> results) {
        if (results.isEmpty()) {
            return "未識別到任何貨幣";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("識別到貨幣：");
        
        for (int i = 0; i < results.size(); i++) {
            CurrencyResult result = results.get(i);
            sb.append(result.getName());
            
            if (!result.getAmount().equals("未知面額")) {
                sb.append("，面額").append(result.getAmount()).append("元");
            }
            
            sb.append("，").append(result.getType());
            
            if (i < results.size() - 1) {
                sb.append("；");
            }
        }
        
        return sb.toString();
    }
    
    /**
     * 格式化詳細結果
     */
    public String formatDetailedResults(List<CurrencyResult> results) {
        if (results.isEmpty()) {
            return "未識別到任何貨幣";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("識別到 %d 種貨幣：\n\n", results.size()));
        
        for (int i = 0; i < results.size(); i++) {
            CurrencyResult result = results.get(i);
            sb.append(String.format("%d. %s\n", i + 1, result.getName()));
            sb.append(String.format("   面額：%s元\n", result.getAmount()));
            sb.append(String.format("   類型：%s\n", result.getType()));
            sb.append(String.format("   置信度：%.0f%%\n", result.getConfidence() * 100));
            sb.append(String.format("   識別方式：%s\n\n", result.getDetectionMethod()));
        }
        
        return sb.toString();
    }
    
    /**
     * 關閉檢測器
     */
    public void close() {
        if (currencyClassifier != null) {
            currencyClassifier.close();
            currencyClassifier = null;
        }
        if (ocrHelper != null) {
            ocrHelper.close();
            ocrHelper = null;
        }
    }
    
    /**
     * 貨幣信息類
     */
    private static class CurrencyInfo {
        private String name;
        private String color;
        private String design;
        private String type;
        
        public CurrencyInfo(String name, String color, String design, String type) {
            this.name = name;
            this.color = color;
            this.design = design;
            this.type = type;
        }
        
        public String getName() { return name; }
        public String getColor() { return color; }
        public String getDesign() { return design; }
        public String getType() { return type; }
    }
    
    /**
     * 貨幣檢測結果類
     */
    public static class CurrencyResult {
        private String name;
        private String amount;
        private String color;
        private String design;
        private String type;
        private float confidence;
        private String detectionMethod;
        
        public CurrencyResult(String name, String amount, String color, 
                            String design, String type, float confidence, 
                            String detectionMethod) {
            this.name = name;
            this.amount = amount;
            this.color = color;
            this.design = design;
            this.type = type;
            this.confidence = confidence;
            this.detectionMethod = detectionMethod;
        }
        
        public String getName() { return name; }
        public String getAmount() { return amount; }
        public String getColor() { return color; }
        public String getDesign() { return design; }
        public String getType() { return type; }
        public float getConfidence() { return confidence; }
        public String getDetectionMethod() { return detectionMethod; }
        
        @Override
        public String toString() {
            return String.format("%s (%s元) - %s (%.0f%%)", 
                name, amount, type, confidence * 100);
        }
    }
}
