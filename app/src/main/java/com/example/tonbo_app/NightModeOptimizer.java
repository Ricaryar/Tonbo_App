package com.example.tonbo_app;

import android.graphics.Bitmap;
import android.util.Log;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;

/**
 * Night mode optimizer for low-light environment detection
 * Automatically adjusts detection parameters and camera settings for better accuracy in dark conditions
 */
public class NightModeOptimizer {
    private static final String TAG = "NightModeOptimizer";
    
    // Lighting thresholds
    private static final float LOW_LIGHT_THRESHOLD = 85.0f;  // Below this is considered low light
    private static final float VERY_LOW_LIGHT_THRESHOLD = 50.0f;  // Below this is very dark
    private static final float NORMAL_LIGHT_THRESHOLD = 120.0f;  // Above this is normal light
    
    // Detection parameter adjustments for night mode
    private static final float NIGHT_MODE_CONFIDENCE_REDUCTION = 0.08f;  // 夜间仅小幅降阈，避免误检暴增
    private static final float VERY_DARK_CONFIDENCE_REDUCTION = 0.15f;  // 极暗场景也保持一定保守性
    private static final int NIGHT_MODE_STABILITY_FRAMES = 5;  // Require more frames for stability in night mode (vs 3 in normal)
    private static final int VERY_DARK_STABILITY_FRAMES = 7;  // Even more frames in very dark conditions
    
    private ColorLightingAnalyzer lightingAnalyzer;
    private boolean isNightMode = false;
    private boolean isVeryDark = false;
    private float currentBrightness = 0.0f;
    private int consecutiveLowLightFrames = 0;
    private static final int LOW_LIGHT_FRAME_THRESHOLD = 5;  // Need 5 consecutive frames to confirm low light
    
    // Detection parameters (adjusted for night mode)
    private float adjustedConfidenceThreshold;
    private float adjustedScoreThreshold;
    private int adjustedStabilityFrames;
    
    public NightModeOptimizer() {
        lightingAnalyzer = new ColorLightingAnalyzer();
        resetToNormalMode();
    }
    
    /**
     * Analyze lighting conditions and update night mode status
     * @param bitmap Image bitmap to analyze
     * @return true if night mode is active, false otherwise
     */
    public boolean analyzeAndUpdateMode(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            Log.w(TAG, "Invalid bitmap for lighting analysis");
            return isNightMode;
        }
        
        try {
            ColorLightingAnalyzer.LightingAnalysisResult lightingResult = 
                lightingAnalyzer.analyzeLighting(bitmap);
            
            currentBrightness = lightingResult.getAverageBrightness();
            String lightingCondition = lightingResult.getLightingCondition();
            
            Log.d(TAG, String.format("Lighting analysis: brightness=%.2f, condition=%s", 
                currentBrightness, lightingCondition));
            
            // Check if it's low light
            boolean isLowLight = currentBrightness < LOW_LIGHT_THRESHOLD;
            boolean isVeryLowLight = currentBrightness < VERY_LOW_LIGHT_THRESHOLD;
            
            if (isLowLight) {
                consecutiveLowLightFrames++;
            } else {
                consecutiveLowLightFrames = 0;
            }
            
            // Update night mode status (require consecutive frames to avoid flickering)
            boolean previousNightMode = isNightMode;
            boolean previousVeryDark = isVeryDark;
            
            if (consecutiveLowLightFrames >= LOW_LIGHT_FRAME_THRESHOLD) {
                isNightMode = true;
                isVeryDark = isVeryLowLight;
            } else if (currentBrightness >= NORMAL_LIGHT_THRESHOLD) {
                // Only exit night mode when brightness is clearly normal
                isNightMode = false;
                isVeryDark = false;
                consecutiveLowLightFrames = 0;
            }
            
            // Update detection parameters if mode changed
            if (isNightMode != previousNightMode || isVeryDark != previousVeryDark) {
                updateDetectionParameters();
                
                if (isNightMode) {
                    Log.d(TAG, String.format("Night mode activated: brightness=%.2f, veryDark=%s", 
                        currentBrightness, isVeryDark));
                } else {
                    Log.d(TAG, "Night mode deactivated, returning to normal mode");
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Lighting analysis failed: " + e.getMessage());
        }
        
        return isNightMode;
    }
    
    /**
     * Update detection parameters based on current lighting conditions
     */
    private void updateDetectionParameters() {
        if (isVeryDark) {
            // Very dark conditions - more aggressive adjustments
            adjustedConfidenceThreshold = Math.max(0.30f, 
                AppConstants.CONFIDENCE_THRESHOLD - VERY_DARK_CONFIDENCE_REDUCTION);
            adjustedScoreThreshold = Math.max(0.30f, 
                AppConstants.SCORE_THRESHOLD - VERY_DARK_CONFIDENCE_REDUCTION);
            adjustedStabilityFrames = VERY_DARK_STABILITY_FRAMES;
            Log.d(TAG, String.format("Very dark mode: confidence=%.2f, score=%.2f, stability=%d", 
                adjustedConfidenceThreshold, adjustedScoreThreshold, adjustedStabilityFrames));
        } else if (isNightMode) {
            // Normal night mode - moderate adjustments
            adjustedConfidenceThreshold = Math.max(0.35f, 
                AppConstants.CONFIDENCE_THRESHOLD - NIGHT_MODE_CONFIDENCE_REDUCTION);
            adjustedScoreThreshold = Math.max(0.35f, 
                AppConstants.SCORE_THRESHOLD - NIGHT_MODE_CONFIDENCE_REDUCTION);
            adjustedStabilityFrames = NIGHT_MODE_STABILITY_FRAMES;
            Log.d(TAG, String.format("Night mode: confidence=%.2f, score=%.2f, stability=%d", 
                adjustedConfidenceThreshold, adjustedScoreThreshold, adjustedStabilityFrames));
        } else {
            // Normal mode - use default parameters
            resetToNormalMode();
        }
    }
    
    /**
     * Reset to normal mode parameters
     */
    private void resetToNormalMode() {
        adjustedConfidenceThreshold = AppConstants.CONFIDENCE_THRESHOLD;
        adjustedScoreThreshold = AppConstants.SCORE_THRESHOLD;
        adjustedStabilityFrames = 3;  // Default stability frame count
    }
    
    /**
     * Get adjusted confidence threshold for current lighting conditions
     */
    public float getAdjustedConfidenceThreshold() {
        return adjustedConfidenceThreshold;
    }
    
    /**
     * Get adjusted score threshold for current lighting conditions
     */
    public float getAdjustedScoreThreshold() {
        return adjustedScoreThreshold;
    }
    
    /**
     * Get adjusted stability frame count for current lighting conditions
     */
    public int getAdjustedStabilityFrames() {
        return adjustedStabilityFrames;
    }
    
    /**
     * Check if night mode is currently active
     */
    public boolean isNightModeActive() {
        return isNightMode;
    }
    
    /**
     * Check if very dark mode is active
     */
    public boolean isVeryDarkMode() {
        return isVeryDark;
    }
    
    /**
     * Get current brightness level
     */
    public float getCurrentBrightness() {
        return currentBrightness;
    }
    
    /**
     * Get lighting condition description for voice announcement
     */
    public String getLightingDescription(String language) {
        if (isVeryDark) {
            switch (language) {
                case "english":
                    return "Very dark environment detected";
                case "mandarin":
                    return "检测到非常暗的环境";
                default:
                    return "檢測到非常暗的環境";
            }
        } else if (isNightMode) {
            switch (language) {
                case "english":
                    return "Low light environment detected, night mode activated";
                case "mandarin":
                    return "检测到低光环境，已激活夜间模式";
                default:
                    return "檢測到低光環境，已激活夜間模式";
            }
        } else {
            switch (language) {
                case "english":
                    return "Normal lighting conditions";
                case "mandarin":
                    return "正常光线条件";
                default:
                    return "正常光線條件";
            }
        }
    }
    
    /**
     * Get camera exposure compensation suggestion
     * Returns suggested EV value for low light conditions
     */
    public int getSuggestedExposureCompensation() {
        if (isVeryDark) {
            return 2;  // Increase exposure more in very dark conditions
        } else if (isNightMode) {
            return 1;  // Moderate increase in night mode
        }
        return 0;  // Normal exposure
    }
    
    /**
     * Reset night mode state (call when detection stops)
     */
    public void reset() {
        isNightMode = false;
        isVeryDark = false;
        consecutiveLowLightFrames = 0;
        currentBrightness = 0.0f;
        resetToNormalMode();
        Log.d(TAG, "Night mode optimizer reset");
    }
}

