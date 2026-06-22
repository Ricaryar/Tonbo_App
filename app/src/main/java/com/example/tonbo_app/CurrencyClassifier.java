package com.example.tonbo_app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.util.Log;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.SessionOptions;
import ai.onnxruntime.OrtSession.SessionOptions.OptLevel;

import java.io.File;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * 港幣紙幣圖像分類器（YOLOv8n-cls ONNX）
 * 輸入 224×224 RGB，輸出 6 類港幣紙幣面額。
 */
public class CurrencyClassifier {
    private static final String TAG = "CurrencyClassifier";

    private static final String[] CLASS_NAMES = {
            "1000_hkd", "100_hkd", "10_hkd", "20_hkd", "500_hkd", "50_hkd"
    };

    private static final Map<String, String> CLASS_TO_AMOUNT = new HashMap<>();

    static {
        CLASS_TO_AMOUNT.put("1000_hkd", "1000");
        CLASS_TO_AMOUNT.put("100_hkd", "100");
        CLASS_TO_AMOUNT.put("10_hkd", "10");
        CLASS_TO_AMOUNT.put("20_hkd", "20");
        CLASS_TO_AMOUNT.put("500_hkd", "500");
        CLASS_TO_AMOUNT.put("50_hkd", "50");
    }

    private final Context context;
    private final Object sessionLock = new Object();
    private OrtEnvironment ortEnvironment;
    private OrtSession ortSession;
    private boolean initialized;

    public static class ClassificationResult {
        private final String className;
        private final String amount;
        private final float confidence;

        public ClassificationResult(String className, String amount, float confidence) {
            this.className = className;
            this.amount = amount;
            this.confidence = confidence;
        }

        public String getClassName() {
            return className;
        }

        public String getAmount() {
            return amount;
        }

        public float getConfidence() {
            return confidence;
        }

        public boolean isConfident() {
            return confidence >= AppConstants.CURRENCY_CLS_CONFIDENCE_THRESHOLD;
        }
    }

    public CurrencyClassifier(Context context) {
        this.context = context.getApplicationContext();
        initialize();
    }

    private void initialize() {
        try {
            ortEnvironment = OrtEnvironment.getEnvironment();
            File modelFile = new File(context.getFilesDir(), "models/" + AppConstants.CURRENCY_CLS_MODEL_FILE);
            if (!BundledAssetExtractor.copyAssetIfNeeded(
                    context.getAssets(), AppConstants.CURRENCY_CLS_MODEL_FILE, modelFile)) {
                Log.w(TAG, "貨幣分類模型不存在: " + AppConstants.CURRENCY_CLS_MODEL_FILE);
                return;
            }

            SessionOptions options = new SessionOptions();
            options.setOptimizationLevel(OptLevel.ALL_OPT);
            options.setIntraOpNumThreads(2);
            options.setInterOpNumThreads(2);
            ortSession = ortEnvironment.createSession(modelFile.getAbsolutePath(), options);
            initialized = true;
            Log.i(TAG, "貨幣分類模型加載成功");
        } catch (Exception e) {
            Log.e(TAG, "貨幣分類模型初始化失敗: " + e.getMessage(), e);
            initialized = false;
        }
    }

    public boolean isReady() {
        return initialized && ortSession != null;
    }

    public ClassificationResult classify(Bitmap bitmap) {
        return classify(bitmap, 0);
    }

    public ClassificationResult classify(Bitmap bitmap, int rotationDegrees) {
        if (!isReady() || bitmap == null) {
            return null;
        }

        Bitmap working = bitmap;
        Bitmap rotated = null;
        Bitmap resized = null;
        Bitmap cropped = null;

        try {
            if (rotationDegrees != 0) {
                rotated = rotateBitmap(bitmap, rotationDegrees);
                working = rotated;
            }

            resized = resizeShortestSide(working, AppConstants.CURRENCY_CLS_INPUT_SIZE);
            cropped = centerCrop(resized, AppConstants.CURRENCY_CLS_INPUT_SIZE, AppConstants.CURRENCY_CLS_INPUT_SIZE);

            float[] input = bitmapToNchw(cropped);
            long[] shape = {1, 3, AppConstants.CURRENCY_CLS_INPUT_SIZE, AppConstants.CURRENCY_CLS_INPUT_SIZE};

            synchronized (sessionLock) {
                if (!isReady()) {
                    return null;
                }

                try (OnnxTensor tensor = OnnxTensor.createTensor(ortEnvironment, FloatBuffer.wrap(input), shape);
                     OrtSession.Result outputs = ortSession.run(Map.of("images", tensor))) {

                    float[] logits = extractLogits(outputs.get(0).getValue());
                    if (logits == null || logits.length == 0) {
                        return null;
                    }

                    int bestIndex = 0;
                    float bestProb = 0f;
                    float[] probs = softmax(logits);
                    for (int i = 0; i < probs.length; i++) {
                        if (probs[i] > bestProb) {
                            bestProb = probs[i];
                            bestIndex = i;
                        }
                    }

                    if (bestIndex < 0 || bestIndex >= CLASS_NAMES.length) {
                        return null;
                    }

                    String className = CLASS_NAMES[bestIndex];
                    String amount = CLASS_TO_AMOUNT.get(className);
                    if (amount == null) {
                        return null;
                    }

                    Log.d(TAG, String.format("分類結果: %s (%.1f%%)", className, bestProb * 100));
                    return new ClassificationResult(className, amount, bestProb);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "貨幣分類推理失敗: " + e.getMessage(), e);
            return null;
        } finally {
            if (rotated != null && rotated != bitmap) {
                rotated.recycle();
            }
            if (resized != null && resized != working && resized != bitmap) {
                resized.recycle();
            }
            if (cropped != null && cropped != resized && cropped != working && cropped != bitmap) {
                cropped.recycle();
            }
        }
    }

    private static float[] extractLogits(Object value) {
        if (value instanceof float[]) {
            return (float[]) value;
        }
        if (value instanceof float[][]) {
            float[][] matrix = (float[][]) value;
            return matrix.length > 0 ? matrix[0] : null;
        }
        if (value instanceof float[][][]) {
            float[][][] tensor = (float[][][]) value;
            return tensor.length > 0 && tensor[0].length > 0 ? tensor[0][0] : null;
        }
        return null;
    }

    private static float[] softmax(float[] logits) {
        float max = Float.NEGATIVE_INFINITY;
        for (float logit : logits) {
            max = Math.max(max, logit);
        }

        float sum = 0f;
        float[] probs = new float[logits.length];
        for (int i = 0; i < logits.length; i++) {
            probs[i] = (float) Math.exp(logits[i] - max);
            sum += probs[i];
        }
        if (sum > 0f) {
            for (int i = 0; i < probs.length; i++) {
                probs[i] /= sum;
            }
        }
        return probs;
    }

    private static Bitmap rotateBitmap(Bitmap bitmap, int degrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private static Bitmap resizeShortestSide(Bitmap bitmap, int targetSize) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int newWidth;
        int newHeight;

        if (width < height) {
            newWidth = targetSize;
            newHeight = Math.max(1, Math.round(height * (targetSize / (float) width)));
        } else {
            newHeight = targetSize;
            newWidth = Math.max(1, Math.round(width * (targetSize / (float) height)));
        }

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
    }

    private static Bitmap centerCrop(Bitmap bitmap, int cropWidth, int cropHeight) {
        int x = Math.max(0, (bitmap.getWidth() - cropWidth) / 2);
        int y = Math.max(0, (bitmap.getHeight() - cropHeight) / 2);
        int width = Math.min(cropWidth, bitmap.getWidth() - x);
        int height = Math.min(cropHeight, bitmap.getHeight() - y);
        return Bitmap.createBitmap(bitmap, x, y, width, height);
    }

    private static float[] bitmapToNchw(Bitmap bitmap) {
        int size = AppConstants.CURRENCY_CLS_INPUT_SIZE;
        float[] data = new float[3 * size * size];
        int planeSize = size * size;

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int pixel = bitmap.getPixel(x, y);
                int index = y * size + x;
                data[index] = ((pixel >> 16) & 0xFF) / 255f;
                data[planeSize + index] = ((pixel >> 8) & 0xFF) / 255f;
                data[2 * planeSize + index] = (pixel & 0xFF) / 255f;
            }
        }
        return data;
    }

    public void close() {
        synchronized (sessionLock) {
            if (ortSession != null) {
                try {
                    ortSession.close();
                } catch (Exception e) {
                    Log.w(TAG, "關閉 ONNX Session 失敗: " + e.getMessage());
                }
                ortSession = null;
            }
            initialized = false;
        }
    }
}
