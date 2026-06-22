package com.example.tonbo_app;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * 定位 Sherpa-ONNX 流式粤语/普通话/英文 ASR 模型目录。
 */
public final class SherpaOnnxModelLocator {
    private static final String TAG = "SherpaOnnxModelLocator";

    public static final String MODEL_DIR_NAME =
            "sherpa-onnx-streaming-paraformer-trilingual-zh-cantonese-en";
    public static final String ENCODER_FILE = "encoder.int8.onnx";
    public static final String DECODER_FILE = "decoder.int8.onnx";
    public static final String TOKENS_FILE = "tokens.txt";

    private static final String INTERNAL_ROOT = "sherpa_asr";

    private SherpaOnnxModelLocator() {}

    public static boolean isModelReady(Context context) {
        return getInternalModelDir(context) != null
                || resolveExternalModelDir(context) != null
                || hasBundledSherpaAssets(context);
    }

    public static String bundledAssetPrefix() {
        return INTERNAL_ROOT + "/" + MODEL_DIR_NAME;
    }

    private static boolean hasBundledSherpaAssets(Context context) {
        try {
            android.content.res.AssetManager assets = context.getApplicationContext().getAssets();
            String prefix = bundledAssetPrefix();
            return BundledAssetExtractor.assetExists(assets, prefix + "/" + ENCODER_FILE)
                    && BundledAssetExtractor.assetExists(assets, prefix + "/" + DECODER_FILE)
                    && BundledAssetExtractor.assetExists(assets, prefix + "/" + TOKENS_FILE);
        } catch (Exception e) {
            return false;
        }
    }

    private static void extractBundledSherpaToInternal(Context context) throws IOException {
        File internalDir = new File(
                new File(context.getApplicationContext().getFilesDir(), INTERNAL_ROOT), MODEL_DIR_NAME);
        if (isValidDir(internalDir)) {
            return;
        }
        if (!hasBundledSherpaAssets(context)) {
            return;
        }
        android.content.res.AssetManager assets = context.getApplicationContext().getAssets();
        String prefix = bundledAssetPrefix();
        internalDir.mkdirs();
        BundledAssetExtractor.copyAssetIfNeeded(assets, prefix + "/" + ENCODER_FILE,
                new File(internalDir, ENCODER_FILE));
        BundledAssetExtractor.copyAssetIfNeeded(assets, prefix + "/" + DECODER_FILE,
                new File(internalDir, DECODER_FILE));
        BundledAssetExtractor.copyAssetIfNeeded(assets, prefix + "/" + TOKENS_FILE,
                new File(internalDir, TOKENS_FILE));
        Log.i(TAG, "已从 APK 解压 Sherpa 模型: " + internalDir.getAbsolutePath());
    }

    /**
     * 确保模型在 App 内部存储，返回模型目录的绝对路径（从 SD 卡加载时 assetManager 须为 null）。
     */
    public static String ensureInternalModelDir(Context context) throws IOException {
        extractBundledSherpaToInternal(context);

        File internalDir = new File(new File(context.getApplicationContext().getFilesDir(), INTERNAL_ROOT),
                MODEL_DIR_NAME);
        if (!isValidDir(internalDir)) {
            File source = resolveExternalModelDir(context);
            if (source == null) {
                return null;
            }
            internalDir.mkdirs();
            copyIfNeeded(new File(source, ENCODER_FILE), new File(internalDir, ENCODER_FILE));
            copyIfNeeded(new File(source, DECODER_FILE), new File(internalDir, DECODER_FILE));
            copyIfNeeded(new File(source, TOKENS_FILE), new File(internalDir, TOKENS_FILE));
            Log.i(TAG, "已复制 Sherpa 模型到内部存储: " + internalDir.getAbsolutePath());
        }
        if (!isValidDir(internalDir)) {
            return null;
        }
        return internalDir.getAbsolutePath();
    }

    private static File getInternalModelDir(Context context) {
        File internalDir = new File(new File(context.getApplicationContext().getFilesDir(), INTERNAL_ROOT),
                MODEL_DIR_NAME);
        return isValidDir(internalDir) ? internalDir : null;
    }

    private static File resolveExternalModelDir(Context context) {
        Context app = context.getApplicationContext();

        File external = new File(app.getExternalFilesDir(INTERNAL_ROOT), MODEL_DIR_NAME);
        if (isValidDir(external)) {
            return external;
        }

        File adbExternal = new File(
                "/sdcard/Android/data/" + app.getPackageName() + "/files/sherpa_asr/" + MODEL_DIR_NAME);
        if (isValidDir(adbExternal)) {
            return adbExternal;
        }

        File adbTmp = new File("/data/local/tmp/sherpa_asr/" + MODEL_DIR_NAME);
        if (isValidDir(adbTmp)) {
            return adbTmp;
        }

        return null;
    }

    private static boolean isValidDir(File dir) {
        return dir != null
                && dir.isDirectory()
                && new File(dir, ENCODER_FILE).exists()
                && new File(dir, DECODER_FILE).exists()
                && new File(dir, TOKENS_FILE).exists();
    }

    private static void copyIfNeeded(File source, File dest) throws IOException {
        if (!source.exists()) {
            throw new IOException("缺少模型文件: " + source.getAbsolutePath());
        }
        if (dest.exists() && dest.length() == source.length()) {
            return;
        }
        dest.getParentFile().mkdirs();
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(dest);
             FileChannel inChannel = in.getChannel();
             FileChannel outChannel = out.getChannel()) {
            outChannel.transferFrom(inChannel, 0, inChannel.size());
        }
    }
}
