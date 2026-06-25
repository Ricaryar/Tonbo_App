package com.example.tonbo_app;

import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.TextView;

import java.util.List;

/**
 * 手勢輸入Activity
 * 在進入主頁前顯示，用戶可以通過手勢進入不同功能，或搖晃兩下進入主頁
 */
public class GestureInputActivity extends BaseAccessibleActivity {
    private static final String TAG = "GestureInput";

    private static final float SHAKE_THRESHOLD = 11.5f;
    private static final int SHAKE_SLOP_TIME_MS = 400;
    private static final int DOUBLE_SHAKE_WINDOW_MS = 2000;
    private static final int REQUIRED_SHAKE_COUNT = 2;

    private GestureDrawView gestureDrawView;
    private TextView pageTitle;
    private TextView hintText;
    private TextView instructionText;
    private TTSManager ttsManager;
    private VibrationManager vibrationManager;
    private GestureRecognitionManager gestureManager;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private ShakeDetector shakeDetector;
    private long lastShakePeakTime;
    private int shakeCount;
    private boolean navigatingHome;

    private final Handler recognizeHandler = new Handler(Looper.getMainLooper());
    private final Runnable recognizeRunnable = this::recognizeAndNavigate;
    private final Handler shakeResetHandler = new Handler(Looper.getMainLooper());
    private final Runnable shakeResetRunnable = () -> shakeCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gesture_input);

        ttsManager = TTSManager.getInstance(this);
        vibrationManager = VibrationManager.getInstance(this);
        gestureManager = GestureRecognitionManager.getInstance(this);

        initViews();
        setupListeners();
        setupShakeDetection();
        updateLanguageUI();
        announceInstructions();
    }

    private void initViews() {
        gestureDrawView = findViewById(R.id.gesture_draw_view);
        pageTitle = findViewById(R.id.page_title);
        hintText = findViewById(R.id.hint_text);
        instructionText = findViewById(R.id.instruction_text);
    }

    private void setupListeners() {
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
        } catch (Exception e) {
            Log.e(TAG, "搖晃檢測初始化失敗: " + e.getMessage());
        }
    }

    private void onShakePeakDetected() {
        if (navigatingHome) {
            return;
        }

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
            navigateToHome();
        }
    }

    private void navigateToHome() {
        if (navigatingHome) {
            return;
        }
        navigatingHome = true;

        vibrationManager.vibrateSuccess();

        String lang = (currentLanguage != null && !currentLanguage.isEmpty())
                ? currentLanguage : LocaleManager.getInstance(this).getCurrentLanguage();
        localeManager.setLanguage(this, lang);

        String cantoneseText = "正在進入主頁";
        String englishText = "Entering home";
        String mandarinText = "正在进入主页";

        if ("english".equals(lang)) {
            ttsManager.speak(englishText, null, false);
        } else if ("mandarin".equals(lang)) {
            ttsManager.speak(mandarinText, null, false);
        } else {
            ttsManager.speak(cantoneseText, englishText, false);
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("language", lang);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startActivity(intent);
            finish();
        }, 500);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null && accelerometer != null && shakeDetector != null) {
            try {
                sensorManager.registerListener(shakeDetector, accelerometer, SensorManager.SENSOR_DELAY_UI);
            } catch (Exception e) {
                Log.e(TAG, "搖晃檢測恢復失敗: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null && shakeDetector != null) {
            try {
                sensorManager.unregisterListener(shakeDetector);
            } catch (Exception ignored) {
            }
        }
        shakeResetHandler.removeCallbacks(shakeResetRunnable);
        shakeCount = 0;
    }

    @Override
    protected void onDestroy() {
        recognizeHandler.removeCallbacks(recognizeRunnable);
        shakeResetHandler.removeCallbacks(shakeResetRunnable);
        if (sensorManager != null && shakeDetector != null) {
            try {
                sensorManager.unregisterListener(shakeDetector);
            } catch (Exception ignored) {
            }
        }
        super.onDestroy();
    }

    private void recognizeAndNavigate() {
        if (!gestureDrawView.hasDrawing()) {
            return;
        }

        List<android.graphics.Path> paths = gestureDrawView.getPaths();

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

        String cantoneseText = "未識別到手勢。請搖晃手機兩下進入主頁，或繪製已保存的手勢進入對應功能";
        String englishText = "Gesture not recognized. Shake your phone twice to enter home, or draw a saved gesture for its function";
        String mandarinText = "未识别到手势。请摇晃手机两下进入主页，或绘制已保存的手势进入对应功能";

        if ("english".equals(currentLanguage)) {
            ttsManager.speak(englishText, null, false);
        } else if ("mandarin".equals(currentLanguage)) {
            ttsManager.speak(mandarinText, null, false);
        } else {
            ttsManager.speak(cantoneseText, englishText, false);
        }

        gestureDrawView.clear();
    }

    private void navigateToFunction(String functionName) {
        Intent intent = null;

        String language = currentLanguage;
        if (language == null) {
            language = LocaleManager.getInstance(this).getCurrentLanguage();
        }

        if (functionName.contains("尋找物品") || functionName.contains("Find Items") || functionName.contains("FindItems")) {
            intent = new Intent(this, FindItemsActivity.class);
        } else if (functionName.contains("環境識別") || functionName.contains("Environment") || functionName.contains("EnvironmentActivity")) {
            intent = new Intent(this, RealAIDetectionActivity.class);
        } else if (functionName.contains("文檔助手") || functionName.contains("Document") || functionName.contains("DocumentAssistant")) {
            intent = new Intent(this, DocumentCurrencyActivity.class);
        } else if (functionName.contains("出行協助") || functionName.contains("Travel") || functionName.contains("TravelAssistant")) {
            intent = new Intent(this, TravelAssistantActivity.class);
        } else if (functionName.contains("即時協助") || functionName.contains("InstantAssistance")) {
            intent = new Intent(this, InstantAssistanceActivity.class);
        } else if (functionName.contains("語音命令") || functionName.contains("Voice Command") || functionName.contains("VoiceCommand")) {
            intent = new Intent(this, VoiceCommandActivity.class);
        }

        if (intent != null) {
            if (language == null || language.isEmpty()) {
                language = LocaleManager.getInstance(this).getCurrentLanguage();
            }
            localeManager.setLanguage(this, language);
            intent.putExtra("language", language);
            intent.putExtra("from_gesture_login", true);

            final Intent finalIntent = intent;

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                startActivity(finalIntent);
                finish();
            }, 500);
        } else {
            Log.w(TAG, "未找到匹配的功能：" + functionName);
            gestureDrawView.clear();
        }
    }

    private void updateLanguageUI() {
        if (pageTitle != null) {
            String title;
            if ("english".equals(currentLanguage)) {
                title = "Gesture Login";
            } else if ("mandarin".equals(currentLanguage)) {
                title = "手势登录";
            } else {
                title = "手勢登入";
            }
            pageTitle.setText(title);
            pageTitle.setContentDescription(title);
        }

        if (instructionText != null) {
            String instruction;
            if ("english".equals(currentLanguage)) {
                instruction = "Shake your phone twice to enter home, or draw a saved gesture for its function";
            } else if ("mandarin".equals(currentLanguage)) {
                instruction = "摇晃手机两下进入主页，或绘制已保存的手势进入对应功能";
            } else {
                instruction = "搖晃手機兩下進入主頁，或繪製已保存的手勢進入對應功能";
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

    private void announceInstructions() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            String cantoneseText = "手勢登入頁面。請搖晃手機兩下進入主頁，或繪製已保存的手勢進入對應功能。";
            String englishText = "Gesture login page. Shake your phone twice to enter home, or draw a saved gesture for its function.";
            String mandarinText = "手势登录页面。请摇晃手机两下进入主页，或绘制已保存的手势进入对应功能。";

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

    private class ShakeDetector implements SensorEventListener {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event == null || event.sensor == null) {
                return;
            }
            if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) {
                return;
            }

            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            float acceleration = (float) Math.sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH;

            if (Math.abs(acceleration) > SHAKE_THRESHOLD) {
                runOnUiThread(GestureInputActivity.this::onShakePeakDetected);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // no-op
        }
    }
}
