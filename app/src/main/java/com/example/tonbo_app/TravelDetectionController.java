package com.example.tonbo_app;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 出行導航期間的路面/前方風險檢測控制器。
 * 由 NavigationController 通過生命週期回調控制啟動與停止，不跳轉頁面。
 * 抽離自 RealAIDetectionActivity 的 YOLO + CameraX 邏輯，僅做檢測與語音播報，不播報環境識別介紹。
 */
public class TravelDetectionController {
    private static final String TAG = "TravelDetection";

    private final Activity activity;
    private final PreviewView previewView;
    private final String language;

    private ProcessCameraProvider cameraProvider;
    private ImageAnalysis imageAnalysis;
    private YoloDetector yoloDetector;
    private ExecutorService cameraExecutor;
    private volatile boolean isRunning = false;

    private final Set<String> lastAnnouncedLabels = new HashSet<>();
    private String lastAnnouncedText = "";
    private static final long MIN_ANNOUNCE_INTERVAL_MS = 2500;
    /** 僅在導航狀態為 NAVIGATING 時播報，避免打斷路線播報或非導航時誤播 */
    private volatile boolean isNavigating = false;
    /** 路線播報期間禁止障礙播報（語音鎖） */
    private volatile boolean blockSpeech = false;

    /** 可選：由 NavigationActivity 設置的步行輔助分析器，使用後者時不再使用本控制器內建的 YOLO/播報 */
    private ImageAnalysis.Analyzer walkAssistAnalyzer;

    public TravelDetectionController(Activity activity, PreviewView previewView, String language) {
        this.activity = activity;
        this.previewView = previewView;
        this.language = language != null ? language : "cantonese";
    }

    /**
     * 啟動路面檢測：初始化 YOLO、綁定 CameraX、開始分析並播報前方風險。
     * 不播報「環境識別已開始」等介紹語。
     */
    public void start() {
        if (isRunning) {
            Log.w(TAG, "start: already running, skip");
            return;
        }
        if (activity == null || activity.isFinishing()) {
            Log.w(TAG, "start: activity invalid, skip");
            return;
        }
        if (previewView == null) {
            Log.w(TAG, "start: previewView null, skip");
            return;
        }
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "start: no camera permission, skip travel detection");
            return;
        }

        isRunning = true;
        lastAnnouncedLabels.clear();
        lastAnnouncedText = "";

        if (cameraExecutor == null || cameraExecutor.isShutdown()) {
            cameraExecutor = Executors.newSingleThreadExecutor();
        }

        if (walkAssistAnalyzer == null) {
            try {
                yoloDetector = new YoloDetector(activity);
            } catch (Exception e) {
                Log.e(TAG, "YOLO init failed: " + e.getMessage());
                isRunning = false;
                return;
            }
        }

        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(activity);
        future.addListener(() -> {
            try {
                if (!isRunning || activity.isFinishing()) return;
                cameraProvider = future.get();
                bindCamera();
            } catch (Exception e) {
                Log.e(TAG, "Camera provider failed: " + e.getMessage());
                isRunning = false;
            }
        }, ContextCompat.getMainExecutor(activity));
    }

    private void bindCamera() {
        if (cameraProvider == null || !isRunning || activity.isFinishing()) return;

        try {
            androidx.camera.core.Preview preview = new androidx.camera.core.Preview.Builder().build();
            previewView.setScaleType(PreviewView.ScaleType.FIT_CENTER);
            preview.setSurfaceProvider(previewView.getSurfaceProvider());

            imageAnalysis = new ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();

            imageAnalysis.setAnalyzer(cameraExecutor,
                    walkAssistAnalyzer != null ? walkAssistAnalyzer : this::analyzeImage);

            CameraSelector selector = CameraSelector.DEFAULT_BACK_CAMERA;
            cameraProvider.bindToLifecycle((LifecycleOwner) activity, selector, preview, imageAnalysis);
            Log.d(TAG, "Travel detection camera bound");
        } catch (Exception e) {
            Log.e(TAG, "bindCamera failed: " + e.getMessage());
        }
    }

    private void analyzeImage(@NonNull ImageProxy image) {
        if (!isRunning || yoloDetector == null) {
            image.close();
            return;
        }
        if (activity.isFinishing()) {
            image.close();
            return;
        }

        try {
            List<YoloDetector.DetectionResult> results = yoloDetector.detect(image);
            int rotationDegrees = image.getImageInfo().getRotationDegrees();

            if (results != null && !results.isEmpty()) {
                List<YoloDetector.DetectionResult> adjusted = adjustResultsForRotation(
                        results, image.getWidth(), image.getHeight(), rotationDegrees);
                List<YoloDetector.DetectionResult> filtered = filterForTravel(adjusted);
                if (!filtered.isEmpty()) {
                    List<YoloDetector.DetectionResult> top = filtered.size() > 2
                            ? new ArrayList<>(filtered.subList(0, 2)) : filtered;
                    activity.runOnUiThread(() -> announceRisks(top));
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "analyzeImage: " + e.getMessage());
        } finally {
            image.close();
        }
    }

    private List<YoloDetector.DetectionResult> filterForTravel(List<YoloDetector.DetectionResult> results) {
        if (results == null || results.isEmpty()) return results;
        float conf = AppConstants.CONFIDENCE_THRESHOLD;
        float score = AppConstants.SCORE_THRESHOLD;
        Set<String> skip = new HashSet<>();
        skip.add("elephant"); skip.add("bear"); skip.add("zebra"); skip.add("giraffe");
        skip.add("cow"); skip.add("sheep"); skip.add("horse");
        skip.add("surfboard"); skip.add("skis"); skip.add("snowboard");

        List<YoloDetector.DetectionResult> out = new ArrayList<>();
        for (YoloDetector.DetectionResult r : results) {
            if (r.getConfidence() < conf) continue;
            if (skip.contains(r.getLabel().toLowerCase())) continue;
            out.add(r);
        }
        return out;
    }

    /** 由 NavigationActivity 在 onNavigationStateChanged 時設置，僅 NAVIGATING 時播報障礙 */
    public void setNavigating(boolean navigating) {
        this.isNavigating = navigating;
    }

    public void setBlockSpeech(boolean block) {
        this.blockSpeech = block;
    }

    /** 設置步行輔助分析器後，將使用此分析器替代內建 YOLO 檢測與播報；調用方需在分析器內關閉 ImageProxy。 */
    public void setWalkAssistAnalyzer(ImageAnalysis.Analyzer analyzer) {
        this.walkAssistAnalyzer = analyzer;
    }

    private void announceRisks(List<YoloDetector.DetectionResult> results) {
        if (results == null || results.isEmpty()) return;
        if (blockSpeech) return;
        if (!isNavigating) return;

        TTSManager tts = TTSManager.getInstance(activity);
        if (tts == null) return;

        boolean useEn = "english".equals(language);
        String sep = useEn ? ", " : "、";
        StringBuilder sb = new StringBuilder(useEn ? "Ahead: " : "前方有");
        for (int i = 0; i < results.size(); i++) {
            YoloDetector.DetectionResult r = results.get(i);
            String label = useEn ? r.getLabel() : r.getLabelZh();
            sb.append(label);
            if (i < results.size() - 1) sb.append(sep);
        }
        String text = sb.toString();

        Set<String> currentLabels = new HashSet<>();
        for (YoloDetector.DetectionResult r : results) {
            currentLabels.add(useEn ? r.getLabel() : r.getLabelZh());
        }

        boolean changed = !currentLabels.equals(lastAnnouncedLabels) || !text.equals(lastAnnouncedText);
        if (!changed) return;

        lastAnnouncedLabels.clear();
        lastAnnouncedLabels.addAll(currentLabels);
        lastAnnouncedText = text;
        tts.speak(text, text, false);
        Log.d(TAG, "Announced: " + text);
    }

    private static List<YoloDetector.DetectionResult> adjustResultsForRotation(
            List<YoloDetector.DetectionResult> results, int w, int h, int rotationDegrees) {
        if (results == null || results.isEmpty() || rotationDegrees == 0) return results;
        List<YoloDetector.DetectionResult> out = new ArrayList<>();
        for (YoloDetector.DetectionResult r : results) {
            Rect bbox = r.getBoundingBox();
            if (bbox == null) {
                out.add(r);
                continue;
            }
            Rect adjusted = rotateBbox(bbox, w, h, rotationDegrees);
            out.add(new YoloDetector.DetectionResult(r.getLabel(), r.getLabelZh(), r.getConfidence(), adjusted));
        }
        return out;
    }

    private static Rect rotateBbox(Rect bbox, int imageWidth, int imageHeight, int rotationDegrees) {
        int l = bbox.left, t = bbox.top, r = bbox.right, b = bbox.bottom;
        switch (rotationDegrees) {
            case 90:
                return new Rect(imageHeight - b, l, imageHeight - t, r);
            case 180:
                return new Rect(imageWidth - r, imageHeight - b, imageWidth - l, imageHeight - t);
            case 270:
                return new Rect(t, imageWidth - r, b, imageWidth - l);
            default:
                return bbox;
        }
    }

    /**
     * 停止檢測並釋放相機與 YOLO。
     */
    public void stop() {
        if (!isRunning) return;
        isRunning = false;

        try {
            if (imageAnalysis != null) {
                imageAnalysis.setAnalyzer(null, null);
                imageAnalysis = null;
            }
        } catch (Exception e) {
            Log.w(TAG, "clearAnalyzer: " + e.getMessage());
        }

        try {
            if (cameraProvider != null) {
                cameraProvider.unbindAll();
                cameraProvider = null;
            }
        } catch (Exception e) {
            Log.w(TAG, "unbindAll: " + e.getMessage());
        }

        try {
            if (yoloDetector != null) {
                yoloDetector.close();
                yoloDetector = null;
            }
        } catch (Exception e) {
            Log.w(TAG, "yolo close: " + e.getMessage());
        }

        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
            try {
                if (!cameraExecutor.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS)) {
                    cameraExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                cameraExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            cameraExecutor = null;
        }
        Log.d(TAG, "Travel detection stopped");
    }

    public boolean isRunning() {
        return isRunning;
    }
}
