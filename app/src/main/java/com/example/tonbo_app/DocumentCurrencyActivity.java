package com.example.tonbo_app;

import android.Manifest;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.NonNull;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class DocumentCurrencyActivity extends BaseAccessibleActivity {
    private static final String TAG = "DocumentCurrencyAct";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};

    private PreviewView cameraPreview;
    private android.widget.ImageButton backButton;
    private Button flashButton;
    private Button textModeButton;
    private Button currencyModeButton;
    private Button captureButton;
    private Button readButton;
    private Button clearButton;
    private TextView pageTitle;
    private TextView resultsTitle;
    private TextView resultsText;

    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;
    private boolean isFlashOn = false;
    private boolean isAnalyzing = false;

    // 文字分析模式：AI 摘要与“继续读剩余内容”的语音流程状态
    private boolean isTextAiWorkflowRunning = false;
    private boolean canReadRemainingText = false; // AI提问后置为 true
    private int remainingTextStartIndex = 0; // 上次截取后的剩余起始位置（基于 lastFullOcrText）
    private String lastFullOcrText = ""; // 识别到的“全文”（取 OCRResult 第一个条目）
    private String lastTextCategory = "";
    private String lastAiSummary = "";

    // 等待语音回复（需要/不需要、yes/no）流程
    private SpeechRecognizer continueDecisionRecognizer;
    private boolean isWaitingForContinueDecision = false;
    private int continueDecisionRetryCount = 0;
    private final int continueDecisionMaxRetries = 1;
    private final int continueDecisionTimeoutMs = 7000;
    private final Handler continueDecisionHandler = new Handler(Looper.getMainLooper());
    private Runnable continueDecisionTimeoutRunnable;
    private boolean isAutoReadingRemaining = false;
    
    // 分析模式：true=文字分析，false=錢幣分析
    private boolean isTextMode = true;

    // 搖晃檢測（用於切換「文字分析 / 錢幣分析」）
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private ShakeDetector shakeDetector;
    private static final float SHAKE_THRESHOLD = 11.5f;
    private static final int SHAKE_SLOP_TIME_MS = 400;
    private static final int DOUBLE_SHAKE_WINDOW_MS = 2000;
    private static final int REQUIRED_SHAKE_COUNT = 2;
    private long lastShakePeakTime;
    private int shakeCount;
    private final Handler shakeResetHandler = new Handler(Looper.getMainLooper());
    private final Runnable shakeResetRunnable = () -> shakeCount = 0;

    // OCR和貨幣檢測相關變量
    private OCRHelper ocrHelper;
    private CurrencyDetector currencyDetector;
    private Bitmap currentBitmap;
    private int currentRotationDegrees = 0;
    private String lastRecognitionResult = "";
    private List<OCRHelper.OCRResult> lastOCRResults;
    private List<CurrencyDetector.CurrencyResult> lastCurrencyResults;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_document_currency);

        initViews();
        cameraExecutor = Executors.newSingleThreadExecutor();

        // 初始化OCR和貨幣檢測器
        ocrHelper = new OCRHelper(this);
        currencyDetector = new CurrencyDetector(this);

        // 初始化搖晃檢測（切換文字分析 / 錢幣分析）
        setupShakeDetection();

        // 檢查相機權限
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }

    @Override
    protected void announcePageTitle() {
        String pageTitle = getString(R.string.document_title);
        announcePageTitle(pageTitle);

        // 進入閱讀助手時的語音引導：提示文字分析/錢幣分析可透過搖晃切換
        String cantonese = "閱讀助手支持文字分析同錢幣分析功能。"
                + "預設係文字分析。你可以搖晃手機兩下切換兩個模式，我會用語音提示你而家係邊個模式。";
        String mandarin = "阅读助手支持文字分析和钱币分析功能。"
                + "默认是文字分析。你可以摇晃手机两下在两个模式之间切换，我会用语音提示你当前是哪个模式。";
        String english = "Document assistant supports text analysis and currency analysis. "
                + "By default it is in text analysis mode. You can shake your phone twice to switch between the two modes, "
                + "and I will announce which mode you are using.";

        if ("english".equals(currentLanguage)) {
            ttsManager.speak(english, null, true);
        } else if ("mandarin".equals(currentLanguage)) {
            ttsManager.speak(mandarin, null, true);
        } else {
            // cantonese
            ttsManager.speak(cantonese, english, true);
        }

        announceNavigation(getString(R.string.document_started_message));
    }

    private void initViews() {
        cameraPreview = findViewById(R.id.cameraPreview);
        backButton = findViewById(R.id.backButton);
        flashButton = findViewById(R.id.flashButton);
        textModeButton = findViewById(R.id.textModeButton);
        currencyModeButton = findViewById(R.id.currencyModeButton);
        captureButton = findViewById(R.id.captureButton);
        readButton = findViewById(R.id.readButton);
        clearButton = findViewById(R.id.clearButton);
        pageTitle = findViewById(R.id.pageTitle);
        resultsTitle = findViewById(R.id.resultsTitle);
        resultsText = findViewById(R.id.resultsText);

        // 返回按鈕
        backButton.setOnClickListener(v -> {
            handleBackPressed();
        });

        // 閃光燈按鈕
        flashButton.setOnClickListener(v -> {
            vibrationManager.vibrateClick();
            toggleFlash();
        });

        // 文字分析模式按鈕
        textModeButton.setOnClickListener(v -> {
            vibrationManager.vibrateClick();
            switchToTextMode();
        });

        // 錢幣分析模式按鈕
        currencyModeButton.setOnClickListener(v -> {
            vibrationManager.vibrateClick();
            switchToCurrencyMode();
        });


        // 拍照掃描按鈕
        captureButton.setOnClickListener(v -> {
            vibrationManager.vibrateClick();
            captureAndAnalyze();
        });

        // 語音朗讀按鈕
        readButton.setOnClickListener(v -> {
            vibrationManager.vibrateClick();
            speakRecognitionResults();
        });

        // 清除按鈕
        clearButton.setOnClickListener(v -> {
            vibrationManager.vibrateClick();
            clearResults();
        });
        
        // 根據當前語言更新界面文字
        updateLanguageUI();
    }
    
    /**
     * 更新語言UI
     */
    private void updateLanguageUI() {
        if (pageTitle != null) {
            pageTitle.setText(getLocalizedString("document_assistant_title"));
        }
        
        if (textModeButton != null) {
            textModeButton.setText(getLocalizedString("text_analysis"));
        }
        
        if (currencyModeButton != null) {
            currencyModeButton.setText(getLocalizedString("currency_analysis"));
        }
        
        if (captureButton != null) {
            captureButton.setText(getLocalizedString("capture_photo"));
        }
        
        if (readButton != null) {
            readButton.setText(getLocalizedString("voice_read"));
        }
        
        if (clearButton != null) {
            clearButton.setText(getLocalizedString("clear"));
        }

        if (resultsTitle != null) {
            resultsTitle.setText(getLocalizedString("scan_results_title"));
        }
    }
    
    /**
     * 根據當前語言獲取本地化字符串
     */
    private String getLocalizedString(String key) {
        switch (key) {
            case "document_assistant_title":
                if ("english".equals(currentLanguage)) {
                    return "Document Assistant";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "阅读助手";
                } else {
                    return "閱讀助手";
                }
            case "text_analysis":
                if ("english".equals(currentLanguage)) {
                    return "Text Analysis";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "文字分析";
                } else {
                    return "文字分析";
                }
            case "currency_analysis":
                if ("english".equals(currentLanguage)) {
                    return "Currency Analysis";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "货币分析";
                } else {
                    return "錢幣分析";
                }
            case "capture_photo":
                if ("english".equals(currentLanguage)) {
                    return "Capture Photo";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "拍照扫描";
                } else {
                    return "拍照掃描";
                }
            case "voice_read":
                if ("english".equals(currentLanguage)) {
                    return "Voice Read";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "语音朗读";
                } else {
                    return "語音朗讀";
                }
            case "clear":
                if ("english".equals(currentLanguage)) {
                    return "Clear";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "清除";
                } else {
                    return "清除";
                }
            case "result_text_header":
                if ("english".equals(currentLanguage)) {
                    return "📄 Text recognition results:";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "📄 文字识别结果：";
                } else {
                    return "📄 文字識別結果：";
                }
            case "result_currency_header":
                if ("english".equals(currentLanguage)) {
                    return "💰 Currency recognition results:";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "💰 货币识别结果：";
                } else {
                    return "💰 貨幣識別結果：";
                }
            case "result_none":
                if ("english".equals(currentLanguage)) {
                    return "No text or currency recognized";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "未识别到任何文字或货币";
                } else {
                    return "未識別到任何文字或貨幣";
                }
            case "scan_results_title":
                if ("english".equals(currentLanguage)) {
                    return "Scan Results";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "扫描结果";
                } else {
                    return "掃描結果";
                }
            case "analyzing":
                if ("english".equals(currentLanguage)) {
                    return "Analyzing image...";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "正在分析图像...";
                } else {
                    return "正在分析圖像...";
                }
            case "analysis_complete":
                if ("english".equals(currentLanguage)) {
                    return "Analysis complete";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "分析完成";
                } else {
                    return "分析完成";
                }
            default:
                return "";
        }
    }
    
    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                announceError(getString(R.string.camera_permission_message));
                finish();
            }
        }
    }

    private void startCamera() {
        com.google.common.util.concurrent.ListenableFuture<ProcessCameraProvider> cameraProviderFuture = 
            ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (Exception e) {
                Log.e(TAG, "獲取相機提供者失敗: " + e.getMessage());
                announceError("相機啟動失敗");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
        } catch (Exception e) {
            Log.e(TAG, "綁定相機失敗: " + e.getMessage());
            announceError("相機設置失敗");
        }
    }

    private void analyzeImage(ImageProxy image) {
        try {
            currentRotationDegrees = image.getImageInfo() != null ? image.getImageInfo().getRotationDegrees() : 0;
            // 保存當前幀供拍照使用
            currentBitmap = imageProxyToBitmap(image);
            
        } catch (Exception e) {
            Log.e(TAG, "圖像分析失敗: " + e.getMessage());
        } finally {
            image.close();
        }
    }

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

            android.graphics.YuvImage yuvImage = new android.graphics.YuvImage(
                    nv21, android.graphics.ImageFormat.NV21,
                    image.getWidth(), image.getHeight(), null);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            yuvImage.compressToJpeg(
                    new android.graphics.Rect(0, 0, image.getWidth(), image.getHeight()),
                    100, out);
            byte[] imageBytes = out.toByteArray();
            return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        } catch (Exception e) {
            Log.e(TAG, "圖像轉換失敗: " + e.getMessage());
            return null;
        }
    }

    private void captureAndAnalyze() {
        if (isAnalyzing) {
            announceInfo("正在分析中，請稍候");
            return;
        }

        announceInfo(getLocalizedString("analyzing"));
        updateStatus(getLocalizedString("analyzing"));

        if (currentBitmap != null) {
            isAnalyzing = true;
            
            new Thread(() -> {
                try {
                    List<OCRHelper.OCRResult> ocrResults;
                    List<CurrencyDetector.CurrencyResult> currencyResults;

                    if (isTextMode) {
                        // 文字分析模式：只做 OCR
                        ocrResults = ocrHelper.recognizeText(currentBitmap, currentRotationDegrees);
                        currencyResults = java.util.Collections.emptyList();
                    } else {
                        // 貨幣分析模式：只做貨幣檢測
                        ocrResults = java.util.Collections.emptyList();
                        currencyResults = currencyDetector.detectCurrency(currentBitmap, currentRotationDegrees);
                    }

                    // 保存結果
                    lastOCRResults = ocrResults;
                    lastCurrencyResults = currencyResults;

                    // 格式化結果
                    String combinedResult = formatCombinedResults(ocrResults, currencyResults);
                    lastRecognitionResult = combinedResult;

                    runOnUiThread(() -> {
                        updateResults(combinedResult);
                        updateStatus(getLocalizedString("analysis_complete"));
                        int totalItems = ocrResults.size() + currencyResults.size();
                        String itemMsg = String.format(getString(R.string.items_detected), totalItems);
                        announceInfo(getLocalizedString("analysis_complete") + ", " + itemMsg);
                        if (isTextMode) {
                            // 文字模式：先朗读摘录 -> AI分类与总结 -> 追问继续读剩余
                            startTextModeAiReadAndSummarize(ocrResults);
                        } else {
                            // 貨幣模式：保持原有语音播报
                            announceRecognitionSpeech(ocrResults, currencyResults);
                        }
                        // 無論是否啟用 AI 總結，掃描流程此時已完成，允許再次點擊「拍照掃描」
                        isAnalyzing = false;
                    });

                } catch (Exception e) {
                    Log.e(TAG, "分析失敗: " + e.getMessage());
                    runOnUiThread(() -> {
                        updateResults("分析失敗：" + e.getMessage());
                        updateStatus("分析失敗");
                        announceError("分析失敗，請重試");
                        isTextAiWorkflowRunning = false;
                        canReadRemainingText = false;
                        isAnalyzing = false;
                    });
                }
            }).start();
        } else {
            announceInfo("請等待相機準備就緒");
            updateStatus("相機未就緒");
        }
    }

    private String formatCombinedResults(List<OCRHelper.OCRResult> ocrResults, 
                                       List<CurrencyDetector.CurrencyResult> currencyResults) {
        StringBuilder sb = new StringBuilder();

        if (!ocrResults.isEmpty()) {
            sb.append(getLocalizedString("result_text_header")).append("\n\n");
            sb.append(ocrHelper.formatDetailedResults(ocrResults));
            sb.append("\n\n");
        }

        if (!currencyResults.isEmpty()) {
            sb.append(getLocalizedString("result_currency_header")).append("\n\n");
            if ("english".equals(currentLanguage)) {
                sb.append(formatCurrencyResultsEnglish(currencyResults));
            } else {
                sb.append(currencyDetector.formatDetailedResults(currencyResults));
            }
        }

        if (ocrResults.isEmpty() && currencyResults.isEmpty()) {
            sb.append(getLocalizedString("result_none"));
        }

        return sb.toString();
    }

    private void speakRecognitionResults() {
        if (isAnalyzing || isTextAiWorkflowRunning) {
            announceInfo("正在分析中，請稍候");
            return;
        }
        if (lastRecognitionResult.isEmpty()) {
            announceInfo(getString(R.string.not_scanned_yet));
        } else {
            // 文字模式：AI提问后，readButton 用于继续读剩余内容
            if (isTextMode && canReadRemainingText && lastFullOcrText != null && !lastFullOcrText.trim().isEmpty()) {
                isAutoReadingRemaining = false;
                speakRemainingOcrTextChunkOnly();
                return;
            }

            // 使用語音播報主要結果（保持原有逻辑）
            String speechText = "";
            
            if (lastOCRResults != null && !lastOCRResults.isEmpty()) {
                speechText += ocrHelper.formatResultsForSpeech(lastOCRResults);
            }
            
            if (lastCurrencyResults != null && !lastCurrencyResults.isEmpty()) {
                if (!speechText.isEmpty()) {
                    speechText += "。";
                }
                speechText += currencyDetector.formatResultsForSpeech(lastCurrencyResults);
            }
            
            if (speechText.isEmpty()) {
                speechText = getString(R.string.no_content_detected);
            }
            
            // 根據當前語言選擇對應的語音內容
            String cantoneseText = currentLanguage.equals("english") ? translateToChinese(speechText) : speechText;
            String englishText = currentLanguage.equals("english") ? speechText : translateToEnglish(speechText);
            ttsManager.speak(cantoneseText, englishText, true);
        }
    }

    private void clearResults() {
        lastRecognitionResult = "";
        lastOCRResults = null;
        lastCurrencyResults = null;
        lastFullOcrText = "";
        remainingTextStartIndex = 0;
        canReadRemainingText = false;
        lastTextCategory = "";
        lastAiSummary = "";
        isTextAiWorkflowRunning = false;
        isWaitingForContinueDecision = false;
        isAutoReadingRemaining = false;
        continueDecisionRetryCount = 0;
        stopContinueDecisionListening();
        if (resultsText != null) {
            resultsText.setText("");
        }
        announceInfo(getString(R.string.results_cleared));
    }

    private static class AiTextSummaryResult {
        String category;
        String summary;
        String followUp;
    }

    /**
     * 文字模式 AI 朗读流程：
     * 1) 先播一段 OCR 摘录
     * 2) LLM 分类 + 总结，并追问是否继续读完剩余内容
     * 3) 用户点击“语音朗读”后继续读剩余（分段朗读，避免过长）
     */
    private void startTextModeAiReadAndSummarize(List<OCRHelper.OCRResult> ocrResults) {
        isTextAiWorkflowRunning = true;
        canReadRemainingText = false;
        remainingTextStartIndex = 0;
        lastTextCategory = "";
        lastAiSummary = "";

        String fullText = extractMainOcrText(ocrResults);
        if (fullText == null || fullText.trim().isEmpty()) {
            // OCR结果为空：给一个兜底问句
            speakAiFallbackSummaryAndQuestion("", "其他");
            return;
        }

        lastFullOcrText = normalizeWhitespace(fullText);

        // 朗读摘录（先读一部分）
        int excerptChars = 220;
        remainingTextStartIndex = Math.min(excerptChars, lastFullOcrText.length());
        String excerpt = lastFullOcrText.substring(0, remainingTextStartIndex).trim();

        ttsManager.stopSpeaking();

        String cantoneseText = "我先朗读识别到的内容节录：" + excerpt;
        String englishText = "I'll first read a short excerpt of the recognized text: " + excerpt;

        // 语音先读摘录，读完后再发给 LLM
        ttsManager.speak(cantoneseText, englishText, true, () -> {
            if (isFinishing() || isDestroyed()) {
                isTextAiWorkflowRunning = false;
                isAnalyzing = false;
                return;
            }
            requestAiCategoryAndSummary(lastFullOcrText);
        });
    }

    private void requestAiCategoryAndSummary(String fullText) {
        try {
            LLMClient llmClient = LLMClient.getInstance(this);
            if (llmClient != null && llmClient.isEnabled()) {
                String prompt = buildTextAiPrompt(fullText);
                llmClient.sendChatMessage(prompt, null, new LLMClient.ChatCallback() {
                    @Override
                    public void onResponse(String response) {
                        AiTextSummaryResult parsed = parseAiTextSummary(response);
                        if (parsed == null) {
                            parsed = fallbackAiSummary(fullText);
                        }
                        String category = parsed.category != null ? parsed.category : "其他";
                        String summary = parsed.summary != null ? parsed.summary : "";
                        String followUp = parsed.followUp != null ? parsed.followUp : "";
                        if (summary.length() > 100) summary = summary.substring(0, 100) + "...";
                        onAiResultReady(category, summary, followUp);
                    }

                    @Override
                    public void onError(String error) {
                        Log.w(TAG, "LLM text summarize failed: " + error);
                        AiTextSummaryResult fallback = fallbackAiSummary(fullText);
                        onAiResultReady(fallback.category, fallback.summary, fallback.followUp);
                    }
                });
            } else {
                // LLM没启用：走兜底分类+总结
                AiTextSummaryResult fallback = fallbackAiSummary(fullText);
                onAiResultReady(fallback.category, fallback.summary, fallback.followUp);
            }
        } catch (Exception e) {
            Log.e(TAG, "LLM request error: " + e.getMessage());
            AiTextSummaryResult fallback = fallbackAiSummary(fullText);
            onAiResultReady(fallback.category, fallback.summary, fallback.followUp);
        }
    }

    private void onAiResultReady(String category, String summary, String followUp) {
        lastTextCategory = category;
        lastAiSummary = summary;

        // 更新界面（在原 OCR 结果后追加）
        runOnUiThread(() -> {
            String categoryDisplay = toCategoryForCurrentLanguage(category);
            String categoryLabel = currentLanguage.equals("english") ? "Category: " : "类型：";
            String summaryLabel = currentLanguage.equals("english") ? "Summary: " : "要点：";
            String followLabel = currentLanguage.equals("english") ? "Question: " : "提问：";

            String base = lastRecognitionResult != null && !lastRecognitionResult.isEmpty() ? lastRecognitionResult : "";
            String aiBlock = "\n\n" + (currentLanguage.equals("english") ? "AI Summary:\n" : "AI 总结：\n")
                    + categoryLabel + categoryDisplay + "\n"
                    + summaryLabel + summary + "\n"
                    + followLabel + followUp + "\n";
            updateResults(base + aiBlock);
        });

        // 语音播报：AI总结/分类 + 追问是否继续读完
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                isTextAiWorkflowRunning = false;
                isAnalyzing = false;
                return;
            }
            String chineseLead = buildAiCategoryLead(category);
            String cantoneseSpeech = chineseLead
                    + "我幫你總結：" + (summary == null || summary.isEmpty() ? "（未能生成总结）" : summary)
                    + (followUp == null || followUp.isEmpty() ? buildFallbackFollowUp(category) : followUp);

            String englishSpeech = "This might be " + toCategoryForCurrentLanguage(category, true) + ". "
                    + "Summary: " + (summary == null || summary.isEmpty() ? "(no summary)" : summary)
                    + (followUp == null || followUp.isEmpty() ? " Would you like me to continue reading the rest?" : (" " + followUp));

            ttsManager.speak(cantoneseSpeech, englishSpeech, true, () -> {
                // 开始等待用户语音回复（需要/不需要、yes/no）
                isAnalyzing = false;
                startContinueReadDecisionListening(category);
            });
        });
    }

    private void speakAiFallbackSummaryAndQuestion(String fullText, String category) {
        AiTextSummaryResult fallback = fallbackAiSummary(fullText);
        lastTextCategory = fallback.category;
        lastAiSummary = fallback.summary;

        String categoryDisplay = toCategoryForCurrentLanguage(fallback.category);
        String cantoneseSpeech;
        String englishSpeech;

        if ("english".equals(currentLanguage)) {
            cantoneseSpeech = "";
            englishSpeech = "This might be " + toCategoryForCurrentLanguage(fallback.category, true) + ". "
                    + "Summary: " + fallback.summary + ". "
                    + "Would you like me to continue reading the rest?";
        } else if ("mandarin".equals(currentLanguage)) {
            cantoneseSpeech = buildAiCategoryLead(fallback.category)
                    + "我帮你总结：" + fallback.summary
                    + buildFallbackFollowUp(fallback.category);
            englishSpeech = null;
        } else {
            cantoneseSpeech = buildAiCategoryLead(fallback.category)
                    + "我幫你總結：" + fallback.summary
                    + buildFallbackFollowUp(fallback.category);
            englishSpeech = null;
        }

        runOnUiThread(() -> {
            ttsManager.speak(cantoneseSpeech, englishSpeech, true, () -> {
                isAnalyzing = false;
                startContinueReadDecisionListening(category);
            });
        });
    }

    private void startContinueReadDecisionListening(String category) {
        if (!isTextMode) return;
        if (isWaitingForContinueDecision) return;

        // 开始等待语音回复：需要/不需要（或 yes/no）
        isWaitingForContinueDecision = true;
        canReadRemainingText = false;

        // 震动提示：告诉用户现在可以说话了
        vibrationManager.vibrateNotification();

        try {
            if (continueDecisionRecognizer == null) {
                continueDecisionRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            }

            // 超时处理
            if (continueDecisionTimeoutRunnable != null) {
                continueDecisionHandler.removeCallbacks(continueDecisionTimeoutRunnable);
            }
            continueDecisionTimeoutRunnable = () -> {
                if (!isWaitingForContinueDecision) return;
                isWaitingForContinueDecision = false;

                // 没听到：给第二次机会
                if (continueDecisionRetryCount < continueDecisionMaxRetries) {
                    continueDecisionRetryCount++;
                    String retryMsg = englishOnlySpeech("I didn't catch that. Please say yes or no.", "我沒有聽清楚。請說需要或不需要。");
                    announceInfo(retryMsg);
                    startContinueReadDecisionListening(category);
                    return;
                }

                // 最终兜底：允许用户手动按“语音朗读”继续
                canReadRemainingText = true;
                isWaitingForContinueDecision = false;
                isAutoReadingRemaining = false;
                isTextAiWorkflowRunning = false;
                announceInfo(currentLanguage.equals("english") ? "If you want to continue, press voice read." : "如果你想继续读完，请按语音朗读。");
            };
            continueDecisionHandler.postDelayed(continueDecisionTimeoutRunnable, continueDecisionTimeoutMs);

            continueDecisionRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {
                    Log.d(TAG, "Continue decision: ready for speech");
                    vibrationManager.vibrateClick();
                }
                @Override public void onBeginningOfSpeech() { }
                @Override public void onRmsChanged(float rmsdB) { }
                @Override public void onBufferReceived(byte[] buffer) { }
                @Override public void onEndOfSpeech() { }
                @Override public void onError(int error) {
                    Log.w(TAG, "Continue decision error: " + error);
                    stopContinueDecisionListening();
                    if (continueDecisionRetryCount < continueDecisionMaxRetries) {
                        continueDecisionRetryCount++;
                        startContinueReadDecisionListening(category);
                    } else {
                        canReadRemainingText = true;
                        isTextAiWorkflowRunning = false;
                    }
                }
                @Override public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    String recognized = (matches != null && !matches.isEmpty()) ? matches.get(0) : "";
                    Log.d(TAG, "Continue decision recognized: " + recognized);
                    handleContinueDecisionRecognized(recognized);
                }
                @Override public void onPartialResults(Bundle partialResults) { }
                @Override public void onEvent(int eventType, Bundle params) { }
            });

            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);

            Locale locale;
            if ("english".equals(currentLanguage)) {
                locale = Locale.ENGLISH;
            } else if ("mandarin".equals(currentLanguage)) {
                locale = Locale.SIMPLIFIED_CHINESE;
            } else {
                locale = new Locale("zh", "HK");
            }

            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale);
            intent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, locale);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);

            continueDecisionRecognizer.startListening(intent);
            Log.d(TAG, "Continue decision listening started, language=" + currentLanguage);
        } catch (Exception e) {
            Log.e(TAG, "startContinueReadDecisionListening failed: " + e.getMessage());
            stopContinueDecisionListening();
            canReadRemainingText = true;
            isTextAiWorkflowRunning = false;
        }
    }

    private void handleContinueDecisionRecognized(String recognizedText) {
        if (!isWaitingForContinueDecision) return;

        stopContinueDecisionListening();

        String cleaned = normalizeRecognizedText(recognizedText);
        boolean yes = isAffirmative(cleaned);
        boolean no = isNegative(cleaned);

        if (yes) {
            vibrationManager.vibrateSuccessPattern();
            isAutoReadingRemaining = true;
            isTextAiWorkflowRunning = true;
            canReadRemainingText = false;
            speakRemainingOcrTextAuto();
        } else if (no) {
            vibrationManager.vibrateErrorPattern();
            isAutoReadingRemaining = false;
            canReadRemainingText = false;
            isTextAiWorkflowRunning = false;
            if ("english".equals(currentLanguage)) {
                announceInfo("Okay. I won't continue reading.");
            } else if ("mandarin".equals(currentLanguage)) {
                announceInfo("好的。我不继续读完了。");
            } else {
                announceInfo("好嘅。我唔會繼續讀完。");
            }
        } else {
            // 不确定：允许一次重试
            vibrationManager.vibrateNotification();
            if (continueDecisionRetryCount < continueDecisionMaxRetries) {
                continueDecisionRetryCount++;
                isTextAiWorkflowRunning = true;
                String retryMsg = currentLanguage.equals("english")
                        ? "Please say yes or no."
                        : "請說需要或不需要。";
                announceInfo(retryMsg);
                startContinueReadDecisionListening(lastTextCategory);
            } else {
                canReadRemainingText = true;
                isAutoReadingRemaining = false;
                isTextAiWorkflowRunning = false;
                announceInfo(currentLanguage.equals("english") ? "If you want to continue, press voice read." : "如果你想继续读完，请按语音朗读。");
            }
        }
    }

    private void stopContinueDecisionListening() {
        isWaitingForContinueDecision = false;
        if (continueDecisionTimeoutRunnable != null) {
            continueDecisionHandler.removeCallbacks(continueDecisionTimeoutRunnable);
        }

        try {
            if (continueDecisionRecognizer != null) {
                continueDecisionRecognizer.stopListening();
            }
        } catch (Exception ignored) { }
    }

    private String normalizeRecognizedText(String text) {
        if (text == null) return "";
        String s = text.trim();
        if ("english".equals(currentLanguage)) {
            s = s.toLowerCase(Locale.ROOT);
            s = s.replaceAll("[\\p{Punct}\\s]+", " ").trim();
        } else {
            // 中文：去掉空格/标点，做简繁差异容错
            s = s.replaceAll("[\\p{Punct}\\s]+", "");
            s = s.replace("不要", "不需要").replace("唔不要", "唔需要");
        }
        return s;
    }

    private boolean isAffirmative(String s) {
        if (s == null) return false;
        if (s.isEmpty()) return false;
        if ("english".equals(currentLanguage)) {
            return s.contains("yes") || s.contains("yeah") || s.contains("yep") || s.contains("sure") || s.contains("continue");
        }
        // 中文：需要/要/可以/继续
        return s.contains("需要") || s.contains("要") || s.contains("可以") || s.contains("繼續") || s.contains("继续") || s.contains("係") || s.contains("好");
    }

    private boolean isNegative(String s) {
        if (s == null) return false;
        if (s.isEmpty()) return false;
        if ("english".equals(currentLanguage)) {
            return s.contains("no") || s.contains("nope") || s.contains("nah") || s.contains("stop");
        }
        // 中文：不需要/不要/唔要/唔使/不用/停止
        return s.contains("不需要") || s.contains("不要") || s.contains("唔需要") || s.contains("唔要") || s.contains("不用") || s.contains("唔使") || s.contains("停止") || s.contains("唔繼續") || s.contains("唔继续");
    }

    private String englishOnlySpeech(String english, String chinese) {
        return currentLanguage.equals("english") ? english : chinese;
    }

    private void speakRemainingOcrTextAuto() {
        speakRemainingOcrTextChunkOnlyWithCallback(true);
    }

    private void speakRemainingOcrTextChunkOnly() {
        speakRemainingOcrTextChunkOnlyWithCallback(false);
    }

    private void speakRemainingOcrTextChunkOnlyWithCallback(boolean auto) {
        if (lastFullOcrText == null || lastFullOcrText.trim().isEmpty()) {
            announceInfo("沒有內容可朗讀");
            canReadRemainingText = false;
            return;
        }

        int maxChunk = 900;
        if (remainingTextStartIndex < 0) remainingTextStartIndex = 0;
        if (remainingTextStartIndex >= lastFullOcrText.length()) {
            announceInfo(currentLanguage.equals("english") ? "No more text." : "没有更多内容");
            canReadRemainingText = false;
            isAutoReadingRemaining = false;
            isTextAiWorkflowRunning = false;
            return;
        }

        int end = Math.min(lastFullOcrText.length(), remainingTextStartIndex + maxChunk);
        String chunk = lastFullOcrText.substring(remainingTextStartIndex, end).trim();
        remainingTextStartIndex = end;

        if (remainingTextStartIndex >= lastFullOcrText.length()) {
            canReadRemainingText = false;
        }

        String cantoneseSpeech = "接下來繼續讀剩下嘅內容：" + chunk;
        String englishSpeech = "Continuing with the rest of the recognized text: " + chunk;
        ttsManager.speak(cantoneseSpeech, englishSpeech, true, () -> {
            if (!auto) {
                isTextAiWorkflowRunning = false;
                return;
            }
            // 自动读完剩余内容
            if (remainingTextStartIndex < lastFullOcrText.length()) {
                speakRemainingOcrTextChunkOnlyWithCallback(true);
            } else {
                isAutoReadingRemaining = false;
                isTextAiWorkflowRunning = false;
                announceInfo(currentLanguage.equals("english") ? "Finished reading." : "已读完剩余内容");
            }
        });
    }

    private String extractMainOcrText(List<OCRHelper.OCRResult> ocrResults) {
        if (ocrResults == null || ocrResults.isEmpty()) return "";
        OCRHelper.OCRResult main = ocrResults.get(0);
        return main.getText();
    }

    private String normalizeWhitespace(String text) {
        if (text == null) return "";
        return text.replaceAll("\\s+", " ").trim();
    }

    private String buildTextAiPrompt(String fullText) {
        // 截断，避免 prompt 太长导致网络慢/失败
        int maxChars = 2000;
        String truncated = fullText;
        if (truncated.length() > maxChars) {
            truncated = truncated.substring(0, maxChars) + "...";
        }

        String[] categories = new String[]{"报告", "故事", "新闻", "说明书", "信件", "教程", "诗歌", "对话", "公告", "其他"};

        String categoriesLine = String.join("、", categories);

        if ("english".equals(currentLanguage)) {
            return "You are a helpful assistant for visually impaired users. "
                    + "Classify the OCR text into exactly ONE category from: " + categoriesLine + ". "
                    + "Then provide a short summary (<=80 Chinese characters if possible) and a question to ask the user if they want to continue reading the rest. "
                    + "Return STRICT JSON only (no markdown), with keys: category, summary, follow_up. "
                    + "The follow_up must contain: \"Would you like me to continue reading the rest?\".\n\n"
                    + "OCR_TEXT:\n" + truncated;
        }

        // Cantonese/Mandarin: system prompt will ensure voice language, but we still enforce JSON keys.
        return "你是視障用戶語音助手。請把以下OCR文字判斷成「最像」的類別：只能从以下类别中選一個："
                + categoriesLine + "。"
                + "然后用一句话总结要点（尽量精简，不要编造原文不存在的内容）。"
                + "最后給出追问：需要帮你继续读完剩下的内容吗？"
                + "请严格输出 STRICT JSON（不要用Markdown或多余文字），键名固定为：category, summary, follow_up。\n\n"
                + "OCR_TEXT:\n" + truncated;
    }

    private AiTextSummaryResult parseAiTextSummary(String response) {
        if (response == null) return null;
        String raw = response.trim();

        // 尽量提取 JSON 段
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            raw = raw.substring(start, end + 1);
        }

        try {
            Gson gson = new Gson();
            JsonObject obj = gson.fromJson(raw, JsonObject.class);
            if (obj == null) return null;

            AiTextSummaryResult result = new AiTextSummaryResult();
            if (obj.has("category")) result.category = obj.get("category").getAsString();
            if (obj.has("summary")) result.summary = obj.get("summary").getAsString();
            if (obj.has("follow_up")) result.followUp = obj.get("follow_up").getAsString();

            if (result.category == null || result.summary == null || result.followUp == null) {
                return null;
            }
            return result;
        } catch (Exception e) {
            Log.w(TAG, "parseAiTextSummary failed: " + e.getMessage());
            return null;
        }
    }

    private AiTextSummaryResult fallbackAiSummary(String fullText) {
        AiTextSummaryResult result = new AiTextSummaryResult();
        String category = guessTextCategory(fullText);
        result.category = category;
        result.summary = extractFirstSentences(fullText, 2, 90);
        result.followUp = buildFallbackFollowUp(category);
        return result;
    }

    private String buildFallbackFollowUp(String category) {
        // follow_up 只负责提问句；“这也许是一份...”由 onAiResultReady 拼接
        if ("english".equals(currentLanguage)) {
            return "Would you like me to continue reading the rest?";
        } else if ("mandarin".equals(currentLanguage)) {
            return "需要帮你继续读完剩下的内容吗？";
        } else {
            return "需要幫你繼續讀完剩下嘅內容嗎？";
        }
    }

    private String guessTextCategory(String text) {
        if (text == null) return "其他";
        String t = text.trim();

        // 简单关键词规则：不追求绝对准确，但可用作 LLM 不可用时的兜底
        String[] reportKeys = new String[]{"摘要", "结论", "报告", "研究", "数据", "目的", "方法", "method", "conclusion"};
        for (String k : reportKeys) if (t.contains(k)) return "报告";

        String[] newsKeys = new String[]{"新闻", "报道", "记者", "快讯", "新华社", "news", "report"};
        for (String k : newsKeys) if (t.contains(k)) return "新闻";

        String[] letterKeys = new String[]{"亲爱的", "敬", "致", "信", "to:", "dear", "sincerely"};
        for (String k : letterKeys) if (t.contains(k)) return "信件";

        String[] announceKeys = new String[]{"公告", "通知", "发布", "紧急", "announcement", "notice"};
        for (String k : announceKeys) if (t.contains(k)) return "公告";

        String[] instructionKeys = new String[]{"说明", "用法", "注意", "步骤", "安装", "警告", "手册", "manual", "instructions"};
        for (String k : instructionKeys) if (t.contains(k)) return "说明书";

        String[] tutorialKeys = new String[]{"教程", "首先", "然后", "接着", "建议", "如何", "how", "guide"};
        for (String k : tutorialKeys) if (t.contains(k)) return "教程";

        String[] poetryKeys = new String[]{"诗", "诗歌", "韵", "诗句", "poem"};
        for (String k : poetryKeys) if (t.contains(k)) return "诗歌";

        String[] dialogueKeys = new String[]{"：", ":", "说", "回答", "对话", "\"", "“", "”"};
        int dialogueHits = 0;
        for (String k : dialogueKeys) if (t.contains(k)) dialogueHits++;
        if (dialogueHits >= 2) return "对话";

        String[] storyKeys = new String[]{"他", "她", "他们", "突然", "故事", "情节", "转折", "小说", "故事里"};
        for (String k : storyKeys) if (t.contains(k)) return "故事";

        return "其他";
    }

    private String extractFirstSentences(String text, int maxSentences, int maxChars) {
        if (text == null) return "";
        String cleaned = normalizeWhitespace(text);
        if (cleaned.isEmpty()) return "";

        String[] parts = cleaned.split("(?<=[。！？!?])");
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String p : parts) {
            if (p == null) continue;
            String seg = p.trim();
            if (seg.isEmpty()) continue;
            sb.append(seg);
            count++;
            if (count >= maxSentences) break;
            if (sb.length() >= maxChars) break;
        }

        String out = sb.toString().trim();
        if (out.length() > maxChars) out = out.substring(0, maxChars) + "...";
        return out;
    }

    private String toCategoryForCurrentLanguage(String categorySimplified) {
        return toCategoryForCurrentLanguage(categorySimplified, false);
    }

    private String toCategoryForCurrentLanguage(String categorySimplified, boolean englishOnly) {
        if (englishOnly) {
            // 仅用于英文语音的 category 词
            if ("报告".equals(categorySimplified)) return "a report";
            if ("故事".equals(categorySimplified)) return "a story";
            if ("新闻".equals(categorySimplified)) return "news";
            if ("说明书".equals(categorySimplified)) return "a manual";
            if ("信件".equals(categorySimplified)) return "a letter";
            if ("教程".equals(categorySimplified)) return "a tutorial";
            if ("诗歌".equals(categorySimplified)) return "a poem";
            if ("对话".equals(categorySimplified)) return "a dialogue";
            if ("公告".equals(categorySimplified)) return "an announcement";
            return "other";
        }

        if ("english".equals(currentLanguage)) {
            return toCategoryForCurrentLanguage(categorySimplified, true);
        }
        if ("mandarin".equals(currentLanguage)) {
            return categorySimplified;
        }
        // cantonese: 简繁转换（仅覆盖分类词）
        switch (categorySimplified) {
            case "报告": return "報告";
            case "新闻": return "新聞";
            case "说明书": return "說明書";
            case "诗歌": return "詩歌";
            case "对话": return "對話";
            default: return categorySimplified;
        }
    }

    private String buildAiCategoryLead(String categorySimplified) {
        // 返回含语气与标点的“这也许是…”句首，用于拼接摘要
        if ("故事".equals(categorySimplified)) {
            return "mandarin".equals(currentLanguage) ? "这也许是一个故事。" : "這也許係一個故事。";
        }
        if ("诗歌".equals(categorySimplified)) {
            return "mandarin".equals(currentLanguage) ? "这也许是一首诗歌。" : "這也許係一首詩歌。";
        }
        if ("对话".equals(categorySimplified)) {
            return "mandarin".equals(currentLanguage) ? "这也许是一段对话。" : "這也許係一段對話。";
        }
        if ("新闻".equals(categorySimplified)) {
            return "mandarin".equals(currentLanguage) ? "这也许是一则新闻。" : "這也許係一則新聞。";
        }
        // 其它默认“一份”
        if ("mandarin".equals(currentLanguage)) {
            return "这也许是一份" + toCategoryForCurrentLanguage(categorySimplified) + "。";
        }
        return "這也許係一份" + toCategoryForCurrentLanguage(categorySimplified) + "。";
    }

    private void announceRecognitionSpeech(List<OCRHelper.OCRResult> ocrResults,
                                           List<CurrencyDetector.CurrencyResult> currencyResults) {
        if ((ocrResults == null || ocrResults.isEmpty()) && (currencyResults == null || currencyResults.isEmpty())) {
            String mandarin = "未识别到任何文字或货币";
            String cantonese = "未識別到任何文字或貨幣";
            String english = "No text or currency detected";
            ttsManager.speak(cantonese, english, true);
            return;
        }

        // 生成货币识别结果优先语音
        String mandarin;
        String cantonese;
        String english;

        if (currencyResults != null && !currencyResults.isEmpty()) {
            CurrencyDetector.CurrencyResult r = currencyResults.get(0);
            // 中文直接用名称避免重复金额（例如：一百元港币）
            mandarin = String.format("识别到 %s", r.getName());
            cantonese = String.format("識別到 %s", r.getName());
            english = String.format("detected %s dollar", r.getAmount());
        } else {
            // 对于文字分析结果，简单读第一条文字内容
            OCRHelper.OCRResult r = ocrResults.get(0);
            mandarin = "识别到文字：" + r.getText();
            cantonese = "識別到文字：" + r.getText();
            english = "Detected text: " + r.getText();
        }

        ttsManager.speak(cantonese, english, true);
    }

    private String translateCurrencyDetailsToEnglish(String text) {
        return text
                .replace("識別到", "Detected")
                .replace("種貨幣：", " currency types:\n\n")
                .replace("面額：", "Amount: ")
                .replace("元", " dollars")
                .replace("類型：", "Type: ")
                .replace("顏色：", "Color: ")
                .replace("圖案：", "Design: ")
                .replace("置信度：", "Confidence: ")
                .replace("識別方式：", "Detection method: ")
                .replace("未識別到任何貨幣", "No currency detected");
    }

    private String formatCurrencyResultsEnglish(List<CurrencyDetector.CurrencyResult> results) {
        if (results.isEmpty()) {
            return "No currency detected";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Detected %d currency types:\n\n", results.size()));

        for (int i = 0; i < results.size(); i++) {
            CurrencyDetector.CurrencyResult result = results.get(i);
            String englishName = translateCurrencyNameToEnglish(result.getName());
            sb.append(String.format("%d. %s\n", i + 1, englishName));
            sb.append(String.format("   Amount: %s dollars\n", result.getAmount()));
            sb.append(String.format("   Type: %s\n", translateCurrencyTypeToEnglish(result.getType())));
            // 不显示 Color/Design
            sb.append(String.format("   Confidence: %.0f%%\n", result.getConfidence() * 100));
            sb.append(String.format("   Detection method: %s\n\n", translateDetectionMethodToEnglish(result.getDetectionMethod())));
        }

        return sb.toString();
    }

    private String translateCurrencyNameToEnglish(String name) {
        switch (name) {
            case "一元港幣": return "HKD 1";
            case "二元港幣": return "HKD 2";
            case "五元港幣": return "HKD 5";
            case "十元港幣": return "HKD 10";
            case "二十元港幣": return "HKD 20";
            case "五十元港幣": return "HKD 50";
            case "一百元港幣": return "HKD 100";
            case "五百元港幣": return "HKD 500";
            case "一千元港幣": return "HKD 1000";
            default:
                if (name.contains("港幣")) {
                    return name.replace("港幣", "HKD");
                }
                return name;
        }
    }

    private String translateCurrencyTypeToEnglish(String type) {
        if ("紙幣".equals(type)) return "banknote";
        if ("硬幣".equals(type)) return "coin";
        return type;
    }

    private String translateCurrencyColorToEnglish(String color) {
        switch (color) {
            case "銀色": return "Silver";
            case "綠色": return "Green";
            case "藍色": return "Blue";
            case "紫色": return "Purple";
            case "紅色": return "Red";
            case "棕色": return "Brown";
            case "金色": return "Gold";
            default: return color;
        }
    }

    private String translateCurrencyDesignToEnglish(String design) {
        if ("紫荊花".equals(design)) return "Bauhinia";
        if ("獅子山".equals(design)) return "Lion Rock";
        return design;
    }

    private String translateDetectionMethodToEnglish(String method) {
        if ("文字識別".equals(method)) return "Text recognition";
        if ("貨幣符號識別".equals(method)) return "Currency symbol recognition";
        if ("圖像分析".equals(method)) return "Image analysis";
        return method;
    }

    private void updateStatus(String status) {
        // 狀態更新現在通過語音播報
        announceInfo(status);
    }

    private void updateResults(String results) {
        if (resultsText != null) {
            resultsText.setText(results);
        }
    }

    private void toggleFlash() {
        if (cameraProvider != null) {
            Camera camera = cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA);
            if (camera.getCameraInfo().hasFlashUnit()) {
                isFlashOn = !isFlashOn;
                camera.getCameraControl().enableTorch(isFlashOn);
                if (isFlashOn) {
                    flashButton.setText("💡");
                    announceInfo("閃光燈已開啟");
                } else {
                    flashButton.setText("💡");
                    announceInfo("閃光燈已關閉");
                }
            } else {
                announceInfo("此設備不支持閃光燈");
            }
        }
    }

    // 切換到文字分析模式
    private void switchToTextMode() {
        isTextMode = true;
        updateModeUI();
        speakModeSwitchedToText();
    }

    // 切換到錢幣分析模式
    private void switchToCurrencyMode() {
        isTextMode = false;
        updateModeUI();
        speakModeSwitchedToCurrency();
    }

    // 更新模式UI
    private void updateModeUI() {
        if (isTextMode) {
            // 文字模式
            textModeButton.setBackgroundResource(R.drawable.button_modern_background);
            currencyModeButton.setBackgroundResource(R.drawable.button_emergency_background);
        } else {
            // 錢幣模式
            textModeButton.setBackgroundResource(R.drawable.button_emergency_background);
            currencyModeButton.setBackgroundResource(R.drawable.button_modern_background);
        }
    }

    // 簡單的翻譯方法
    private String translateToEnglish(String chinese) {
        return chinese
                .replace("識別到", "Recognized:")
                .replace("文字", "text")
                .replace("貨幣", "currency")
                .replace("港幣", "Hong Kong Dollar")
                .replace("元", "dollars")
                .replace("紙幣", "banknote")
                .replace("硬幣", "coin");
    }

    /**
     * 初始化搖晃檢測，用於在文字分析 / 錢幣分析之間切換
     */
    private void setupShakeDetection() {
        try {
            sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            if (sensorManager == null) {
                Log.w(TAG, "搖晃檢測：無法獲取 SensorManager");
                return;
            }

            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (accelerometer == null) {
                Log.w(TAG, "搖晃檢測：設備不支持加速度感測器");
                return;
            }

            shakeDetector = new ShakeDetector();
            Log.d(TAG, "搖晃檢測已啟動");
        } catch (Exception e) {
            Log.e(TAG, "搖晃檢測初始化失敗: " + e.getMessage());
        }
    }

    /**
     * 檢測到一次有效搖晃峰值；連續搖晃兩次後才切換模式
     */
    private void onShakePeakDetected() {
        long now = System.currentTimeMillis();
        if (now - lastShakePeakTime < SHAKE_SLOP_TIME_MS) {
            return;
        }
        lastShakePeakTime = now;

        shakeCount++;
        vibrationManager.vibrateClick();
        Log.d(TAG, "搖晃計數: " + shakeCount + "/" + REQUIRED_SHAKE_COUNT);

        if (shakeCount == 1) {
            shakeResetHandler.removeCallbacks(shakeResetRunnable);
            shakeResetHandler.postDelayed(shakeResetRunnable, DOUBLE_SHAKE_WINDOW_MS);
        } else if (shakeCount >= REQUIRED_SHAKE_COUNT) {
            shakeResetHandler.removeCallbacks(shakeResetRunnable);
            shakeCount = 0;
            switchModeByShake();
        }
    }

    /**
     * 連續搖晃兩次後切換模式，並進行語音播報
     */
    private void switchModeByShake() {
        if (isTextMode) {
            switchToCurrencyMode();
        } else {
            switchToTextMode();
        }
    }

    private void speakModeSwitchedToCurrency() {
        if ("english".equals(currentLanguage)) {
            ttsManager.speak("Switched to currency analysis mode.", null, true);
        } else if ("mandarin".equals(currentLanguage)) {
            ttsManager.speak("已切换到钱币分析模式。", "已切換到錢幣分析模式。", true);
        } else {
            // cantonese
            ttsManager.speak("已切換到錢幣分析模式。", "已切換到錢幣分析模式。", true);
        }
    }

    private void speakModeSwitchedToText() {
        if ("english".equals(currentLanguage)) {
            ttsManager.speak("Switched to text analysis mode.", null, true);
        } else if ("mandarin".equals(currentLanguage)) {
            ttsManager.speak("已切换到文字分析模式。", "已切換到文字分析模式。", true);
        } else {
            ttsManager.speak("已切換到文字分析模式。", "已切換到文字分析模式。", true);
        }
    }

    private class ShakeDetector implements SensorEventListener {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event == null || event.sensor == null) return;
            if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;

            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            float acceleration = (float) Math.sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH;
            if (Math.abs(acceleration) > SHAKE_THRESHOLD) {
                Log.d(TAG, "搖晃檢測觸發: " + acceleration);
                runOnUiThread(DocumentCurrencyActivity.this::onShakePeakDetected);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // no-op
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        if (ocrHelper != null) {
            ocrHelper.close();
        }
        if (currencyDetector != null) {
            currencyDetector.close();
        }

        if (sensorManager != null && shakeDetector != null) {
            try {
                sensorManager.unregisterListener(shakeDetector);
            } catch (Exception ignored) {}
        }
        shakeResetHandler.removeCallbacks(shakeResetRunnable);
        shakeCount = 0;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }

        if (sensorManager != null && shakeDetector != null) {
            try {
                sensorManager.unregisterListener(shakeDetector);
            } catch (Exception ignored) {}
        }
        shakeResetHandler.removeCallbacks(shakeResetRunnable);
        shakeCount = 0;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (allPermissionsGranted()) {
            startCamera();
        }

        if (sensorManager != null && accelerometer != null && shakeDetector != null) {
            try {
                sensorManager.registerListener(shakeDetector, accelerometer, SensorManager.SENSOR_DELAY_UI);
            } catch (Exception e) {
                Log.e(TAG, "搖晃檢測恢復失敗: " + e.getMessage());
            }
        }
    }


    /**
     * 將英文描述翻譯為中文
     */
    private String translateToChinese(String english) {
        // 簡單的翻譯映射
        return english
                .replace("No content detected", "未識別到任何內容")
                .replace("Text recognized", "識別到文字")
                .replace("Currency detected", "檢測到貨幣")
                .replace("Scanning", "掃描中")
                .replace("Ready to scan", "準備掃描")
                .replace("Results cleared", "結果已清除")
                .replace("Camera permission needed", "需要相機權限")
                .replace("Text content", "文字內容")
                .replace("Currency amount", "貨幣金額")
                .replace("Document", "文件")
                .replace("Menu", "菜單")
                .replace("Banknote", "紙幣")
                .replace("Coin", "硬幣")
                .replace("Dollar", "美元")
                .replace("Euro", "歐元")
                .replace("Yuan", "人民幣")
                .replace("Yen", "日圓")
                .replace("Pound", "英鎊")
                .replace("Franc", "法郎")
                .replace("Mark", "馬克")
                .replace("Lira", "里拉")
                .replace("Peseta", "比塞塔")
                .replace("Guilder", "荷蘭盾")
                .replace("Crown", "克朗")
                .replace("Krone", "克朗")
                .replace("Krona", "克朗")
                .replace("Rupee", "盧比")
                .replace("Peso", "比索")
                .replace("Real", "雷亞爾")
                .replace("Rand", "蘭特")
                .replace("Dinar", "第納爾")
                .replace("Dirham", "迪拉姆")
                .replace("Riyal", "里亞爾")
                .replace("Ruble", "盧布")
                .replace("Hryvnia", "格里夫納")
                .replace("Zloty", "茲羅提")
                .replace("Forint", "福林")
                .replace("Koruna", "克朗")
                .replace("Leu", "列伊")
                .replace("Lev", "列弗")
                .replace("Kuna", "庫納")
                .replace("Dram", "德拉姆")
                .replace("Taka", "塔卡")
                .replace("Rupiah", "印尼盾")
                .replace("Ringgit", "林吉特")
                .replace("Baht", "泰銖")
                .replace("Won", "韓圓")
                .replace("Dong", "越南盾")
                .replace("Kip", "基普")
                .replace("Riel", "瑞爾")
                .replace("Pataca", "澳門元")
                .replace("Singapore dollar", "新加坡元")
                .replace("Hong Kong dollar", "港幣")
                .replace("New Taiwan dollar", "新台幣")
                .replace("Korean won", "韓圓")
                .replace("Japanese yen", "日圓")
                .replace("Chinese yuan", "人民幣")
                .replace("British pound", "英鎊")
                .replace("US dollar", "美元")
                .replace("Canadian dollar", "加拿大元")
                .replace("Australian dollar", "澳元")
                .replace("New Zealand dollar", "紐西蘭元")
                .replace("Swiss franc", "瑞士法郎")
                .replace("Swedish krona", "瑞典克朗")
                .replace("Norwegian krone", "挪威克朗")
                .replace("Danish krone", "丹麥克朗")
                .replace("Polish zloty", "波蘭茲羅提")
                .replace("Czech koruna", "捷克克朗")
                .replace("Hungarian forint", "匈牙利福林")
                .replace("Romanian leu", "羅馬尼亞列伊")
                .replace("Bulgarian lev", "保加利亞列弗")
                .replace("Croatian kuna", "克羅地亞庫納")
                .replace("Serbian dinar", "塞爾維亞第納爾")
                .replace("Turkish lira", "土耳其里拉")
                .replace("Israeli shekel", "以色列謝克爾")
                .replace("Saudi riyal", "沙烏地里亞爾")
                .replace("UAE dirham", "阿聯酋迪拉姆")
                .replace("Qatari riyal", "卡塔爾里亞爾")
                .replace("Kuwaiti dinar", "科威特第納爾")
                .replace("Bahraini dinar", "巴林第納爾")
                .replace("Omani rial", "阿曼里亞爾")
                .replace("Jordanian dinar", "約旦第納爾")
                .replace("Lebanese pound", "黎巴嫩鎊")
                .replace("Egyptian pound", "埃及鎊")
                .replace("South African rand", "南非蘭特")
                .replace("Nigerian naira", "奈及利亞奈拉")
                .replace("Kenyan shilling", "肯尼亞先令")
                .replace("Ugandan shilling", "烏干達先令")
                .replace("Tanzanian shilling", "坦桑尼亞先令")
                .replace("Ethiopian birr", "衣索比亞比爾")
                .replace("Moroccan dirham", "摩洛哥迪拉姆")
                .replace("Algerian dinar", "阿爾及利亞第納爾")
                .replace("Tunisian dinar", "突尼斯第納爾")
                .replace("Libyan dinar", "利比亞第納爾")
                .replace("Sudanese pound", "蘇丹鎊")
                .replace("Ghanaian cedi", "加納塞地")
                .replace("Botswana pula", "博茨瓦納普拉")
                .replace("Namibian dollar", "納米比亞元")
                .replace("Zambian kwacha", "贊比亞克瓦查")
                .replace("Zimbabwean dollar", "津巴布韋元")
                .replace("Mauritian rupee", "毛里求斯盧比")
                .replace("Seychellois rupee", "塞舌爾盧比")
                .replace("Malagasy ariary", "馬達加斯加阿里亞里")
                .replace("Comorian franc", "科摩羅法郎")
                .replace("Djiboutian franc", "吉布提法郎")
                .replace("Eritrean nakfa", "厄立特里亞納克法")
                .replace("Somalian shilling", "索馬里先令")
                .replace("Burundian franc", "布隆迪法郎")
                .replace("Rwandan franc", "盧旺達法郎")
                .replace("Congolese franc", "剛果法郎")
                .replace("Central African franc", "中非法郎")
                .replace("West African franc", "西非法郎")
                .replace("Cape Verdean escudo", "佛得角埃斯庫多")
                .replace("Sao Tomean dobra", "聖多美多布拉")
                .replace("Guinean franc", "幾內亞法郎")
                .replace("Sierra Leonean leone", "塞拉利昂利昂")
                .replace("Liberian dollar", "利比里亞元")
                .replace("Gambian dalasi", "岡比亞達拉西")
                .replace("Senegalese franc", "塞內加爾法郎")
                .replace("Mauritanian ouguiya", "毛里塔尼亞烏吉亞")
                .replace("Malian franc", "馬里法郎")
                .replace("Burkina Faso franc", "布基納法索法郎")
                .replace("Nigerian franc", "尼日爾法郎")
                .replace("Chadian franc", "乍得法郎")
                .replace("Cameroonian franc", "喀麥隆法郎")
                .replace("Gabonese franc", "加蓬法郎")
                .replace("Equatorial Guinean franc", "赤道幾內亞法郎")
                .replace("Central African franc", "中非法郎")
                .replace("Republic of Congo franc", "剛果共和國法郎")
                .replace("Democratic Republic of Congo franc", "剛果民主共和國法郎")
                .replace("Angolan kwanza", "安哥拉寬扎")
                .replace("Mozambican metical", "莫桑比克梅蒂卡爾")
                .replace("Malawian kwacha", "馬拉維克瓦查")
                .replace("Lesotho loti", "萊索托洛蒂")
                .replace("Swazi lilangeni", "斯威士蘭里蘭吉尼")
                .replace("South African rand", "南非蘭特")
                .replace("Namibian dollar", "納米比亞元")
                .replace("Botswana pula", "博茨瓦納普拉")
                .replace("Zambian kwacha", "贊比亞克瓦查")
                .replace("Zimbabwean dollar", "津巴布韋元")
                .replace("Malagasy ariary", "馬達加斯加阿里亞里")
                .replace("Mauritian rupee", "毛里求斯盧比")
                .replace("Seychellois rupee", "塞舌爾盧比")
                .replace("Comorian franc", "科摩羅法郎");
    }
    
    /**
     * 顯示識別結果彈窗
     */
    private void showResultDialog(List<OCRHelper.OCRResult> ocrResults, 
                                 List<CurrencyDetector.CurrencyResult> currencyResults) {
        // 創建對話框
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_ocr_result);
        
        // 設置窗口大小
        Window window = dialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.9);
            params.height = (int) (getResources().getDisplayMetrics().heightPixels * 0.8);
            window.setAttributes(params);
        }
        
        // 獲取視圖元素
        TextView resultText = dialog.findViewById(R.id.resultText);
        Button copyButton = dialog.findViewById(R.id.copyButton);
        Button speakButton = dialog.findViewById(R.id.speakButton);
        Button closeButton = dialog.findViewById(R.id.closeButton);
        TextView dialogTitle = dialog.findViewById(R.id.dialogTitle);
        
        // 設置標題
        if (isTextMode) {
            dialogTitle.setText(currentLanguage.equals("english") ? "Text recognition results" : "文字識別結果");
        } else {
            dialogTitle.setText(currentLanguage.equals("english") ? "Currency recognition results" : "貨幣識別結果");
        }
        
        // 格式化結果文本
        StringBuilder resultBuilder = new StringBuilder();
        
        if (!ocrResults.isEmpty()) {
            resultBuilder.append(getLocalizedString("result_text_header")).append("\n\n");
            for (int i = 0; i < ocrResults.size(); i++) {
                OCRHelper.OCRResult result = ocrResults.get(i);
                resultBuilder.append(result.getText());
                if (i < ocrResults.size() - 1) {
                    resultBuilder.append("\n\n");
                }
            }
        }
        
        if (!currencyResults.isEmpty()) {
            if (resultBuilder.length() > 0) {
                resultBuilder.append("\n\n");
            }
            resultBuilder.append(getLocalizedString("result_currency_header")).append("\n\n");
            for (int i = 0; i < currencyResults.size(); i++) {
                CurrencyDetector.CurrencyResult result = currencyResults.get(i);
                resultBuilder.append(result.getName())
                            .append(" ")
                            .append(result.getAmount())
                            .append(" ")
                            .append(currentLanguage.equals("english") ? "dollars" : "元");
                if (i < currencyResults.size() - 1) {
                    resultBuilder.append("\n\n");
                }
            }
        }
        
        if (resultBuilder.length() == 0) {
            resultBuilder.append(getLocalizedString("result_none"));
        }
        
        resultText.setText(resultBuilder.toString());
        
        // 複製按鈕
        copyButton.setOnClickListener(v -> {
            vibrationManager.vibrateClick();
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("識別結果", resultBuilder.toString());
            clipboard.setPrimaryClip(clip);
            
            String message = currentLanguage.equals("english") 
                ? "Copied to clipboard" 
                : "已複製到剪貼板";
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            announceInfo(message);
        });
        
        // 朗讀按鈕
        speakButton.setOnClickListener(v -> {
            vibrationManager.vibrateClick();
            String textToSpeak = resultBuilder.toString();
            if (textToSpeak.isEmpty() || textToSpeak.equals(getLocalizedString("result_none"))) {
                announceInfo(currentLanguage.equals("english") ? "No content to read" : "沒有內容可朗讀");
            } else {
                if ("english".equals(currentLanguage)) {
                    textToSpeak = translateCurrencyDetailsToEnglish(textToSpeak)
                            .replace("識別到", "Detected")
                            .replace("文字識別結果", "Text recognition results")
                            .replace("貨幣識別結果", "Currency recognition results");
                }

                String cantoneseText = currentLanguage.equals("english") ? "" : textToSpeak;
                String englishText = currentLanguage.equals("english") ? textToSpeak : "";
                ttsManager.speak(cantoneseText, englishText, true);
            }
        });
        
        // 關閉按鈕
        closeButton.setOnClickListener(v -> {
            vibrationManager.vibrateClick();
            dialog.dismiss();
            announceInfo("已關閉結果視窗");
        });
        
        // 顯示對話框
        dialog.show();
        announceInfo("識別結果已顯示");
    }
    
    /**
     * 顯示錯誤彈窗
     */
    private void showErrorDialog(String errorMessage) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_ocr_result);
        
        Window window = dialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.9);
            window.setAttributes(params);
        }
        
        TextView resultText = dialog.findViewById(R.id.resultText);
        TextView dialogTitle = dialog.findViewById(R.id.dialogTitle);
        Button closeButton = dialog.findViewById(R.id.closeButton);
        Button copyButton = dialog.findViewById(R.id.copyButton);
        Button speakButton = dialog.findViewById(R.id.speakButton);
        
        dialogTitle.setText("錯誤");
        resultText.setText(errorMessage);
        
        copyButton.setVisibility(View.GONE);
        speakButton.setVisibility(View.GONE);
        
        closeButton.setOnClickListener(v -> {
            vibrationManager.vibrateClick();
            dialog.dismiss();
        });
        
        dialog.show();
    }
}
