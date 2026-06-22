package com.example.tonbo_app;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * ML Kit object detector (temporarily disabled version)
 * 
 * Note: Due to network issues, ML Kit dependency has been temporarily disabled
 * When network can access Google services, ML Kit can be restored
 * 
 * Restoration steps:
 * 1. Uncomment ML Kit dependency in build.gradle.kts
 * 2. Restore ML Kit imports and implementation in this file
 * 3. Enable setupMLKitDetector() in ObjectDetectorHelper
 * 
 * @author Auto (AI Assistant)
 */
public class MLKitObjectDetector {
    private static final String TAG = "MLKitObjectDetector";
    
    private Context context;
    private boolean isInitialized = false;
    
    public MLKitObjectDetector(Context context) {
        this.context = context;
        // ML Kit temporarily disabled to avoid compilation errors
        isInitialized = false;
        Log.d(TAG, "ML Kit detector disabled (network issues)");
    }
    
    /**
     * Detect objects in image (temporarily disabled)
     * @param bitmap Input image
     * @return Empty detection result list
     */
    public List<ObjectDetectorHelper.DetectionResult> detect(Bitmap bitmap) {
        // ML Kit temporarily disabled, return empty results
        Log.d(TAG, "ML Kit detector disabled, returning empty results");
        return new ArrayList<>();
    }
    
    /**
     * Close detector, release resources
     */
    public void close() {
        Log.d(TAG, "ML Kit detector closed (disabled)");
        isInitialized = false;
    }
    
    /**
     * Check if detector is initialized
     */
    public boolean isInitialized() {
        return false;  // Always return false because disabled
    }
}
