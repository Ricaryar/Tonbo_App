package com.example.tonbo_app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.util.Log;

import androidx.camera.core.ImageProxy;

import org.tensorflow.lite.Interpreter;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Real AI object detector
 * Implementation based on TensorFlow Lite SSD MobileNet model
 * Provides real AI detection capability, supports 90 COCO categories
 */
public class YoloDetector {
    private static final String TAG = "YoloDetector";
    
    // Model parameters - use AppConstants
    
    private Context context;
    private Interpreter tflite;
    private boolean isInitialized = false;
    /** Protect interpreter lifecycle against close/detect races. */
    private final Object interpreterLock = new Object();
    private DetectionPerformanceMonitor performanceMonitor;
    
    // COCO dataset category names (Traditional Chinese)
    private static final Map<String, String> CLASS_NAMES_ZH = new HashMap<>();
    
    static {
        CLASS_NAMES_ZH.put("person", "人");
        CLASS_NAMES_ZH.put("bicycle", "腳踏車");
        CLASS_NAMES_ZH.put("car", "汽車");
        CLASS_NAMES_ZH.put("motorcycle", "摩托車");
        CLASS_NAMES_ZH.put("airplane", "飛機");
        CLASS_NAMES_ZH.put("bus", "公車");
        CLASS_NAMES_ZH.put("train", "火車");
        CLASS_NAMES_ZH.put("truck", "卡車");
        CLASS_NAMES_ZH.put("boat", "船");
        CLASS_NAMES_ZH.put("traffic light", "交通燈");
        CLASS_NAMES_ZH.put("fire hydrant", "消防栓");
        CLASS_NAMES_ZH.put("stop sign", "停車標誌");
        CLASS_NAMES_ZH.put("parking meter", "停車計時器");
        CLASS_NAMES_ZH.put("bench", "長椅");
        CLASS_NAMES_ZH.put("bird", "鳥");
        CLASS_NAMES_ZH.put("cat", "貓");
        CLASS_NAMES_ZH.put("dog", "狗");
        CLASS_NAMES_ZH.put("horse", "馬");
        CLASS_NAMES_ZH.put("sheep", "羊");
        CLASS_NAMES_ZH.put("cow", "牛");
        CLASS_NAMES_ZH.put("elephant", "大象");
        CLASS_NAMES_ZH.put("bear", "熊");
        CLASS_NAMES_ZH.put("zebra", "斑馬");
        CLASS_NAMES_ZH.put("giraffe", "長頸鹿");
        CLASS_NAMES_ZH.put("backpack", "背包");
        CLASS_NAMES_ZH.put("umbrella", "雨傘");
        CLASS_NAMES_ZH.put("handbag", "手提包");
        CLASS_NAMES_ZH.put("tie", "領帶");
        CLASS_NAMES_ZH.put("suitcase", "手提箱");
        CLASS_NAMES_ZH.put("frisbee", "飛盤");
        CLASS_NAMES_ZH.put("skis", "滑雪板");
        CLASS_NAMES_ZH.put("snowboard", "滑雪板");
        CLASS_NAMES_ZH.put("sports ball", "運動球");
        CLASS_NAMES_ZH.put("kite", "風箏");
        CLASS_NAMES_ZH.put("baseball bat", "棒球棒");
        CLASS_NAMES_ZH.put("baseball glove", "棒球手套");
        CLASS_NAMES_ZH.put("skateboard", "滑板");
        CLASS_NAMES_ZH.put("surfboard", "衝浪板");
        CLASS_NAMES_ZH.put("tennis racket", "網球拍");
        CLASS_NAMES_ZH.put("bottle", "瓶子");
        CLASS_NAMES_ZH.put("wine glass", "酒杯");
        CLASS_NAMES_ZH.put("cup", "杯子");
        CLASS_NAMES_ZH.put("fork", "叉子");
        CLASS_NAMES_ZH.put("knife", "刀");
        CLASS_NAMES_ZH.put("spoon", "湯匙");
        CLASS_NAMES_ZH.put("bowl", "碗");
        CLASS_NAMES_ZH.put("banana", "香蕉");
        CLASS_NAMES_ZH.put("apple", "蘋果");
        CLASS_NAMES_ZH.put("sandwich", "三明治");
        CLASS_NAMES_ZH.put("orange", "橙");
        CLASS_NAMES_ZH.put("broccoli", "西蘭花");
        CLASS_NAMES_ZH.put("carrot", "紅蘿蔔");
        CLASS_NAMES_ZH.put("hot dog", "熱狗");
        CLASS_NAMES_ZH.put("pizza", "披薩");
        CLASS_NAMES_ZH.put("donut", "甜甜圈");
        CLASS_NAMES_ZH.put("cake", "蛋糕");
        CLASS_NAMES_ZH.put("chair", "椅子");
        CLASS_NAMES_ZH.put("couch", "沙發");
        CLASS_NAMES_ZH.put("potted plant", "盆栽");
        CLASS_NAMES_ZH.put("bed", "床");
        CLASS_NAMES_ZH.put("dining table", "餐桌");
        CLASS_NAMES_ZH.put("toilet", "馬桶");
        CLASS_NAMES_ZH.put("tv", "電視");
        CLASS_NAMES_ZH.put("laptop", "筆記本電腦");
        CLASS_NAMES_ZH.put("mouse", "滑鼠");
        CLASS_NAMES_ZH.put("remote", "遙控器");
        CLASS_NAMES_ZH.put("keyboard", "鍵盤");
        CLASS_NAMES_ZH.put("cell phone", "手機");
        CLASS_NAMES_ZH.put("microwave", "微波爐");
        CLASS_NAMES_ZH.put("oven", "烤箱");
        CLASS_NAMES_ZH.put("toaster", "烤麵包機");
        CLASS_NAMES_ZH.put("sink", "水槽");
        CLASS_NAMES_ZH.put("refrigerator", "冰箱");
        CLASS_NAMES_ZH.put("book", "書");
        CLASS_NAMES_ZH.put("clock", "時鐘");
        CLASS_NAMES_ZH.put("vase", "花瓶");
        CLASS_NAMES_ZH.put("scissors", "剪刀");
        CLASS_NAMES_ZH.put("teddy bear", "泰迪熊");
        CLASS_NAMES_ZH.put("hair drier", "吹風機");
        CLASS_NAMES_ZH.put("toothbrush", "牙刷");
        
        // Add more categories supported by SSD model
        CLASS_NAMES_ZH.put("background", "背景");
        CLASS_NAMES_ZH.put("aeroplane", "飛機");
        CLASS_NAMES_ZH.put("bicycle", "腳踏車");
        CLASS_NAMES_ZH.put("bird", "鳥");
        CLASS_NAMES_ZH.put("boat", "船");
        CLASS_NAMES_ZH.put("bottle", "瓶子");
        CLASS_NAMES_ZH.put("bus", "公車");
        CLASS_NAMES_ZH.put("car", "汽車");
        CLASS_NAMES_ZH.put("cat", "貓");
        CLASS_NAMES_ZH.put("chair", "椅子");
        CLASS_NAMES_ZH.put("cow", "牛");
        CLASS_NAMES_ZH.put("diningtable", "餐桌");
        CLASS_NAMES_ZH.put("dog", "狗");
        CLASS_NAMES_ZH.put("horse", "馬");
        CLASS_NAMES_ZH.put("motorbike", "摩托車");
        CLASS_NAMES_ZH.put("pottedplant", "盆栽");
        CLASS_NAMES_ZH.put("sheep", "羊");
        CLASS_NAMES_ZH.put("sofa", "沙發");
        CLASS_NAMES_ZH.put("train", "火車");
        CLASS_NAMES_ZH.put("tvmonitor", "電視");
    }
    
    // COCO category name array (in index order) - SSD MobileNet format
    private static final String[] COCO_CLASSES = {
        "background", "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat",
        "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat",
        "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "backpack",
        "umbrella", "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard", "sports ball",
        "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket",
        "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
        "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake",
        "chair", "couch", "potted plant", "bed", "dining table", "toilet", "tv", "laptop",
        "mouse", "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink",
        "refrigerator", "book", "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush",
        "aeroplane", "bicycle", "bird", "boat", "bottle", "bus", "car", "cat", "chair", "cow",
        "diningtable", "dog", "horse", "motorbike", "pottedplant", "sheep", "sofa", "train", "tvmonitor"
    };
    
    public YoloDetector(Context context) {
        this.context = context;
        this.performanceMonitor = new DetectionPerformanceMonitor();
        initialize();
    }
    
    private void initialize() {
        try {
            Log.d(TAG, "Start initializing real AI detector...");
            
            // Load TensorFlow Lite model
            tflite = new Interpreter(loadModelFile());
            
            if (tflite != null) {
                isInitialized = true;
                Log.d(TAG, "Real AI detector initialized successfully - using YOLOv8 model");
            } else {
                Log.e(TAG, "Unable to load TensorFlow Lite model");
                isInitialized = false;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Real AI detector initialization failed: " + e.getMessage());
            isInitialized = false;
        }
    }
    
    /**
     * Load model file
     */
    private MappedByteBuffer loadModelFile() throws IOException {
        try {
            // Try to load model file from assets
            return loadModelFromAssets();
        } catch (IOException e) {
            Log.w(TAG, "Unable to load model from assets, using fallback detection method");
            return null;
        }
    }
    
    /**
     * Load model file from assets
     */
    private MappedByteBuffer loadModelFromAssets() throws IOException {
        android.content.res.AssetFileDescriptor fileDescriptor = 
            context.getAssets().openFd(AppConstants.YOLO_MODEL_FILE);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
    
    /**
     * Detect objects in image
     */
    public List<DetectionResult> detect(ImageProxy image) {
        if (!isInitialized) {
            Log.w(TAG, "Detector not initialized");
            return new ArrayList<>();
        }
        
        try {
            // Convert ImageProxy to Bitmap
            Bitmap bitmap = imageProxyToBitmap(image);
            if (bitmap == null) {
                return new ArrayList<>();
            }
            
            return detect(bitmap);
            
        } catch (Exception e) {
            Log.e(TAG, "Detection failed: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Detect Bitmap image
     */
    public List<DetectionResult> detect(Bitmap bitmap) {
        if (bitmap == null) {
            return new ArrayList<>();
        }
        
        try {
            synchronized (interpreterLock) {
                if (!isInitialized || tflite == null) {
                    Log.w(TAG, "Real AI model not loaded, using fallback detection method");
                    return getFallbackDetections(bitmap);
                }

                long startTime = System.currentTimeMillis();
                
                // Dynamically detect model input tensor requirements
                org.tensorflow.lite.Tensor inputTensor = tflite.getInputTensor(0);
                int[] inputShape = inputTensor.shape();
                int inputHeight = inputShape[1];
                int inputWidth = inputShape[2];
                int inputChannels = inputShape.length > 3 ? inputShape[3] : 3;
                
                Log.d(TAG, String.format("Model input tensor shape: [%s], expected size: %dx%d, channels: %d", 
                    java.util.Arrays.toString(inputShape), inputWidth, inputHeight, inputChannels));
                
                // Check data type (float32 vs uint8)
                org.tensorflow.lite.DataType inputDataType = inputTensor.dataType();
                boolean isFloatInput = (inputDataType == org.tensorflow.lite.DataType.FLOAT32);
                Log.d(TAG, "Input data type: " + inputDataType + ", is float: " + isFloatInput);
                
                // Adjust image size according to model requirements
                Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true);
                ByteBuffer inputBuffer = bitmapToByteBuffer(resizedBitmap, inputWidth, inputHeight, isFloatInput);
                
                // Dynamically get output tensor shapes
                int numOutputs = tflite.getOutputTensorCount();
                Log.d(TAG, "Model output tensor count: " + numOutputs);
                
                // Check first output tensor shape to determine model format
                org.tensorflow.lite.Tensor firstOutputTensor = tflite.getOutputTensor(0);
                int[] firstOutputShape = firstOutputTensor.shape();
                Log.d(TAG, "First output tensor shape: " + java.util.Arrays.toString(firstOutputShape));
                
                List<DetectionResult> results = new ArrayList<>();
                
                // Determine model format based on first output tensor shape
                // YOLOv8 format: [1, 84, 8400] = [batch, 4+80, num_anchors]
                // Post-processed format: [1, num_detections, 4] or [1, num_detections, 6]
                boolean isYoloV8Format = (numOutputs == 1 && firstOutputShape.length == 3 && 
                                          firstOutputShape[0] == 1 && 
                                          firstOutputShape[1] == 84 && 
                                          firstOutputShape[2] == 8400);
                boolean isPostProcessed = (numOutputs == 1 && firstOutputShape.length == 3 && 
                                          firstOutputShape[0] == 1 && 
                                          !isYoloV8Format &&
                                          (firstOutputShape[2] == 4 || firstOutputShape[2] == 6));
                
                if (isYoloV8Format) {
                // YOLOv8 native format: [1, 84, 8400]
                // 84 = 4 (bbox coordinates) + 80 (COCO categories)
                // 8400 = number of detection positions
                Log.d(TAG, "Detected YOLOv8 native format: [1, 84, 8400]");
                
                // Allocate output buffer [1, 84, 8400]
                float[][][] detectionOutput = new float[1][84][8400];
                
                // Execute inference
                Object[] inputs = {inputBuffer};
                Map<Integer, Object> outputs = new HashMap<>();
                outputs.put(0, detectionOutput);
                
                tflite.runForMultipleInputsOutputs(inputs, outputs);
                
                // Process YOLOv8 output format
                results = postProcessYoloV8Output(
                    detectionOutput[0], inputWidth, inputHeight, bitmap.getWidth(), bitmap.getHeight());
                    
                } else if (isPostProcessed || numOutputs == 1) {
                // Post-processed version: single output [1, num_detections, 4] or [1, num_detections, 6]
                int maxDetections = firstOutputShape.length >= 2 ? firstOutputShape[1] : 10;
                int coordsPerDetection = firstOutputShape.length >= 3 ? firstOutputShape[2] : 4;
                
                Log.d(TAG, "Detected post-processed version, max detections: " + maxDetections + ", coords per detection: " + coordsPerDetection);
                
                // Allocate output buffer
                float[][][] detectionOutput = new float[1][maxDetections][coordsPerDetection];
                
                // Execute inference
                Object[] inputs = {inputBuffer};
                Map<Integer, Object> outputs = new HashMap<>();
                outputs.put(0, detectionOutput);
                
                tflite.runForMultipleInputsOutputs(inputs, outputs);
                
                // Process post-processed output format [1, num_detections, 6] or [1, num_detections, 4]
                results = postProcessDetections(
                    detectionOutput[0], bitmap.getWidth(), bitmap.getHeight(), coordsPerDetection);
                    
                } else if (numOutputs >= 2) {
                // Post-processed version: multiple output tensors
                // Format may be: boxes [1, N, 4], classes [1, N], scores [1, N], num_detections [1]
                // Or: boxes [1, N, 4], classes [1, N, 91], scores [1, N], num_detections [1]
                Log.d(TAG, "Detected multi-output format, dynamically detect output tensor shapes");
                
                // Check all output tensor shapes
                int maxDetections = 10; // Default value
                boolean isClassIndexFormat = false; // classes is index or probability matrix
                
                // Check first output tensor (boxes) shape
                if (firstOutputShape.length >= 2) {
                    maxDetections = firstOutputShape[1];
                    Log.d(TAG, "Detected max detections from first output tensor: " + maxDetections);
                }
                
                // Check second output tensor (classes) shape
                if (numOutputs > 1) {
                    org.tensorflow.lite.Tensor classesTensor = tflite.getOutputTensor(1);
                    int[] classesShape = classesTensor.shape();
                    Log.d(TAG, "Second output tensor (classes) shape: " + java.util.Arrays.toString(classesShape));
                    
                    // If shape is [1, N], it's class index format
                    // If shape is [1, N, num_classes], it's class probability matrix format
                    if (classesShape.length == 2) {
                        isClassIndexFormat = true;
                        Log.d(TAG, "Detected class index format [1, N]");
                    } else if (classesShape.length == 3) {
                        Log.d(TAG, "Detected class probability matrix format [1, N, num_classes]");
                    }
                }
                
                // Allocate output buffers based on actual shapes
                float[][][] detectionBoxes = new float[1][maxDetections][4];
                
                Object[] inputs = {inputBuffer};
                Map<Integer, Object> outputs = new HashMap<>();
                outputs.put(0, detectionBoxes);
                
                // Allocate different output buffers based on format
                if (isClassIndexFormat) {
                    // Post-processed format: classes is index array [1, N]
                    float[][] detectionClasses = new float[1][maxDetections];
                    float[][] detectionScores = numOutputs > 2 ? new float[1][maxDetections] : null;
                    float[] numDetections = numOutputs > 3 ? new float[1] : null;
                    
                    if (numOutputs > 1) outputs.put(1, detectionClasses);
                    if (numOutputs > 2 && detectionScores != null) outputs.put(2, detectionScores);
                    if (numOutputs > 3 && numDetections != null) outputs.put(3, numDetections);
                    
                    tflite.runForMultipleInputsOutputs(inputs, outputs);
                    
                    // Process post-processed format output
                    results = postProcessPostProcessedOutput(
                        detectionBoxes[0],
                        detectionClasses[0],
                        detectionScores != null ? detectionScores[0] : null,
                        numDetections != null ? (int)numDetections[0] : maxDetections,
                        bitmap.getWidth(), bitmap.getHeight());
                } else {
                    // Original format: classes is probability matrix [1, N, num_classes]
                    int numClasses = 91; // Default value
                    if (numOutputs > 1) {
                        org.tensorflow.lite.Tensor classesTensor = tflite.getOutputTensor(1);
                        int[] classesShape = classesTensor.shape();
                        if (classesShape.length >= 3) {
                            numClasses = classesShape[2];
                        }
                    }
                    
                    float[][][] detectionClasses = new float[1][maxDetections][numClasses];
                    float[][] detectionScores = numOutputs > 2 ? new float[1][maxDetections] : null;
                    float[] numDetections = numOutputs > 3 ? new float[1] : null;
                    
                    if (numOutputs > 1) outputs.put(1, detectionClasses);
                    if (numOutputs > 2 && detectionScores != null) outputs.put(2, detectionScores);
                    if (numOutputs > 3 && numDetections != null) outputs.put(3, numDetections);
                    
                    tflite.runForMultipleInputsOutputs(inputs, outputs);
                    
                    results = postProcessSSDOutput(
                        detectionBoxes[0], 
                        detectionClasses[0],
                        detectionScores != null ? detectionScores[0] : null, 
                        numDetections != null ? (int)numDetections[0] : maxDetections, 
                        bitmap.getWidth(), bitmap.getHeight());
                }
                } else {
                    Log.e(TAG, "Unknown output format, output tensor count: " + numOutputs);
                    return getFallbackDetections(bitmap);
                }
                
                // Record performance data
                long detectionTime = System.currentTimeMillis() - startTime;
                performanceMonitor.recordDetectionTime(detectionTime);
                performanceMonitor.recordDetectionResult(results);
                
                // Recycle temporary bitmap
                if (resizedBitmap != bitmap) {
                    resizedBitmap.recycle();
                }
                
                return results;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Real AI detection failed, using fallback method: " + e.getMessage());
            e.printStackTrace();
            return getFallbackDetections(bitmap);
        }
    }
    
    /**
     * Convert Bitmap to ByteBuffer
     * Supports two formats:
     * - uint8: 1 byte/channel, value range 0-255
     * - float32: 4 bytes/channel, value range 0.0-1.0 (normalized)
     */
    private ByteBuffer bitmapToByteBuffer(Bitmap bitmap, int inputWidth, int inputHeight, boolean isFloatInput) {
        // Calculate buffer size based on data type
        int bufferSize;
        if (isFloatInput) {
            // float32: width × height × 3 × 4 bytes
            bufferSize = inputWidth * inputHeight * 3 * 4;
        } else {
            // uint8: width × height × 3 bytes
            bufferSize = inputWidth * inputHeight * 3;
        }
        
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(bufferSize);
        byteBuffer.order(ByteOrder.nativeOrder());
        
        // Ensure bitmap is correct size
        if (bitmap.getWidth() != inputWidth || bitmap.getHeight() != inputHeight) {
            Log.w(TAG, String.format("Bitmap size incorrect: %dx%d, expected: %dx%d", 
                bitmap.getWidth(), bitmap.getHeight(), inputWidth, inputHeight));
            return byteBuffer;
        }
        
        int[] pixels = new int[inputWidth * inputHeight];
        bitmap.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight);
        
        // Convert pixel values to RGB and add to buffer
        for (int pixel : pixels) {
            // Extract RGB values (ARGB format)
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;
            
            if (isFloatInput) {
                // float32 format: normalize to 0.0-1.0
                byteBuffer.putFloat(r / 255.0f);
                byteBuffer.putFloat(g / 255.0f);
                byteBuffer.putFloat(b / 255.0f);
            } else {
                // uint8 format: value range 0-255
                byteBuffer.put((byte) r);
                byteBuffer.put((byte) g);
                byteBuffer.put((byte) b);
            }
        }
        
        byteBuffer.rewind(); // Reset position to start
        return byteBuffer;
    }
    
    /**
     * Process YOLOv8 native output format [84, 8400]
     * output[0-3][j] = bbox coordinates (x_center, y_center, width, height), relative to model input size
     * output[4-83][j] = confidence scores for 80 categories
     * j = 0-8399 are different detection positions
     */
    private List<DetectionResult> postProcessYoloV8Output(float[][] output, 
                                                          int modelWidth, int modelHeight,
                                                          int originalWidth, int originalHeight) {
        List<DetectionResult> rawResults = new ArrayList<>();
        int numAnchors = 8400;
        int numClasses = 80;
        
        // Step 1: Extract all valid detections
        for (int j = 0; j < numAnchors; j++) {
            // YOLOv8 output format: bbox coordinates may be normalized (0-1) or pixel coordinates
            // Format: output[0-3][j] = (x_center, y_center, width, height)
            float x_center = output[0][j];
            float y_center = output[1][j];
            float width = output[2][j];
            float height = output[3][j];
            
            // Find class with highest confidence
            float maxConfidence = 0.0f;
            int bestClassIndex = 0;
            for (int c = 0; c < numClasses; c++) {
                float confidence = output[4 + c][j];
                if (confidence > maxConfidence) {
                    maxConfidence = confidence;
                    bestClassIndex = c;
                }
            }
            
            // Filter low confidence detections
            if (maxConfidence < AppConstants.CONFIDENCE_THRESHOLD) {
                continue;
            }
            
            // Determine coordinate format: if coordinate value > 1, it's pixel coordinates; otherwise normalized (0-1)
            boolean isNormalized = (x_center <= 1.0f && y_center <= 1.0f && 
                                   width <= 1.0f && height <= 1.0f && 
                                   x_center >= 0 && y_center >= 0);
            
            float left, top, right, bottom;
            if (isNormalized) {
                // Normalized coordinates: directly convert to (left, top, right, bottom)
                left = x_center - width / 2;
                top = y_center - height / 2;
                right = x_center + width / 2;
                bottom = y_center + height / 2;
            } else {
                // Pixel coordinates: normalize first then convert
                left = (x_center - width / 2) / modelWidth;
                top = (y_center - height / 2) / modelHeight;
                right = (x_center + width / 2) / modelWidth;
                bottom = (y_center + height / 2) / modelHeight;
            }
            
            // Skip invalid bbox
            if (width <= 0 || height <= 0 || left >= right || top >= bottom ||
                left < 0 || top < 0 || right > 1.0f || bottom > 1.0f) {
                continue;
            }
            
            // Ensure coordinates are within valid range (0-1)
            left = Math.max(0.0f, Math.min(1.0f, left));
            top = Math.max(0.0f, Math.min(1.0f, top));
            right = Math.max(left + 0.01f, Math.min(1.0f, right));
            bottom = Math.max(top + 0.01f, Math.min(1.0f, bottom));
            
            // Convert to pixel coordinates
            int pixelLeft = (int)(left * originalWidth);
            int pixelTop = (int)(top * originalHeight);
            int pixelRight = (int)(right * originalWidth);
            int pixelBottom = (int)(bottom * originalHeight);
            
            // Verify bbox size (at least 20 pixels)
            if (pixelRight - pixelLeft < 20 || pixelBottom - pixelTop < 20) {
                continue;
            }
            
            // Get category name (YOLOv8 uses COCO categories, indices 0-79 correspond to categories 1-80, excluding background)
            // COCO_CLASSES[0] is "background", so YOLOv8 index 0 corresponds to COCO_CLASSES[1]
            String className = "object";
            int cocoClassIndex = bestClassIndex + 1; // YOLOv8 index + 1 = COCO index
            if (cocoClassIndex >= 1 && cocoClassIndex < COCO_CLASSES.length) {
                className = COCO_CLASSES[cocoClassIndex];
            } else if (bestClassIndex >= 0 && bestClassIndex < COCO_CLASSES.length) {
                // Fallback: use index directly (if array aligned)
                className = COCO_CLASSES[bestClassIndex];
            }
            String chineseName = CLASS_NAMES_ZH.get(className);
            if (chineseName == null) {
                chineseName = className;
            }
            
            // Create detection result
            android.graphics.Rect boundingBox = new android.graphics.Rect(
                pixelLeft, pixelTop, pixelRight, pixelBottom);
            DetectionResult result = new DetectionResult(className, chineseName, maxConfidence, boundingBox);
            rawResults.add(result);
        }
        
        Log.d(TAG, "YOLOv8 raw detection count: " + rawResults.size());
        
        // Step 2: Apply Non-Maximum Suppression (NMS) to filter duplicate detections
        List<DetectionResult> filteredResults = applyNMS(rawResults, AppConstants.NMS_THRESHOLD);
        
        // Step 3: Sort by confidence and limit count
        Collections.sort(filteredResults, new Comparator<DetectionResult>() {
            @Override
            public int compare(DetectionResult a, DetectionResult b) {
                return Float.compare(b.getConfidence(), a.getConfidence());
            }
        });
        
        int maxResults = Math.min(AppConstants.MAX_RESULTS, filteredResults.size());
        List<DetectionResult> finalResults = filteredResults.subList(0, maxResults);
        
        Log.d(TAG, "YOLOv8 final detection count: " + finalResults.size());
        return finalResults;
    }
    
    /**
     * Non-Maximum Suppression (NMS) to filter overlapping detection boxes
     */
    private List<DetectionResult> applyNMS(List<DetectionResult> detections, float iouThreshold) {
        if (detections.isEmpty()) {
            return detections;
        }
        
        List<DetectionResult> kept = new ArrayList<>();
        boolean[] suppressed = new boolean[detections.size()];
        
        for (int i = 0; i < detections.size(); i++) {
            if (suppressed[i]) {
                continue;
            }
            
            DetectionResult detection = detections.get(i);
            kept.add(detection);
            
            android.graphics.Rect box1 = detection.getBoundingBox();
            int area1 = box1.width() * box1.height();
            
            for (int j = i + 1; j < detections.size(); j++) {
                if (suppressed[j]) {
                    continue;
                }
                
                DetectionResult other = detections.get(j);
                android.graphics.Rect box2 = other.getBoundingBox();
                
                // Calculate IoU
                int x1 = Math.max(box1.left, box2.left);
                int y1 = Math.max(box1.top, box2.top);
                int x2 = Math.min(box1.right, box2.right);
                int y2 = Math.min(box1.bottom, box2.bottom);
                
                int intersectionArea = Math.max(0, x2 - x1) * Math.max(0, y2 - y1);
                int area2 = box2.width() * box2.height();
                int unionArea = area1 + area2 - intersectionArea;
                
                float iou = unionArea > 0 ? (float)intersectionArea / unionArea : 0.0f;
                
                // If IoU exceeds threshold, suppress detection
                if (iou > iouThreshold) {
                    suppressed[j] = true;
                }
            }
        }
        
        return kept;
    }
    
    /**
     * Process post-processed version detection output
     * Output format may be [num_detections, 4] or [num_detections, 6]
     * Format: [ymin, xmin, ymax, xmax] or [ymin, xmin, ymax, xmax, class, score]
     */
    private List<DetectionResult> postProcessDetections(float[][] detections, 
                                                       int originalWidth, int originalHeight, 
                                                       int coordsPerDetection) {
        List<DetectionResult> results = new ArrayList<>();
        
        for (float[] detection : detections) {
            if (detection == null || detection.length < 4) {
                continue;
            }
            
            float ymin, xmin, ymax, xmax;
            float confidence = 1.0f;
            int classIndex = 0;
            
            if (coordsPerDetection >= 6) {
                // Format: [ymin, xmin, ymax, xmax, class, score]
                ymin = detection[0];
                xmin = detection[1];
                ymax = detection[2];
                xmax = detection[3];
                classIndex = (int) detection[4];
                confidence = detection[5];
            } else {
                // Format: [ymin, xmin, ymax, xmax] or [xmin, ymin, xmax, ymax]
                // Try to detect format
                if (detection[0] > detection[2] || detection[1] > detection[3]) {
                    // May be [xmin, ymin, xmax, ymax]
                    xmin = detection[0];
                    ymin = detection[1];
                    xmax = detection[2];
                    ymax = detection[3];
                } else {
                    // May be [ymin, xmin, ymax, xmax]
                    ymin = detection[0];
                    xmin = detection[1];
                    ymax = detection[2];
                    xmax = detection[3];
                }
            }
            
            // Filter low confidence detections
            if (confidence < AppConstants.CONFIDENCE_THRESHOLD) {
                continue;
            }
            
            // Convert to relative coordinates (0-1), because DetectionResult expects RectF format
            // Coordinates are already relative (0-1), use directly
            // Ensure coordinates are within valid range (0-1)
            xmin = Math.max(0.0f, Math.min(1.0f, xmin));
            ymin = Math.max(0.0f, Math.min(1.0f, ymin));
            xmax = Math.max(0.0f, Math.min(1.0f, xmax));
            ymax = Math.max(0.0f, Math.min(1.0f, ymax));
            
            // Ensure xmax >= xmin and ymax >= ymin
            if (xmax < xmin) {
                float temp = xmin;
                xmin = xmax;
                xmax = temp;
            }
            if (ymax < ymin) {
                float temp = ymin;
                ymin = ymax;
                ymax = temp;
            }
            
            // Verify bounding box validity (relative coordinates)
            float width = xmax - xmin;
            float height = ymax - ymin;
            // Minimum size approximately 2% of image size
            if (width < 0.02f || height < 0.02f || width <= 0 || height <= 0) {
                continue;
            }
            
            // Get category name
            String className = "object";
            if (classIndex > 0 && classIndex <= COCO_CLASSES.length) {
                className = COCO_CLASSES[classIndex - 1];
            }
            String chineseName = CLASS_NAMES_ZH.get(className);
            if (chineseName == null) {
                chineseName = className;
            }
            
            // Create detection result (convert relative coordinates to pixel coordinates)
            // DetectionResult expects Rect, so need to convert to pixel coordinates
            int left = (int)(xmin * originalWidth);
            int top = (int)(ymin * originalHeight);
            int right = (int)(xmax * originalWidth);
            int bottom = (int)(ymax * originalHeight);
            
            // Ensure coordinates are within valid range
            left = Math.max(0, Math.min(originalWidth - 1, left));
            top = Math.max(0, Math.min(originalHeight - 1, top));
            right = Math.max(left + 1, Math.min(originalWidth, right));
            bottom = Math.max(top + 1, Math.min(originalHeight, bottom));
            
            android.graphics.Rect boundingBox = new android.graphics.Rect(left, top, right, bottom);
            results.add(new DetectionResult(className, chineseName, confidence, boundingBox));
        }
        
        return results;
    }
    
    /**
     * 處理後處理版本的輸出格式
     * boxes: [N, 4] - 邊界框座標（相對座標 0-1）
     * classes: [N] - 類別索引
     * scores: [N] - 置信度分數
     * numDetections: 實際檢測數量
     */
    private List<DetectionResult> postProcessPostProcessedOutput(float[][] boxes, 
                                                                 float[] classes,
                                                                 float[] scores,
                                                                 int numDetections,
                                                                 int originalWidth, 
                                                                 int originalHeight) {
        List<DetectionResult> results = new ArrayList<>();
        
        int actualDetections = Math.min(numDetections, boxes.length);
        Log.d(TAG, "處理後處理輸出，實際檢測數: " + actualDetections);
        
        for (int i = 0; i < actualDetections; i++) {
            // 獲取置信度
            float confidence = (scores != null && i < scores.length) ? scores[i] : 1.0f;
            
            // 過濾低置信度檢測
            if (confidence < AppConstants.CONFIDENCE_THRESHOLD) {
                continue;
            }
            
            // 獲取類別索引
            int classIndex = (classes != null && i < classes.length) ? (int)classes[i] : 0;
            
            // 跳過背景類別 (索引0)
            if (classIndex == 0) {
                continue;
            }
            
            // 獲取邊界框座標（相對座標 0-1）
            // 格式通常是 [ymin, xmin, ymax, xmax]
            float ymin = boxes[i][0];
            float xmin = boxes[i][1];
            float ymax = boxes[i][2];
            float xmax = boxes[i][3];
            
            // 確保座標順序正確
            if (xmax < xmin) {
                float temp = xmin;
                xmin = xmax;
                xmax = temp;
            }
            if (ymax < ymin) {
                float temp = ymin;
                ymin = ymax;
                ymax = temp;
            }
            
            // 驗證邊界框合理性（相對座標）
            float width = xmax - xmin;
            float height = ymax - ymin;
            if (width < 0.02f || height < 0.02f || width <= 0 || height <= 0) {
                continue;
            }
            
            // 轉換為像素座標
            int left = (int)(xmin * originalWidth);
            int top = (int)(ymin * originalHeight);
            int right = (int)(xmax * originalWidth);
            int bottom = (int)(ymax * originalHeight);
            
            // 確保座標在有效範圍內
            left = Math.max(0, Math.min(originalWidth - 1, left));
            top = Math.max(0, Math.min(originalHeight - 1, top));
            right = Math.max(left + 1, Math.min(originalWidth, right));
            bottom = Math.max(top + 1, Math.min(originalHeight, bottom));
            
            // 獲取類別名稱
            String className = "object";
            if (classIndex > 0 && classIndex <= COCO_CLASSES.length) {
                className = COCO_CLASSES[classIndex - 1];
            }
            String chineseName = CLASS_NAMES_ZH.get(className);
            if (chineseName == null) {
                chineseName = className;
            }
            
            // 創建檢測結果
            android.graphics.Rect boundingBox = new android.graphics.Rect(left, top, right, bottom);
            results.add(new DetectionResult(className, chineseName, confidence, boundingBox));
        }
        
        // 按置信度排序
        Collections.sort(results, new Comparator<DetectionResult>() {
            @Override
            public int compare(DetectionResult a, DetectionResult b) {
                return Float.compare(b.getConfidence(), a.getConfidence());
            }
        });
        
        // 只返回置信度最高的2個物體
        if (results.size() > 2) {
            results = results.subList(0, 2);
            Log.d(TAG, "後處理檢測限制為2個物體");
        }
        
        return results;
    }
    
    /**
     * 後處理 SSD MobileNet 輸出
     */
    private List<DetectionResult> postProcessSSDOutput(float[][] boxes, float[][] classes, 
                                                     float[] scores, int numDetections, 
                                                     int originalWidth, int originalHeight) {
        List<DetectionResult> results = new ArrayList<>();
        
        for (int i = 0; i < Math.min(numDetections, scores.length); i++) {
            float confidence = scores[i];
            
            // 過濾低置信度檢測
            if (confidence < AppConstants.CONFIDENCE_THRESHOLD) {
                continue;
            }
            
            // 找到最高概率的類別
            int maxClassIndex = 0;
            float maxClassScore = classes[i][0];
            
            for (int j = 1; j < classes[i].length; j++) {
                if (classes[i][j] > maxClassScore) {
                    maxClassScore = classes[i][j];
                    maxClassIndex = j;
                }
            }
            
            // 跳過背景類別 (索引0)
            if (maxClassIndex == 0) {
                continue;
            }
            
            // 獲取邊界框座標 (y1, x1, y2, x2)
            float y1 = boxes[i][0];
            float x1 = boxes[i][1];
            float y2 = boxes[i][2];
            float x2 = boxes[i][3];
            
            // 轉換為像素座標
            int left = (int)(x1 * originalWidth);
            int top = (int)(y1 * originalHeight);
            int right = (int)(x2 * originalWidth);
            int bottom = (int)(y2 * originalHeight);
            
            // 確保座標在有效範圍內
            left = Math.max(0, Math.min(originalWidth - 1, left));
            top = Math.max(0, Math.min(originalHeight - 1, top));
            right = Math.max(0, Math.min(originalWidth - 1, right));
            bottom = Math.max(0, Math.min(originalHeight - 1, bottom));
            
            // 獲取類別名稱
            if (maxClassIndex - 1 < COCO_CLASSES.length) {
                String className = COCO_CLASSES[maxClassIndex - 1];
                String chineseName = CLASS_NAMES_ZH.get(className);
                
                if (chineseName != null) {
                    Rect boundingBox = new Rect(left, top, right, bottom);
                    
                    // 驗證邊界框合理性：寬度和高度必須大於20像素
                    int width = right - left;
                    int height = bottom - top;
                    if (width >= 20 && height >= 20 && width > 0 && height > 0) {
                        results.add(new DetectionResult(className, chineseName, confidence, boundingBox));
                    } else {
                        Log.d(TAG, "濾除不合理邊界框: " + chineseName + " (" + width + "x" + height + ")");
                    }
                }
            }
        }
        
        // 應用 NMS
        results = applyNMS(results);
        
        // 按置信度排序
        Collections.sort(results, new Comparator<DetectionResult>() {
            @Override
            public int compare(DetectionResult a, DetectionResult b) {
                return Float.compare(b.getConfidence(), a.getConfidence());
            }
        });
        
        // 只返回置信度最高的3個物體
        if (results.size() > 3) {
            results = results.subList(0, 3);
            Log.d(TAG, "SSD檢測限制為3個物體");
        }
        
        return results;
    }
    
    /**
     * 後處理 YOLO 輸出（保留作為備用）
     */
    private List<DetectionResult> postProcessOutput(float[][] output, int originalWidth, int originalHeight) {
        List<DetectionResult> results = new ArrayList<>();
        
        for (int i = 0; i < output.length; i++) {
            float[] detection = output[i];
            
            // 提取邊界框座標 (x_center, y_center, width, height)
            float x_center = detection[0];
            float y_center = detection[1];
            float width = detection[2];
            float height = detection[3];
            
            // 找到最高置信度的類別
            int maxClassIndex = 4;
            float maxConfidence = detection[4];
            
            for (int j = 5; j < detection.length; j++) {
                if (detection[j] > maxConfidence) {
                    maxConfidence = detection[j];
                    maxClassIndex = j;
                }
            }
            
            // 計算總置信度
            float confidence = maxConfidence;
            
            // 過濾低置信度檢測
            if (confidence < AppConstants.CONFIDENCE_THRESHOLD) {
                continue;
            }
            
            // 轉換為邊界框座標
            float left = (x_center - width / 2) / AppConstants.INPUT_SIZE;
            float top = (y_center - height / 2) / AppConstants.INPUT_SIZE;
            float right = (x_center + width / 2) / AppConstants.INPUT_SIZE;
            float bottom = (y_center + height / 2) / AppConstants.INPUT_SIZE;
            
            // 確保座標在有效範圍內
            left = Math.max(0, Math.min(1, left));
            top = Math.max(0, Math.min(1, top));
            right = Math.max(0, Math.min(1, right));
            bottom = Math.max(0, Math.min(1, bottom));
            
            // 獲取類別名稱
            int classIndex = maxClassIndex - 4;
            if (classIndex >= 0 && classIndex < COCO_CLASSES.length) {
                String className = COCO_CLASSES[classIndex];
                String chineseName = CLASS_NAMES_ZH.get(className);
                
                // 創建邊界框 - 使用相對座標 (0-1)，保持浮點數精度
                Rect boundingBox = new Rect(
                    (int)(left * 1000), (int)(top * 1000), 
                    (int)(right * 1000), (int)(bottom * 1000)
                );
                
                results.add(new DetectionResult(className, chineseName, confidence, boundingBox));
            }
        }
        
        // 應用 NMS (Non-Maximum Suppression)
        results = applyNMS(results);
        
        // 按置信度排序
        Collections.sort(results, new Comparator<DetectionResult>() {
            @Override
            public int compare(DetectionResult a, DetectionResult b) {
                return Float.compare(b.getConfidence(), a.getConfidence());
            }
        });
        
        // 只返回置信度最高的2個物體
        if (results.size() > 2) {
            results = results.subList(0, 2);
            Log.d(TAG, "YOLO檢測限制為2個物體");
        }
        
        return results;
    }
    
    /**
     * 應用非極大值抑制 (NMS)
     */
    private List<DetectionResult> applyNMS(List<DetectionResult> detections) {
        List<DetectionResult> filtered = new ArrayList<>();
        
        for (DetectionResult detection : detections) {
            boolean shouldKeep = true;
            
            for (DetectionResult existing : filtered) {
                if (detection.getLabel().equals(existing.getLabel())) {
                    float iou = calculateIoU(detection.getBoundingBox(), existing.getBoundingBox());
                    if (iou > AppConstants.IOU_THRESHOLD) {
                        shouldKeep = false;
                        break;
                    }
                }
            }
            
            if (shouldKeep) {
                filtered.add(detection);
            }
        }
        
        return filtered;
    }
    
    /**
     * 計算 IoU (Intersection over Union)
     */
    private float calculateIoU(Rect box1, Rect box2) {
        // 轉換為浮點數座標
        float left1 = box1.left / 1000.0f;
        float top1 = box1.top / 1000.0f;
        float right1 = box1.right / 1000.0f;
        float bottom1 = box1.bottom / 1000.0f;
        
        float left2 = box2.left / 1000.0f;
        float top2 = box2.top / 1000.0f;
        float right2 = box2.right / 1000.0f;
        float bottom2 = box2.bottom / 1000.0f;
        
        // 計算交集
        float intersectionLeft = Math.max(left1, left2);
        float intersectionTop = Math.max(top1, top2);
        float intersectionRight = Math.min(right1, right2);
        float intersectionBottom = Math.min(bottom1, bottom2);
        
        if (intersectionRight <= intersectionLeft || intersectionBottom <= intersectionTop) {
            return 0.0f;
        }
        
        float intersection = (intersectionRight - intersectionLeft) * (intersectionBottom - intersectionTop);
        
        // 計算並集
        float area1 = (right1 - left1) * (bottom1 - top1);
        float area2 = (right2 - left2) * (bottom2 - top2);
        float union = area1 + area2 - intersection;
        
        return intersection / union;
    }
    
    /**
     * 將 ImageProxy 轉換為 Bitmap
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
            Log.e(TAG, "圖像轉換失敗: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 備用檢測方法（當 YOLO 模型不可用時使用）
     * 不使用簡陋的特徵檢測，直接返回空結果以避免誤報
     */
    private List<DetectionResult> getFallbackDetections(Bitmap bitmap) {
        Log.w(TAG, "模型未載入，無法進行準確檢測，返回空結果");
        return new ArrayList<>(); // 返回空列表，避免誤報
    }
    
    /**
     * 檢測人體（基於顏色和形狀特徵）
     */
    private boolean detectPerson(int[] pixels, int width, int height) {
        // 簡單的膚色檢測
        int skinPixels = 0;
        for (int pixel : pixels) {
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;
            
            // 膚色範圍檢測
            if (r > 95 && g > 40 && b > 20 && 
                Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) > 15 &&
                Math.abs(r - g) > 15 && r > g && r > b) {
                skinPixels++;
            }
        }
        
        // 如果膚色像素超過一定比例，認為有人
        return (float)skinPixels / pixels.length > 0.05f;
    }
    
    /**
     * 檢測家具（基於邊緣和形狀特徵）
     */
    private boolean detectFurniture(int[] pixels, int width, int height) {
        // 簡單的邊緣檢測
        int edgePixels = 0;
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                int center = pixels[y * width + x];
                int right = pixels[y * width + (x + 1)];
                int bottom = pixels[(y + 1) * width + x];
                
                // 計算梯度
                int gradX = Math.abs((center & 0xFF) - (right & 0xFF));
                int gradY = Math.abs((center & 0xFF) - (bottom & 0xFF));
                
                if (gradX > 30 || gradY > 30) {
                    edgePixels++;
                }
            }
        }
        
        // 如果邊緣像素超過一定比例，認為有家具
        return (float)edgePixels / pixels.length > 0.1f;
    }
    
    /**
     * 檢測電子產品（基於顏色和亮度特徵）
     */
    private boolean detectElectronics(int[] pixels, int width, int height) {
        int brightPixels = 0;
        int darkPixels = 0;
        
        for (int pixel : pixels) {
            int brightness = ((pixel >> 16) & 0xFF) + ((pixel >> 8) & 0xFF) + (pixel & 0xFF);
            brightness /= 3;
            
            if (brightness > 200) {
                brightPixels++;
            } else if (brightness < 50) {
                darkPixels++;
            }
        }
        
        // 電子產品通常有高對比度
        return (float)brightPixels / pixels.length > 0.1f && 
               (float)darkPixels / pixels.length > 0.1f;
    }
    
    /**
     * 格式化檢測結果為語音文本
     * 只播報物體名稱，不包含前綴、距離或位置信息
     */
    public String formatResultsForSpeech(List<DetectionResult> results) {
        if (results.isEmpty()) {
            return "未偵測到任何物體";
        }
        
        StringBuilder sb = new StringBuilder();
        // 最多播報2個物體
        int maxObjects = Math.min(results.size(), 2);
        for (int i = 0; i < maxObjects; i++) {
            DetectionResult result = results.get(i);
            sb.append(result.getLabelZh());
            if (i < maxObjects - 1) {
                sb.append("、");
            }
        }
        
        // 如果物體超過2個，添加總數
        if (results.size() > 2) {
            sb.append("等").append(results.size()).append("個");
        }
        
        return sb.toString();
    }
    
    /**
     * 獲取中文類別名稱
     */
    public static String getChineseLabel(String englishLabel) {
        String chineseLabel = CLASS_NAMES_ZH.get(englishLabel);
        return chineseLabel != null ? chineseLabel : englishLabel;
    }
    
    /**
     * 獲取檢測性能報告
     */
    public String getPerformanceReport() {
        if (performanceMonitor != null) {
            return performanceMonitor.getPerformanceReport();
        }
        return "性能監控未初始化";
    }
    
    /**
     * 檢查檢測性能是否良好
     */
    public boolean isPerformanceGood() {
        if (performanceMonitor != null) {
            return performanceMonitor.isPerformanceGood();
        }
        return false;
    }
    
    /**
     * 重置性能統計
     */
    public void resetPerformanceStats() {
        if (performanceMonitor != null) {
            performanceMonitor.reset();
        }
    }
    
    public void close() {
        synchronized (interpreterLock) {
            isInitialized = false;
            if (tflite != null) {
                tflite.close();
                tflite = null;
            }
            Log.d(TAG, "真實AI檢測器資源已釋放");
        }
    }
    
    /**
     * 檢測結果類
     */
    public static class DetectionResult {
        private String label;
        private String labelZh;
        private float confidence;
        private Rect boundingBox;
        
        public DetectionResult(String label, String labelZh, float confidence) {
            this.label = label;
            this.labelZh = labelZh;
            this.confidence = confidence;
        }
        
        public DetectionResult(String label, String labelZh, float confidence, Rect boundingBox) {
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
        
        public Rect getBoundingBox() {
            return boundingBox;
        }
        
        @Override
        public String toString() {
            return String.format("%s (%.2f%%)", labelZh, confidence * 100);
        }
    }
}