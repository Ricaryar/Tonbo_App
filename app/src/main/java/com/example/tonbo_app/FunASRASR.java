package com.example.tonbo_app;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.SessionOptions;
import ai.onnxruntime.OrtSession.SessionOptions.OptLevel;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * FunASR 語音識別實現
 * 使用 ONNX Runtime 進行離線語音識別
 * 支持中文（普通話、廣東話）和英文
 * 
 * 參考：https://github.com/modelscope/FunASR
 */
public class FunASRASR {
    private static final String TAG = "FunASRASR";
    
    private Context context;
    private boolean isInitialized = false;
    private boolean isListening = false;
    private AudioRecord audioRecord;
    private ExecutorService executorService;
    
    // ONNX Runtime 相關
    private OrtEnvironment ortEnvironment;
    private OrtSession ortSession;
    private String modelPath;
    
    // 音頻參數
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
    
    // 流式識別參數
    private static final int CHUNK_SIZE_MS = 600; // 600ms 塊大小
    private static final int CHUNK_SIZE_SAMPLES = SAMPLE_RATE * CHUNK_SIZE_MS / 1000;
    
    private AtomicBoolean shouldStop = new AtomicBoolean(false);
    
    public interface FunASRCallback {
        void onResult(String text, float confidence);
        void onPartialResult(String partialText);
        void onError(String error);
        void onListeningStarted();
        void onListeningStopped();
    }
    
    public FunASRASR(Context context) {
        this.context = context;
        this.executorService = Executors.newSingleThreadExecutor();
        this.ortEnvironment = OrtEnvironment.getEnvironment();
        initializeFunASR();
    }
    
    /**
     * 初始化 FunASR
     */
    private void initializeFunASR() {
        executorService.execute(() -> {
            try {
                Log.d(TAG, "開始初始化 FunASR");
                
                // 檢查模型文件是否存在
                // 模型文件應該放在 assets/funasr/ 目錄下
                // 或者從網絡下載到本地存儲
                modelPath = getModelPath();
                
                if (modelPath == null || !new File(modelPath).exists()) {
                    Log.w(TAG, "FunASR 模型文件不存在，將使用備用方案");
                    // 可以嘗試從網絡下載模型，或使用在線 API
                    isInitialized = false;
                    return;
                }
                
                // 創建 ONNX Runtime Session
                SessionOptions sessionOptions = new SessionOptions();
                sessionOptions.setOptimizationLevel(OptLevel.ALL_OPT);
                sessionOptions.setIntraOpNumThreads(4);
                sessionOptions.setInterOpNumThreads(4);
                
                ortSession = ortEnvironment.createSession(modelPath, sessionOptions);
                Log.d(TAG, "FunASR 模型加載成功: " + modelPath);
                
                isInitialized = true;
                Log.d(TAG, "FunASR 初始化成功");
                
            } catch (Exception e) {
                Log.e(TAG, "FunASR 初始化失敗: " + e.getMessage(), e);
                isInitialized = false;
            }
        });
    }
    
    /**
     * 獲取模型路徑
     */
    private String getModelPath() {
        // 優先從本地存儲查找
        File localModelDir = new File(context.getFilesDir(), "funasr_models");
        if (localModelDir.exists()) {
            File[] modelFiles = localModelDir.listFiles((dir, name) -> name.endsWith(".onnx"));
            if (modelFiles != null && modelFiles.length > 0) {
                return modelFiles[0].getAbsolutePath();
            }
        }
        
        // 嘗試從 assets 複製（首次運行時）
        try {
            String[] assetFiles = context.getAssets().list("funasr");
            if (assetFiles != null && assetFiles.length > 0) {
                // 需要將 assets 中的模型複製到本地存儲
                // 這裡先返回 null，實際使用時需要實現複製邏輯
                Log.d(TAG, "找到 assets 中的模型文件，需要複製到本地");
            }
        } catch (IOException e) {
            Log.w(TAG, "無法讀取 assets: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 開始語音識別
     */
    public void startRecognition(FunASRCallback callback) {
        if (!isInitialized) {
            callback.onError("FunASR 尚未初始化，請檢查模型文件");
            return;
        }
        
        if (isListening) {
            Log.w(TAG, "已經在監聽中");
            return;
        }
        
        shouldStop.set(false);
        isListening = true;
        
        executorService.execute(() -> {
            try {
                // 檢查錄音權限
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) 
                            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        callback.onError("需要錄音權限");
                        isListening = false;
                        return;
                    }
                }
                
                // 初始化 AudioRecord
                audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    BUFFER_SIZE * 2
                );
                
                if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                    callback.onError("無法初始化 AudioRecord");
                    isListening = false;
                    return;
                }
                
                audioRecord.startRecording();
                callback.onListeningStarted();
                
                Log.d(TAG, "開始錄音和識別");
                
                // 流式識別循環
                byte[] audioBuffer = new byte[CHUNK_SIZE_SAMPLES * 2]; // 16-bit = 2 bytes per sample
                Map<String, Object> cache = new HashMap<>();
                
                while (!shouldStop.get() && isListening) {
                    int bytesRead = audioRecord.read(audioBuffer, 0, audioBuffer.length);
                    
                    if (bytesRead > 0) {
                        // 轉換為浮點數數組
                        float[] audioFloats = convertBytesToFloats(audioBuffer, bytesRead);
                        
                        // 執行 ONNX 推理
                        String result = recognizeChunk(audioFloats, cache, shouldStop.get());
                        
                        if (result != null && !result.isEmpty()) {
                            if (shouldStop.get()) {
                                // 最終結果
                                callback.onResult(result, 1.0f);
                            } else {
                                // 部分結果
                                callback.onPartialResult(result);
                            }
                        }
                    }
                }
                
                // 最終識別
                if (!shouldStop.get()) {
                    // 處理最後的緩存數據
                    String finalResult = recognizeChunk(new float[0], cache, true);
                    if (finalResult != null && !finalResult.isEmpty()) {
                        callback.onResult(finalResult, 1.0f);
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "語音識別失敗: " + e.getMessage(), e);
                callback.onError("識別失敗: " + e.getMessage());
            } finally {
                stopRecording();
                callback.onListeningStopped();
                isListening = false;
            }
        });
    }
    
    /**
     * 識別音頻塊
     */
    private String recognizeChunk(float[] audioData, Map<String, Object> cache, boolean isFinal) {
        try {
            if (ortSession == null) {
                return null;
            }
            
            // 準備輸入張量
            // 根據 FunASR 的模型輸入格式準備數據
            long[] shape = {1, audioData.length}; // [batch_size, sequence_length]
            
            // 將 float[] 轉換為 FloatBuffer
            FloatBuffer floatBuffer = FloatBuffer.allocate(audioData.length);
            floatBuffer.put(audioData);
            floatBuffer.rewind();
            
            OnnxTensor inputTensor = OnnxTensor.createTensor(ortEnvironment, floatBuffer, shape);
            
            // 準備輸入 Map（根據實際模型調整）
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("speech", inputTensor);
            
            // 如果有緩存，也加入輸入
            if (cache.containsKey("encoder_cache")) {
                inputs.put("encoder_cache", (OnnxTensor) cache.get("encoder_cache"));
            }
            
            // 執行推理
            OrtSession.Result outputs = ortSession.run(inputs);
            
            // 提取結果（根據實際模型輸出調整）
            OnnxTensor outputTensor = (OnnxTensor) outputs.get(0);
            if (outputTensor != null) {
                // 根據實際輸出格式解析文本
                String text = parseOutput(outputTensor);
                
                // 更新緩存（如果有）
                if (outputs.get("encoder_cache") != null) {
                    cache.put("encoder_cache", outputs.get("encoder_cache"));
                }
                
                inputTensor.close();
                outputTensor.close();
                outputs.close();
                
                return text;
            }
            
            inputTensor.close();
            outputs.close();
            
        } catch (Exception e) {
            Log.e(TAG, "推理失敗: " + e.getMessage(), e);
        }
        
        return null;
    }
    
    /**
     * 解析輸出張量為文本
     */
    private String parseOutput(OnnxTensor outputTensor) {
        try {
            // 根據 FunASR 的輸出格式解析
            // 這裡需要根據實際模型輸出進行調整
            long[] shape = outputTensor.getInfo().getShape();
            
            // 根據 FunASR 的輸出格式，可能是 token IDs 或直接是文本
            // 需要根據實際模型調整
            if (shape.length >= 1) {
                // 獲取輸出值（需要根據實際輸出類型調整）
                Object value = outputTensor.getValue();
                
                // TODO: 實現 token IDs 到文本的轉換
                // 需要詞彙表（vocab）來將 token IDs 轉換為文本
                // 這裡簡化處理，實際需要完整的解碼邏輯
                
                Log.d(TAG, "輸出形狀: " + Arrays.toString(shape));
                return ""; // 暫時返回空，需要實現完整的解碼邏輯
            }
        } catch (Exception e) {
            Log.e(TAG, "解析輸出失敗: " + e.getMessage(), e);
        }
        
        return "";
    }
    
    /**
     * 將字節數組轉換為浮點數數組
     */
    private float[] convertBytesToFloats(byte[] bytes, int length) {
        int sampleCount = length / 2; // 16-bit = 2 bytes per sample
        float[] floats = new float[sampleCount];
        
        // 將字節轉換為 16-bit 整數，然後歸一化為浮點數
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes, 0, length)
            .order(ByteOrder.LITTLE_ENDIAN);
        ShortBuffer shortBuffer = byteBuffer.asShortBuffer();
        
        for (int i = 0; i < sampleCount; i++) {
            // 讀取 16-bit 整數並歸一化到 [-1, 1]
            floats[i] = shortBuffer.get(i) / 32768.0f;
        }
        
        return floats;
    }
    
    /**
     * 停止錄音
     */
    private void stopRecording() {
        if (audioRecord != null) {
            try {
                if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop();
                }
                audioRecord.release();
            } catch (Exception e) {
                Log.e(TAG, "停止錄音失敗: " + e.getMessage(), e);
            }
            audioRecord = null;
        }
    }
    
    /**
     * 停止語音識別
     */
    public void stopRecognition() {
        shouldStop.set(true);
        isListening = false;
        stopRecording();
    }
    
    /**
     * 釋放資源
     */
    public void release() {
        stopRecognition();
        
        if (executorService != null) {
            executorService.shutdown();
        }
        
        if (ortSession != null) {
            try {
                ortSession.close();
            } catch (Exception e) {
                Log.e(TAG, "關閉 ONNX Session 失敗: " + e.getMessage(), e);
            }
            ortSession = null;
        }
        
        isInitialized = false;
    }
}

