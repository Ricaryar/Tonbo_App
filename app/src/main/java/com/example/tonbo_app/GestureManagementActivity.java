package com.example.tonbo_app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 手勢管理Activity
 * 用於創建、管理和使用手勢
 */
public class GestureManagementActivity extends BaseAccessibleActivity {
    private static final String TAG = "GestureManagement";
    
    private GestureDrawView gestureDrawView;
    private Button saveButton;
    private Button clearButton;
    private android.widget.ImageButton backButton;
    private TextView pageTitle;
    private LinearLayout savedGesturesList;
    
    private GestureRecognitionManager gestureManager;
    private TTSManager ttsManager;
    private VibrationManager vibrationManager;
    private Switch gestureLoginSwitch;
    private static final String PREFS_NAME = "GestureSettings";
    private static final String KEY_GESTURE_LOGIN_ENABLED = "gesture_login_enabled";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gesture_management);
        
        gestureManager = GestureRecognitionManager.getInstance(this);
        ttsManager = TTSManager.getInstance(this);
        vibrationManager = VibrationManager.getInstance(this);
        
        initViews();
        setupListeners();
        updateLanguageUI();
        loadSavedGestures();
        announcePageTitle();
    }
    
    private void initViews() {
        gestureDrawView = findViewById(R.id.gesture_draw_view);
        saveButton = findViewById(R.id.save_button);
        clearButton = findViewById(R.id.clear_button);
        backButton = findViewById(R.id.back_button);
        pageTitle = findViewById(R.id.page_title);
        savedGesturesList = findViewById(R.id.saved_gestures_list);
        gestureLoginSwitch = findViewById(R.id.gesture_login_switch);
        
        // 載入開關狀態
        loadGestureLoginSetting();
    }
    
    private void setupListeners() {
        // 返回按鈕
        backButton.setOnClickListener(v -> {
            vibrationManager.vibrateClick();
            finish();
        });
        
        // 保存手勢
        saveButton.setOnClickListener(v -> {
            vibrationManager.vibrateClick();
            if (gestureDrawView.hasDrawing()) {
                showSaveGestureDialog();
            } else {
                announceInfo("請先繪製手勢");
            }
        });
        
        // 清除手勢
        clearButton.setOnClickListener(v -> {
            vibrationManager.vibrateClick();
            gestureDrawView.clear();
            announceInfo("手勢已清除");
        });
        
        // 手勢登入開關
        gestureLoginSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            vibrationManager.vibrateClick();
            saveGestureLoginSetting(isChecked);
            String message = isChecked ? "已啟用手勢登入" : "已關閉手勢登入";
            String englishMessage = isChecked ? "Gesture login enabled" : "Gesture login disabled";
            announceInfo(message);
        });
        
        // 繪畫手勢時的觸覺反饋，讓視障用戶感知正在繪製
        gestureDrawView.setOnDrawListener(new GestureDrawView.OnDrawListener() {
            @Override
            public void onDrawStart() {
                vibrationManager.vibrateFocus();
            }

            @Override
            public void onDrawingProgress() {
                vibrationManager.vibrateFocus();
            }

            @Override
            public void onDrawEnd() {
                vibrationManager.vibrateClick();
            }
        });

        // 手勢繪畫完成檢測（簡化實現）- 使用延遲檢測
        gestureDrawView.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                // 手勢繪畫完成，延遲檢查
                new android.os.Handler().postDelayed(() -> {
                    if (gestureDrawView.hasDrawing()) {
                        checkAndRecognizeGesture();
                    }
                }, 500);
            }
            return false;
        });
    }
    
    /**
     * 檢查並識別手勢
     */
    private void checkAndRecognizeGesture() {
        if (!gestureDrawView.hasDrawing()) {
            return;
        }
        
        // 識別手勢
        String recognizedGesture = gestureManager.recognizeGesture(
            gestureDrawView.getPaths()
        );
        
        if (recognizedGesture != null) {
            String functionName = gestureManager.getFunctionForGesture(recognizedGesture);
            
            if (functionName != null) {
                announceInfo("識別到手勢：" + recognizedGesture + "，跳轉到：" + functionName);
                
                // 根據功能名稱跳轉
                navigateToFunction(functionName);
            }
        } else {
            // 顯示創建新手勢的選項
            showCreateGestureDialog();
        }
    }
    
    /**
     * 顯示創建新手勢對話框
     */
    private void showCreateGestureDialog() {
        announceInfo("未識別到已保存的手勢，是否創建新手勢？");
        
        // TODO: 顯示創建手勢對話框，讓用戶選擇綁定功能
        // 簡化實現：直接提示
        Toast.makeText(this, "請繪製手勢並綁定功能", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * 顯示保存手勢對話框
     */
    private void showSaveGestureDialog() {
        // 簡化實現：直接顯示功能選擇對話框
        String[] functions = {
            "環境識別", "文檔助手", "語音命令", "尋找物品", 
            "即時協助", "出行協助"
        };
        
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("綁定功能");
        builder.setItems(functions, (dialog, which) -> {
            String gestureName = "手勢_" + System.currentTimeMillis();
            String selectedFunction = functions[which];
            
            // 將Path轉換為GesturePoint列表
            List<android.graphics.Path> paths = gestureDrawView.getPaths();
            List<GestureRecognitionManager.GesturePoint> points = convertPathsToPoints(paths);
            
            // 保存手勢
            gestureManager.saveGesture(
                gestureName,
                points,
                selectedFunction
            );
            
            // 清除繪畫區域
            gestureDrawView.clear();
            
            announceInfo("手勢已綁定到：" + selectedFunction);
            loadSavedGestures();
        });
        builder.show();
    }
    
    /**
     * 導航到指定功能
     */
    private void navigateToFunction(String functionName) {
        Intent intent = null;
        
        // 獲取當前語言（從Intent或LocaleManager）
        String language = currentLanguage;
        if (language == null) {
            language = LocaleManager.getInstance(this).getCurrentLanguage();
        }
        
        switch (functionName) {
            case "尋找物品":
            case "Find Items":
            case "FindItems":
                intent = new Intent(this, FindItemsActivity.class);
                break;
                
            case "環境識別":
            case "Environment":
            case "EnvironmentActivity":
                // 使用與主頁相同的環境識別Activity（RealAIDetectionActivity）
                intent = new Intent(this, RealAIDetectionActivity.class);
                break;
                
            case "文檔助手":
            case "Document":
            case "DocumentAssistant":
                intent = new Intent(this, DocumentCurrencyActivity.class);
                break;
                
            case "出行協助":
            case "Travel":
            case "TravelAssistant":
                intent = new Intent(this, TravelAssistantActivity.class);
                break;
                
            case "即時協助":
            case "InstantAssistance":
                intent = new Intent(this, InstantAssistanceActivity.class);
                break;
                
            default:
                Log.w(TAG, "未知功能: " + functionName);
                break;
        }
        
        if (intent != null) {
            // 傳遞語言參數，確保跳轉後的頁面保持相同語言
            intent.putExtra("language", language);
            startActivity(intent);
        }
    }
    
    /**
     * 載入已保存的手勢列表
     */
    private void loadSavedGestures() {
        savedGesturesList.removeAllViews();
        
        Map<String, String> gestures = gestureManager.getAllGestures();
        
        for (Map.Entry<String, String> entry : gestures.entrySet()) {
            addGestureItem(entry.getKey(), entry.getValue());
        }
    }
    
    /**
     * 添加手勢項到列表
     */
    private void addGestureItem(String gestureName, String functionName) {
        View itemView = getLayoutInflater().inflate(R.layout.item_gesture, null);
        
        TextView gestureNameText = itemView.findViewById(R.id.gesture_name);
        TextView functionNameText = itemView.findViewById(R.id.function_name);
        Button deleteButton = itemView.findViewById(R.id.delete_button);
        
        gestureNameText.setText("手勢: " + gestureName);
        functionNameText.setText("功能: " + functionName);
        
        deleteButton.setOnClickListener(v -> {
            gestureManager.deleteGesture(gestureName);
            loadSavedGestures();
            announceInfo("已刪除手勢：" + gestureName);
        });
        
        savedGesturesList.addView(itemView);
    }
    
    /**
     * 更新語言UI
     */
    private void updateLanguageUI() {
        if (pageTitle != null) {
            String title = getLocalizedString("gesture_management_title");
            pageTitle.setText(title);
        }
        
        if (saveButton != null) {
            saveButton.setText(getLocalizedString("bind_function"));
        }
        if (clearButton != null) {
            String text = getLocalizedString("clear");
            clearButton.setText(text);
        }
    }
    
    /**
     * 獲取本地化字符串
     */
    private String getLocalizedString(String key) {
        String currentLang = currentLanguage != null ? currentLanguage : LocaleManager.getInstance(this).getCurrentLanguage();
        
        switch (key) {
            case "gesture_management_title":
                if ("english".equals(currentLang)) {
                    return "Gesture Management";
                } else if ("mandarin".equals(currentLang)) {
                    return "手势管理";
                } else {
                    return "手勢管理";
                }
            case "bind_function":
                if ("english".equals(currentLang)) {
                    return "Bind function";
                } else if ("mandarin".equals(currentLang)) {
                    return "绑定功能";
                } else {
                    return "綁定功能";
                }
            case "clear":
                if ("english".equals(currentLang)) {
                    return "Clear";
                } else if ("mandarin".equals(currentLang)) {
                    return "清除";
                } else {
                    return "清除";
                }
            default:
                return key;
        }
    }
    
    @Override
    protected void announcePageTitle() {
        new android.os.Handler().postDelayed(() -> {
            String description = getLocalizedString("gesture_management_title") + "頁面。可以繪製手勢並綁定功能。";
            ttsManager.speak(description, null, true);
        }, 500);
    }
    
    /**
     * 載入手勢登入設置
     */
    private void loadGestureLoginSetting() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // 默認值為 false，表示首次打開時手勢登入是關閉狀態
        boolean enabled = prefs.getBoolean(KEY_GESTURE_LOGIN_ENABLED, false);
        gestureLoginSwitch.setChecked(enabled);
    }
    
    /**
     * 保存手勢登入設置
     */
    private void saveGestureLoginSetting(boolean enabled) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_GESTURE_LOGIN_ENABLED, enabled).apply();
        Log.d(TAG, "手勢登入設置已保存: " + enabled);
    }
    
    /**
     * 檢查手勢登入是否啟用（靜態方法，供其他類使用）
     */
    public static boolean isGestureLoginEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_GESTURE_LOGIN_ENABLED, false);
    }
    
    /**
     * 將Path轉換為GesturePoint列表
     */
    private List<GestureRecognitionManager.GesturePoint> convertPathsToPoints(List<android.graphics.Path> paths) {
        List<GestureRecognitionManager.GesturePoint> points = new java.util.ArrayList<>();
        
        for (android.graphics.Path path : paths) {
            // 簡化實現：從Path中提取關鍵點
            // 實際應該從Path中提取所有點
            android.graphics.PathMeasure pm = new android.graphics.PathMeasure(path, false);
            float length = pm.getLength();
            
            // 取樣點數
            int sampleCount = 20;
            for (int i = 0; i < sampleCount; i++) {
                float distance = (i / (float) (sampleCount - 1)) * length;
                float[] coords = new float[2];
                if (pm.getPosTan(distance, coords, null)) {
                    points.add(new GestureRecognitionManager.GesturePoint(coords[0], coords[1]));
                }
            }
        }
        
        return points;
    }
}
