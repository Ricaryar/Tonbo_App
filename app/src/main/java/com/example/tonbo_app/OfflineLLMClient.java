package com.example.tonbo_app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 离线 LLM 客户端 — 加载用户自训练的 qwen-tonbo-q8_0.gguf
 */
public class OfflineLLMClient {
    private static final String TAG = "OfflineLLMClient";

    private static final String PREFS_NAME = "OfflineLLM";
    private static final String KEY_MODEL_READY = "model_ready";
    private static final String KEY_USE_OFFLINE = "use_offline_llm";

    public static final String CUSTOM_GGUF_FILENAME = "qwen-tonbo-q8_0.gguf";
    public static final String CUSTOM_GGUF_ALT_FILENAME = "tonbo_assistant_q4.gguf";
    public static final String ADB_MODEL_DIR = "/data/local/tmp/llm/";

    private static OfflineLLMClient instance;

    private final Context context;
    private final ExecutorService executor;
    private final AtomicBoolean isInitializing = new AtomicBoolean(false);
    private final AtomicBoolean isReady = new AtomicBoolean(false);

    private OfflineGGUFInference ggufInference;
    private String modelPath;

    public static synchronized OfflineLLMClient getInstance(Context context) {
        if (instance == null) {
            instance = new OfflineLLMClient(context);
        }
        return instance;
    }

    private OfflineLLMClient(Context context) {
        this.context = context.getApplicationContext();
        this.executor = Executors.newSingleThreadExecutor();
    }

    public static boolean isOfflineModeEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_USE_OFFLINE, true);
    }

    public static void setOfflineModeEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_USE_OFFLINE, enabled).apply();
    }

    public boolean isReady() {
        return isReady.get();
    }

    public boolean isInitializing() {
        return isInitializing.get();
    }

    public void initializeAsync(InitCallback callback) {
        if (isReady.get()) {
            if (callback != null) callback.onReady();
            return;
        }
        if (!isInitializing.compareAndSet(false, true)) {
            return;
        }

        executor.execute(() -> {
            try {
                extractBundledLlmIfNeeded();
                modelPath = resolveModelPath();
                if (modelPath == null) {
                    Log.w(TAG, "找不到 GGUF 模型");
                    if (callback != null) {
                        callback.onError("模型文件不存在，请重新安装含模型的 APK");
                    }
                    return;
                }

                ggufInference = new OfflineGGUFInference(context);
                ggufInference.loadModel(modelPath);
                ggufInference.setSystemPrompt(buildSystemPrompt());
                isReady.set(true);

                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().putBoolean(KEY_MODEL_READY, true).apply();

                Log.i(TAG, "✅ 瞳伴自训练 GGUF 模型加载成功: " + modelPath);
                if (callback != null) callback.onReady();

            } catch (Exception e) {
                Log.e(TAG, "离线 LLM 初始化失败", e);
                if (callback != null) callback.onError(e.getMessage());
            } finally {
                isInitializing.set(false);
            }
        });
    }

    public void sendChatMessage(String message,
                                List<ConversationManager.ConversationTurn> history,
                                LLMClient.ChatCallback callback) {
        if (!isReady.get() || ggufInference == null) {
            callback.onError("离线 LLM 未就绪");
            return;
        }

        executor.execute(() -> {
            try {
                ggufInference.setSystemPrompt(buildSystemPrompt());
                String response = ggufInference.generate(message);

                if (response != null && !response.trim().isEmpty()) {
                    Log.i(TAG, "离线 LLM 回复长度: " + response.length());
                    callback.onResponse(response.trim());
                } else {
                    callback.onError("离线模型返回空回复");
                }
            } catch (Exception e) {
                Log.e(TAG, "离线 LLM 推理失败", e);
                callback.onError("推理失败: " + e.getMessage());
            }
        });
    }

    public void testConnection(LLMClient.ConnectionCallback callback) {
        sendChatMessage("你好", null, new LLMClient.ChatCallback() {
            @Override
            public void onResponse(String response) {
                callback.onResult(true, "离线模型就绪: " + response.substring(0, Math.min(20, response.length())));
            }

            @Override
            public void onError(String error) {
                callback.onResult(false, error);
            }
        });
    }

    private String buildSystemPrompt() {
        SharedPreferences prefs = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE);
        String language = prefs.getString("current_language", "cantonese");

        if ("cantonese".equals(language)) {
            return "你是一個名叫瞳伴的語音助手，專為視障人士設計。請用廣東話回應，回答要簡潔自然，適合語音播報。";
        } else if ("mandarin".equals(language)) {
            return "你是一个名叫瞳伴的语音助手，专为视障人士设计。请用普通话回应，回答要简洁自然，适合语音播报。";
        } else {
            return "You are Tonbo, a friendly voice assistant designed for visually impaired users. "
                    + "Please respond in English, keep answers concise and natural, suitable for voice broadcast.";
        }
    }

    public static boolean hasBundledLlmAssets(Context context) {
        android.content.res.AssetManager assets = context.getApplicationContext().getAssets();
        return BundledAssetExtractor.assetExists(assets, "llm/" + CUSTOM_GGUF_FILENAME)
                || BundledAssetExtractor.assetExists(assets, "llm/" + CUSTOM_GGUF_ALT_FILENAME);
    }

    /** 首次运行从 APK assets 解压 GGUF 到内部存储（约 1.5GB，仅解压一次） */
    private void extractBundledLlmIfNeeded() {
        String[] ggufNames = {CUSTOM_GGUF_FILENAME, CUSTOM_GGUF_ALT_FILENAME};
        android.content.res.AssetManager assets = context.getAssets();
        for (String name : ggufNames) {
            File dest = new File(context.getFilesDir(), "llm/" + name);
            if (dest.exists() && dest.length() > 0) {
                return;
            }
            String assetPath = "llm/" + name;
            try {
                if (BundledAssetExtractor.copyAssetIfNeeded(assets, assetPath, dest)) {
                    Log.i(TAG, "已从 APK 解压 LLM: " + dest.getAbsolutePath());
                    return;
                }
            } catch (IOException e) {
                Log.w(TAG, "解压 bundled LLM 失败: " + e.getMessage());
            }
        }
    }

    private String resolveModelPath() {
        String[] ggufNames = {CUSTOM_GGUF_FILENAME, CUSTOM_GGUF_ALT_FILENAME};
        String[] subDirs = {"llm/", "models/", ""};

        for (String subDir : subDirs) {
            for (String name : ggufNames) {
                File internal = new File(context.getFilesDir(), subDir + name);
                if (internal.exists() && internal.length() > 0) {
                    Log.d(TAG, "找到模型(内部): " + internal.getAbsolutePath());
                    return internal.getAbsolutePath();
                }
            }
        }

        for (String name : ggufNames) {
            File external = new File(context.getExternalFilesDir("llm"), name);
            if (external != null && external.exists() && external.length() > 0) {
                Log.d(TAG, "找到模型(外部私有): " + external.getAbsolutePath());
                return external.getAbsolutePath();
            }
        }

        for (String name : ggufNames) {
            File adbFile = new File(ADB_MODEL_DIR + name);
            if (adbFile.exists() && adbFile.length() > 0) {
                File dest = new File(context.getFilesDir(), "llm/" + name);
                try {
                    copyModelToInternal(adbFile, dest);
                    return dest.getAbsolutePath();
                } catch (IOException e) {
                    return adbFile.getAbsolutePath();
                }
            }
            File sdcard = new File("/sdcard/Download/" + name);
            if (sdcard.exists() && sdcard.length() > 0) {
                File dest = new File(context.getFilesDir(), "llm/" + name);
                try {
                    copyModelToInternal(sdcard, dest);
                    return dest.getAbsolutePath();
                } catch (IOException e) {
                    return sdcard.getAbsolutePath();
                }
            }
        }

        return null;
    }

    private void copyModelToInternal(File source, File dest) throws IOException {
        dest.getParentFile().mkdirs();
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(dest);
             FileChannel inChannel = in.getChannel();
             FileChannel outChannel = out.getChannel()) {
            outChannel.transferFrom(inChannel, 0, inChannel.size());
        }
    }

    public float getModelSizeMB() {
        if (modelPath == null) {
            modelPath = resolveModelPath();
        }
        if (modelPath == null) return 0;
        File f = new File(modelPath);
        return f.exists() ? f.length() / (1024f * 1024f) : 0;
    }

    public void release() {
        isReady.set(false);
        if (ggufInference != null) {
            ggufInference.release();
            ggufInference = null;
        }
        executor.shutdown();
    }

    public interface InitCallback {
        void onReady();
        void onError(String error);
    }
}
