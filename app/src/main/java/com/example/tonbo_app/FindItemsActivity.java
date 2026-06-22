package com.example.tonbo_app;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class FindItemsActivity extends BaseAccessibleActivity {
    private static final String ZHIPU_API_KEY = "207a4d394f6f46c4b0d6cfb9facdd9d6.57b0ryN8dPE81WIX";
    private static final String API_URL = "https://open.bigmodel.cn/api/paas/v4/chat/completions";
    private static final int PERMISSION_REQUEST_FIND_ITEMS = 101;
    private static final String MODEL_MEMORY_PRIMARY = "glm-4-flash";
    private static final String MODEL_MEMORY_FALLBACK = "glm-4v-flash";
    private static final String MODEL_TITLE = "glm-4-flash";
    private static final String MODEL_VISION = "glm-4v-flash";

    private PreviewView cameraPreview;
    private ImageView capturedImage;
    private TextView statusText;
    private Button btnCapture, btnRetake, btnVoiceAsk, btnMemory;
    private LinearLayout layoutActionButtons;
    private ExecutorService cameraExecutor;
    private ImageCapture imageCapture;
    private Bitmap currentBitmap;

    private SpeechRecognizer speechRecognizer;
    private Intent speechIntent;
    private final OkHttpClient httpClient = new OkHttpClient();
    private boolean isAILoading = false;
    private boolean isMemoryMode = false;
    private final Handler aiTimeoutHandler = new Handler();
    private final Runnable aiTimeoutRunnable = () -> {
        if (isAILoading) {
            isAILoading = false;
            statusText.setText("分析超時，請重試一次。");
            ttsManager.speak("分析超时，请重试。", null, true);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_find_items);
        cameraExecutor = Executors.newSingleThreadExecutor();
        initViews();
        initSpeechEngine();
        checkPermissions();
    }

    private void initViews() {
        cameraPreview = findViewById(R.id.camera_preview);
        capturedImage = findViewById(R.id.captured_image);
        statusText = findViewById(R.id.status_text);
        btnCapture = findViewById(R.id.btn_capture);
        btnVoiceAsk = findViewById(R.id.btn_voice_ask);
        btnRetake = findViewById(R.id.btn_retake);
        btnMemory = findViewById(R.id.btn_memory); // 現在這裡不會報錯了
        layoutActionButtons = findViewById(R.id.layout_action_buttons);

        btnCapture.setOnClickListener(v -> takePhoto());
        btnRetake.setOnClickListener(v -> resetToCamera());
        btnVoiceAsk.setOnClickListener(v -> { isMemoryMode = false; startVoiceInteraction(); });
        btnMemory.setOnClickListener(v -> { isMemoryMode = true; startVoiceInteraction(); });

        if (layoutActionButtons != null) layoutActionButtons.setVisibility(View.GONE);
    }

    // --- 智能保存：自動提取關鍵詞當標題 ---
    private void saveWithAutoTitle(String content) {
        // 發送一個超短請求給 AI 提取標題
        JSONObject json = new JSONObject();
        try {
            json.put("model", MODEL_TITLE);
            JSONArray messages = new JSONArray();
            JSONObject msg = new JSONObject();
            msg.put("role", "user");
            msg.put("content", "將以下描述提取為 5 個字以內的標題： " + content);
            messages.put(msg);
            json.put("messages", messages);

            RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
            Request request = new Request.Builder()
                    .url(API_URL)
                    .header("Authorization", "Bearer " + ZHIPU_API_KEY)
                    .post(body)
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    try {
                        if (response.isSuccessful() && response.body() != null) {
                            try {
                                JSONObject resp = new JSONObject(response.body().string());
                                String title = resp.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
                                actualSave(title, content);
                            } catch (Exception e) {
                                actualSave("未知物品", content);
                            }
                        }
                    } finally {
                        response.close();
                    }
                }

                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    actualSave("識別歷史", content);
                }
            });
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void actualSave(String title, String content) {
        SharedPreferences prefs = getSharedPreferences("AppMemory", MODE_PRIVATE);
        String history = prefs.getString("history", "[]");
        try {
            JSONArray array = new JSONArray(history);
            JSONObject entry = new JSONObject();
            entry.put("title", title.replace("\"", "")); // 清理引號
            entry.put("time", new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date()));
            entry.put("content", content);
            array.put(entry);
            if (array.length() > 20) array.remove(0);
            prefs.edit().putString("history", array.toString()).apply();
        } catch (Exception e) { e.printStackTrace(); }
    }

    // --- 記憶對話邏輯 ---
    private void runMemoryChat(String query) {
        statusText.setText("檢索記憶中...");
        SharedPreferences prefs = getSharedPreferences("AppMemory", MODE_PRIVATE);
        String history = prefs.getString("history", "[]");

        try {
            JSONObject json = new JSONObject();
            json.put("model", MODEL_MEMORY_PRIMARY);
            JSONArray messages = new JSONArray();
            JSONObject sys = new JSONObject();
            sys.put("role", "system");
            sys.put("content", "你是盲人助手的記憶模塊。歷史紀錄(標題與內容)：" + history + "。請回答用戶的問題。");

            JSONObject user = new JSONObject();
            user.put("role", "user");
            user.put("content", query);

            messages.put(sys);
            messages.put(user);
            json.put("messages", messages);

            sendZhipuRequest(json, true, false, query);
        } catch (Exception e) {
            isAILoading = false;
            statusText.setText("請求建立失敗，請重試。");
        }
    }

    // --- 物品識別邏輯 ---
    private void runZhipuAIDetection() {
        if (currentBitmap == null) { isAILoading = false; return; }
        statusText.setText("正在分析畫面...");

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        currentBitmap.compress(Bitmap.CompressFormat.JPEG, 60, os);
        String base64Image = Base64.encodeToString(os.toByteArray(), Base64.NO_WRAP);

        try {
            JSONObject json = new JSONObject();
            json.put("model", MODEL_VISION);
            JSONArray messages = new JSONArray();
            JSONObject msg = new JSONObject();
            msg.put("role", "user");
            JSONArray content = new JSONArray();

            JSONObject tPart = new JSONObject();
            tPart.put("type", "text");
            tPart.put("text", "請簡潔描述正前方的物品。");

            JSONObject iPart = new JSONObject();
            iPart.put("type", "image_url");
            JSONObject iUrl = new JSONObject();
            // 智谱 GLM-4V 等多模态接口要求 data URI 或 https URL，不能裸传 base64
            iUrl.put("url", "data:image/jpeg;base64," + base64Image);
            iPart.put("image_url", iUrl);

            content.put(tPart); content.put(iPart);
            msg.put("content", content);
            messages.put(msg);
            json.put("messages", messages);

            sendZhipuRequest(json, false, false, null);
        } catch (Exception e) {
            isAILoading = false;
            statusText.setText("分析請求失敗，請重試。");
        }
    }

    private void sendZhipuRequest(JSONObject json, boolean isChat, boolean memoryRetried, String memoryQuery) {
        aiTimeoutHandler.removeCallbacks(aiTimeoutRunnable);
        aiTimeoutHandler.postDelayed(aiTimeoutRunnable, 20000);
        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(API_URL).header("Authorization", "Bearer " + ZHIPU_API_KEY).post(body).build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                String res = null;
                String errRes = null;
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        res = response.body().string();
                    } else if (response.body() != null) {
                        errRes = response.body().string();
                    }
                } catch (IOException e) {
                    Log.e("FindItems", "read body", e);
                } finally {
                    response.close();
                }
                final String resFinal = res;
                final String errFinal = errRes;
                runOnUiThread(() -> {
                    try {
                        if (resFinal != null) {
                            JSONObject jobj = new JSONObject(resFinal);
                            String text = jobj.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
                            statusText.setText(text);
                            ttsManager.speak(text, null, true);
                            if (!isChat) saveWithAutoTitle(text);
                        } else if (errFinal != null && !errFinal.isEmpty()) {
                            // 記憶對話遇到主模型配額/可用性問題時，自動降級一次到視覺同檔位模型
                            if (isChat && !memoryRetried) {
                                String low = errFinal.toLowerCase();
                                if (low.contains("insufficient") || low.contains("余额") || low.contains("quota") || low.contains("balance")) {
                                    retryMemoryChatWithFallbackModel(memoryQuery);
                                    return;
                                }
                            }
                            String msg = "分析失敗：" + errFinal;
                            if (msg.length() > 140) {
                                msg = msg.substring(0, 140) + "...";
                            }
                            statusText.setText(msg);
                            ttsManager.speak("分析失败，请检查接口配置后重试。", null, true);
                        } else {
                            statusText.setText("分析失敗，請檢查網路後重試。");
                            ttsManager.speak("分析失败，请检查网络后重试。", null, true);
                        }
                    } catch (Exception e) {
                        Log.e("FindItems", "parse", e);
                        statusText.setText("結果解析失敗，請重試。");
                    } finally {
                        aiTimeoutHandler.removeCallbacks(aiTimeoutRunnable);
                        isAILoading = false;
                    }
                });
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    aiTimeoutHandler.removeCallbacks(aiTimeoutRunnable);
                    isAILoading = false;
                    statusText.setText("網路請求失敗，請重試。");
                    ttsManager.speak("网络请求失败，请重试。", null, true);
                });
            }
        });
    }

    private void retryMemoryChatWithFallbackModel(String query) {
        try {
            JSONObject json = new JSONObject();
            json.put("model", MODEL_MEMORY_FALLBACK);
            JSONArray messages = new JSONArray();
            JSONObject sys = new JSONObject();
            SharedPreferences prefs = getSharedPreferences("AppMemory", MODE_PRIVATE);
            String history = prefs.getString("history", "[]");
            sys.put("role", "system");
            sys.put("content", "你是盲人助手的記憶模塊。歷史紀錄(標題與內容)：" + history + "。請回答用戶的問題。");

            JSONObject user = new JSONObject();
            user.put("role", "user");
            user.put("content", query == null ? "" : query);
            messages.put(sys);
            messages.put(user);
            json.put("messages", messages);
            sendZhipuRequest(json, true, true, query);
        } catch (Exception e) {
            isAILoading = false;
            statusText.setText("記憶對話重試失敗，請稍後再試。");
        }
    }

    // --- 語音、相機等基礎方法（與之前版本一致） ---
    private void initSpeechEngine() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new CustomRecognitionListener());
        speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        String speechLang = "zh-CN";
        if ("english".equals(currentLanguage)) {
            speechLang = "en-US";
        } else if ("cantonese".equals(currentLanguage)) {
            speechLang = "zh-HK";
        }
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechLang);
    }

    private void startVoiceInteraction() {
        if (isAILoading) return;
        vibrationManager.vibrateClick();
        speechRecognizer.cancel();
        new Handler().postDelayed(() -> speechRecognizer.startListening(speechIntent), 100);
    }

    private class CustomRecognitionListener implements RecognitionListener {
        @Override public void onReadyForSpeech(Bundle params) { statusText.setText("請提問..."); ttsManager.speak("請說話", null, true); }
        @Override public void onResults(Bundle results) {
            ArrayList<String> m = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (m != null && !m.isEmpty()) {
                if (!isMemoryMode && currentBitmap == null) {
                    statusText.setText("請先拍照定格畫面，再語音提問。");
                    ttsManager.speak("請先拍照，再提問找物。", null, true);
                    return;
                }
                isAILoading = true;
                if (isMemoryMode) runMemoryChat(m.get(0)); else runZhipuAIDetection();
            }
        }
        @Override public void onError(int error) { isAILoading = false; }
        @Override public void onBeginningOfSpeech() {}
        @Override public void onEndOfSpeech() {}
        @Override public void onRmsChanged(float rmsdB) {}
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onPartialResults(Bundle partialResults) {}
        @Override public void onEvent(int eventType, Bundle params) {}
    }

    private void takePhoto() {
        if (imageCapture == null) return;
        imageCapture.takePicture(cameraExecutor, new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                ByteBuffer b = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[b.remaining()];
                b.get(bytes);
                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                image.close();
                runOnUiThread(() -> {
                    if (bmp == null) {
                        statusText.setText("無法解碼照片，請重試。");
                        ttsManager.speak("拍照失敗，請重試。", null, true);
                        return;
                    }
                    currentBitmap = bmp;
                    capturedImage.setImageBitmap(currentBitmap);
                    cameraPreview.setVisibility(View.GONE);
                    capturedImage.setVisibility(View.VISIBLE);
                    btnCapture.setVisibility(View.GONE);
                    if (layoutActionButtons != null) layoutActionButtons.setVisibility(View.VISIBLE);
                });
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e("FindItems", "capture", exception);
                runOnUiThread(() -> {
                    statusText.setText("拍照失敗，請重試。");
                    ttsManager.speak("拍照失敗。", null, true);
                });
            }
        });
    }

    private void resetToCamera() {
        currentBitmap = null;
        capturedImage.setVisibility(View.GONE);
        cameraPreview.setVisibility(View.VISIBLE);
        btnCapture.setVisibility(View.VISIBLE);
        if (layoutActionButtons != null) layoutActionButtons.setVisibility(View.GONE);
    }

    private void setupCamera() {
        ListenableFuture<ProcessCameraProvider> pf = ProcessCameraProvider.getInstance(this);
        pf.addListener(() -> {
            try {
                ProcessCameraProvider p = pf.get();
                Preview pr = new Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_16_9).build();
                pr.setSurfaceProvider(cameraPreview.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder().setTargetAspectRatio(AspectRatio.RATIO_16_9).build();
                p.unbindAll();
                p.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, pr, imageCapture);
            } catch (Exception e) {}
        }, ContextCompat.getMainExecutor(this));
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, PERMISSION_REQUEST_FIND_ITEMS);
        } else { setupCamera(); }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != PERMISSION_REQUEST_FIND_ITEMS) {
            return;
        }
        boolean cameraOk = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean audioOk = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        if (cameraOk && audioOk) {
            setupCamera();
        } else {
            statusText.setText("需要相機與麥克風權限才能使用查找物品。");
            ttsManager.speak("請在設定中開啟相機與麥克風權限。", null, true);
        }
    }

    @Override
    protected void onDestroy() {
        aiTimeoutHandler.removeCallbacks(aiTimeoutRunnable);
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        if (cameraExecutor != null && !cameraExecutor.isShutdown()) {
            cameraExecutor.shutdown();
        }
        super.onDestroy();
    }

    @Override
    protected void announcePageTitle() {
        if ("english".equals(currentLanguage)) {
            ttsManager.speak("Find items", null, true);
        } else if ("mandarin".equals(currentLanguage)) {
            ttsManager.speak("查找物品", null, true);
        } else {
            ttsManager.speak("尋找物品", null, true);
        }
    }
}