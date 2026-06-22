package com.example.tonbo_app;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.k2fsa.sherpa.onnx.OnlineModelConfig;
import com.k2fsa.sherpa.onnx.OnlineParaformerModelConfig;
import com.k2fsa.sherpa.onnx.OnlineRecognizer;
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OnlineStream;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sherpa-ONNX 流式离线 ASR（粤语 + 普通话 + 英文）
 */
public class SherpaOnnxASR {
    private static final String TAG = "SherpaOnnxASR";

    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private final Context context;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final AtomicBoolean initStarted = new AtomicBoolean(false);

    private OnlineRecognizer recognizer;
    private AudioRecord audioRecord;
    private volatile boolean isInitialized;
    private volatile boolean isListening;
    private volatile String initError;

    public interface SherpaOnnxCallback {
        void onResult(String text, float confidence);
        void onPartialResult(String partialText);
        void onError(String error);
        void onListeningStarted();
        void onListeningStopped();
    }

    public SherpaOnnxASR(Context context) {
        this.context = context.getApplicationContext();
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void startInitAsync() {
        executor.execute(this::ensureInitializedSync);
    }

    private synchronized void ensureInitializedSync() {
        if (isInitialized || initError != null) {
            return;
        }
        if (!initStarted.compareAndSet(false, true)) {
            return;
        }
        try {
            String modelDir = SherpaOnnxModelLocator.ensureInternalModelDir(context);
            if (modelDir == null) {
                initError = "Sherpa 模型未部署，请运行 deploy_sherpa_asr.ps1";
                Log.e(TAG, initError);
                return;
            }

            OnlineParaformerModelConfig paraformer = new OnlineParaformerModelConfig();
            paraformer.setEncoder(modelDir + "/" + SherpaOnnxModelLocator.ENCODER_FILE);
            paraformer.setDecoder(modelDir + "/" + SherpaOnnxModelLocator.DECODER_FILE);

            OnlineModelConfig modelConfig = new OnlineModelConfig();
            modelConfig.setParaformer(paraformer);
            modelConfig.setTokens(modelDir + "/" + SherpaOnnxModelLocator.TOKENS_FILE);
            modelConfig.setNumThreads(2);
            modelConfig.setDebug(false);
            modelConfig.setProvider("cpu");
            modelConfig.setModelType("paraformer");

            OnlineRecognizerConfig config = new OnlineRecognizerConfig();
            config.setModelConfig(modelConfig);
            config.setEnableEndpoint(true);
            config.setDecodingMethod("greedy_search");

            // 从 filesDir/SD 卡加载须传 null，不能用 AssetManager
            recognizer = new OnlineRecognizer(null, config);
            isInitialized = true;
            Log.i(TAG, "Sherpa-ONNX 初始化成功, modelDir=" + modelDir);
        } catch (Throwable e) {
            initError = "Sherpa 初始化失败: " + e.getMessage();
            Log.e(TAG, initError, e);
            isInitialized = false;
        }
    }

    public boolean waitForInit(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!isInitialized && initError == null && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return isInitialized;
    }

    public void startRecognition(SherpaOnnxCallback callback) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            callback.onError("需要录音权限");
            return;
        }

        executor.execute(() -> {
            if (!isInitialized && initError == null) {
                ensureInitializedSync();
            }
            if (!waitForInit(30000)) {
                postError(callback, initError != null ? initError : "Sherpa 初始化超时");
                return;
            }
            if (isListening) {
                postError(callback, "已在监听中");
                return;
            }
            if (recognizer == null) {
                postError(callback, "Sherpa 识别器未就绪");
                return;
            }

            try {
                int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
                audioRecord = new AudioRecord(
                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        Math.max(bufferSize * 2, SAMPLE_RATE));
                if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                    postError(callback, "音频录制初始化失败");
                    return;
                }

                isListening = true;
                postListeningStarted(callback);
                audioRecord.startRecording();

                waitUntilSafeToListen();

                OnlineStream stream = recognizer.createStream("");
                short[] buffer = new short[Math.max(1600, bufferSize)];
                String lastPartial = "";

                while (isListening) {
                    if (shouldIgnoreCapturedAudio()) {
                        Thread.sleep(30);
                        continue;
                    }

                    int read = audioRecord.read(buffer, 0, buffer.length);
                    if (read <= 0) {
                        continue;
                    }

                    float[] samples = new float[read];
                    for (int i = 0; i < read; i++) {
                        samples[i] = buffer[i] / 32768.0f;
                    }
                    stream.acceptWaveform(samples, SAMPLE_RATE);
                    while (recognizer.isReady(stream)) {
                        recognizer.decode(stream);
                    }

                    String text = recognizer.getResult(stream).getText();
                    if (text != null && !text.isEmpty() && !text.equals(lastPartial)) {
                        lastPartial = text;
                        postPartial(callback, text);
                    }

                    if (recognizer.isEndpoint(stream)) {
                        float[] tail = new float[(int) (0.8 * SAMPLE_RATE)];
                        stream.acceptWaveform(tail, SAMPLE_RATE);
                        while (recognizer.isReady(stream)) {
                            recognizer.decode(stream);
                        }
                        String finalText = recognizer.getResult(stream).getText();
                        recognizer.reset(stream);
                        if (finalText != null && !finalText.trim().isEmpty()) {
                            postResult(callback, finalText.trim(), 0.9f);
                            lastPartial = "";
                        }
                    }
                }

                stopAudioRecord();
                stream.release();
                postListeningStopped(callback);
            } catch (Throwable e) {
                Log.e(TAG, "Sherpa 识别错误", e);
                isListening = false;
                stopAudioRecord();
                postError(callback, "语音识别错误: " + e.getMessage());
                postListeningStopped(callback);
            }
        });
    }

    public void stopRecognition() {
        isListening = false;
        stopAudioRecord();
    }

    private void stopAudioRecord() {
        if (audioRecord != null) {
            try {
                if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop();
                }
                audioRecord.release();
            } catch (Exception e) {
                Log.w(TAG, "停止录音异常: " + e.getMessage());
            }
            audioRecord = null;
        }
    }

    public boolean isListening() {
        return isListening;
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public String getInitError() {
        return initError;
    }

    public void release() {
        stopRecognition();
        if (recognizer != null) {
            try {
                recognizer.release();
            } catch (Exception ignored) {}
            recognizer = null;
        }
        isInitialized = false;
        executor.shutdown();
    }

    private void postListeningStarted(SherpaOnnxCallback callback) {
        mainHandler.post(callback::onListeningStarted);
    }

    private void postListeningStopped(SherpaOnnxCallback callback) {
        mainHandler.post(callback::onListeningStopped);
    }

    private void postPartial(SherpaOnnxCallback callback, String text) {
        mainHandler.post(() -> callback.onPartialResult(text));
    }

    private void postResult(SherpaOnnxCallback callback, String text, float confidence) {
        mainHandler.post(() -> callback.onResult(text, confidence));
    }

    private void waitUntilSafeToListen() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        while (isListening && shouldIgnoreCapturedAudio() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
    }

    /** TTS 播報或電話通話時不送入識別器，避免聽到電話/喇叭聲音 */
    private boolean shouldIgnoreCapturedAudio() {
        if (TTSManager.getInstance(context).isSpeaking()) {
            return true;
        }
        return isPhoneCallActive();
    }

    private boolean isPhoneCallActive() {
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                int mode = am.getMode();
                if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION) {
                    return true;
                }
            }
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null && tm.getCallState() != TelephonyManager.CALL_STATE_IDLE) {
                return true;
            }
        } catch (Exception e) {
            Log.w(TAG, "检查通话状态失败: " + e.getMessage());
        }
        return false;
    }

    private void postError(SherpaOnnxCallback callback, String error) {
        mainHandler.post(() -> callback.onError(error));
    }
}
