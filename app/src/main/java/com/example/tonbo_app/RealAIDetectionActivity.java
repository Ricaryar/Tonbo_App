package com.example.tonbo_app;

import android.Manifest;
import android.graphics.Rect;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.YuvImage;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Real AI detection demo activity
 * Demonstrates real AI object detection functionality
 */
public class RealAIDetectionActivity extends BaseAccessibleActivity {
    private static final String TAG = "RealAIDetection";
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1001;

    // Static flag to track if in environment recognition page (for voice commands)
    private static boolean isEnvironmentActivityActive = false;

    /**
     * Check if in environment recognition page (for other Activities to call)
     */
    public static boolean isActive() {
        return isEnvironmentActivityActive;
    }

    private PreviewView previewView;
    private View statusIndicator;
    private android.widget.ImageButton backButton;
    private Button startButton;
    private Button stopButton;

    private TTSManager ttsManager;
    private String currentLanguage = "cantonese";
    private TextView pageTitle;

    private ProcessCameraProvider cameraProvider;
    private ImageAnalysis imageAnalysis;
    private YoloDetector yoloDetector;
    private OptimizedDetectionOverlayView detectionOverlay;
    private ExecutorService cameraExecutor;
    private boolean isDetecting = false;

    // Night mode optimizer for low-light environment detection
    private NightModeOptimizer nightModeOptimizer;
    private boolean nightModeAnnounced = false;  // Track if night mode has been announced
    private int frameCount = 0;  // Frame counter for reducing lighting analysis frequency
    private static final int LIGHTING_ANALYSIS_INTERVAL = 15;  // Analyze lighting every 15 frames (reduce overhead and avoid crashes)

    // For deduplication, avoid repeating same recognition results
    private String lastAnnouncedObjects = "";
    private Set<String> lastAnnouncedLabels = new HashSet<>(); // For more precise comparison
    private long lastAnnounceTime = 0;
    private static final long MIN_ANNOUNCE_INTERVAL_MS = 200; // Minimum announcement interval (0.2 seconds, avoid excessive repetition of identical results, shortened to reduce delay)

    // For extending detection box display time
    private Handler detectionBoxHandler = new Handler(Looper.getMainLooper());
    private Runnable clearDetectionBoxRunnable;
    private static final long DETECTION_BOX_DISPLAY_DURATION_MS = 2000; // Detection box retained for 2 seconds
    // 小物体专项放宽阈值：提高水樽召回率，同时不影响全局保守阈值
    private static final float BOTTLE_CONFIDENCE_THRESHOLD = 0.36f;
    
    // Flag to track if should auto-start detection (from voice command "what's in front")
    private boolean shouldAutoStart = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_real_ai_detection);

        // Get language settings
        if (getIntent() != null && getIntent().hasExtra("language")) {
            currentLanguage = getIntent().getStringExtra("language");
        }
        
        // Check if should auto-start detection (from voice command "what's in front")
        shouldAutoStart = getIntent().getBooleanExtra("auto_start_detection", false);

        // Initialize TTS
        ttsManager =TTSManager.getInstance(this);
        ttsManager.changeLanguage(currentLanguage);

        initViews();
        initDetector();
        initNightModeOptimizer();
        checkCameraPermission();

        // Announce page title
        announcePageTitle();
    }

    private void initViews() {
        previewView = findViewById(R.id.previewView);
        detectionOverlay = findViewById(R.id.detectionOverlay);
        // Set detection box language to ensure labels display correct language
        if (detectionOverlay != null) {
            detectionOverlay.setLanguage(currentLanguage);
        }
        statusIndicator = findViewById(R.id.statusIndicator);
        backButton = findViewById(R.id.backButton);
        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);
        pageTitle = findViewById(R.id.pageTitle);

        // Set button click events
        backButton.setOnClickListener(v -> {
            // Stop detection
            if (isDetecting) {
                stopDetection();
            }
            // Return to main page (will check if entered from gesture login)
            handleBackPressed();
        });

        startButton.setOnClickListener(v -> startDetection());
        stopButton.setOnClickListener(v -> stopDetection());

        // Update UI text based on current language
        updateLanguageUI();

        // Initialize status indicator
        updateStatusIndicator("ready");
        stopButton.setEnabled(false);
    }

    /**
     * Update language UI
     */
    private void updateLanguageUI() {
        if (pageTitle != null) {
            pageTitle.setText(getLocalizedString("environment_recognition_title"));
        }

        if (startButton != null) {
            startButton.setText(getLocalizedString("start_recognition"));
        }

        if (stopButton != null) {
            stopButton.setText(getLocalizedString("stop_recognition"));
        }
    }

    /**
     * 根據當前語言獲取本地化字符串
     */
    private String getLocalizedString(String key) {
        switch (key) {
            case "environment_recognition_title":
                if ("english".equals(currentLanguage)) {
                    return "Environment Recognition";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "环境识别助手";
                } else {
                    return "環境識別助手";
                }
            case "start_recognition":
                if ("english".equals(currentLanguage)) {
                    return "Start Recognition";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "开始识别";
                } else {
                    return "開始識別";
                }
            case "stop_recognition":
                if ("english".equals(currentLanguage)) {
                    return "Stop Recognition";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "停止识别";
                } else {
                    return "停止識別";
                }
            default:
                return "";
        }
    }

    private void initDetector() {
        try {
            yoloDetector = new YoloDetector(this);
            Log.d(TAG, "環境識別器初始化完成");
            updateStatusIndicator("ready");
        } catch (Exception e) {
            Log.e(TAG, "環境識別器初始化失敗: " + e.getMessage());
            updateStatusIndicator("error");
            Toast.makeText(this, "環境識別器初始化失敗", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Initialize night mode optimizer for low-light detection
     */
    private void initNightModeOptimizer() {
        nightModeOptimizer = new NightModeOptimizer();
        nightModeAnnounced = false;
        Log.d(TAG, "Night mode optimizer initialized");
    }

    /**
     * Convert ImageProxy to Bitmap for lighting analysis
     */
    private Bitmap imageProxyToBitmap(ImageProxy image) {
        try {
            ImageProxy.PlaneProxy[] planes = image.getPlanes();
            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 85, out);
            byte[] imageBytes = out.toByteArray();
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        } catch (Exception e) {
            Log.e(TAG, "Image conversion failed: " + e.getMessage());
            return null;
        }
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST_CODE);
        } else {
            initializeCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeCamera();
            } else {
                updateStatusIndicator("error");
                Toast.makeText(this, "需要相機權限才能使用環境識別", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void initializeCamera() {
        try {
            ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                    ProcessCameraProvider.getInstance(this);

            cameraProviderFuture.addListener(() -> {
                try {
                    cameraProvider = cameraProviderFuture.get();
                    setupCamera();
                } catch (Exception e) {
                    Log.e(TAG, "相機初始化失敗: " + e.getMessage());
                    updateStatusIndicator("error");
                }
            }, ContextCompat.getMainExecutor(this));

        } catch (Exception e) {
            Log.e(TAG, "Camera provider acquisition failed: " + e.getMessage());
            updateStatusIndicator("error");
        }
    }

    private void setupCamera() {
        try {
            // Set camera preview
            androidx.camera.core.Preview preview = new androidx.camera.core.Preview.Builder()
                    .build();

            // Set preview view scale type to FIT (maintain aspect ratio, center display)
            // This matches coordinate conversion logic
            previewView.setScaleType(androidx.camera.view.PreviewView.ScaleType.FIT_CENTER);

            preview.setSurfaceProvider(previewView.getSurfaceProvider());

            // Set image analysis
            imageAnalysis = new ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();

            // Set empty analyzer (no detection)
            imageAnalysis.setAnalyzer(cameraExecutor, new ImageAnalysis.Analyzer() {
                @Override
                public void analyze(@NonNull ImageProxy image) {
                    // Initial state: no detection, just close image
                    image.close();
                }
            });

            // Bind camera
            CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            updateStatusIndicator("ready");
            startButton.setEnabled(true);
            
            // Auto-start detection if requested (from voice command "what's in front")
            if (shouldAutoStart) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (yoloDetector != null && !isDetecting) {
                        Log.d(TAG, "Auto-starting detection from voice command");
                        startDetection();
                    }
                }, 1500); // Wait 1.5 seconds for camera to be fully ready
            }

        } catch (Exception e) {
            Log.e(TAG, "Camera setup failed: " + e.getMessage());
            updateStatusIndicator("error");
        }
    }

    private void analyzeImage(ImageProxy image) {
        try {
            // Check if Activity is finishing or destroyed
            if (isFinishing() || isDestroyed()) {
                Log.d(TAG, "Activity is finishing, skip image analysis");
                image.close();
                return;
            }

            if (!isDetecting) {
                image.close();
                return;
            }

            Log.d(TAG, "Start analyzing image, width: " + image.getWidth() + ", height: " + image.getHeight());

            // Execute AI detection first (YoloDetector will handle ImageProxy conversion internally)
            // This avoids reading ImageProxy buffer twice which causes crashes
            List<YoloDetector.DetectionResult> results = yoloDetector.detect(image);

            // Analyze lighting conditions using detection results (indirect method to avoid crashes)
            // Only analyze every N frames to reduce overhead
            frameCount++;
            if (nightModeOptimizer != null && frameCount % LIGHTING_ANALYSIS_INTERVAL == 0) {
                try {
                    // Use detection confidence as a proxy for lighting conditions
                    // Low confidence might indicate low light (though not always accurate)
                    // This is safer than reading ImageProxy buffer twice
                    float avgConfidence = 0.0f;
                    int validResults = 0;

                    if (results != null && !results.isEmpty()) {
                        for (YoloDetector.DetectionResult result : results) {
                            if (result != null && result.getConfidence() > 0) {
                                avgConfidence += result.getConfidence();
                                validResults++;
                            }
                        }
                        if (validResults > 0) {
                            avgConfidence /= validResults;
                        }
                    }

                    // Heuristic: Very low average confidence might indicate low light
                    // But we need to be conservative to avoid false positives
                    boolean wasNightMode = nightModeOptimizer.isNightModeActive();
                    boolean shouldActivateNightMode = false;

                    if (validResults == 0 || (validResults > 0 && avgConfidence < 0.25f)) {
                        // No detections or very low confidence - might be low light
                        // But only activate if we've seen this pattern consistently
                        shouldActivateNightMode = true;
                        Log.d(TAG, String.format("Low confidence pattern detected (avg: %.2f, count: %d), considering night mode",
                                avgConfidence, validResults));
                    } else if (avgConfidence >= 0.5f && wasNightMode) {
                        // High confidence - likely normal light, deactivate night mode
                        shouldActivateNightMode = false;
                        Log.d(TAG, String.format("High confidence detected (avg: %.2f), deactivating night mode", avgConfidence));
                    }

                    // Update night mode based on heuristic (conservative approach)
                    if (shouldActivateNightMode && !wasNightMode && !nightModeAnnounced) {
                        // Activate night mode conservatively
                        String lightingDesc = nightModeOptimizer.getLightingDescription(currentLanguage);
                        announceInfo(lightingDesc);
                        nightModeAnnounced = true;
                        Log.d(TAG, "Night mode activated based on confidence heuristic");
                    } else if (!shouldActivateNightMode && wasNightMode) {
                        nightModeAnnounced = false;
                        Log.d(TAG, "Night mode deactivated based on confidence heuristic");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Lighting analysis failed: " + e.getMessage());
                }
            }

            Log.d(TAG, "Detection complete, result count: " + (results != null ? results.size() : 0));
            if (results != null && !results.isEmpty()) {
                for (int i = 0; i < results.size(); i++) {
                    YoloDetector.DetectionResult result = results.get(i);
                    android.graphics.Rect bbox = result.getBoundingBox();
                    Log.d(TAG, String.format("Detection result[%d]: %s, confidence: %.2f, bbox: [%d,%d,%d,%d]",
                            i, result.getLabelZh(), result.getConfidence(),
                            bbox != null ? bbox.left : -1,
                            bbox != null ? bbox.top : -1,
                            bbox != null ? bbox.right : -1,
                            bbox != null ? bbox.bottom : -1));
                }
            }

            // Get image rotation angle (consider camera orientation)
            int rotationDegrees = image.getImageInfo().getRotationDegrees();
            Log.d(TAG, "Image rotation angle: " + rotationDegrees + " degrees, image size: " + image.getWidth() + "x" + image.getHeight());

            // Update UI on main thread
            runOnUiThread(() -> {
                if (results != null && !results.isEmpty()) {
                    updateStatusIndicator("scanning");

                    // Cancel previous clear task (if any)
                    if (clearDetectionBoxRunnable != null) {
                        detectionBoxHandler.removeCallbacks(clearDetectionBoxRunnable);
                        clearDetectionBoxRunnable = null;
                    }

                    // Adjust result coordinates to adapt to image rotation
                    List<YoloDetector.DetectionResult> adjustedResults = adjustResultsForRotation(
                            results, image.getWidth(), image.getHeight(), rotationDegrees);
                    Log.d(TAG, "Result count after coordinate adjustment: " + adjustedResults.size());

                    // Filter out unreasonable detection results for indoor environments (e.g., surfboard, refrigerator)
                    // Use night mode adjusted thresholds if night mode is active
                    float confidenceThreshold = nightModeOptimizer != null && nightModeOptimizer.isNightModeActive()
                            ? nightModeOptimizer.getAdjustedConfidenceThreshold()
                            : AppConstants.CONFIDENCE_THRESHOLD;
                    float scoreThreshold = nightModeOptimizer != null && nightModeOptimizer.isNightModeActive()
                            ? nightModeOptimizer.getAdjustedScoreThreshold()
                            : AppConstants.SCORE_THRESHOLD;

                    List<YoloDetector.DetectionResult> filteredResults = filterUnreasonableDetections(
                            adjustedResults, confidenceThreshold, scoreThreshold);
                    Log.d(TAG, "Result count after filtering: " + filteredResults.size() +
                            (nightModeOptimizer != null && nightModeOptimizer.isNightModeActive()
                                    ? " (night mode thresholds applied)" : ""));

                    // Uniformly limit to top 2 results, ensure display and voice announcement are completely synchronized
                    List<YoloDetector.DetectionResult> displayResults;
                    if (filteredResults.size() > 2) {
                        displayResults = new ArrayList<>(filteredResults.subList(0, 2));
                        Log.d(TAG, "Limit display and announcement to top 2 detection results");
                    } else {
                        displayResults = filteredResults;
                    }

                    // Update detection result overlay
                    if (detectionOverlay != null) {
                        Log.d(TAG, "=== Update detection results to overlay, count: " + displayResults.size() + " ===");

                        // Get preview view's actual display size
                        int previewWidth = previewView.getWidth();
                        int previewHeight = previewView.getHeight();
                        Log.d(TAG, "Preview view size: " + previewWidth + "x" + previewHeight);

                        // Inform overlay of source image size to ensure pixel coordinates are correctly mapped
                        // Use image's actual size (YoloDetector returns coordinates based on this size)
                        detectionOverlay.setSourceImageSize(image.getWidth(), image.getHeight());

                        // Set current language to ensure labels display correct language (must be set before updating results)
                        detectionOverlay.setLanguage(currentLanguage);
                        Log.d(TAG, "Set detection overlay language to: " + currentLanguage);

                        // Record labels to be displayed (for consistency verification)
                        for (int i = 0; i < displayResults.size(); i++) {
                            YoloDetector.DetectionResult result = displayResults.get(i);
                            String displayLabel;
                            if ("english".equals(currentLanguage)) {
                                displayLabel = result.getLabel();
                            } else {
                                displayLabel = result.getLabelZh();
                            }
                            Log.d(TAG, String.format("Display result[%d]: %s (English: %s, Chinese: %s, confidence: %.2f)",
                                    i, displayLabel, result.getLabel(), result.getLabelZh(), result.getConfidence()));
                        }

                        // Directly pass limited results, no longer limit in overlay
                        detectionOverlay.updateDetectionResults(displayResults);
                        Log.d(TAG, "Updated detection overlay, result count: " + displayResults.size());
                    } else {
                        Log.e(TAG, "❌ detectionOverlay is null, cannot update detection results!");
                    }

                    // Use same result list for voice announcement to ensure complete synchronization
                    Log.d(TAG, "=== Start voice announcement, using same result list ===");

                    // Verification: record labels to be announced, ensure consistency with display
                    StringBuilder verificationLog = new StringBuilder("Verify announcement label order: ");
                    for (int i = 0; i < displayResults.size(); i++) {
                        YoloDetector.DetectionResult result = displayResults.get(i);
                        String label;
                        if ("english".equals(currentLanguage)) {
                            label = result.getLabel();
                        } else {
                            label = result.getLabelZh();
                        }
                        verificationLog.append("[").append(i).append("]=").append(label).append(" ");
                    }
                    Log.d(TAG, verificationLog.toString());

                    announceDetectionResults(displayResults);
                } else {
                    Log.d(TAG, "Detection results empty, delay clearing overlay");
                    updateStatusIndicator("scanning");

                    // Delay clearing detection result overlay to keep detection boxes visible longer
                    if (clearDetectionBoxRunnable != null) {
                        detectionBoxHandler.removeCallbacks(clearDetectionBoxRunnable);
                    }

                    clearDetectionBoxRunnable = () -> {
                        if (detectionOverlay != null) {
                            detectionOverlay.clearDetectionResults();
                            Log.d(TAG, "Delayed clearing detection boxes complete");
                        }
                        clearDetectionBoxRunnable = null;
                    };

                    detectionBoxHandler.postDelayed(clearDetectionBoxRunnable, DETECTION_BOX_DISPLAY_DURATION_MS);
                }
            });

            image.close();

        } catch (Exception e) {
            Log.e(TAG, "Image analysis failed: " + e.getMessage(), e);
            runOnUiThread(() -> updateStatusIndicator("error"));
            image.close();
        }
    }

    private void startDetection() {
        if (yoloDetector == null) {
            Toast.makeText(this, "環境識別器未初始化", Toast.LENGTH_SHORT).show();
            return;
        }

        // Immediately stop all ongoing speech playback
        if (ttsManager != null) {
            ttsManager.stopSpeaking();
            Log.d(TAG, "Stopped current speech playback");
        }

        isDetecting = true;
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        updateStatusIndicator("scanning");

        // Set real image analyzer, start recognition immediately (don't wait for speech playback)
        if (imageAnalysis != null) {
            imageAnalysis.setAnalyzer(cameraExecutor, new ImageAnalysis.Analyzer() {
                @Override
                public void analyze(@NonNull ImageProxy image) {
                    if (isDetecting && yoloDetector != null) {
                        analyzeImage(image);
                    } else {
                        image.close();
                    }
                }
            });
        }

        // Announce "Environment recognition started" (but won't block recognition function startup)
        String announcement = (currentLanguage.equals("english"))
                ? "Environment recognition started"
                : (currentLanguage.equals("mandarin") ? "環境識別已開始" : "環境識別已開始");
        ttsManager.speak(announcement, announcement, true);

        Log.d(TAG, "Environment recognition started immediately, speech playback in background");
        Toast.makeText(this, "環境識別已開始", Toast.LENGTH_SHORT).show();
    }

    private void stopDetection() {
        isDetecting = false;
        startButton.setEnabled(true);
        stopButton.setEnabled(false);

        // Stop image analysis to avoid background processing
        if (imageAnalysis != null) {
            imageAnalysis.setAnalyzer(null, null);
        }

        // Cancel delayed clear task
        if (clearDetectionBoxRunnable != null) {
            detectionBoxHandler.removeCallbacks(clearDetectionBoxRunnable);
            clearDetectionBoxRunnable = null;
        }

        // Clear detection result display
        if (detectionOverlay != null) {
            detectionOverlay.clearDetectionResults();
        }

        // Reset night mode optimizer
        if (nightModeOptimizer != null) {
            nightModeOptimizer.reset();
            nightModeAnnounced = false;
        }

        updateStatusIndicator("ready");

        Toast.makeText(this, "環境識別已停止", Toast.LENGTH_SHORT).show();

        // Stop all speech playback
        if (ttsManager != null) {
            ttsManager.stopSpeaking();
        }
    }


    private void updateStatusIndicator(String status) {
        int drawableRes;
        switch (status) {
            case "ready":
                drawableRes = R.drawable.status_indicator_ready;
                break;
            case "scanning":
                drawableRes = R.drawable.status_indicator_scanning;
                break;
            case "error":
                drawableRes = R.drawable.status_indicator_error;
                break;
            default:
                drawableRes = R.drawable.status_indicator_ready;
                break;
        }
        statusIndicator.setBackgroundResource(drawableRes);
        Log.d(TAG, "Status indicator updated: " + status);
    }


    private void announceDetectionResults(List<YoloDetector.DetectionResult> results) {
        if (results == null || results.isEmpty()) {
            // Don't announce empty results to avoid information overload
            lastAnnouncedObjects = ""; // Clear last announcement record
            lastAnnouncedLabels.clear(); // Clear label set
            Log.d(TAG, "Voice announcement: results empty, skip announcement and clear records");
            return;
        }

        // Directly use passed results (already limited to top 2 before call, completely synchronized with display)
        // Ensure announced objects completely match detection boxes displayed on screen
        StringBuilder announcement = new StringBuilder();

        // Select labels based on current language
        boolean useEnglish = "english".equals(currentLanguage);
        String separator = useEnglish ? ", " : "、";

        Log.d(TAG, "Voice announcement: current language = " + currentLanguage + ", result count = " + results.size());

        // Announce all passed results (already limited to top 2, completely synchronized with display)
        // Ensure label selection logic completely matches display (completely consistent with OptimizedDetectionOverlayView.drawDetectionResult())
        // Use exactly the same order and label selection logic as display
        for (int i = 0; i < results.size(); i++) {
            YoloDetector.DetectionResult result = results.get(i);
            if (result == null) {
                Log.w(TAG, "Voice announcement result[" + i + "] is null, skip");
                continue;
            }

            // Select labels based on language (completely consistent with logic in OptimizedDetectionOverlayView)
            String label;
            if ("english".equals(currentLanguage)) {
                label = result.getLabel(); // English mode uses English labels
            } else if ("mandarin".equals(currentLanguage)) {
                label = result.getLabelZh(); // Mandarin mode uses Chinese labels
            } else {
                label = result.getLabelZh(); // Cantonese mode uses Chinese labels
            }

            // Detailed logging to ensure consistency with display
            Log.d(TAG, String.format("Voice announcement result[%d]: %s (English: %s, Chinese: %s, confidence: %.2f, bbox: %s)",
                    i, label, result.getLabel(), result.getLabelZh(), result.getConfidence(),
                    result.getBoundingBox() != null ? result.getBoundingBox().toString() : "null"));

            announcement.append(label);
            if (i < results.size() - 1) {
                announcement.append(separator);
            }
        }

        String currentObjects = announcement.toString();
        long currentTime = System.currentTimeMillis();
        Log.d(TAG, "Voice announcement: constructed announcement text = \"" + currentObjects + "\"");

        // Verification: ensure announcement text completely matches display labels
        StringBuilder expectedText = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            YoloDetector.DetectionResult result = results.get(i);
            if (result == null) continue;
            String label;
            if ("english".equals(currentLanguage)) {
                label = result.getLabel();
            } else {
                label = result.getLabelZh();
            }
            expectedText.append(label);
            if (i < results.size() - 1) {
                expectedText.append(separator);
            }
        }
        String expected = expectedText.toString();
        if (!currentObjects.equals(expected)) {
            Log.e(TAG, "❌ Voice announcement text inconsistent! Expected: \"" + expected + "\", Actual: \"" + currentObjects + "\"");
        } else {
            Log.d(TAG, "✅ Voice announcement text verification passed: \"" + currentObjects + "\"");
        }

        // Build current detected label set (for more precise comparison)
        Set<String> currentLabels = new HashSet<>();
        for (YoloDetector.DetectionResult result : results) {
            String label;
            if ("english".equals(currentLanguage)) {
                label = result.getLabel();
            } else {
                label = result.getLabelZh();
            }
            currentLabels.add(label);
        }

        // Check if need to announce:
        // 1. First time detecting items (last record empty) - announce immediately (no delay)
        // 2. Detected object set differs from last time (use set comparison, order-independent) - announce immediately (no delay)
        // 3. Detection result count changed - announce immediately (no delay)
        // 4. Announcement text differs from last time - announce immediately (no delay)
        // 5. Time since last announcement exceeds minimum interval (even if labels same, periodically announce to ensure responsiveness)
        boolean isFirstDetection = lastAnnouncedLabels.isEmpty();
        boolean labelsChanged = !currentLabels.equals(lastAnnouncedLabels);
        boolean countChanged = currentLabels.size() != lastAnnouncedLabels.size();
        boolean textChanged = !currentObjects.equals(lastAnnouncedObjects);

        // Priority: announce immediately when detection results change (no delay)
        // If announcement text is exactly the same, don't announce (avoid repeating same content, e.g., "pen pen pen pen pen")
        // Only announce when detection results really change, ensure won't repeat same content
        boolean hasChange = isFirstDetection || labelsChanged || countChanged || textChanged;

        // If detection results are exactly the same, don't repeat even if time expired (avoid repeating same content)
        // Only announce when detection results really change
        boolean shouldAnnounce = hasChange;

        if (!hasChange) {
            Log.d(TAG, "⏭️ Skip duplicate announcement: announcement text exactly the same (\"" + currentObjects + "\"), don't repeat");
        }

        Log.d(TAG, String.format("Announcement decision - First detection: %s, Label change: %s, Count change: %s, Text change: %s, Result: %s",
                isFirstDetection, labelsChanged, countChanged, textChanged, shouldAnnounce));

        if (shouldAnnounce) {
            // Announce based on language (ensure completely consistent with display labels)
            // currentObjects already selected correct labels (English or Chinese) based on currentLanguage
            String cantoneseText;
            String englishText;

            if ("english".equals(currentLanguage)) {
                // English mode: currentObjects contains English labels
                englishText = currentObjects; // Use directly, completely consistent with display labels
                // For English mode, cantoneseText can be empty or English (TTS will use englishText)
                cantoneseText = currentObjects;
                Log.d(TAG, "English mode announcement - English text: " + englishText + " (consistent with display labels)");
            } else if ("mandarin".equals(currentLanguage)) {
                // Mandarin mode: currentObjects contains Chinese labels
                cantoneseText = currentObjects; // Use directly, completely consistent with display labels
                // Get corresponding English labels for backup (ensure order consistent with display)
                StringBuilder englishBuilder = new StringBuilder();
                for (int i = 0; i < results.size(); i++) {
                    YoloDetector.DetectionResult result = results.get(i);
                    if (result == null) continue;
                    englishBuilder.append(result.getLabel());
                    if (i < results.size() - 1) {
                        englishBuilder.append(", ");
                    }
                }
                englishText = englishBuilder.toString();
                Log.d(TAG, "Mandarin mode announcement - Chinese text: " + cantoneseText + " (consistent with display labels), English text: " + englishText);
            } else {
                // Cantonese mode: currentObjects contains Chinese labels
                cantoneseText = currentObjects; // Use directly, completely consistent with display labels
                // Get corresponding English labels for backup (ensure order consistent with display)
                StringBuilder englishBuilder = new StringBuilder();
                for (int i = 0; i < results.size(); i++) {
                    YoloDetector.DetectionResult result = results.get(i);
                    if (result == null) continue;
                    englishBuilder.append(result.getLabel());
                    if (i < results.size() - 1) {
                        englishBuilder.append(", ");
                    }
                }
                englishText = englishBuilder.toString();
                Log.d(TAG, "Cantonese mode announcement - Chinese text: " + cantoneseText + " (consistent with display labels), English text: " + englishText);
            }

            Log.d(TAG, "Prepare announcement - Current language: " + currentLanguage + ", Chinese text: " + cantoneseText + ", English text: " + englishText);

            // Final verification: ensure announcement text completely matches display labels
            Log.d(TAG, "=== Final verification: announcement text ===");
            Log.d(TAG, "Display label order: " + currentObjects);
            Log.d(TAG, "TTS will announce text (Chinese mode): " + cantoneseText);
            Log.d(TAG, "TTS will announce text (English mode): " + englishText);
            if (!cantoneseText.equals(currentObjects) && !"english".equals(currentLanguage)) {
                Log.e(TAG, "❌ Warning: TTS Chinese text inconsistent with display labels!");
            }
            if (!englishText.equals(currentObjects) && "english".equals(currentLanguage)) {
                Log.e(TAG, "❌ Warning: TTS English text inconsistent with display labels!");
            }

            // Use priority announcement, immediately stop current speech and play new, reduce delay
            ttsManager.speak(cantoneseText, englishText, true);

            // Update records (update both string and label set)
            lastAnnouncedObjects = currentObjects;
            lastAnnouncedLabels = new HashSet<>(currentLabels); // Create copy
            lastAnnounceTime = currentTime;

            Log.d(TAG, "✅ Announced detection results: " + currentObjects + " (label set: " + currentLabels + ", completely consistent with display labels)");
        } else {
            Log.d(TAG, String.format("⏭️ Skip duplicate announcement: %s (last: %s, current labels: %s, last labels: %s, time since last: %dms)",
                    currentObjects, lastAnnouncedObjects, currentLabels, lastAnnouncedLabels, (currentTime - lastAnnounceTime)));
        }
    }

    private String categorizeImportance(String label) {
        // Critical objects: person, vehicles, obstacles
        if (label.contains("person") || label.contains("car") ||
                label.contains("truck") || label.contains("bus") ||
                label.contains("motorcycle") || label.contains("obstacle")) {
            return "critical";
        }

        // Important objects: furniture, doors, switches
        if (label.contains("chair") || label.contains("table") ||
                label.contains("door") || label.contains("sofa") ||
                label.contains("bed") || label.contains("keyboard")) {
            return "important";
        }

        // Optional objects: decorations
        return "optional";
    }

    /**
     * Filter out clearly unreasonable detection results
     *
     * 調整策略：
     * - 只過濾「極不可能」的類別（如長頸鹿、斑馬、衝浪板等），避免錯殺手機、杯子、瓶子等日常物品
     * - 仍然會套用置信度閾值，防止「亂識別」信心很低的結果
     */
    private List<YoloDetector.DetectionResult> filterUnreasonableDetections(
            List<YoloDetector.DetectionResult> results,
            float confidenceThreshold,
            float scoreThreshold) {
        if (results == null || results.isEmpty()) {
            return results;
        }

        // 僅保留一個極小的黑名單：幾乎不會在日常使用場景中出現的物體
        Set<String> unreasonableItems = new HashSet<>();

        // 大型野生動物
        unreasonableItems.add("elephant");
        unreasonableItems.add("bear");
        unreasonableItems.add("zebra");
        unreasonableItems.add("giraffe");
        unreasonableItems.add("cow");
        unreasonableItems.add("sheep");
        unreasonableItems.add("horse");

        // 極少見運動用品
        unreasonableItems.add("surfboard");
        unreasonableItems.add("skis");
        unreasonableItems.add("snowboard");
        unreasonableItems.add("baseball bat");
        unreasonableItems.add("baseball glove");

        // 其它非常不常見的物體可以視情況再補充

        List<YoloDetector.DetectionResult> filteredResults = new ArrayList<>();
        int filteredCount = 0;

        // 使用更严格的有效阈值，确保 confidence/score 两个门槛都生效
        float effectiveThreshold = Math.max(confidenceThreshold, scoreThreshold);

        for (YoloDetector.DetectionResult result : results) {
            String label = result.getLabel().toLowerCase();
            float confidence = result.getConfidence();
            float labelThreshold = "bottle".equals(label) ? BOTTLE_CONFIDENCE_THRESHOLD : effectiveThreshold;

            Log.d(TAG, "Check detection result: " + label + " (confidence: " + confidence +
                    ", threshold: " + labelThreshold + ")");

            // 1. 置信度太低，一律過濾（減少「亂識別」）
            if (confidence < labelThreshold) {
                Log.d(TAG, "Filter out low confidence detection: " + label + " (confidence: " + confidence +
                        " < threshold: " + labelThreshold + ")");
                filteredCount++;
                continue;
            }

            // 2. 僅對極不可能出現的物體做黑名單過濾
            if (unreasonableItems.contains(label)) {
                Log.d(TAG, "Filter out unreasonable detection: " + label + " (confidence: " + confidence + ")");
                filteredCount++;
                continue;
            }

            filteredResults.add(result);
        }

        if (filteredCount > 0) {
            Log.d(TAG, "Filter statistics: filtered " + filteredCount + " detections, kept " +
                    filteredResults.size() + " results (original: " + results.size() + ")");
        }

        return filteredResults;
    }

    /**
     * Adjust detection result coordinates based on image rotation angle
     */
    private List<YoloDetector.DetectionResult> adjustResultsForRotation(
            List<YoloDetector.DetectionResult> results,
            int imageWidth, int imageHeight,
            int rotationDegrees) {

        if (results == null || results.isEmpty() || rotationDegrees == 0) {
            return results;
        }

        List<YoloDetector.DetectionResult> adjustedResults = new ArrayList<>();

        for (YoloDetector.DetectionResult result : results) {
            Rect bbox = result.getBoundingBox();
            if (bbox == null) {
                adjustedResults.add(result);
                continue;
            }

            Rect adjustedBbox = rotateBoundingBox(bbox, imageWidth, imageHeight, rotationDegrees);

            // Create new detection result with adjusted bounding box
            YoloDetector.DetectionResult adjustedResult = new YoloDetector.DetectionResult(
                    result.getLabel(),
                    result.getLabelZh(),
                    result.getConfidence(),
                    adjustedBbox
            );
            adjustedResults.add(adjustedResult);
        }

        return adjustedResults;
    }

    /**
     * Rotate bounding box coordinates
     */
    private Rect rotateBoundingBox(Rect bbox, int imageWidth, int imageHeight, int rotationDegrees) {
        int left = bbox.left;
        int top = bbox.top;
        int right = bbox.right;
        int bottom = bbox.bottom;

        int newLeft, newTop, newRight, newBottom;

        switch (rotationDegrees) {
            case 90:
                // Clockwise rotate 90 degrees: top-left becomes top-right
                newLeft = imageHeight - bottom;
                newTop = left;
                newRight = imageHeight - top;
                newBottom = right;
                return new Rect(newLeft, newTop, newRight, newBottom);

            case 180:
                // Rotate 180 degrees: flip top-bottom and left-right
                newLeft = imageWidth - right;
                newTop = imageHeight - bottom;
                newRight = imageWidth - left;
                newBottom = imageHeight - top;
                return new Rect(newLeft, newTop, newRight, newBottom);

            case 270:
                // Clockwise rotate 270 degrees (or counterclockwise 90 degrees): top-left becomes bottom-left
                newLeft = top;
                newTop = imageWidth - right;
                newRight = bottom;
                newBottom = imageWidth - left;
                return new Rect(newLeft, newTop, newRight, newBottom);

            default:
                // 0 degrees or other, no adjustment needed
                return bbox;
        }
    }

    private String estimateDistance(Rect boundingBox) {
        if (boundingBox == null) return "";

        // Estimate relative distance
        float area = boundingBox.width() * boundingBox.height();
        float normalizedArea = area / (1080 * 1920); // Assume standard screen

        if (normalizedArea > 0.1) {
            return "約1米處";
        } else if (normalizedArea > 0.05) {
            return "約2米處";
        } else if (normalizedArea > 0.02) {
            return "約3米處";
        } else {
            return "遠處約";
        }
    }

    private String translateToEnglish(String text) {
        // Simple translation mapping
        if (text.contains("檢測到")) text = text.replace("檢測到", "detected");
        if (text.contains("個物體")) text = text.replace("個物體", " objects");
        if (text.contains("有")) text = text.replace("有", "has");
        if (text.contains("左側")) text = text.replace("左側", "left side");
        if (text.contains("右側")) text = text.replace("右側", "right side");
        if (text.contains("上方")) text = text.replace("上方", "above");
        if (text.contains("下方")) text = text.replace("下方", "below");
        if (text.contains("中央")) text = text.replace("中央", "center");
        return text;
    }

    private String getPositionDescription(Rect boundingBox) {
        if (boundingBox == null) {
            return "未知位置";
        }

        // Assume standard screen aspect ratio, calculate relative position
        float centerX = (boundingBox.left + boundingBox.right) / 2.0f;
        float centerY = (boundingBox.top + boundingBox.bottom) / 2.0f;

        // Assume screen width 1000, height 1500 (can adjust based on actual situation)
        float relativeX = centerX / 1000.0f;
        float relativeY = centerY / 1500.0f;

        String horizontal = relativeX < 0.33f ? "左側" : (relativeX > 0.66f ? "右側" : "中間");
        String vertical = relativeY < 0.33f ? "上方" : (relativeY > 0.66f ? "下方" : "中間");

        if (horizontal.equals("中間") && vertical.equals("中間")) {
            return "正中央";
        } else if (horizontal.equals("中間")) {
            return vertical;
        } else if (vertical.equals("中間")) {
            return horizontal;
        } else {
            return horizontal + vertical;
        }
    }

    @Override
    protected void announcePageTitle() {
        String cantoneseText = "環境識別助手。這個功能可以幫助你識別前方的物體。點擊開始識別按鈕開始掃描環境。";
        String englishText = "Environment Recognition Assistant. This feature can help you identify objects in front of you. Tap the start recognition button to begin scanning the environment.";
        ttsManager.speak(cantoneseText, englishText, true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        isEnvironmentActivityActive = true;
        if (cameraExecutor == null || cameraExecutor.isShutdown()) {
            cameraExecutor = Executors.newSingleThreadExecutor();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isEnvironmentActivityActive = false;

        Log.d(TAG, "onPause: Stop detection and clean up camera resources");

        // Stop detection
        if (isDetecting) {
            stopDetection();
        }

        // Unbind camera (prevent continuing to write to SurfaceView when Activity is paused)
        if (cameraProvider != null) {
            try {
                cameraProvider.unbindAll();
                Log.d(TAG, "onPause: Camera unbound");
            } catch (Exception e) {
                Log.e(TAG, "onPause: Failed to unbind camera: " + e.getMessage());
            }
        }

        // Clean up image analyzer
        if (imageAnalysis != null) {
            try {
                imageAnalysis.clearAnalyzer();
                Log.d(TAG, "onPause: Image analyzer cleared");
            } catch (Exception e) {
                Log.e(TAG, "onPause: Failed to clear image analyzer: " + e.getMessage());
            }
        }

        // Close camera executor
        if (cameraExecutor != null) {
            try {
                cameraExecutor.shutdown();
                // Wait for tasks to complete, but don't force terminate
                if (!cameraExecutor.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS)) {
                    Log.w(TAG, "onPause: Camera executor didn't close within 1 second, force shutdown");
                    cameraExecutor.shutdownNow();
                }
                Log.d(TAG, "onPause: Camera executor closed");
            } catch (InterruptedException e) {
                Log.e(TAG, "onPause: Camera executor shutdown interrupted");
                cameraExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Log.d(TAG, "onDestroy: Start cleaning up all resources");

        // Stop detection (if still running)
        if (isDetecting) {
            stopDetection();
        }

        // Ensure stop detection flag is set
        isDetecting = false;

        // Clean up image analyzer
        if (imageAnalysis != null) {
            try {
                imageAnalysis.clearAnalyzer();
                imageAnalysis = null;
                Log.d(TAG, "onDestroy: Image analyzer cleared");
            } catch (Exception e) {
                Log.e(TAG, "onDestroy: Failed to clear image analyzer: " + e.getMessage());
            }
        }

        // Unbind camera
        if (cameraProvider != null) {
            try {
                cameraProvider.unbindAll();
                cameraProvider = null;
                Log.d(TAG, "onDestroy: Camera unbound");
            } catch (Exception e) {
                Log.e(TAG, "onDestroy: Failed to unbind camera: " + e.getMessage());
            }
        }

        // Close YOLO detector
        if (yoloDetector != null) {
            try {
                yoloDetector.close();
                yoloDetector = null;
                Log.d(TAG, "onDestroy: YOLO detector closed");
            } catch (Exception e) {
                Log.e(TAG, "onDestroy: Failed to close YOLO detector: " + e.getMessage());
            }
        }

        // Close camera executor
        if (cameraExecutor != null) {
            try {
                cameraExecutor.shutdown();
                if (!cameraExecutor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    Log.w(TAG, "onDestroy: Camera executor didn't close within 2 seconds, force shutdown");
                    cameraExecutor.shutdownNow();
                }
                cameraExecutor = null;
                Log.d(TAG, "onDestroy: Camera executor closed");
            } catch (InterruptedException e) {
                Log.e(TAG, "onDestroy: Camera executor shutdown interrupted");
                cameraExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        Log.d(TAG, "onDestroy: Resource cleanup complete");
    }
}

