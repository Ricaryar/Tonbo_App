package com.example.tonbo_app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.util.Log;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * OCR文字識別助手類
 * 使用Google ML Kit進行中文和英文文字識別
 */
public class OCRHelper {
    private static final String TAG = "OCRHelper";

    private com.google.mlkit.vision.text.TextRecognizer chineseTextRecognizer;
    private com.google.mlkit.vision.text.TextRecognizer englishTextRecognizer;
    private Context context;

    private static class OcrCandidate {
        List<OCRResult> results;
        String fullText;
        float score;
    }

    public OCRHelper(Context context) {
        this.context = context;
        initializeTextRecognizer();
    }

    /**
     * 初始化文字識別器
     */
    private void initializeTextRecognizer() {
        // 初始化中文文字識別器
        chineseTextRecognizer = TextRecognition.getClient(
                new ChineseTextRecognizerOptions.Builder().build()
        );
        
        // 初始化英文文字識別器
        englishTextRecognizer = TextRecognition.getClient(
                TextRecognizerOptions.DEFAULT_OPTIONS
        );
        
        Log.d(TAG, "OCR文字識別器初始化完成");
    }

    /**
     * 識別圖片中的文字
     * @param bitmap 要識別的圖片
     * @return 識別結果列表
     */
    public List<OCRResult> recognizeText(Bitmap bitmap) {
        return recognizeText(bitmap, 0);
    }

    /**
     * 識別圖片中的文字（支持旋轉角度與圖片預處理）
     *
     * @param bitmap 要識別的圖片
     * @param rotationDegrees 相機幀旋轉角度（由 ImageProxy.getImageInfo().getRotationDegrees() 提供）
     */
    public List<OCRResult> recognizeText(Bitmap bitmap, int rotationDegrees) {
        List<OCRResult> empty = new ArrayList<>();
        if (bitmap == null) return empty;

        try {
            Bitmap preprocessed = preprocessForOcr(bitmap);
            InputImage image = InputImage.fromBitmap(preprocessed, rotationDegrees);

            OcrCandidate chinese = new OcrCandidate();
            OcrCandidate english = new OcrCandidate();

            CountDownLatch latch = new CountDownLatch(2);

            chineseTextRecognizer
                    .process(image)
                    .addOnSuccessListener(visionText -> {
                        List<OCRResult> tmp = new ArrayList<>();
                        processTextRecognitionResult(visionText, tmp, "中文識別");
                        chinese.results = tmp;
                        chinese.fullText = visionText != null ? visionText.getText() : null;
                        chinese.score = scoreOcrText(chinese.fullText);
                        latch.countDown();
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "中文OCR識別失敗: " + e.getMessage());
                        chinese.score = 0f;
                        latch.countDown();
                    });

            englishTextRecognizer
                    .process(image)
                    .addOnSuccessListener(visionText -> {
                        List<OCRResult> tmp = new ArrayList<>();
                        processTextRecognitionResult(visionText, tmp, "英文識別");
                        english.results = tmp;
                        english.fullText = visionText != null ? visionText.getText() : null;
                        english.score = scoreOcrText(english.fullText);
                        latch.countDown();
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "英文OCR識別失敗: " + e.getMessage());
                        english.score = 0f;
                        latch.countDown();
                    });

            latch.await();

            OcrCandidate best = chinese.score >= english.score ? chinese : english;
            if (best != null && best.results != null && !best.results.isEmpty()) {
                return best.results;
            }

            return empty;
        } catch (Exception e) {
            Log.e(TAG, "OCR處理異常: " + e.getMessage());
            return empty;
        }
    }

    /**
     * 處理文字識別結果
     */
    private void processTextRecognitionResult(Text visionText, List<OCRResult> results, String recognizerType) {
        if (visionText == null) return;

        String fullText = visionText.getText();
        Log.d(TAG, "識別到的完整文字: " + fullText);

        if (fullText != null && !fullText.trim().isEmpty()) {
            String normalized = normalizeOcrText(fullText);

            // 創建主要識別結果
            OCRResult mainResult = new OCRResult(
                    normalized,
                    recognizerType + "完整文字",
                    calculateConfidence(normalized)
            );
            results.add(mainResult);
        }
    }

    /**
     * 計算識別置信度（簡單實現）
     */
    private float calculateConfidence(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0.0f;
        }

        // 簡單的置信度計算：基於文字長度和字符類型
        float confidence = 0.5f; // 基礎置信度

        // 文字長度影響
        if (text.length() > 10) {
            confidence += 0.2f;
        }

        // 中文字符加分
        int chineseCount = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if ((ch >= 0x4E00 && ch <= 0x9FFF) || // 基本中文字符
                    (ch >= 0x3400 && ch <= 0x4DBF) || // 擴展A區
                    (ch >= 0x20000 && ch <= 0x2A6DF)) {   // 擴展B區
                chineseCount++;
            }
        }

        if (chineseCount > 0) {
            confidence += 0.3f;
        }

        return Math.min(confidence, 1.0f);
    }

    private float scoreOcrText(String text) {
        if (text == null) return 0f;
        String normalized = normalizeOcrText(text);
        if (normalized.isEmpty()) return 0f;

        int chineseCount = 0;
        int latinCount = 0;
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if ((ch >= 0x4E00 && ch <= 0x9FFF) || (ch >= 0x3400 && ch <= 0x4DBF) || (ch >= 0x20000 && ch <= 0x2A6DF)) {
                chineseCount++;
            } else if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')) {
                latinCount++;
            }
        }

        // 尽量偏向“更像文字”的结果：越长越好；同时尽量让含中文/英文字符更多的结果得分更高
        return Math.min(1.5f * normalized.length() + 10f * chineseCount + 5f * latinCount, 1_000_000f);
    }

    private Bitmap preprocessForOcr(Bitmap source) {
        try {
            int w = source.getWidth();
            int h = source.getHeight();
            if (w <= 0 || h <= 0) return source;

            // 中心裁剪：去掉上下左右的大面积噪声，提高文字区域占比
            // 不要裁得太狠，避免把文字边缘裁掉
            float cropRatio = 0.96f;
            int cropW = Math.max(1, (int) (w * cropRatio));
            int cropH = Math.max(1, (int) (h * cropRatio));
            int left = (w - cropW) / 2;
            int top = (h - cropH) / 2;
            Bitmap cropped = Bitmap.createBitmap(source, left, top, cropW, cropH);

            // 统一缩放：让文字尺寸落在更合适的范围
            int targetW = 1024;
            int targetH = (int) ((float) cropH * (targetW / (float) cropW));
            Bitmap scaled = Bitmap.createScaledBitmap(cropped, targetW, targetH, true);

            // 灰度 + 轻微对比度增强（ML Kit 对这种输入通常更稳）
            Bitmap gray = Bitmap.createBitmap(scaled.getWidth(), scaled.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(gray);

            Paint paint = new Paint();
            ColorMatrix saturation = new ColorMatrix();
            saturation.setSaturation(0f);
            paint.setColorFilter(new ColorMatrixColorFilter(saturation));
            canvas.drawBitmap(scaled, 0, 0, paint);

            float contrast = 1.15f;
            float translate = (1f - contrast) * 128f;
            ColorMatrix contrastMatrix = new ColorMatrix(new float[]{
                    contrast, 0, 0, 0, translate,
                    0, contrast, 0, 0, translate,
                    0, 0, contrast, 0, translate,
                    0, 0, 0, 1, 0
            });
            Paint contrastPaint = new Paint();
            contrastPaint.setColorFilter(new ColorMatrixColorFilter(contrastMatrix));

            Bitmap result = Bitmap.createBitmap(gray.getWidth(), gray.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas contrastCanvas = new Canvas(result);
            contrastCanvas.drawBitmap(gray, 0, 0, contrastPaint);

            return result;
        } catch (Exception e) {
            Log.w(TAG, "OCR preProcess failed: " + e.getMessage());
            return source;
        }
    }

    private String normalizeOcrText(String text) {
        if (text == null) return "";

        String s = text.replace('\n', ' ')
                .replace('\r', ' ')
                .replace('\t', ' ')
                .replaceAll("\\s+", " ")
                .trim();

        // 去掉中英文字符间多余空格（尤其是中文分隔）
        s = s.replaceAll("(?<=[\\u4E00-\\u9FFF])\\s+(?=[\\u4E00-\\u9FFF])", "");
        s = s.replaceAll("\\s+([，。！？：；,\\.!?;:])", "$1");
        s = s.replaceAll("([，。！？：；,\\.!?;:])\\s+", "$1");
        return s;
    }

    /**
     * 格式化識別結果為語音文本
     */
    public String formatResultsForSpeech(List<OCRResult> results) {
        if (results.isEmpty()) {
            return "未識別到任何文字";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("識別到以下內容：\n\n");

        // 使用第一個結果（通常是最完整的）
        OCRResult mainResult = results.get(0);
        sb.append(mainResult.getText());

        return sb.toString();
    }

    /**
     * 格式化詳細結果
     */
    public String formatDetailedResults(List<OCRResult> results) {
        if (results.isEmpty()) {
            return "未識別到任何文字";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("識別到 %d 個文字區域：\n\n", results.size()));

        for (int i = 0; i < Math.min(results.size(), 5); i++) {
            OCRResult result = results.get(i);
            sb.append(String.format("%d. %s (%.0f%%)\n",
                    i + 1,
                    result.getText(),
                    result.getConfidence() * 100
            ));
        }

        if (results.size() > 5) {
            sb.append(String.format("\n...還有 %d 個文字區域", results.size() - 5));
        }

        return sb.toString();
    }

    /**
     * 關閉文字識別器
     */
    public void close() {
        if (chineseTextRecognizer != null) {
            chineseTextRecognizer.close();
            chineseTextRecognizer = null;
            Log.d(TAG, "中文OCR文字識別器已關閉");
        }
        if (englishTextRecognizer != null) {
            englishTextRecognizer.close();
            englishTextRecognizer = null;
            Log.d(TAG, "英文OCR文字識別器已關閉");
        }
    }

    /**
     * OCR識別結果類
     */
    public static class OCRResult {
        private String text;
        private String type;
        private float confidence;

        public OCRResult(String text, String type, float confidence) {
            this.text = text;
            this.type = type;
            this.confidence = confidence;
        }

        public String getText() { return text; }
        public String getType() { return type; }
        public float getConfidence() { return confidence; }

        @Override
        public String toString() {
            return String.format("[%s] %s (%.0f%%)", type, text, confidence * 100);
        }
    }
}
