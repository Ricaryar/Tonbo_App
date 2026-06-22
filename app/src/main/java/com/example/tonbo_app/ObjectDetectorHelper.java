package com.example.tonbo_app;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Real object detection helper
 * Uses TensorFlow Lite Task Vision API
 */
public class ObjectDetectorHelper {
    private static final String TAG = "ObjectDetectorHelper";
    
    // Environment recognition relevant object categories (only detect these common objects)
    private static final Set<String> ENVIRONMENT_RELEVANT_OBJECTS = new HashSet<>();
    
    // Blacklist: explicitly exclude uncommon or irrelevant objects
    private static final Set<String> EXCLUDED_OBJECTS = new HashSet<>();
    
    static {
        // Blacklist: exclude uncommon objects
        EXCLUDED_OBJECTS.add("giraffe");          // Giraffe
        EXCLUDED_OBJECTS.add("zebra");            // Zebra
        EXCLUDED_OBJECTS.add("elephant");         // Elephant
        EXCLUDED_OBJECTS.add("bear");             // Bear
        EXCLUDED_OBJECTS.add("cow");              // Cow
        EXCLUDED_OBJECTS.add("sheep");            // Sheep
        EXCLUDED_OBJECTS.add("horse");            // Horse
        EXCLUDED_OBJECTS.add("airplane");         // Airplane (usually not in indoor environments)
        EXCLUDED_OBJECTS.add("train");            // Train
        EXCLUDED_OBJECTS.add("boat");             // Boat
        EXCLUDED_OBJECTS.add("surfboard");        // Surfboard
        EXCLUDED_OBJECTS.add("kite");             // Kite
        EXCLUDED_OBJECTS.add("frisbee");          // Frisbee
        EXCLUDED_OBJECTS.add("baseball bat");     // Baseball bat
        EXCLUDED_OBJECTS.add("baseball glove");   // Baseball glove
        EXCLUDED_OBJECTS.add("tennis racket");    // Tennis racket
        EXCLUDED_OBJECTS.add("skateboard");       // Skateboard
        
        // Environment recognition relevant important objects (whitelist) - only includes common objects useful for visually impaired users
        // Transportation related
        ENVIRONMENT_RELEVANT_OBJECTS.add("person");           // Person
        ENVIRONMENT_RELEVANT_OBJECTS.add("car");              // Car
        ENVIRONMENT_RELEVANT_OBJECTS.add("truck");            // Truck
        ENVIRONMENT_RELEVANT_OBJECTS.add("bus");              // Bus
        ENVIRONMENT_RELEVANT_OBJECTS.add("motorcycle");       // Motorcycle
        ENVIRONMENT_RELEVANT_OBJECTS.add("bicycle");          // Bicycle
        ENVIRONMENT_RELEVANT_OBJECTS.add("traffic light");    // Traffic light
        ENVIRONMENT_RELEVANT_OBJECTS.add("stop sign");        // Stop sign
        
        // Furniture
        ENVIRONMENT_RELEVANT_OBJECTS.add("bench");            // Bench
        ENVIRONMENT_RELEVANT_OBJECTS.add("chair");            // Chair
        ENVIRONMENT_RELEVANT_OBJECTS.add("table");            // Table
        ENVIRONMENT_RELEVANT_OBJECTS.add("dining table");     // Dining table
        ENVIRONMENT_RELEVANT_OBJECTS.add("bed");              // Bed
        ENVIRONMENT_RELEVANT_OBJECTS.add("couch");            // Couch
        ENVIRONMENT_RELEVANT_OBJECTS.add("toilet");           // Toilet
        
        // Electronic devices
        ENVIRONMENT_RELEVANT_OBJECTS.add("tv");               // TV
        ENVIRONMENT_RELEVANT_OBJECTS.add("laptop");           // Laptop
        ENVIRONMENT_RELEVANT_OBJECTS.add("mouse");            // Mouse
        ENVIRONMENT_RELEVANT_OBJECTS.add("remote");           // Remote
        ENVIRONMENT_RELEVANT_OBJECTS.add("keyboard");         // Keyboard
        ENVIRONMENT_RELEVANT_OBJECTS.add("cell phone");       // Cell phone
        ENVIRONMENT_RELEVANT_OBJECTS.add("clock");            // Clock
        
        // Kitchen items
        ENVIRONMENT_RELEVANT_OBJECTS.add("microwave");        // Microwave
        ENVIRONMENT_RELEVANT_OBJECTS.add("oven");             // Oven
        ENVIRONMENT_RELEVANT_OBJECTS.add("toaster");          // Toaster
        ENVIRONMENT_RELEVANT_OBJECTS.add("sink");             // Sink
        ENVIRONMENT_RELEVANT_OBJECTS.add("refrigerator");     // Refrigerator
        ENVIRONMENT_RELEVANT_OBJECTS.add("bottle");           // Bottle
        ENVIRONMENT_RELEVANT_OBJECTS.add("cup");              // Cup
        ENVIRONMENT_RELEVANT_OBJECTS.add("wine glass");       // Wine glass
        ENVIRONMENT_RELEVANT_OBJECTS.add("bowl");             // Bowl
        ENVIRONMENT_RELEVANT_OBJECTS.add("fork");             // Fork
        ENVIRONMENT_RELEVANT_OBJECTS.add("knife");            // Knife
        ENVIRONMENT_RELEVANT_OBJECTS.add("spoon");            // Spoon
        
        // Daily items
        ENVIRONMENT_RELEVANT_OBJECTS.add("book");             // Book
        ENVIRONMENT_RELEVANT_OBJECTS.add("umbrella");         // Umbrella
        ENVIRONMENT_RELEVANT_OBJECTS.add("handbag");          // Handbag
        ENVIRONMENT_RELEVANT_OBJECTS.add("backpack");         // Backpack
        ENVIRONMENT_RELEVANT_OBJECTS.add("suitcase");         // Suitcase
        ENVIRONMENT_RELEVANT_OBJECTS.add("vase");             // Vase
        ENVIRONMENT_RELEVANT_OBJECTS.add("scissors");         // Scissors
        ENVIRONMENT_RELEVANT_OBJECTS.add("hair drier");       // Hair drier
        ENVIRONMENT_RELEVANT_OBJECTS.add("toothbrush");       // Toothbrush
        
        // Food (common)
        ENVIRONMENT_RELEVANT_OBJECTS.add("banana");           // Banana
        ENVIRONMENT_RELEVANT_OBJECTS.add("apple");            // Apple
        ENVIRONMENT_RELEVANT_OBJECTS.add("orange");           // Orange
        ENVIRONMENT_RELEVANT_OBJECTS.add("sandwich");         // Sandwich
        ENVIRONMENT_RELEVANT_OBJECTS.add("pizza");            // Pizza
    }
    
    // Stability enhancement parameters
    private static final int MAX_RETRY_ATTEMPTS = 4;  // Increased retry count
    private static final long RETRY_DELAY_MS = 50;   // Reduced retry delay
    private static final int MAX_CONSECUTIVE_FAILURES = 5;  // Maximum consecutive failures
    private static final long DETECTION_TIMEOUT_MS = 5000;  // Detection timeout
    
    private ObjectDetector objectDetector;  // TensorFlow Lite SSD detector
    private YoloDetector yoloDetector;     // YOLO detector
    private MLKitObjectDetector mlKitDetector;  // ML Kit detector (temporarily disabled)
    private Context context;
    private boolean useYolo = false;  // Whether to use YOLO detector
    private boolean useMLKit = false;  // Whether to use ML Kit detector (temporarily disabled to avoid network issues)
    
    // Stability monitoring variables
    private int consecutiveFailures = 0;
    private long lastSuccessfulDetection = 0;
    private int totalDetections = 0;
    private int successfulDetections = 0;
    private List<DetectionResult> lastSuccessfulResults = new ArrayList<>();
    private long lastDetectionTime = 0;
    
    // Multi-frame fusion stability filtering (improves accuracy)
    private static final int STABILITY_FRAME_COUNT = 2;  // Requires 2 consecutive frames to be considered stable (lowered to improve detection rate)
    private final Map<String, Integer> detectionStability = new HashMap<>();  // Object label -> consecutive detection count
    private final Map<String, Float> detectionConfidenceSum = new HashMap<>();  // Object label -> confidence sum
    private final Map<String, android.graphics.RectF> detectionBoundingBox = new HashMap<>();  // Object label -> bounding box
    
    /**
     * Object priority enum - used for intelligent voice announcement priority sorting
     */
    public enum ObjectPriority {
        CRITICAL(0),   // Critical priority: person, vehicle, obstacles (safety related)
        HIGH(1),       // High priority: traffic signs, doors, stairs
        MEDIUM(2),     // Medium priority: furniture, electronic products
        LOW(3);        // Low priority: decorations, small items
        
        private final int value;
        ObjectPriority(int value) {
            this.value = value;
        }
        public int getValue() {
            return value;
        }
    }
    
    // COCO category Chinese mapping
    private static final Map<String, String> LABEL_MAP_ZH = new HashMap<>();
    
    static {
        LABEL_MAP_ZH.put("person", "人");
        LABEL_MAP_ZH.put("bicycle", "腳踏車");
        LABEL_MAP_ZH.put("car", "汽車");
        LABEL_MAP_ZH.put("motorcycle", "摩托車");
        LABEL_MAP_ZH.put("airplane", "飛機");
        LABEL_MAP_ZH.put("bus", "公車");
        LABEL_MAP_ZH.put("train", "火車");
        LABEL_MAP_ZH.put("truck", "卡車");
        LABEL_MAP_ZH.put("boat", "船");
        LABEL_MAP_ZH.put("traffic light", "交通燈");
        LABEL_MAP_ZH.put("fire hydrant", "消防栓");
        LABEL_MAP_ZH.put("stop sign", "停車標誌");
        LABEL_MAP_ZH.put("parking meter", "停車計時器");
        LABEL_MAP_ZH.put("bench", "長椅");
        LABEL_MAP_ZH.put("bird", "鳥");
        LABEL_MAP_ZH.put("cat", "貓");
        LABEL_MAP_ZH.put("dog", "狗");
        LABEL_MAP_ZH.put("horse", "馬");
        LABEL_MAP_ZH.put("sheep", "羊");
        LABEL_MAP_ZH.put("cow", "牛");
        LABEL_MAP_ZH.put("elephant", "大象");
        LABEL_MAP_ZH.put("bear", "熊");
        LABEL_MAP_ZH.put("zebra", "斑馬");
        LABEL_MAP_ZH.put("giraffe", "長頸鹿");
        LABEL_MAP_ZH.put("backpack", "背包");
        LABEL_MAP_ZH.put("umbrella", "雨傘");
        LABEL_MAP_ZH.put("handbag", "手提包");
        LABEL_MAP_ZH.put("tie", "領帶");
        LABEL_MAP_ZH.put("suitcase", "手提箱");
        LABEL_MAP_ZH.put("frisbee", "飛盤");
        LABEL_MAP_ZH.put("skis", "滑雪板");
        LABEL_MAP_ZH.put("snowboard", "滑雪板");
        LABEL_MAP_ZH.put("sports ball", "運動球");
        LABEL_MAP_ZH.put("kite", "風箏");
        LABEL_MAP_ZH.put("baseball bat", "棒球棒");
        LABEL_MAP_ZH.put("baseball glove", "棒球手套");
        LABEL_MAP_ZH.put("skateboard", "滑板");
        LABEL_MAP_ZH.put("surfboard", "衝浪板");
        LABEL_MAP_ZH.put("tennis racket", "網球拍");
        LABEL_MAP_ZH.put("bottle", "瓶子");
        LABEL_MAP_ZH.put("wine glass", "酒杯");
        LABEL_MAP_ZH.put("cup", "杯子");
        LABEL_MAP_ZH.put("fork", "叉子");
        LABEL_MAP_ZH.put("knife", "刀");
        LABEL_MAP_ZH.put("spoon", "湯匙");
        LABEL_MAP_ZH.put("bowl", "碗");
        LABEL_MAP_ZH.put("banana", "香蕉");
        LABEL_MAP_ZH.put("apple", "蘋果");
        LABEL_MAP_ZH.put("sandwich", "三明治");
        LABEL_MAP_ZH.put("orange", "橙");
        LABEL_MAP_ZH.put("broccoli", "西蘭花");
        LABEL_MAP_ZH.put("carrot", "紅蘿蔔");
        LABEL_MAP_ZH.put("hot dog", "熱狗");
        LABEL_MAP_ZH.put("pizza", "披薩");
        LABEL_MAP_ZH.put("donut", "甜甜圈");
        LABEL_MAP_ZH.put("cake", "蛋糕");
        LABEL_MAP_ZH.put("chair", "椅子");
        LABEL_MAP_ZH.put("couch", "沙發");
        LABEL_MAP_ZH.put("potted plant", "盆栽");
        LABEL_MAP_ZH.put("bed", "床");
        LABEL_MAP_ZH.put("dining table", "餐桌");
        LABEL_MAP_ZH.put("toilet", "馬桶");
        LABEL_MAP_ZH.put("tv", "電視");
        LABEL_MAP_ZH.put("laptop", "筆記本電腦");
        LABEL_MAP_ZH.put("mouse", "滑鼠");
        LABEL_MAP_ZH.put("remote", "遙控器");
        LABEL_MAP_ZH.put("keyboard", "鍵盤");
        LABEL_MAP_ZH.put("cell phone", "手機");
        LABEL_MAP_ZH.put("microwave", "微波爐");
        LABEL_MAP_ZH.put("oven", "烤箱");
        LABEL_MAP_ZH.put("toaster", "烤麵包機");
        LABEL_MAP_ZH.put("sink", "水槽");
        LABEL_MAP_ZH.put("refrigerator", "冰箱");
        LABEL_MAP_ZH.put("book", "書");
        LABEL_MAP_ZH.put("clock", "時鐘");
        LABEL_MAP_ZH.put("vase", "花瓶");
        LABEL_MAP_ZH.put("scissors", "剪刀");
        LABEL_MAP_ZH.put("teddy bear", "泰迪熊");
        LABEL_MAP_ZH.put("hair drier", "吹風機");
        LABEL_MAP_ZH.put("toothbrush", "牙刷");
    }
    
    public ObjectDetectorHelper(Context context) {
        this.context = context;
        // Temporarily disable ML Kit (to avoid network issues), prioritize TensorFlow Lite
        // setupMLKitDetector();  // Priority initialization ML Kit (free open source, officially maintained)
        setupObjectDetector();  // TensorFlow Lite as primary detector
        setupYoloDetector();    // YOLO as backup
    }
    
    private void setupObjectDetector() {
        try {
            ObjectDetector.ObjectDetectorOptions options =
                    ObjectDetector.ObjectDetectorOptions.builder()
                            .setScoreThreshold(AppConstants.SCORE_THRESHOLD)
                            .setMaxResults(AppConstants.MAX_RESULTS)
                            .build();
            
            objectDetector = ObjectDetector.createFromFileAndOptions(
                    context,
                    AppConstants.MODEL_FILE,
                    options
            );
            
            Log.d(TAG, "✅ SSD object detector initialized successfully!");
        } catch (IOException e) {
            Log.e(TAG, "❌ Failed to initialize SSD object detector: " + e.getMessage());
        }
    }
    
    /**
     * Initialize ML Kit detector (priority use)
     */
    private void setupMLKitDetector() {
        try {
            mlKitDetector = new MLKitObjectDetector(context);
            if (mlKitDetector.isInitialized()) {
                useMLKit = true;
                Log.d(TAG, "✅ ML Kit detector initialized successfully (priority use)!");
            } else {
                useMLKit = false;
                Log.w(TAG, "⚠️ ML Kit detector initialization failed, will use backup detector");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to initialize ML Kit detector: " + e.getMessage());
            useMLKit = false;
        }
    }
    
    private void setupYoloDetector() {
        try {
            yoloDetector = new YoloDetector(context);
            // Environment recognition mainly uses ML Kit, YOLO as final backup
            useYolo = false; // Default disable YOLO, focus on environment recognition
            Log.d(TAG, "✅ YOLO detector initialized successfully (as final backup)!");
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to initialize YOLO detector: " + e.getMessage());
            useYolo = false;
        }
    }
    
    /**
     * Detect objects in image - uses dual detector fusion to improve accuracy and stability
     */
    public List<DetectionResult> detect(Bitmap bitmap) {
        List<DetectionResult> results = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        
        if (bitmap == null || bitmap.isRecycled()) {
            Log.w(TAG, "Invalid bitmap");
            return getLastSuccessfulResults();
        }
        
        // Check detection frequency to avoid being too frequent
        if (System.currentTimeMillis() - lastDetectionTime < 100) {
            Log.d(TAG, "Detection frequency too high, returning last result");
            return getLastSuccessfulResults();
        }
        lastDetectionTime = System.currentTimeMillis();
        
        totalDetections++;
        
        try {
            // Check consecutive failure count
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                Log.w(TAG, "Too many consecutive failures, resetting detector state");
                resetDetectorState();
            }
            
            // Use retry mechanism for detection
            results = detectWithRetry(bitmap);
            
            if (!results.isEmpty()) {
                // Detection successful
                consecutiveFailures = 0;
                successfulDetections++;
                lastSuccessfulDetection = System.currentTimeMillis();
                lastSuccessfulResults = new ArrayList<>(results);
                
                // Apply post-processing
                results = applyPostProcessing(results);
                
                Log.d(TAG, String.format("Detection successful: %d objects (success rate: %.1f%%)", 
                    results.size(), (float)successfulDetections / totalDetections * 100));
            } else {
                // Detection failed, return last successful result
                Log.w(TAG, "Detection failed, returning last successful result");
                results = getLastSuccessfulResults();
                consecutiveFailures++;
            }
            
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "Out of memory, detection failed: " + e.getMessage());
            System.gc();
            consecutiveFailures++;
            results = getLastSuccessfulResults();
        } catch (Exception e) {
            Log.e(TAG, "Error occurred during detection: " + e.getMessage());
            consecutiveFailures++;
            results = getLastSuccessfulResults();
        }
        
        long detectionTime = System.currentTimeMillis() - startTime;
        if (detectionTime > 1000) {
            Log.w(TAG, "Detection time too long: " + detectionTime + "ms");
        }
        
        // Only return top 2 objects by confidence
        if (results.size() > 2) {
            // Sort by confidence
            Collections.sort(results, new Comparator<DetectionResult>() {
                @Override
                public int compare(DetectionResult a, DetectionResult b) {
                    return Float.compare(b.getConfidence(), a.getConfidence());
                }
            });
            results = results.subList(0, 2);
            Log.d(TAG, "Limited detection results to 2 objects");
        }
        
        return results;
    }
    
    /**
     * Detect using retry mechanism
     */
    private List<DetectionResult> detectWithRetry(Bitmap bitmap) {
        List<DetectionResult> results = new ArrayList<>();
        
        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                // Priority: use TensorFlow Lite SSD detector (primary detector, avoids network issues)
                if (objectDetector != null) {
                    results = detectWithSSD(bitmap);
                    if (!results.isEmpty()) {
                        Log.d(TAG, String.format("SSD detection successful (attempt %d/%d): %d objects", 
                            attempt + 1, MAX_RETRY_ATTEMPTS, results.size()));
                        break;
                    }
                }
                
                // Optional: ML Kit detector (can be enabled if network is available)
                // if (useMLKit && mlKitDetector != null && mlKitDetector.isInitialized()) {
                //     results = detectWithMLKit(bitmap);
                //     if (!results.isEmpty()) {
                //         Log.d(TAG, String.format("ML Kit detection successful (attempt %d/%d): %d objects", 
                //             attempt + 1, MAX_RETRY_ATTEMPTS, results.size()));
                //         break;
                //     }
                // }
                
                // Only try YOLO when SSD fails (as backup)
                if (useYolo && yoloDetector != null && results.isEmpty()) {
                    results = detectWithYolo(bitmap);
                    if (!results.isEmpty()) {
                        Log.d(TAG, String.format("YOLO detection successful (attempt %d/%d): %d objects", 
                            attempt + 1, MAX_RETRY_ATTEMPTS, results.size()));
                        break;
                    }
                }
                
                // If detection fails, wait before retrying
                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Log.w(TAG, "Retry delay interrupted");
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, String.format("Detection attempt %d/%d failed: %s", 
                    attempt + 1, MAX_RETRY_ATTEMPTS, e.getMessage()));
                if (attempt == MAX_RETRY_ATTEMPTS - 1) {
                    throw e;
                }
            }
        }
        
        return results;
    }
    
    /**
     * Apply post-processing
     */
    private List<DetectionResult> applyPostProcessing(List<DetectionResult> results) {
        // Filter environment relevant objects
        results = filterEnvironmentRelevantObjects(results);
        
        // Apply non-maximum suppression
        results = applyNMS(results);
        
        // Apply multi-frame fusion stability filtering (improves accuracy)
        results = applyStabilityFilter(results);
        
        // Intelligent sorting: first by priority, then by confidence
        // This ensures safety-related objects (person, vehicle) are announced first
        Collections.sort(results, (a, b) -> {
            ObjectPriority priorityA = getObjectPriority(a.getLabel());
            ObjectPriority priorityB = getObjectPriority(b.getLabel());
            
            // First compare priority (smaller value = higher priority)
            int priorityCompare = Integer.compare(priorityA.getValue(), priorityB.getValue());
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            
            // When priority is the same, sort by confidence (higher confidence first)
            return Float.compare(b.getConfidence(), a.getConfidence());
        });
        
        // Limit result count
        if (results.size() > AppConstants.MAX_RESULTS) {
            results = results.subList(0, AppConstants.MAX_RESULTS);
        }
        
        return results;
    }
    
    /**
     * Apply multi-frame fusion stability filtering - only keep detection results that appear stably in consecutive frames
     * This can significantly improve accuracy and reduce false positives and flickering
     */
    private List<DetectionResult> applyStabilityFilter(List<DetectionResult> results) {
        if (results.isEmpty()) {
            // If current frame has no detection results, decrease stability count for all objects
            List<String> toRemove = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : detectionStability.entrySet()) {
                int count = entry.getValue() - 1;
                if (count <= 0) {
                    toRemove.add(entry.getKey());
                } else {
                    detectionStability.put(entry.getKey(), count);
                }
            }
            for (String key : toRemove) {
                detectionStability.remove(key);
                detectionConfidenceSum.remove(key);
                detectionBoundingBox.remove(key);
            }
            return new ArrayList<>();
        }
        
        // Update objects detected in current frame
        Set<String> currentDetections = new HashSet<>();
        for (DetectionResult result : results) {
            String key = result.getLabel() + "_" + result.getLabelZh();
            currentDetections.add(key);
            
            // Update stability count
            int stabilityCount = detectionStability.getOrDefault(key, 0) + 1;
            detectionStability.put(key, Math.min(stabilityCount, STABILITY_FRAME_COUNT));
            
            // Accumulate confidence (for calculating average confidence)
            float currentSum = detectionConfidenceSum.getOrDefault(key, 0f);
            detectionConfidenceSum.put(key, currentSum + result.getConfidence());
            
            // Update bounding box (use latest)
            detectionBoundingBox.put(key, result.getBoundingBox());
        }
        
        // Decrease stability count for undetected objects
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : detectionStability.entrySet()) {
            String key = entry.getKey();
            if (!currentDetections.contains(key)) {
                int count = entry.getValue() - 1;
                if (count <= 0) {
                    toRemove.add(key);
                } else {
                    detectionStability.put(key, count);
                }
            }
        }
        for (String key : toRemove) {
            detectionStability.remove(key);
            detectionConfidenceSum.remove(key);
            detectionBoundingBox.remove(key);
        }
        
        // Only return detection results that reach stability threshold
        List<DetectionResult> stableResults = new ArrayList<>();
        for (DetectionResult result : results) {
            String key = result.getLabel() + "_" + result.getLabelZh();
            int stability = detectionStability.getOrDefault(key, 0);
            
            // Only objects detected consecutively enough times are considered stable
            if (stability >= STABILITY_FRAME_COUNT) {
                // Use average confidence (more stable)
                float avgConfidence = detectionConfidenceSum.getOrDefault(key, 0f) / stability;
                android.graphics.RectF bbox = detectionBoundingBox.getOrDefault(key, result.getBoundingBox());
                
                stableResults.add(new DetectionResult(
                    result.getLabel(),
                    result.getLabelZh(),
                    avgConfidence,
                    bbox
                ));
                
                Log.d(TAG, String.format("Stable detection: %s (stability: %d/%d, avg confidence: %.2f)", 
                    result.getLabelZh(), stability, STABILITY_FRAME_COUNT, avgConfidence));
            } else {
                Log.d(TAG, String.format("Unstable detection (skipped): %s (stability: %d/%d)", 
                    result.getLabelZh(), stability, STABILITY_FRAME_COUNT));
            }
        }
        
        Log.d(TAG, String.format("Stability filter: %d -> %d", results.size(), stableResults.size()));
        return stableResults;
    }
    
    /**
     * Filter detection results (whitelist + blacklist + basic validation)
     * Only keep common objects useful for visually impaired users, exclude uncommon or irrelevant objects (e.g., giraffe, zebra)
     */
    private List<DetectionResult> filterEnvironmentRelevantObjects(List<DetectionResult> results) {
        List<DetectionResult> filtered = new ArrayList<>();

        for (DetectionResult result : results) {
            String label = result.getLabel().toLowerCase();
            
            // 1. Check blacklist (prioritize excluding uncommon objects)
            if (EXCLUDED_OBJECTS.contains(label)) {
                Log.d(TAG, "Blacklist filter: " + result.getLabelZh() + " (uncommon object)");
                continue;
            }
            
            // 2. Check bounding box validity (filter abnormal detections)
            if (!isValidBoundingBox(result.getBoundingBox())) {
                Log.d(TAG, "Filter invalid bounding box: " + result.getLabelZh());
                continue;
        }
        
            // 3. Check confidence threshold
            if (result.getConfidence() < AppConstants.SCORE_THRESHOLD) {
                Log.d(TAG, "Filter low confidence object: " + result.getLabelZh() + " (confidence: " + result.getConfidence() + ")");
                continue;
            }
            
            // 4. Whitelist check: only keep environment recognition relevant common objects
            if (!ENVIRONMENT_RELEVANT_OBJECTS.contains(label)) {
                Log.d(TAG, "Whitelist filter: " + result.getLabelZh() + " (not in common objects list)");
                continue;
            }

            // Passed all checks, keep this object
            filtered.add(result);
            Log.d(TAG, "Keep object: " + result.getLabelZh() + " (confidence: " + String.format("%.2f", result.getConfidence()) + ")");
        }

        // Sort by confidence, prioritize showing high confidence results
        filtered.sort((a, b) -> Float.compare(b.getConfidence(), a.getConfidence()));
        
        Log.d(TAG, String.format("Object filter: %d -> %d (whitelist+blacklist filter)", results.size(), filtered.size()));
        return filtered;
    }
    
    /**
     * Check if bounding box is reasonable - filter abnormal detections, improve accuracy
     */
    private boolean isValidBoundingBox(android.graphics.RectF bbox) {
        if (bbox == null) {
            return false;
        }
        
        float width = bbox.right - bbox.left;
        float height = bbox.bottom - bbox.top;
        
        // Check if bounding box size is reasonable (cannot be too small or too large)
        if (width <= 0 || height <= 0) {
            return false;
        }
        
        // Check if bounding box is within valid range (0-1)
        if (bbox.left < 0 || bbox.top < 0 || bbox.right > 1.0f || bbox.bottom > 1.0f) {
            return false;
        }
        
        // Check if bounding box area is reasonable (cannot be too small, avoid noise detection)
        // Lower minimum area requirement to make small objects (e.g., bottles) easier to detect
        float area = width * height;
        if (area < 0.0005f) {  // Detections with area less than 0.05% are considered noise (lower threshold to improve detection rate)
            return false;
        }
        
        // Check if aspect ratio is reasonable (avoid extreme ratios)
        // Relax aspect ratio limits to make elongated objects (e.g., bottles) easier to detect
        float aspectRatio = width / height;
        if (aspectRatio < 0.05f || aspectRatio > 20.0f) {  // Relaxed limits (changed from 0.1-10 to 0.05-20)
            return false;
        }
        
        return true;
    }
    
    /**
     * Get last successful detection results
     */
    private List<DetectionResult> getLastSuccessfulResults() {
        if (lastSuccessfulResults.isEmpty()) {
            Log.d(TAG, "No available historical detection results");
            return new ArrayList<>();
        }
        
        // Check if historical results are expired
        if (System.currentTimeMillis() - lastSuccessfulDetection > 10000) { // Expires after 10 seconds
            Log.d(TAG, "Historical detection results expired");
            return new ArrayList<>();
        }
        
        Log.d(TAG, "Return historical detection results: " + lastSuccessfulResults.size() + " objects");
        return new ArrayList<>(lastSuccessfulResults);
    }
    
    /**
     * Reset detector state
     */
    private void resetDetectorState() {
        consecutiveFailures = 0;
        useYolo = true; // Re-enable YOLO
        Log.d(TAG, "Detector state reset");
    }
    
    /**
     * Detect using ML Kit detector (priority use)
     */
    private List<DetectionResult> detectWithMLKit(Bitmap bitmap) {
        if (mlKitDetector == null || !mlKitDetector.isInitialized()) {
            return new ArrayList<>();
        }
        
        try {
            return mlKitDetector.detect(bitmap);
        } catch (Exception e) {
            Log.e(TAG, "ML Kit detection failed: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Detect using SSD detector
     */
    private List<DetectionResult> detectWithSSD(Bitmap bitmap) {
        List<DetectionResult> results = new ArrayList<>();
        TensorImage tensorImage = null;
        
        try {
            tensorImage = TensorImage.fromBitmap(bitmap);
            List<Detection> detections = objectDetector.detect(tensorImage);
            
            for (Detection detection : detections) {
                if (detection.getCategories().size() > 0) {
                    String label = detection.getCategories().get(0).getLabel();
                    float score = detection.getCategories().get(0).getScore();
                    
                    String labelZh = LABEL_MAP_ZH.get(label);
                    if (labelZh == null) {
                        labelZh = label;
                    }
                    
                    results.add(new DetectionResult(
                            label,
                            labelZh,
                            score,
                            detection.getBoundingBox()
                    ));
                }
            }
        } finally {
            tensorImage = null;
        }
        
        return results;
    }
    
    /**
     * Detect using YOLO detector
     */
    private List<DetectionResult> detectWithYolo(Bitmap bitmap) {
        List<DetectionResult> results = new ArrayList<>();
        
        try {
            List<YoloDetector.DetectionResult> yoloResults = yoloDetector.detect(bitmap);
            int imageWidth = bitmap.getWidth();
            int imageHeight = bitmap.getHeight();
            
            for (YoloDetector.DetectionResult yoloResult : yoloResults) {
                if (yoloResult.getConfidence() >= AppConstants.SCORE_THRESHOLD) {
                    String labelZh = LABEL_MAP_ZH.get(yoloResult.getLabel());
                    if (labelZh == null) {
                        labelZh = yoloResult.getLabel();
                    }
                    
                    // Check if bounding box is null
                    android.graphics.Rect rect = yoloResult.getBoundingBox();
                    if (rect != null && imageWidth > 0 && imageHeight > 0) {
                        // YOLO returns pixel coordinates, need to normalize to 0-1 range
                        // Convert to normalized RectF (0.0-1.0)
                        android.graphics.RectF rectF = new android.graphics.RectF(
                                (float)rect.left / imageWidth,      // Normalized left
                                (float)rect.top / imageHeight,      // Normalized top
                                (float)rect.right / imageWidth,     // Normalized right
                                (float)rect.bottom / imageHeight    // Normalized bottom
                        );
                        
                        // Ensure coordinates are within valid range (0-1)
                        rectF.left = Math.max(0.0f, Math.min(1.0f, rectF.left));
                        rectF.top = Math.max(0.0f, Math.min(1.0f, rectF.top));
                        rectF.right = Math.max(rectF.left + 0.01f, Math.min(1.0f, rectF.right));
                        rectF.bottom = Math.max(rectF.top + 0.01f, Math.min(1.0f, rectF.bottom));
                        
                        results.add(new DetectionResult(
                                yoloResult.getLabel(),
                                labelZh,
                                yoloResult.getConfidence(),
                                rectF
                        ));
                    } else {
                        // If bounding box is null, create a default bounding box
                        Log.w(TAG, "YOLO detection result bounding box is null or image size invalid, using default bounding box");
                        android.graphics.RectF defaultRect = new android.graphics.RectF(0.1f, 0.1f, 0.9f, 0.9f);
                        results.add(new DetectionResult(
                                yoloResult.getLabel(),
                                labelZh,
                                yoloResult.getConfidence(),
                                defaultRect
                        ));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "YOLO detection failed: " + e.getMessage());
            // When YOLO fails, try using SSD detector
            Log.d(TAG, "YOLO detection failed, trying SSD detector");
            if (objectDetector != null) {
                results = detectWithSSD(bitmap);
            }
        }
        
        return results;
    }
    
    /**
     * Apply non-maximum suppression (NMS) to remove duplicate detections
     */
    private List<DetectionResult> applyNMS(List<DetectionResult> detections) {
        if (detections.size() <= 1) {
            return detections;
        }
        
        List<DetectionResult> filtered = new ArrayList<>();
        boolean[] suppressed = new boolean[detections.size()];
        
        for (int i = 0; i < detections.size(); i++) {
            if (suppressed[i]) continue;
            
            DetectionResult current = detections.get(i);
            filtered.add(current);
            
            // Suppress other detections with high overlap with current detection
            for (int j = i + 1; j < detections.size(); j++) {
                if (suppressed[j]) continue;
                
                DetectionResult other = detections.get(j);
                
                // Calculate IoU (Intersection over Union)
                float iou = calculateIoU(current.getBoundingBox(), other.getBoundingBox());
                
                // If IoU exceeds threshold and is same class object, suppress detection with lower confidence
                if (iou > AppConstants.NMS_THRESHOLD && current.getLabel().equals(other.getLabel())) {
                    if (other.getConfidence() < current.getConfidence()) {
                        suppressed[j] = true;
                    }
                }
            }
        }
        
        return filtered;
    }
    
    /**
     * Calculate IoU of two bounding boxes
     */
    private float calculateIoU(android.graphics.RectF box1, android.graphics.RectF box2) {
        float x1 = Math.max(box1.left, box2.left);
        float y1 = Math.max(box1.top, box2.top);
        float x2 = Math.min(box1.right, box2.right);
        float y2 = Math.min(box1.bottom, box2.bottom);
        
        if (x2 <= x1 || y2 <= y1) {
            return 0.0f;
        }
        
        float intersection = (x2 - x1) * (y2 - y1);
        float area1 = (box1.right - box1.left) * (box1.bottom - box1.top);
        float area2 = (box2.right - box2.left) * (box2.bottom - box2.top);
        float union = area1 + area2 - intersection;
        
        return intersection / union;
    }
    
    /**
     * Format detection results as speech text - optimized for visually impaired users (includes position description)
     * Announce object name and position information (left/right/center)
     */
    public String formatResultsForSpeech(List<DetectionResult> results) {
        if (results.isEmpty()) {
            return getNoObjectsDetectedText();
        }
        
        StringBuilder sb = new StringBuilder();
        String currentLang = LocaleManager.getInstance(context).getCurrentLanguage();
        
        Log.d(TAG, "🔊 Start formatting speech text, current language: " + currentLang + ", object count: " + results.size());
        
        // Concise object description, maximum 2 objects
        int maxObjects = Math.min(results.size(), 2);
        
        Log.d(TAG, "🔊 Will announce " + maxObjects + " objects");
        
        for (int i = 0; i < maxObjects; i++) {
            DetectionResult result = results.get(i);
            
            // Get object name - select corresponding label based on current language
            String objectLabel = getObjectLabelForCurrentLanguage(result);
            Log.d(TAG, "🔊 Object " + (i + 1) + " original label - English: [" + result.getLabel() + "], Chinese: [" + result.getLabelZh() + "]");
            Log.d(TAG, "🔊 Object " + (i + 1) + " current language label: [" + objectLabel + "]");
            
            if (objectLabel == null || objectLabel.trim().isEmpty()) {
                Log.w(TAG, "⚠️ Object " + (i + 1) + " label is empty, skipping");
                continue;
            }
            
            sb.append(objectLabel);
            
            // Add position description (left/right/center)
            android.graphics.RectF bbox = result.getBoundingBox();
            if (bbox != null) {
                String positionDesc = getPositionDescription(bbox);
                if (positionDesc != null && !positionDesc.isEmpty()) {
                    sb.append(positionDesc);
                    Log.d(TAG, "🔊 Object " + (i + 1) + " added position description: [" + positionDesc + "]");
                } else {
                    Log.w(TAG, "⚠️ Object " + (i + 1) + " position description is empty or null");
                }
            } else {
                Log.w(TAG, "⚠️ Object " + (i + 1) + " bounding box is null, cannot add position description");
            }
            
            // Separator
            if (i < maxObjects - 1) {
                if (currentLang.equals("english")) {
                    sb.append(", ");
                } else {
                    sb.append("，");
                }
            }
        }
        
        // If objects exceed 2, add total count
        if (results.size() > 2) {
            if (currentLang.equals("english")) {
                sb.append(" and ").append(results.size()).append(" more objects");
            } else {
                sb.append("等").append(results.size()).append("個");
        }
        }
        
        String finalText = sb.toString().trim();
        Log.d(TAG, "🔊 Final speech text: [" + finalText + "]");
        
        if (finalText.isEmpty()) {
            Log.e(TAG, "❌ Final speech text is empty!");
        }
        
        return finalText;
    }
    
    /**
     * Get text when no objects detected
     */
    private String getNoObjectsDetectedText() {
        String currentLang = LocaleManager.getInstance(context).getCurrentLanguage();
        switch (currentLang) {
            case "english":
                return "No objects detected in the environment";
            case "mandarin":
                return "環境中未檢測到任何物體";
            case "cantonese":
            default:
                return "環境中未檢測到任何物體";
        }
    }
    
    /**
     * Get text for detected object count
     */
    private String getDetectedObjectsCountText(int count) {
        String currentLang = LocaleManager.getInstance(context).getCurrentLanguage();
        switch (currentLang) {
            case "english":
                return "Detected " + count + " objects: ";
            case "mandarin":
                return "檢測到" + count + "個物體：";
            case "cantonese":
            default:
                return "檢測到" + count + "個物體：";
        }
    }
    
    /**
     * Get object number text
     */
    private String getObjectNumberText(int number) {
        String currentLang = LocaleManager.getInstance(context).getCurrentLanguage();
        switch (currentLang) {
            case "english":
                return "Number " + number + ", ";
            case "mandarin":
                return "第" + number + "個，";
            case "cantonese":
            default:
                return "第" + number + "個，";
        }
    }
    
    /**
     * Get confidence description
     */
    private String getConfidenceDescription(float confidence) {
        String currentLang = LocaleManager.getInstance(context).getCurrentLanguage();
        int percentage = Math.round(confidence * 100);
        
        String confidenceText;
        if (percentage >= 80) {
            confidenceText = currentLang.equals("english") ? "high confidence" : "高置信度";
        } else if (percentage >= 60) {
            confidenceText = currentLang.equals("english") ? "medium confidence" : "中等置信度";
        } else {
            confidenceText = currentLang.equals("english") ? "low confidence" : "低置信度";
        }
        
        return "，" + confidenceText + "（" + percentage + "%）";
    }
    
    /**
     * Get object label based on current language
     */
    private String getObjectLabelForCurrentLanguage(DetectionResult result) {
        String currentLang = LocaleManager.getInstance(context).getCurrentLanguage();
        
        Log.d(TAG, "🔊 getObjectLabelForCurrentLanguage - current language: " + currentLang);
        Log.d(TAG, "🔊 Detection result - English label: [" + result.getLabel() + "], Chinese label: [" + result.getLabelZh() + "]");
        
        switch (currentLang) {
            case "english":
                // English mode: prioritize English label, if empty or contains Chinese characters then map from Chinese label back to English
                String englishLabel = result.getLabel();
                
                // Check if English label contains Chinese characters (some detectors may return Chinese)
                boolean containsChinese = false;
                if (englishLabel != null && !englishLabel.trim().isEmpty()) {
                    // Check if contains Chinese characters
                    for (char c : englishLabel.toCharArray()) {
                        if (c >= 0x4E00 && c <= 0x9FFF) { // Chinese character range
                            containsChinese = true;
                            Log.d(TAG, "⚠️ English label contains Chinese characters: " + englishLabel);
                            break;
                        }
                    }
                }
                
                // If English label is valid and doesn't contain Chinese, return directly
                if (englishLabel != null && !englishLabel.trim().isEmpty() && !containsChinese) {
                    Log.d(TAG, "✅ Using English label: " + englishLabel);
                    return englishLabel;
                }
                
                // If English label is empty or contains Chinese, try mapping from Chinese label back to English
                String chineseLabel = result.getLabelZh();
                if (chineseLabel != null && !chineseLabel.trim().isEmpty()) {
                    Log.d(TAG, "🔍 Attempting to map from Chinese label to English: " + chineseLabel);
                    // Map from Chinese back to English (reverse lookup)
                    for (Map.Entry<String, String> entry : LABEL_MAP_ZH.entrySet()) {
                        if (entry.getValue().equals(chineseLabel)) {
                            Log.d(TAG, "✅ Mapping successful: " + chineseLabel + " -> " + entry.getKey());
                            return entry.getKey();
                        }
                    }
                    Log.w(TAG, "⚠️ Cannot map Chinese label to English: " + chineseLabel);
                }
                
                // If all else fails, return default value
                Log.w(TAG, "⚠️ Cannot get English label, returning default 'object'");
                return "object";
                
            case "mandarin":
                // Mandarin mode: prioritize Chinese label
                return result.getLabelZh() != null && !result.getLabelZh().trim().isEmpty() 
                    ? result.getLabelZh() 
                    : (result.getLabel() != null ? result.getLabel() : "物體");
                    
            case "cantonese":
            default:
                // Cantonese mode: prioritize Chinese label
                return result.getLabelZh() != null && !result.getLabelZh().trim().isEmpty() 
                    ? result.getLabelZh() 
                    : (result.getLabel() != null ? result.getLabel() : "物體");
        }
    }
    
    /**
     * Get position description (concise version - only describes horizontal position: left/right/center)
     * Optimized for visually impaired users, provides clear position guidance
     */
    private String getPositionDescription(android.graphics.RectF boundingBox) {
        if (boundingBox == null) {
            Log.w(TAG, "Bounding box is null, cannot get position description");
            return "";
        }
        
        String currentLang = LocaleManager.getInstance(context).getCurrentLanguage();
        
        // Calculate object's horizontal position in frame (relative to frame center)
        // Bounding box coordinates are normalized (0.0-1.0)
        float centerX = (boundingBox.left + boundingBox.right) / 2.0f;
        
        String horizontalPos;
        
        // Horizontal position divided into three zones: left, center, right
        // Use more precise thresholds to avoid boundary blur
        if (centerX < 0.35f) {
            // Left side (0-35%)
            horizontalPos = currentLang.equals("english") ? " on the left" : "在左側";
            Log.d(TAG, String.format("Position description: left (centerX=%.2f)", centerX));
        } else if (centerX > 0.65f) {
            // Right side (65-100%)
            horizontalPos = currentLang.equals("english") ? " on the right" : "在右側";
            Log.d(TAG, String.format("Position description: right (centerX=%.2f)", centerX));
        } else {
            // Center (35-65%)
            horizontalPos = currentLang.equals("english") ? " in the center" : "在中央";
            Log.d(TAG, String.format("Position description: center (centerX=%.2f)", centerX));
        }
        
        return horizontalPos;
    }
    
    /**
     * Get object priority - used for intelligent voice announcement priority sorting
     * Safety-related objects (person, vehicle) are announced first, static objects (furniture) have reduced announcement frequency
     */
    public ObjectPriority getObjectPriority(String label) {
        if (label == null) {
            return ObjectPriority.LOW;
        }
        
        String labelLower = label.toLowerCase();
        
        // CRITICAL priority: safety-related objects (person, vehicles, obstacles)
        if (labelLower.equals("person") || 
            labelLower.equals("car") || 
            labelLower.equals("truck") || 
            labelLower.equals("bus") || 
            labelLower.equals("motorcycle") || 
            labelLower.equals("bicycle")) {
            return ObjectPriority.CRITICAL;
        }
        
        // HIGH priority: traffic signs, doors, stairs, important facilities
        if (labelLower.equals("traffic light") || 
            labelLower.equals("stop sign") ||
            labelLower.equals("toilet") ||
            labelLower.equals("door")) {
            return ObjectPriority.HIGH;
        }
        
        // MEDIUM priority: furniture, electronic products, daily items
        if (labelLower.equals("chair") || 
            labelLower.equals("table") || 
            labelLower.equals("dining table") ||
            labelLower.equals("bench") ||
            labelLower.equals("bed") ||
            labelLower.equals("couch") ||
            labelLower.equals("tv") ||
            labelLower.equals("laptop") ||
            labelLower.equals("keyboard") ||
            labelLower.equals("mouse") ||
            labelLower.equals("cell phone") ||
            labelLower.equals("microwave") ||
            labelLower.equals("oven") ||
            labelLower.equals("refrigerator") ||
            labelLower.equals("sink") ||
            labelLower.equals("umbrella") ||
            labelLower.equals("handbag") ||
            labelLower.equals("backpack") ||
            labelLower.equals("suitcase")) {
            return ObjectPriority.MEDIUM;
        }
        
        // LOW priority: decorations, small items, food
        return ObjectPriority.LOW;
    }
    
    /**
     * Get detector stability statistics
     */
    public String getStabilityStats() {
        float successRate = totalDetections > 0 ? (float)successfulDetections / totalDetections * 100 : 0;
        long timeSinceLastSuccess = System.currentTimeMillis() - lastSuccessfulDetection;
        
        return String.format("Detection stats - Total: %d, Success: %d, Success rate: %.1f%%, Consecutive failures: %d, Last success: %d seconds ago", 
            totalDetections, successfulDetections, successRate, consecutiveFailures, timeSinceLastSuccess / 1000);
    }
    
    /**
     * Check detector health status
     */
    public boolean isHealthy() {
        return consecutiveFailures < MAX_CONSECUTIVE_FAILURES && 
               (System.currentTimeMillis() - lastSuccessfulDetection) < 30000; // Has successful detection within 30 seconds
    }
    
    /**
     * Force reset detector
     */
    public void forceReset() {
        Log.d(TAG, "Force reset detector");
        consecutiveFailures = 0;
        useYolo = true;
        lastSuccessfulDetection = 0;
        lastSuccessfulResults.clear();
    }
    
    public void close() {
        if (mlKitDetector != null) {
            mlKitDetector.close();
            Log.d(TAG, "ML Kit detector closed");
        }
        if (objectDetector != null) {
            objectDetector.close();
            Log.d(TAG, "SSD object detector closed");
        }
        if (yoloDetector != null) {
            yoloDetector.close();
            Log.d(TAG, "YOLO detector closed");
        }
        
        // Output final statistics
        Log.d(TAG, getStabilityStats());
    }
    
    /**
     * Detection result class
     */
    public static class DetectionResult {
        private String label;
        private String labelZh;
        private float confidence;
        private android.graphics.RectF boundingBox;
        
        public DetectionResult(String label, String labelZh, float confidence, android.graphics.RectF boundingBox) {
            this.label = label;
            this.labelZh = labelZh;
            this.confidence = confidence;
            this.boundingBox = boundingBox;
        }
        
        public String getLabel() {
            return label;
        }
        
        public String getLabelZh() {
            return labelZh;
        }
        
        public float getConfidence() {
            return confidence;
        }
        
        public android.graphics.RectF getBoundingBox() {
            return boundingBox;
        }
        
        @Override
        public String toString() {
            return String.format("%s (%.0f%%)", labelZh, confidence * 100);
        }
    }
}

