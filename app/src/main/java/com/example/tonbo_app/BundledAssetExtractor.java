package com.example.tonbo_app;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 从 APK assets 解压大模型到内部存储（首次运行或文件不完整时）。
 */
public final class BundledAssetExtractor {
    private static final String TAG = "BundledAssetExtractor";
    private static final int BUFFER_SIZE = 1024 * 256;

    private BundledAssetExtractor() {}

    public static boolean assetExists(AssetManager assets, String assetPath) {
        try (InputStream in = assets.open(assetPath)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static long getAssetSize(AssetManager assets, String assetPath) throws IOException {
        try (AssetFileDescriptor afd = assets.openFd(assetPath)) {
            return afd.getLength();
        } catch (IOException e) {
            long total = 0;
            try (InputStream in = assets.open(assetPath)) {
                byte[] buf = new byte[BUFFER_SIZE];
                int read;
                while ((read = in.read(buf)) > 0) {
                    total += read;
                }
            }
            return total;
        }
    }

    /**
     * 若 assets 中有该文件且目标不存在或大小不一致，则解压。
     *
     * @return true 表示目标文件已就绪（原本就有或刚解压成功）
     */
    public static boolean copyAssetIfNeeded(AssetManager assets, String assetPath, File dest)
            throws IOException {
        if (!assetExists(assets, assetPath)) {
            return false;
        }
        long expectedSize = getAssetSize(assets, assetPath);
        if (dest.exists() && dest.length() == expectedSize) {
            return true;
        }
        File parent = dest.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        Log.i(TAG, "解压 assets → " + dest.getAbsolutePath() + " (" + (expectedSize / (1024 * 1024)) + " MB)");
        try (InputStream in = assets.open(assetPath);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buf)) > 0) {
                out.write(buf, 0, read);
            }
        }
        return dest.exists() && dest.length() == expectedSize;
    }
}
