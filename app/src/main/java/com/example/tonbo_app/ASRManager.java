package com.example.tonbo_app;

import android.content.Context;
import android.util.Log;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * ASR管理器 - 支持多種語音識別引擎
 * 支持：sherpa-onnx、Whisper.cpp、Azure ASR等
 */
public class ASRManager {
    private static final String TAG = "ASRManager";
    
    // ASR引擎類型
    public enum ASREngine {
        ANDROID_NATIVE,    // Android原生SpeechRecognizer
        SHERPA_ONNX,       // sherpa-onnx離線識別
        WHISPER_CPP,       // Whisper.cpp離線識別
        AZURE_ASR,         // Azure雲端識別
        FUNASR             // FunASR離線識別
    }
    
    private Context context;
    private ASREngine currentEngine;
    private boolean isOnlineMode;
    
    // 各引擎實例
    private AndroidNativeASR androidNativeASR;
    private SherpaOnnxASR sherpaOnnxASR;
    private WhisperCppASR whisperCppASR;
    private AzureASR azureASR;
    private FunASR funASR;
    
    public interface ASRCallback {
        void onResult(String text, float confidence);
        void onError(String error);
        void onPartialResult(String partialText);
    }
    
    public ASRManager(Context context) {
        this.context = context;
        this.currentEngine = ASREngine.ANDROID_NATIVE; // 默認使用原生
        this.isOnlineMode = true;
        initializeEngines();
    }
    
    /**
     * 初始化所有ASR引擎
     */
    private void initializeEngines() {
        try {
            androidNativeASR = new AndroidNativeASR(context);
            // Sherpa 延迟加载，避免启动时原生库崩溃
            
            // 初始化Whisper.cpp（如果可用）
            if (isWhisperCppAvailable()) {
                whisperCppASR = new WhisperCppASR(context);
                Log.d(TAG, "Whisper.cpp ASR初始化成功");
            }
            
            // 初始化Azure ASR（如果配置了）
            if (isAzureASRConfigured()) {
                azureASR = new AzureASR(context);
                Log.d(TAG, "Azure ASR初始化成功");
            }
            
            // 初始化FunASR（如果可用）
            if (isFunASRAvailable()) {
                funASR = new FunASR(context);
                Log.d(TAG, "FunASR初始化成功");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "ASR引擎初始化失敗: " + e.getMessage());
        }
    }
    
    /**
     * 設置ASR引擎
     */
    public void setASREngine(ASREngine engine) {
        this.currentEngine = engine;
        Log.d(TAG, "切換ASR引擎到: " + engine);
    }

    public ASREngine getCurrentEngine() {
        return currentEngine;
    }

    public boolean hasSherpaModel() {
        return SherpaOnnxModelLocator.isModelReady(context);
    }

    public boolean isSherpaReady() {
        return sherpaOnnxASR != null && sherpaOnnxASR.isInitialized();
    }

    private synchronized SherpaOnnxASR getOrCreateSherpa() {
        if (!hasSherpaModel()) {
            return null;
        }
        if (sherpaOnnxASR == null) {
            try {
                sherpaOnnxASR = new SherpaOnnxASR(context);
            } catch (Throwable e) {
                Log.e(TAG, "创建 Sherpa 失败: " + e.getMessage(), e);
                sherpaOnnxASR = null;
            }
        }
        return sherpaOnnxASR;
    }

    public String getSherpaInitError() {
        return sherpaOnnxASR != null ? sherpaOnnxASR.getInitError() : null;
    }

    public void warmUpSherpa() {
        if (!hasSherpaModel()) {
            return;
        }
        SherpaOnnxASR sherpa = getOrCreateSherpa();
        if (sherpa != null) {
            sherpa.startInitAsync();
        }
    }
    
    public boolean prepareSherpaIfAvailable() {
        return prepareSherpaIfAvailable(30000);
    }

    public boolean prepareSherpaIfAvailable(long timeoutMs) {
        if (!hasSherpaModel()) {
            return false;
        }
        SherpaOnnxASR sherpa = getOrCreateSherpa();
        if (sherpa == null) {
            return false;
        }
        sherpa.startInitAsync();
        boolean ready = sherpa.waitForInit(timeoutMs);
        if (!ready) {
            Log.e(TAG, "Sherpa 未就绪: " + sherpa.getInitError());
        }
        return ready;
    }

    /**
     * 設置在線/離線模式
     */
    public void setOnlineMode(boolean online) {
        this.isOnlineMode = online;
        Log.d(TAG, "設置模式: " + (online ? "在線" : "離線"));
    }
    
    /**
     * 開始語音識別
     */
    public void startRecognition(ASRCallback callback) {
        Log.d(TAG, "開始語音識別，引擎: " + currentEngine);
        
        switch (currentEngine) {
            case ANDROID_NATIVE:
                if (androidNativeASR != null) {
                    androidNativeASR.startRecognition(callback);
                }
                break;
                
            case SHERPA_ONNX:
                SherpaOnnxASR sherpa = getOrCreateSherpa();
                if (sherpa != null) {
                    sherpa.startRecognition(new SherpaOnnxASR.SherpaOnnxCallback() {
                        @Override
                        public void onResult(String text, float confidence) {
                            callback.onResult(text, confidence);
                        }

                        @Override
                        public void onPartialResult(String partialText) {
                            callback.onPartialResult(partialText);
                        }

                        @Override
                        public void onError(String error) {
                            callback.onError(error);
                        }

                        @Override
                        public void onListeningStarted() {
                            Log.d(TAG, "Sherpa 开始监听");
                        }

                        @Override
                        public void onListeningStopped() {
                            Log.d(TAG, "Sherpa 停止监听");
                        }
                    });
                } else {
                    callback.onError("sherpa-onnx ASR 不可用，请运行 deploy_sherpa_asr.ps1");
                }
                break;
                
            case WHISPER_CPP:
                if (whisperCppASR != null) {
                    whisperCppASR.startRecognition(callback);
                } else {
                    callback.onError("Whisper.cpp ASR不可用");
                }
                break;
                
            case AZURE_ASR:
                if (azureASR != null && isOnlineMode) {
                    azureASR.startRecognition(callback);
                } else {
                    callback.onError("Azure ASR不可用或離線模式");
                }
                break;
                
            case FUNASR:
                if (funASR != null) {
                    funASR.startRecognition(callback);
                } else {
                    callback.onError("FunASR不可用");
                }
                break;
                
            default:
                callback.onError("未知的ASR引擎");
                break;
        }
    }
    
    /**
     * 停止語音識別
     */
    public void stopRecognition() {
        Log.d(TAG, "停止語音識別");
        
        switch (currentEngine) {
            case ANDROID_NATIVE:
                if (androidNativeASR != null) {
                    androidNativeASR.stopRecognition();
                }
                break;
                
            case SHERPA_ONNX:
                if (sherpaOnnxASR != null) {
                    sherpaOnnxASR.stopRecognition();
                }
                break;
                
            case WHISPER_CPP:
                if (whisperCppASR != null) {
                    whisperCppASR.stopRecognition();
                }
                break;
                
            case AZURE_ASR:
                if (azureASR != null) {
                    azureASR.stopRecognition();
                }
                break;
                
            case FUNASR:
                if (funASR != null) {
                    funASR.stopRecognition();
                }
                break;
        }
    }
    
    /**
     * 獲取當前引擎信息
     */
    public String getCurrentEngineInfo() {
        return "當前引擎: " + currentEngine + ", 模式: " + (isOnlineMode ? "在線" : "離線");
    }
    
    /**
     * 獲取可用引擎列表
     */
    public List<ASREngine> getAvailableEngines() {
        List<ASREngine> availableEngines = new java.util.ArrayList<>();
        availableEngines.add(ASREngine.ANDROID_NATIVE);

        if (hasSherpaModel()) {
            availableEngines.add(ASREngine.SHERPA_ONNX);
        }
        
        if (isWhisperCppAvailable()) {
            availableEngines.add(ASREngine.WHISPER_CPP);
        }
        
        if (isAzureASRConfigured() && isOnlineMode) {
            availableEngines.add(ASREngine.AZURE_ASR);
        }
        
        if (isFunASRAvailable()) {
            availableEngines.add(ASREngine.FUNASR);
        }
        
        return availableEngines;
    }
    
    // 檢查各引擎可用性
    private boolean isSherpaOnnxAvailable() {
        try {
            Class.forName("com.k2fsa.sherpa.onnx.OnlineRecognizer");
            return SherpaOnnxModelLocator.isModelReady(context);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    private boolean isWhisperCppAvailable() {
        // 檢查Whisper.cpp是否已集成
        try {
            Class.forName("com.whispercpp.WhisperCpp");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    private boolean isAzureASRConfigured() {
        // 檢查Azure ASR配置
        // 這裡可以檢查API密鑰等配置
        return false; // 暫時返回false，需要配置後啟用
    }
    
    private boolean isFunASRAvailable() {
        // 檢查FunASR是否已集成
        try {
            Class.forName("com.funasr.FunASR");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * 釋放資源
     */
    public void release() {
        if (androidNativeASR != null) {
            androidNativeASR.release();
        }
        if (sherpaOnnxASR != null) {
            sherpaOnnxASR.release();
        }
        if (whisperCppASR != null) {
            whisperCppASR.release();
        }
        if (azureASR != null) {
            azureASR.release();
        }
        if (funASR != null) {
            funASR.release();
        }
    }
}

/**
 * Android原生ASR實現
 */
class AndroidNativeASR {
    private Context context;
    
    public AndroidNativeASR(Context context) {
        this.context = context;
    }
    
    public void startRecognition(ASRManager.ASRCallback callback) {
        // 語音識別功能已移除
        callback.onError("語音識別功能已停用");
    }
    
    public void stopRecognition() {
        // 語音識別功能已移除
    }
    
    public void release() {
        // 語音識別功能已移除
    }
}

/**
 * sherpa-onnx ASR實現（示例）
 * 注意：實際的SherpaOnnxASR類已移至單獨文件
 */

/**
 * Whisper.cpp ASR實現（示例）
 */
class WhisperCppASR {
    private Context context;
    
    public WhisperCppASR(Context context) {
        this.context = context;
    }
    
    public void startRecognition(ASRManager.ASRCallback callback) {
        // TODO: 實現Whisper.cpp語音識別
        callback.onError("Whisper.cpp ASR尚未實現");
    }
    
    public void stopRecognition() {
        // TODO: 停止Whisper.cpp識別
    }
    
    public void release() {
        // TODO: 釋放Whisper.cpp資源
    }
}

/**
 * Azure ASR實現（示例）
 */
class AzureASR {
    private Context context;
    
    public AzureASR(Context context) {
        this.context = context;
    }
    
    public void startRecognition(ASRManager.ASRCallback callback) {
        // TODO: 實現Azure ASR語音識別
        callback.onError("Azure ASR尚未實現");
    }
    
    public void stopRecognition() {
        // TODO: 停止Azure ASR識別
    }
    
    public void release() {
        // TODO: 釋放Azure ASR資源
    }
}

/**
 * FunASR實現（示例）
 */
class FunASR {
    private Context context;
    
    public FunASR(Context context) {
        this.context = context;
    }
    
    public void startRecognition(ASRManager.ASRCallback callback) {
        // TODO: 實現FunASR語音識別
        callback.onError("FunASR尚未實現");
    }
    
    public void stopRecognition() {
        // TODO: 停止FunASR識別
    }
    
    public void release() {
        // TODO: 釋放FunASR資源
    }
}
