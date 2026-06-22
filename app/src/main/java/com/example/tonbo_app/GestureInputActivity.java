package com.example.tonbo_app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 手勢輸入Activity
 * 在進入主頁前顯示，用戶可以通過手勢進入不同功能
 */
public class GestureInputActivity extends BaseAccessibleActivity {
    private static final String TAG = "GestureInput";
    
    private GestureDrawView gestureDrawView;
    private TextView hintText;
    private TextView instructionText;
    private TTSManager ttsManager;
    private VibrationManager vibrationManager;
    private GestureRecognitionManager gestureManager;
    
    private final Handler recognizeHandler = new Handler(Looper.getMainLooper());
    private final Runnable recognizeRunnable = this::recognizeAndNavigate;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gesture_input);
        
        ttsManager = TTSManager.getInstance(this);
        vibrationManager = VibrationManager.getInstance(this);
        gestureManager = GestureRecognitionManager.getInstance(this);
        
        initViews();
        setupListeners();
        updateLanguageUI();
        announceInstructions();
    }
    
    private void initViews() {
        gestureDrawView = findViewById(R.id.gesture_draw_view);
        hintText = findViewById(R.id.hint_text);
        instructionText = findViewById(R.id.instruction_text);
    }
    
    private void setupListeners() {
        // 手勢繪畫完成檢測（多筆如「叉」會多次 ACTION_UP，先取消再延遲，避免重複跳轉主頁）
        gestureDrawView.setOnTouchListener((v, event) -> {
            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN) {
                recognizeHandler.removeCallbacks(recognizeRunnable);
            } else if (action == MotionEvent.ACTION_UP) {
                recognizeHandler.removeCallbacks(recognizeRunnable);
                recognizeHandler.postDelayed(recognizeRunnable, 500);
            }
            return false;
        });
    }
    
    @Override
    protected void onDestroy() {
        recognizeHandler.removeCallbacks(recognizeRunnable);
        super.onDestroy();
    }
    
    /**
     * 識別手勢並導航
     */
    private void recognizeAndNavigate() {
        if (!gestureDrawView.hasDrawing()) {
            return;
        }
        
        List<android.graphics.Path> paths = gestureDrawView.getPaths();
        
        // 優先匹配已保存並綁定功能的手勢，避免幾何形狀被誤判為「叉」而直跳主頁
        String savedGesture = gestureManager.recognizeGesture(paths);
        Log.d(TAG, "已保存手勢識別結果: " + savedGesture);
        
        if (savedGesture != null) {
            String functionName = gestureManager.getFunctionForGesture(savedGesture);
            if (functionName != null) {
                Log.d(TAG, "識別到已保存的手勢：" + savedGesture + "，綁定功能：" + functionName);
                vibrationManager.vibrateClick();
                
                String cantoneseText = "識別到手勢，正在進入" + functionName;
                String englishText = "Gesture recognized, entering " + functionName;
                String mandarinText = "识别到手势，正在进入" + functionName;
                
                if ("english".equals(currentLanguage)) {
                    ttsManager.speak(englishText, null, false);
                } else if ("mandarin".equals(currentLanguage)) {
                    ttsManager.speak(mandarinText, null, false);
                } else {
                    ttsManager.speak(cantoneseText, englishText, false);
                }
                
                navigateToFunction(functionName);
                return;
            }
        }
        
        String simpleGesture = recognizeSimpleGesture(paths);
        Log.d(TAG, "簡單手勢識別結果: " + simpleGesture);
        
        if (simpleGesture != null) {
            vibrationManager.vibrateClick();
            navigateToDestination(simpleGesture);
            return;
        }
        
        // 如果都沒有識別到
        {
            // 未識別到手勢，提示用戶
            String cantoneseText = "未識別到手勢。請畫❌進入主頁，或繪製已保存的手勢進入對應功能";
            String englishText = "Gesture not recognized. Draw ❌ for home, or draw a saved gesture for its function";
            String mandarinText = "未识别到手势。请画❌进入主页，或绘制已保存的手势进入对应功能";
            
            if ("english".equals(currentLanguage)) {
                ttsManager.speak(englishText, null, false);
            } else if ("mandarin".equals(currentLanguage)) {
                ttsManager.speak(mandarinText, null, false);
            } else {
                ttsManager.speak(cantoneseText, englishText, false);
            }
            
            // 清除繪畫區域
            gestureDrawView.clear();
        }
    }
    
    /**
     * 識別簡單手勢（叉）
     */
    private String recognizeSimpleGesture(List<android.graphics.Path> paths) {
        if (paths.isEmpty()) {
            return null;
        }
        
        // 提取所有點
        List<GestureRecognitionManager.GesturePoint> allPoints = new ArrayList<>();
        for (android.graphics.Path path : paths) {
            allPoints.addAll(extractPointsFromPath(path));
        }
        
        if (allPoints.size() < 10) {
            return null; // 點太少，無法識別
        }
        
        // 判斷是否為叉
        boolean isCross = isCrossGesture(allPoints);
        
        if (isCross) {
            Log.d(TAG, "識別到手勢：叉");
            return "cross";
        }
        
        Log.d(TAG, "簡單手勢識別：未識別到叉");
        return null;
    }
    
    /**
     * 判斷是否為叉手勢
     * 叉的特徵：有兩條交叉的線，形成X形狀
     */
    private boolean isCrossGesture(List<GestureRecognitionManager.GesturePoint> points) {
        if (points.size() < 10) {
            return false;
        }
        
        // 計算手勢的邊界
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE, maxY = Float.MIN_VALUE;
        
        for (GestureRecognitionManager.GesturePoint p : points) {
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
        }
        
        float width = maxX - minX;
        float height = maxY - minY;
        float centerX = (minX + maxX) / 2;
        float centerY = (minY + maxY) / 2;
        
        if (width < 50 || height < 50) {
            return false; // 太小，不是叉
        }
        
        // 檢查是否有兩條對角線
        // 方法：檢查點是否分布在兩條對角線上
        int diagonal1Count = 0; // 左上到右下 (y = x)
        int diagonal2Count = 0; // 右上到左下 (y = -x)
        
        float tolerance = Math.max(width, height) * 0.25f; // 允許25%的誤差
        
        for (GestureRecognitionManager.GesturePoint p : points) {
            float dx = p.x - centerX;
            float dy = p.y - centerY;
            
            // 檢查是否在對角線1附近（左上到右下：y = x，即 dx ≈ dy）
            float dist1 = Math.abs(dx - dy);
            if (dist1 < tolerance) {
                diagonal1Count++;
            }
            
            // 檢查是否在對角線2附近（右上到左下：y = -x，即 dx ≈ -dy）
            float dist2 = Math.abs(dx + dy);
            if (dist2 < tolerance) {
                diagonal2Count++;
            }
        }
        
        // 如果兩條對角線都有足夠的點，認為是叉
        int threshold = points.size() / 4; // 至少25%的點在每條對角線上
        boolean hasDiagonal1 = diagonal1Count >= threshold;
        boolean hasDiagonal2 = diagonal2Count >= threshold;
        
        // 兩條對角線都應該有足夠的點
        return hasDiagonal1 && hasDiagonal2;
    }
    
    /**
     * 從Path中提取點
     */
    private List<GestureRecognitionManager.GesturePoint> extractPointsFromPath(android.graphics.Path path) {
        List<GestureRecognitionManager.GesturePoint> points = new ArrayList<>();
        
        android.graphics.PathMeasure pm = new android.graphics.PathMeasure(path, false);
        float length = pm.getLength();
        
        if (length > 0) {
            int sampleCount = Math.max(20, (int) (length / 10)); // 根據長度決定採樣點數
            float[] coords = new float[2];
            
            for (int i = 0; i < sampleCount; i++) {
                float distance = (i / (float) (sampleCount - 1)) * length;
                if (pm.getPosTan(distance, coords, null)) {
                    points.add(new GestureRecognitionManager.GesturePoint(coords[0], coords[1]));
                }
            }
        }
        
        return points;
    }
    
    /**
     * 導航到指定功能（根據功能名稱）
     */
    private void navigateToFunction(String functionName) {
        Intent intent = null;
        String announcement = "";
        
        // 獲取當前語言
        String language = currentLanguage;
        if (language == null) {
            language = LocaleManager.getInstance(this).getCurrentLanguage();
        }
        
        // 根據功能名稱跳轉
        if (functionName.contains("尋找物品") || functionName.contains("Find Items") || functionName.contains("FindItems")) {
            intent = new Intent(this, FindItemsActivity.class);
            announcement = "正在進入尋找物品";
        } else if (functionName.contains("環境識別") || functionName.contains("Environment") || functionName.contains("EnvironmentActivity")) {
            intent = new Intent(this, RealAIDetectionActivity.class);
            announcement = "正在進入環境識別";
        } else if (functionName.contains("文檔助手") || functionName.contains("Document") || functionName.contains("DocumentAssistant")) {
            intent = new Intent(this, DocumentCurrencyActivity.class);
            announcement = "正在進入文檔助手";
        } else if (functionName.contains("出行協助") || functionName.contains("Travel") || functionName.contains("TravelAssistant")) {
            intent = new Intent(this, TravelAssistantActivity.class);
            announcement = "正在進入出行協助";
        } else if (functionName.contains("即時協助") || functionName.contains("InstantAssistance")) {
            intent = new Intent(this, InstantAssistanceActivity.class);
            announcement = "正在進入即時協助";
        } else if (functionName.contains("語音命令") || functionName.contains("Voice Command") || functionName.contains("VoiceCommand")) {
            intent = new Intent(this, VoiceCommandActivity.class);
            announcement = "正在進入語音命令";
        }
        
        if (intent != null) {
            if (language == null || language.isEmpty()) {
                language = LocaleManager.getInstance(this).getCurrentLanguage();
            }
            localeManager.setLanguage(this, language);
            intent.putExtra("language", language);
            // 標記是從手勢登入進入的，返回時應該回到主頁
            intent.putExtra("from_gesture_login", true);
            
            // 創建 final 副本供 lambda 使用
            final Intent finalIntent = intent;
            
            // 延遲跳轉，讓語音播放
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                startActivity(finalIntent);
                finish();
            }, 500);
        } else {
            // 如果沒有匹配的功能，跳轉到主頁
            Log.w(TAG, "未找到匹配的功能：" + functionName + "，跳轉到主頁");
            navigateToDestination("cross");
        }
    }
    
    /**
     * 根據識別的手勢導航到目標頁面（叉）
     */
    private void navigateToDestination(String gesture) {
        Intent intent = null;
        String announcement = "";
        
        if ("cross".equals(gesture)) {
            // 畫叉 = 進入主頁
            intent = new Intent(this, MainActivity.class);
            announcement = "正在進入主頁";
        }
        
        if (intent != null) {
            String lang = (currentLanguage != null && !currentLanguage.isEmpty())
                    ? currentLanguage : LocaleManager.getInstance(this).getCurrentLanguage();
            localeManager.setLanguage(this, lang);
            intent.putExtra("language", lang);
            
            // 語音提示
            String englishAnnouncement = "Entering home";
            if ("english".equals(lang)) {
                ttsManager.speak(englishAnnouncement, null, false);
            } else if ("mandarin".equals(lang)) {
                ttsManager.speak(announcement, null, false);
            } else {
                ttsManager.speak(announcement, englishAnnouncement, false);
            }
            
            // 創建 final 副本供 lambda 使用
            final Intent finalIntent = intent;
            
            // 延遲跳轉，讓語音播放
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                startActivity(finalIntent);
                finish();
            }, 500);
        }
    }
    
    /**
     * 更新語言UI
     */
    private void updateLanguageUI() {
        if (instructionText != null) {
            String instruction;
            if ("english".equals(currentLanguage)) {
                instruction = "Draw ❌ for home, or a saved gesture for its function";
            } else if ("mandarin".equals(currentLanguage)) {
                instruction = "画❌进入主页，或绘制已保存的手势进入对应功能";
            } else {
                instruction = "畫❌進入主頁，或繪製已保存的手勢進入對應功能";
            }
            instructionText.setText(instruction);
        }
        
        if (hintText != null) {
            String hint;
            if ("english".equals(currentLanguage)) {
                hint = "Draw your gesture in the area below";
            } else if ("mandarin".equals(currentLanguage)) {
                hint = "请在下方区域绘制手势";
            } else {
                hint = "請在下方區域繪製手勢";
            }
            hintText.setText(hint);
        }
    }
    
    /**
     * 播報使用說明
     */
    private void announceInstructions() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            String cantoneseText = "手勢登入頁面。請在下方區域繪製手勢。畫❌進入主頁，或繪製已保存的手勢進入對應功能。";
            String englishText = "Gesture login page. Please draw your gesture in the area below. Draw ❌ for home, or draw a saved gesture for its function.";
            String mandarinText = "手势登录页面。请在下方区域绘制手势。画❌进入主页，或绘制已保存的手势进入对应功能。";
            
            if ("english".equals(currentLanguage)) {
                ttsManager.speak(englishText, null, true);
            } else if ("mandarin".equals(currentLanguage)) {
                ttsManager.speak(mandarinText, null, true);
            } else {
                ttsManager.speak(cantoneseText, englishText, true);
            }
        }, 500);
    }
    
    @Override
    protected void announcePageTitle() {
        announceInstructions();
    }
}

