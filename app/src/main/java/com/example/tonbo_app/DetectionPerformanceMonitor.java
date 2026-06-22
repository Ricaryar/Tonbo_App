package com.example.tonbo_app;

import android.util.Log;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Detection performance monitor - optimized version
 * Uses more efficient data structures and algorithms
 */
public class DetectionPerformanceMonitor {
    private static final String TAG = "DetectionPerformance";
    
    // Use Deque instead of ArrayList for better performance
    private final Deque<Long> detectionTimes = new ArrayDeque<>();
    private final Deque<Float> confidenceScores = new ArrayDeque<>();
    
    private int totalDetections = 0;
    private int successfulDetections = 0;
    private long totalDetectionTime = 0;
    private float totalConfidence = 0f;
    
    /**
     * Record detection time - optimized version
     */
    public void recordDetectionTime(long detectionTimeMs) {
        detectionTimes.offerLast(detectionTimeMs);
        totalDetectionTime += detectionTimeMs;
        totalDetections++;
        
        // Keep records of last N detections, remove oldest
        if (detectionTimes.size() > AppConstants.MAX_DETECTION_TIME_RECORDS) {
            Long removed = detectionTimes.pollFirst();
            if (removed != null) {
                totalDetectionTime -= removed;
            }
        }
        
        Log.d(TAG, "Detection time: " + detectionTimeMs + "ms");
    }
    
    /**
     * Record detection results - optimized version
     */
    public void recordDetectionResult(List<YoloDetector.DetectionResult> results) {
        if (results != null && !results.isEmpty()) {
            successfulDetections++;
            
            // Record confidence scores
            for (YoloDetector.DetectionResult result : results) {
                float confidence = result.getConfidence();
                confidenceScores.offerLast(confidence);
                totalConfidence += confidence;
                
                // Keep last N confidence records
                if (confidenceScores.size() > AppConstants.MAX_CONFIDENCE_RECORDS) {
                    Float removed = confidenceScores.pollFirst();
                    if (removed != null) {
                        totalConfidence -= removed;
                    }
                }
            }
            
            Log.d(TAG, "Detected " + results.size() + " objects");
        }
    }
    
    /**
     * Get average detection time - optimized version
     */
    public float getAverageDetectionTime() {
        return detectionTimes.isEmpty() ? 0f : (float) totalDetectionTime / detectionTimes.size();
    }
    
    /**
     * Get detection success rate
     */
    public float getSuccessRate() {
        return totalDetections == 0 ? 0f : (float) successfulDetections / totalDetections * 100f;
    }
    
    /**
     * Get average confidence - optimized version
     */
    public float getAverageConfidence() {
        return confidenceScores.isEmpty() ? 0f : totalConfidence / confidenceScores.size();
    }
    
    /**
     * Get performance report
     */
    public String getPerformanceReport() {
        return String.format(
            "檢測性能報告:\n" +
            "- 總檢測次數: %d\n" +
            "- 成功檢測次數: %d\n" +
            "- 成功率: %.1f%%\n" +
            "- 平均檢測時間: %.1fms\n" +
            "- 平均置信度: %.3f",
            totalDetections,
            successfulDetections,
            getSuccessRate(),
            getAverageDetectionTime(),
            getAverageConfidence()
        );
    }
    
    /**
     * Check if performance is good
     */
    public boolean isPerformanceGood() {
        return getSuccessRate() > 70f && 
               getAverageDetectionTime() < 500f && 
               getAverageConfidence() > 0.5f;
    }
    
    /**
     * Reset statistics
     */
    public void reset() {
        detectionTimes.clear();
        confidenceScores.clear();
        totalDetections = 0;
        successfulDetections = 0;
        totalDetectionTime = 0;
        totalConfidence = 0f;
        Log.d(TAG, "Performance statistics reset");
    }
}