package com.example.tonbo_app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.telephony.SmsManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageProxy;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.CameraPosition;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.LatLngBounds;
import com.amap.api.maps.model.Polyline;
import com.amap.api.maps.model.PolylineOptions;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 導航頁：規劃好路線後跳轉到此頁，播報「前行100米/左转/右转」+ 路面障礙檢測。
 * 不需豆包 API，不需 Google 導航；使用高德路線步驟 + 本機 YOLO。
 */
public class NavigationActivity extends BaseAccessibleActivity {
    private static final String TAG = "NavigationActivity";
    private static final String EXTRA_DESTINATION = "destination";
    private static final String EXTRA_LANGUAGE = "language";
    private static final int REQUEST_LOCATION_FOR_NAV = 200;
    private static final int REQUEST_CAMERA_FOR_WALK = 202;
    private static final int REQUEST_EMERGENCY_PERMISSIONS = 0xE001;

    private TextView navTitle;
    private TextView navDestination;
    private TextView stepsText;
    private LinearLayout stepButtonsLayout;
    private Button btnRepeatStep;
    private Button btnNextStep;
    private View travelDetectionContainer;
    private PreviewView navPreviewView;

    private NavigationController navigationController;
    private TravelDetectionController travelDetectionController;
    private String destination;
    private String currentLanguage;
    /** 當前路線步驟（完成一段再播下一段，適合視障用戶） */
    private List<RoutePlanner.RouteStep> routeSteps = new ArrayList<>();
    private int currentStepIndex = 0;
    /** 路線播報期間禁止障礙播報（語音鎖） */
    private volatile boolean isRouteSpeaking = false;
    private long routeSpeakStartTime = 0;

    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private float currentAzimuth = 0f;
    private boolean hasOrientation = false;
    private boolean waitingForAlignment = false;
    /** 对齐后继续第一段时跳过 summary，只播 routeSentence + tip */
    private boolean resumeFirstSegmentAfterAlignment = false;

    /** 高德地圖：底層地圖顯示與實時定位藍點 */
    private MapView mapView;
    private AMap aMap;
    private long lastMapCameraTime = 0;
    private static final long MAP_CAMERA_THROTTLE_MS = 3000;
    private long lastFollowCameraTime = 0;
    private static final long FOLLOW_CAMERA_THROTTLE_MS = 2000;
    /** 當前實時定位坐標，作為路線規劃起點 */
    private LatLng currentLatLng = null;
    private boolean navigationStartTriggered = false;

    /** 接近轉彎時自動播報當前段指令的距離閾值（米） */
    private static final float TURN_REMIND_DISTANCE = 20f;

    /** 偏離路線檢測與自動重規劃 */
    private List<LatLng> routePoints = new ArrayList<>();
    /** 進度可視化：已走過為灰、未走為藍；drawRoute 繪製的整條藍線，首次 updateRouteProgress 時會被替換 */
    private Polyline fullRoutePolyline;
    private Polyline passedPolyline;
    private Polyline remainingPolyline;

    private static final float OFF_ROUTE_THRESHOLD = 78f;
    private long lastReplanTime = 0L;
    private static final long REPLAN_INTERVAL = 28000L;
    /** 進入導航態後短時間內不因 GPS 抖動觸發重算（毫秒） */
    private long navigatingStableSinceMs = 0L;
    private static final long OFF_ROUTE_GRACE_AFTER_NAV_MS = 22000L;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** 路線總距離/時長（用於頂部剩餘距離顯示，第二階段） */
    private String routeTotalDistanceKm = null;
    private String routeTotalDurationMin = null;
    private TextView remainingDistanceText = null;

    /** 步行輔助：YOLO 檢測 + 可步行路徑分析與播報 */
    private YoloDetector yoloDetector;
    private WalkAssistManager walkAssistManager;
    private TextToSpeech tts;
    private OverlayView overlayView;

    // 緊急位置分享 UI 與狀態
    private FloatingActionButton fabEmergencyLocation;
    private ProgressBar progressEmergencyLocation;
    private boolean isEmergencyPressing = false;
    private long emergencyPressStartTime = 0L;
    private Handler emergencyHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navigation);

//        if (1 == 1) {
//            Log.d(TAG, "start RoadCameraActivity");
//            startActivity(new Intent(this, RoadCameraActivity.class));
//            return;
//        }

        destination = getIntent() != null ? getIntent().getStringExtra(EXTRA_DESTINATION) : null;
        currentLanguage = getIntent() != null ? getIntent().getStringExtra(EXTRA_LANGUAGE) : null;
        if (currentLanguage == null) currentLanguage = LocaleManager.getInstance(this).getCurrentLanguage();

        if (TextUtils.isEmpty(destination)) {
            if (ttsManager != null) ttsManager.speak("未設定目的地", "No destination", true);
            finish();
            return;
        }

        navTitle = findViewById(R.id.nav_title);
        navDestination = findViewById(R.id.nav_destination);
        remainingDistanceText = findViewById(R.id.remaining_distance_text);
        stepsText = findViewById(R.id.steps_text);
        stepButtonsLayout = findViewById(R.id.step_buttons_layout);
        btnRepeatStep = findViewById(R.id.btn_repeat_step);
        btnNextStep = findViewById(R.id.btn_next_step);
        travelDetectionContainer = findViewById(R.id.travel_detection_container);
        navPreviewView = findViewById(R.id.nav_preview_view);

        // 高德地圖：隱私合規（未調用會導致白屏，8.1.0+ 必須）
        try {
            Class<?> clz = Class.forName("com.amap.api.maps.MapsInitializer");
            java.lang.reflect.Method show = clz.getMethod("updatePrivacyShow", Context.class, boolean.class, boolean.class);
            show.invoke(null, this, true, true);
            java.lang.reflect.Method agree = clz.getMethod("updatePrivacyAgree", Context.class, boolean.class);
            agree.invoke(null, this, true);
        } catch (Throwable t) {
            android.util.Log.w(TAG, "MapsInitializer privacy not found: " + t.getMessage());
        }

        // 高德地圖：底層顯示 + 藍點定位
        mapView = findViewById(R.id.mapView);
        if (mapView != null) {
            mapView.onCreate(savedInstanceState);
            aMap = mapView.getMap();
            if (aMap != null) {
                // 設置默認視野，避免白屏（無初始視野時地圖不載入瓦片）
                aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(22.3193, 114.1694), 15f));
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    aMap.setMyLocationEnabled(true);
                }
                if (aMap.getUiSettings() != null) {
                    aMap.getUiSettings().setMyLocationButtonEnabled(true);
                }
                // 監聽實時定位，用於路線規劃起點
                aMap.setOnMyLocationChangeListener(location -> {
                    if (location != null) {
                        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                        currentLatLng = latLng;

                        // 地圖自動跟隨定位：平滑移動 + 朝向旋轉（像真正導航），節流避免過於頻繁
                        long now = System.currentTimeMillis();
                        if (now - lastFollowCameraTime >= FOLLOW_CAMERA_THROTTLE_MS) {
                            lastFollowCameraTime = now;
                            float bearing = location.hasBearing() ? location.getBearing() : 0f;
                            CameraPosition cameraPosition = new CameraPosition(latLng, 18f, 0f, bearing);
                            aMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                        }

                        if (!navigationStartTriggered && destination != null && hasLocationPermission()) {
                            navigationStartTriggered = true;
                            android.location.Location loc = new android.location.Location("");
                            loc.setLatitude(currentLatLng.latitude);
                            loc.setLongitude(currentLatLng.longitude);
                            navigationController.setPlanningOrigin(loc);
                            navigationController.startNavigation(destination);
                        }

                        // 偏離路線檢測：僅在已進入導航態時重算，避免與規劃中狀態打架
                        if (navigationController != null
                                && navigationController.getCurrentState() == NavigationController.NavigationState.NAVIGATING
                                && isOffRoute(latLng)) {
                            long sinceNav = navigatingStableSinceMs > 0 ? now - navigatingStableSinceMs : Long.MAX_VALUE;
                            if (sinceNav >= OFF_ROUTE_GRACE_AFTER_NAV_MS
                                    && now - lastReplanTime >= REPLAN_INTERVAL
                                    && destination != null
                                    && routePoints.size() >= 2) {
                                lastReplanTime = now;
                                Toast.makeText(NavigationActivity.this, "已偏离路线，正在重新规划...", Toast.LENGTH_SHORT).show();
                                android.location.Location loc = new android.location.Location("");
                                loc.setLatitude(latLng.latitude);
                                loc.setLongitude(latLng.longitude);
                                navigationController.replanRoute(loc);
                            }
                        }

                        // 接近轉彎時自動播報下一步導航指令
                        checkTurnReminder(latLng);
                        updateRemainingDisplay(latLng);
                        updateRouteProgress(latLng);
                    }
                });
            }
        }

        navDestination.setText("目的地：" + destination);

        if (btnRepeatStep != null) {
            btnRepeatStep.setOnClickListener(v -> {
                if (vibrationManager != null) vibrationManager.vibrateClick();
                speakCurrentStep();
            });
        }
        if (btnNextStep != null) {
            btnNextStep.setOnClickListener(v -> {
                if (vibrationManager != null) vibrationManager.vibrateClick();
                advanceToNextStep();
            });
        }

        ImageButton backButton = findViewById(R.id.back_button);
        if (backButton != null) {
            backButton.setOnClickListener(v -> {
                if (vibrationManager != null) vibrationManager.vibrateClick();
                if (ttsManager != null) ttsManager.stopSpeaking();
                if (navigationController != null) navigationController.stopNavigation();
                if (travelDetectionController != null) travelDetectionController.stop();
                finish();
            });
        }

        if (navPreviewView != null) {
            travelDetectionController = new TravelDetectionController(this, navPreviewView, currentLanguage);
        }

        overlayView = findViewById(R.id.nav_overlay_view);
        tts = new TextToSpeech(this, status -> {});
        walkAssistManager = new WalkAssistManager(tts);
        try {
            yoloDetector = new YoloDetector(this);
        } catch (Exception e) {
            android.util.Log.e(TAG, "YOLO init failed: " + (e != null ? e.getMessage() : ""));
        }

        initEmergencyLocationButton();
        if (travelDetectionController != null && yoloDetector != null) {
            travelDetectionController.setWalkAssistAnalyzer(this::analyzeWalkAssistFrame);
        }

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }

        navigationController = new NavigationController(this);
        navigationController.setNavigationListener(new NavigationController.NavigationListener() {
            @Override
            public void onLocationObtained(android.location.Location location) {
                moveMapToLocation(location);
            }

            @Override
            public void onRoutePlanned(String routeInfo) {
                if (stepsText != null) stepsText.setText(routeInfo);
                // 不在这里播报，避免和第一段冲突
            }

            @Override
            public void onRouteSummary(String totalDistanceKm, String totalDurationMin) {
                routeTotalDistanceKm = totalDistanceKm;
                routeTotalDurationMin = totalDurationMin;
                if (remainingDistanceText != null && (totalDistanceKm != null || totalDurationMin != null)) {
                    remainingDistanceText.setVisibility(View.VISIBLE);
                    updateRemainingDisplay(currentLatLng);
                }
            }

            @Override
            public void onRoutePolyline(String polyline) {
                drawRoute(polyline);
            }

            @Override
            public void onRouteSteps(List<RoutePlanner.RouteStep> steps) {
                if (steps == null || steps.isEmpty()) return;

                routeSteps.clear();
                routeSteps.addAll(steps);
                currentStepIndex = 0;

                if (stepButtonsLayout != null) {
                    stepButtonsLayout.setVisibility(View.VISIBLE);
                }

                // 1️⃣ 先播报路线规划总结
                String summary = "前往" + destination + "的路线已规划。";
                if (ttsManager != null) {
                    ttsManager.speak(summary, summary, true);
                }

                // 2️⃣ 延迟播报第一段真实路线
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    speakCurrentStep();
                }, 2500);
            }

            @Override
            public void onNavigationStarted() {
                if (travelDetectionContainer != null) travelDetectionContainer.setVisibility(View.VISIBLE);
                // 視頻識別改為在 speakCurrentStep 第一段播報完成後再啟動，不在此處 start()
            }

            @Override
            public void onNavigationStopped() {
                if (travelDetectionController != null) travelDetectionController.stop();
                if (travelDetectionContainer != null) travelDetectionContainer.setVisibility(View.GONE);
                if (stepButtonsLayout != null) stepButtonsLayout.setVisibility(View.GONE);
            }

            @Override
            public void onLocationTimeout() {
                if (stepsText != null) stepsText.setText("定位较慢，已进入辅助模式。\n当前仅提供前方障碍播报，无路线规划。");
                if (navTitle != null) navTitle.setText("導航中（輔助）");
            }

            @Override
            public void onNavigationStateChanged(NavigationController.NavigationState state) {
                if (travelDetectionController != null) {
                    travelDetectionController.setNavigating(state == NavigationController.NavigationState.NAVIGATING);
                }
                if (state == NavigationController.NavigationState.NAVIGATING) {
                    navigatingStableSinceMs = System.currentTimeMillis();
                }
                if (navTitle != null) {
                    switch (state) {
                        case NAVIGATING: navTitle.setText("導航中"); break;
                        case PLANNING_ROUTE:
                            navTitle.setText("規劃路線中");
                            if (stepsText != null) stepsText.setText("正在規劃路線…");
                            break;
                        case ARRIVED: navTitle.setText("已到達"); break;
                        default: navTitle.setText("導航"); break;
                    }
                }
            }

            @Override
            public void onError(String error) {
                if (stepsText != null) stepsText.setText(error);
                if (ttsManager != null) ttsManager.speak(error, error, true);
            }

            @Override
            public void onLocationUpdate(android.location.Location location) {
                moveMapToLocation(location);
                if (location != null) {
                    LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                    checkTurnReminder(latLng);
                    updateRemainingDisplay(latLng);
                    updateRouteProgress(latLng);
                }
                tryAutoAdvanceByLocation(location);
            }
        });

        if (ttsManager != null) {
            ttsManager.setOnSpeechCompleteListener(id -> {

                if (id == null) return;

                switch (id) {

                    case "FIRST_ROUTE":
                        routeSpeakStartTime = System.currentTimeMillis();
                        ttsManager.speakWithId(
                                "现在为您开启实时障碍识别。请开始行走。",
                                "Starting real-time obstacle detection. Please begin walking.",
                                true,
                                "FIRST_ROUTE_FULL"
                        );
                        break;

                    case "FIRST_ROUTE_FULL":
                        startTravelDetectionAfterRouteIntro();

                        isRouteSpeaking = false;

                        if (travelDetectionController != null) {
                            travelDetectionController.setBlockSpeech(false);
                        }
                        break;
                }
            });
        }

        if (hasLocationPermission()) {
            // 起點改為地圖實時定位：等 OnMyLocationChangeListener 回調後再 startNavigation
            // 若 8 秒內未收到定位，提示後用 getSafeLocation 結果作為起點
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (navigationStartTriggered || navigationController == null || destination == null) return;
                if (currentLatLng == null) {
                    Toast.makeText(this, "正在获取当前位置，请稍候...", Toast.LENGTH_SHORT).show();
                }
                navigationStartTriggered = true;
                navigationController.startNavigation(destination);
            }, 8000);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_LOCATION_FOR_NAV);
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /** 步行輔助：由 TravelDetectionController 調用的單幀分析，執行 YOLO 檢測、疊加層更新與可步行路徑播報。 */
    private void analyzeWalkAssistFrame(@NonNull ImageProxy image) {
        if (yoloDetector == null) {
            image.close();
            return;
        }
        try {
            List<YoloDetector.DetectionResult> results = yoloDetector.detect(image);
            final int imageWidth = image.getWidth();
            final int imageHeight = image.getHeight();
            final List<YoloDetector.DetectionResult> resultList = results;
            image.close();

            runOnUiThread(() -> {
                if (overlayView == null) return;
                int vw = overlayView.getWidth();
                int vh = overlayView.getHeight();
                if (resultList == null || vw <= 0 || vh <= 0) {
                    overlayView.setDetections(null);
                    return;
                }
                float sx = (float) vw / imageWidth;
                float sy = (float) vh / imageHeight;
                List<Detection> detections = new ArrayList<>();
                for (YoloDetector.DetectionResult r : resultList) {
                    Rect box = r.getBoundingBox();
                    if (box == null) continue;
                    float left   = box.left   * sx;
                    float top    = box.top    * sy;
                    float right  = box.right  * sx;
                    float bottom = box.bottom * sy;
                    String label = r.getLabel() != null ? r.getLabel() : "";
                    float conf = r.getConfidence();
                    detections.add(new Detection(left, top, right, bottom, label, conf));
                }
                Collections.sort(detections, (a, b) -> Float.compare(b.getConfidence(), a.getConfidence()));
                overlayView.setDetections(detections);

                String state = walkAssistManager.analyzeWalkablePath(
                        detections,
                        overlayView.getWidth(),
                        overlayView.getHeight()
                );
                walkAssistManager.speak(state);
            });
        } catch (Exception e) {
            image.close();
        }
    }

    /**
     * 判斷當前位置是否偏離路線：取到折線各線段的最短距離（僅比頂點可避免「在兩點之間卻顯示偏離」）。
     */
    private boolean isOffRoute(LatLng current) {
        if (routePoints.size() < 2) return false;
        double minMeters = distanceToPolylineMeters(current, routePoints);
        return minMeters > OFF_ROUTE_THRESHOLD;
    }

    /** 點到折線（各段）的最短距離（米），用線段上採樣近似投影距離 */
    private static double distanceToPolylineMeters(LatLng p, List<LatLng> poly) {
        float[] r = new float[1];
        double min = Double.MAX_VALUE;
        for (int i = 0; i < poly.size() - 1; i++) {
            LatLng a = poly.get(i);
            LatLng b = poly.get(i + 1);
            min = Math.min(min, distancePointToSegmentMeters(p, a, b, r));
        }
        return min;
    }

    private static double distancePointToSegmentMeters(LatLng p, LatLng a, LatLng b, float[] reuse) {
        Location.distanceBetween(p.latitude, p.longitude, a.latitude, a.longitude, reuse);
        double ap = reuse[0];
        Location.distanceBetween(p.latitude, p.longitude, b.latitude, b.longitude, reuse);
        double bp = reuse[0];
        Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, reuse);
        double ab = reuse[0];
        if (ab < 2.0) return Math.min(ap, bp);
        int samples = Math.min(24, Math.max(4, (int) (ab / 25f)));
        double best = Math.min(ap, bp);
        for (int i = 1; i < samples; i++) {
            double t = i / (double) samples;
            double lat = a.latitude + t * (b.latitude - a.latitude);
            double lng = a.longitude + t * (b.longitude - a.longitude);
            Location.distanceBetween(p.latitude, p.longitude, lat, lng, reuse);
            best = Math.min(best, reuse[0]);
        }
        return best;
    }

    /** 首段路線播報結束後：顯示預覽區、請求相機權限並啟動路面/步行輔助檢測 */
    private void startTravelDetectionAfterRouteIntro() {
        if (isFinishing()) return;
        if (travelDetectionContainer != null) {
            travelDetectionContainer.setVisibility(View.VISIBLE);
        }
        if (travelDetectionController == null || navPreviewView == null) {
            Log.w(TAG, "startTravelDetectionAfterRouteIntro: controller or preview null");
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_FOR_WALK);
            return;
        }
        mainHandler.post(() -> {
            if (!isFinishing()) travelDetectionController.start();
        });
    }

    /** 將地圖視野移動到指定位置，解決白屏並跟隨用戶；定位更新時節流避免卡頓 */
    private void moveMapToLocation(android.location.Location location) {
        if (aMap == null || location == null) return;
        long now = System.currentTimeMillis();
        if (now - lastMapCameraTime < MAP_CAMERA_THROTTLE_MS) return;
        lastMapCameraTime = now;
        runOnUiThread(() -> {
            try {
                LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f));
            } catch (Throwable t) {
                android.util.Log.w(TAG, "moveMapToLocation: " + t.getMessage());
            }
        });
    }

    /**
     * 使用 RoutePlanner 返回的 polyline 數據在地圖上繪製藍色路線，並自動縮放到路線範圍。
     * polyline 格式：高德 "lng,lat;lng,lat;..."
     */
    private void drawRoute(String polylineStr) {
        if (aMap == null || polylineStr == null || polylineStr.isEmpty()) return;
        runOnUiThread(() -> {
            try {
                aMap.clear();
                if (ContextCompat.checkSelfPermission(NavigationActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        || ContextCompat.checkSelfPermission(NavigationActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    aMap.setMyLocationEnabled(true);
                }
                List<LatLng> points = new ArrayList<>();
                String[] pairs = polylineStr.split(";");
                for (String pair : pairs) {
                    String trimmed = pair.trim();
                    if (trimmed.isEmpty()) continue;
                    String[] lngLat = trimmed.split(",");
                    if (lngLat.length >= 2) {
                        double lng = Double.parseDouble(lngLat[0].trim());
                        double lat = Double.parseDouble(lngLat[1].trim());
                        points.add(new LatLng(lat, lng));
                    }
                }
                if (points.isEmpty()) return;

                routePoints = new ArrayList<>(points);
                if (fullRoutePolyline != null) { fullRoutePolyline.remove(); fullRoutePolyline = null; }
                if (passedPolyline != null) { passedPolyline.remove(); passedPolyline = null; }
                if (remainingPolyline != null) { remainingPolyline.remove(); remainingPolyline = null; }

                fullRoutePolyline = aMap.addPolyline(new PolylineOptions()
                        .addAll(points)
                        .width(12f)
                        .color(Color.BLUE));

                LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
                for (LatLng point : points) {
                    boundsBuilder.include(point);
                }
                LatLngBounds bounds = boundsBuilder.build();
                aMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
            } catch (Throwable t) {
                android.util.Log.w(TAG, "drawRoute: " + t.getMessage());
            }
        });
    }

    /**
     * 進度可視化：根據當前位置將路線分為已走（灰）與未走（藍），不影響 TTS、偏離檢測、轉彎提醒。
     */
    private void updateRouteProgress(LatLng current) {
        if (routePoints == null || routePoints.isEmpty() || aMap == null) return;
        int size = routePoints.size();
        int nearestIndex = 0;
        float minDistance = Float.MAX_VALUE;
        float[] results = new float[1];
        for (int i = 0; i < size; i++) {
            LatLng point = routePoints.get(i);
            Location.distanceBetween(
                    current.latitude, current.longitude,
                    point.latitude, point.longitude,
                    results
            );
            if (results[0] < minDistance) {
                minDistance = results[0];
                nearestIndex = i;
            }
        }
        final List<LatLng> passed = new ArrayList<>(routePoints.subList(0, nearestIndex));
        final List<LatLng> remaining = new ArrayList<>(routePoints.subList(nearestIndex, size));
        runOnUiThread(() -> {
            try {
                if (fullRoutePolyline != null) {
                    fullRoutePolyline.remove();
                    fullRoutePolyline = null;
                }
                if (passedPolyline != null) {
                    passedPolyline.remove();
                    passedPolyline = null;
                }
                if (remainingPolyline != null) {
                    remainingPolyline.remove();
                    remainingPolyline = null;
                }
                if (passed.size() >= 2) {
                    passedPolyline = aMap.addPolyline(new PolylineOptions()
                            .addAll(passed)
                            .width(12f)
                            .color(Color.GRAY));
                }
                if (remaining.size() >= 2) {
                    remainingPolyline = aMap.addPolyline(new PolylineOptions()
                            .addAll(remaining)
                            .width(12f)
                            .color(Color.BLUE));
                }
            } catch (Throwable t) {
                android.util.Log.w(TAG, "updateRouteProgress: " + t.getMessage());
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_LOCATION_FOR_NAV) {
            if (navigationController == null || TextUtils.isEmpty(destination)) return;
            // 無論同意與否都開始導航：無權限時控制器會進入輔助模式（僅障礙播報）
            navigationController.startNavigation(destination);
            if (grantResults.length > 0
                    && grantResults[0] != PackageManager.PERMISSION_GRANTED
                    && (grantResults.length <= 1 || grantResults[1] != PackageManager.PERMISSION_GRANTED)
                    && ttsManager != null) {
                ttsManager.speak("未取得位置權限，當前僅提供前方障礙播報",
                        "Location permission denied, only obstacle announcement is available",
                        true);
            }
            return;
        }

        if (requestCode == REQUEST_EMERGENCY_PERMISSIONS) {
            boolean smsGranted = hasSmsPermission();
            boolean locGranted = hasLocationPermission();

            if (!smsGranted || !locGranted) {
                if (ttsManager != null) {
                    ttsManager.speak(
                            null,
                            "Emergency location permission denied.",
                            true
                    );
                }
            } else {
                fetchLocationAndSendEmergency();
            }
            return;
        }

        if (requestCode == REQUEST_CAMERA_FOR_WALK) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (travelDetectionController != null) {
                    travelDetectionController.start();
                }
            } else if (ttsManager != null) {
                ttsManager.speak("未授予相機權限，無法開啟路面識別", "Camera permission denied, walk assist preview off", true);
            }
        }
    }

    @Override
    protected void announcePageTitle() {
        if (ttsManager != null) ttsManager.speak("導航頁，目的地 " + destination, "Navigation, destination " + destination, true);
    }

    // ======== 緊急位置分享：UI 初始化與長按邏輯 ========

    private void initEmergencyLocationButton() {
        fabEmergencyLocation = findViewById(R.id.fab_emergency_location);
        progressEmergencyLocation = findViewById(R.id.progress_emergency_location);

        if (fabEmergencyLocation == null || progressEmergencyLocation == null) {
            return;
        }

        progressEmergencyLocation.setMax(3000);
        progressEmergencyLocation.setProgress(0);
        progressEmergencyLocation.setVisibility(View.GONE);

        fabEmergencyLocation.setOnTouchListener(new View.OnTouchListener() {

            private final Runnable longPressRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!isEmergencyPressing) return;

                    long elapsed = System.currentTimeMillis() - emergencyPressStartTime;
                    if (elapsed >= 3000L) {
                        // 長按完成，觸發緊急位置分享
                        isEmergencyPressing = false;
                        progressEmergencyLocation.setVisibility(View.GONE);
                        progressEmergencyLocation.setProgress(0);

                        if (ttsManager != null) {
                            ttsManager.speak(
                                    null,
                                    "Emergency location sent.",
                                    true
                            );
                        }
                        startEmergencyLocationFlow();
                    }
                }
            };

            private final Runnable progressRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!isEmergencyPressing) return;
                    long elapsed = System.currentTimeMillis() - emergencyPressStartTime;
                    if (elapsed < 3000L) {
                        progressEmergencyLocation.setProgress((int) elapsed);
                        emergencyHandler.postDelayed(this, 50L);
                    }
                }
            };

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        isEmergencyPressing = true;
                        emergencyPressStartTime = System.currentTimeMillis();
                        progressEmergencyLocation.setVisibility(View.VISIBLE);
                        progressEmergencyLocation.setProgress(0);

                        if (vibrationManager != null) {
                            vibrationManager.vibrateLongPress();
                        }

                        if (ttsManager != null) {
                            ttsManager.speak(
                                    null,
                                    "Hold to send emergency location.",
                                    true
                            );
                        }

                        emergencyHandler.post(progressRunnable);
                        emergencyHandler.postDelayed(longPressRunnable, 3000L);
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (isEmergencyPressing) {
                            isEmergencyPressing = false;
                            emergencyHandler.removeCallbacks(longPressRunnable);
                            emergencyHandler.removeCallbacks(progressRunnable);
                            progressEmergencyLocation.setVisibility(View.GONE);
                            progressEmergencyLocation.setProgress(0);
                        }
                        return true;
                }
                return false;
            }
        });
    }

    // ======== 緊急位置分享：權限與定位 ========

    private boolean hasSmsPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestEmergencyPermissions() {
        ActivityCompat.requestPermissions(
                this,
                new String[] {
                        Manifest.permission.SEND_SMS,
                        Manifest.permission.ACCESS_FINE_LOCATION
                },
                REQUEST_EMERGENCY_PERMISSIONS
        );
    }

    private void startEmergencyLocationFlow() {
        if (!hasSmsPermission() || !hasLocationPermission()) {
            if (ttsManager != null) {
                ttsManager.speak(
                        null,
                        "Permissions required for emergency location. Please allow SMS and location.",
                        true
                );
            }
            requestEmergencyPermissions();
            return;
        }

        fetchLocationAndSendEmergency();
    }

    private void fetchLocationAndSendEmergency() {
        LatLng latLng = currentLatLng;
        if (latLng == null) {
            if (ttsManager != null) {
                ttsManager.speak(
                        null,
                        "Failed to get current location.",
                        true
                );
            }
            return;
        }

        double lat = latLng.latitude;
        double lng = latLng.longitude;

        String batteryText = EmergencyLocationHelper.getBatteryLevelText(this);
        String message = EmergencyLocationHelper.buildEmergencyMessage(
                lat,
                lng,
                destination,
                batteryText
        );

        sendEmergencySms(message);
    }

    private void sendEmergencySms(String message) {
        try {
            // 從「緊急聯絡人」設置中讀取號碼列表（假設由緊急設置頁面統一寫入）
            // SharedPreferences 名稱與 key 可與緊急設置模塊對齊
            android.content.SharedPreferences sp =
                    getSharedPreferences("emergency_settings", Context.MODE_PRIVATE);
            String raw = sp.getString("emergency_contact_numbers", "");

            if (raw == null) raw = "";
            String[] parts = raw.split("[,;\\n]");
            java.util.List<String> phones = new java.util.ArrayList<>();
            for (String p : parts) {
                if (p == null) continue;
                String t = p.trim();
                if (!t.isEmpty()) phones.add(t);
            }

            if (phones.isEmpty()) {
                if (ttsManager != null) {
                    ttsManager.speak(
                            null,
                            "No emergency contacts set.",
                            true
                    );
                }
                return;
            }

            SmsManager smsManager = SmsManager.getDefault();
            for (String phone : phones) {
                smsManager.sendTextMessage(phone, null, message, null, null);
            }

            if (ttsManager != null) {
                ttsManager.speak(
                        null,
                        "Emergency location sent.",
                        true
                );
            }
        } catch (Exception e) {
            if (ttsManager != null) {
                ttsManager.speak(
                        null,
                        "Failed to send emergency message.",
                        true
                );
            }
        }
    }

    /** 溫柔陪伴式路線播報；第一段時加語音鎖並在播報內容後延遲啟動視頻識別。 */
    private void speakCurrentStep() {
        if (routeSteps.isEmpty() || ttsManager == null) return;
        if (currentStepIndex >= routeSteps.size()) {
            ttsManager.speak("已是最後一段，即將到達目的地", "Last segment, arriving at destination soon", true);
            return;
        }

        if (currentStepIndex == 0) {

            if (waitingForAlignment) return;

            isRouteSpeaking = true;

            if (travelDetectionController != null)
                travelDetectionController.setBlockSpeech(true);

            RoutePlanner.RouteStep s = routeSteps.get(0);
            String routePhrase = buildGentleStepPhrase(s, 0);

            String routeSentence =
                    "前方是一段直路，大约"
                            + routePhrase.replace("我们先", "")
                            + "。我会在需要转弯时提醒您。";

            // ✅ 如果是对齐后恢复，不再播 summary
            if (resumeFirstSegmentAfterAlignment) {

                resumeFirstSegmentAfterAlignment = false;

                ttsManager.speakWithId(
                        routeSentence,
                        routeSentence,
                        true,
                        "FIRST_ROUTE"
                );

                return;
            }

            // ✅ 正常第一次进入流程

            String summary = "前往" + destination + "的路线已规划。";

            ttsManager.speak(summary, summary, true);

            // ✅ 方向判断
            if (hasOrientation && s.startLat != null && s.startLng != null
                    && s.endLat != null && s.endLng != null) {

                float routeBearing = calculateBearing(
                        s.startLat,
                        s.startLng,
                        s.endLat,
                        s.endLng
                );

                float diff = routeBearing - currentAzimuth;

                if (diff > 180) diff -= 360;
                if (diff < -180) diff += 360;

                if (Math.abs(diff) > 15) {

                    String adjust =
                            diff > 0 ?
                                    "请向左调整方向。" :
                                    "请向右调整方向。";

                    ttsManager.speak(adjust, adjust, true);

                    waitingForAlignment = true;
                    return;
                }
            }

            // ✅ 方向正确直接播路线
            ttsManager.speakWithId(
                    "当前方向正确。" + routeSentence,
                    "Direction aligned. " + routeSentence,
                    true,
                    "FIRST_ROUTE"
            );

            return;
        }

        isRouteSpeaking = true;
        if (travelDetectionController != null) travelDetectionController.setBlockSpeech(true);

        RoutePlanner.RouteStep s = routeSteps.get(currentStepIndex);
        String routePhrase = buildGentleStepPhrase(s, currentStepIndex);
        String toSpeak = routePhrase;
        String toSpeakEn = routePhrase;
        ttsManager.speak(toSpeak, toSpeakEn, true);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            isRouteSpeaking = false;
            if (travelDetectionController != null) travelDetectionController.setBlockSpeech(false);
        }, 3000);
    }

    /** 使用 RoutePlanner 返回的真实 instruction，仅做距离模糊（如 298米→约300米）及首段/后续前缀。 */
    private String buildGentleStepPhrase(RoutePlanner.RouteStep s, int index) {
        String instruction = s.instruction != null ? s.instruction : "";

        // 模糊距离，例如 298米 → 约300米
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(\\d+)米");
        java.util.regex.Matcher m = p.matcher(instruction);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            try {
                int meters = Integer.parseInt(m.group(1));
                int fuzzy = meters < 100 ? 100 : (int) (Math.round(meters / 50.0) * 50);
                m.appendReplacement(sb, "约" + fuzzy + "米");
            } catch (Exception e) {
                m.appendReplacement(sb, m.group(0));
            }
        }
        m.appendTail(sb);
        instruction = sb.toString();

        if (index == 0) {
            return "我们先" + instruction;
        } else {
            return "接下来，" + instruction;
        }
    }

    /** 下一段：完成當前段後由用戶主動觸發，再播下一段 */
    private void advanceToNextStep() {
        if (routeSteps.isEmpty() || ttsManager == null) return;
        if (currentStepIndex + 1 >= routeSteps.size()) {
            ttsManager.speak("已是最後一段，即將到達目的地", "Last segment, arriving at destination soon", true);
            return;
        }
        currentStepIndex++;
        speakCurrentStep();
    }

    /** 依定位自動切換：接近當前段終點時播報下一段（步行約 35 米內視為到達段終點） */
    private static final float AUTO_ADVANCE_DISTANCE_METERS = 35f;

    /**
     * 接近轉彎時自動播報當前段導航指令（如「前方右转」「直行 100 米」），播報後切到下一段。
     * 不影響原有 TTS 與偏離檢測，僅在距離當前段終點小於 TURN_REMIND_DISTANCE 時觸發一次。
     */
    private void checkTurnReminder(LatLng current) {
        if (currentStepIndex >= routeSteps.size() || ttsManager == null) return;
        RoutePlanner.RouteStep step = routeSteps.get(currentStepIndex);
        if (step.endLat == null || step.endLng == null) return;
        float[] results = new float[1];
        Location.distanceBetween(
                current.latitude,
                current.longitude,
                step.endLat,
                step.endLng,
                results
        );
        if (results[0] < TURN_REMIND_DISTANCE && step.instruction != null && !step.instruction.isEmpty()) {
            ttsManager.speak(step.instruction, step.instruction, true);
            currentStepIndex++;
        }
    }

    private void tryAutoAdvanceByLocation(android.location.Location userLocation) {
        if (routeSteps.isEmpty() || currentStepIndex >= routeSteps.size() || ttsManager == null) return;
        RoutePlanner.RouteStep step = routeSteps.get(currentStepIndex);
        if (step.endLat == null || step.endLng == null) return;
        android.location.Location stepEnd = new android.location.Location("step");
        stepEnd.setLatitude(step.endLat);
        stepEnd.setLongitude(step.endLng);
        float distanceMeters = userLocation.distanceTo(stepEnd);
        if (distanceMeters <= AUTO_ADVANCE_DISTANCE_METERS && currentStepIndex + 1 < routeSteps.size()) {
            currentStepIndex++;
            speakCurrentStep();
        }
    }

    private float distanceBetween(double lat1, double lng1, double lat2, double lng2) {
        float[] results = new float[1];
        android.location.Location.distanceBetween(lat1, lng1, lat2, lng2, results);
        return results[0];
    }

    /** 根據當前位置與當前步驟索引計算剩餘距離（米）並更新頂部「剩余 XXX 米 / 约 X 分钟」。 */
    private void updateRemainingDisplay(LatLng current) {
        if (remainingDistanceText == null || routeSteps.isEmpty()) return;
        int totalMeters = 0;
        for (RoutePlanner.RouteStep s : routeSteps) {
            totalMeters += s.distanceMeters;
        }
        if (totalMeters <= 0 && routeTotalDistanceKm != null) {
            try {
                totalMeters = (int) (Double.parseDouble(routeTotalDistanceKm) * 1000);
            } catch (NumberFormatException ignored) {}
        }
        int remainingMeters = totalMeters;
        if (current != null && currentStepIndex < routeSteps.size()) {
            RoutePlanner.RouteStep step = routeSteps.get(currentStepIndex);
            if (step.endLat != null && step.endLng != null) {
                float toStepEnd = distanceBetween(current.latitude, current.longitude, step.endLat, step.endLng);
                remainingMeters = (int) toStepEnd;
                for (int i = currentStepIndex + 1; i < routeSteps.size(); i++) {
                    remainingMeters += routeSteps.get(i).distanceMeters;
                }
            } else {
                remainingMeters = 0;
                for (int i = currentStepIndex; i < routeSteps.size(); i++) {
                    remainingMeters += routeSteps.get(i).distanceMeters;
                }
            }
        } else if (currentStepIndex >= routeSteps.size()) {
            remainingMeters = 0;
        }
        remainingMeters = Math.max(0, remainingMeters);
        int remainingMin = totalMeters > 0 && routeTotalDurationMin != null
                ? (int) Math.max(1, (double) remainingMeters / totalMeters * parseDurationMin(routeTotalDurationMin))
                : 0;
        String distStr = remainingMeters >= 1000
                ? String.format("%.1f公里", remainingMeters / 1000.0)
                : remainingMeters + "米";
        String line1 = "剩余 " + distStr;
        String line2 = remainingMin > 0 ? "约 " + remainingMin + " 分钟" : "";
        String text = line2.isEmpty() ? line1 : (line1 + "\n" + line2);
        remainingDistanceText.setText(text);
    }

    private int parseDurationMin(String s) {
        if (s == null || s.isEmpty()) return 0;
        try {
            return Integer.parseInt(s.trim().replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private final SensorEventListener orientationListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                float[] rotationMatrix = new float[9];
                float[] orientation = new float[3];

                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
                SensorManager.getOrientation(rotationMatrix, orientation);

                float azimuthRad = orientation[0];
                float azimuthDeg = (float) Math.toDegrees(azimuthRad);

                currentAzimuth = (azimuthDeg + 360) % 360;
                hasOrientation = true;

                if (waitingForAlignment && currentStepIndex == 0 && !routeSteps.isEmpty()) {

                    RoutePlanner.RouteStep firstStep = routeSteps.get(0);

                    if (firstStep.startLat != null && firstStep.startLng != null
                            && firstStep.endLat != null && firstStep.endLng != null) {

                        float routeBearing = calculateBearing(
                                firstStep.startLat,
                                firstStep.startLng,
                                firstStep.endLat,
                                firstStep.endLng
                        );

                        float diff = routeBearing - currentAzimuth;

                        if (diff > 180) diff -= 360;
                        if (diff < -180) diff += 360;

                        if (Math.abs(diff) <= 12) {

                            waitingForAlignment = false;

                            runOnUiThread(() -> {
                                resumeFirstSegmentAfterAlignment = true;
                                speakCurrentStep();
                            });
                        }
                    }
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    private float calculateBearing(double lat1, double lon1, double lat2, double lon2) {
        double dLon = Math.toRadians(lon2 - lon1);
        lat1 = Math.toRadians(lat1);
        lat2 = Math.toRadians(lat2);

        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2)
                - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);

        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (float) ((bearing + 360) % 360);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
        if (sensorManager != null && rotationSensor != null) {
            sensorManager.registerListener(orientationListener, rotationSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(orientationListener);
        }
        if (mapView != null) {
            mapView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (yoloDetector != null) {
            yoloDetector.close();
        }
        if (travelDetectionController != null) {
            travelDetectionController.stop();
            travelDetectionController = null;
        }
        if (navigationController != null) {
            navigationController.stopNavigation();
            navigationController = null;
        }
        if (mapView != null) {
            mapView.onDestroy();
            mapView = null;
        }
        aMap = null;
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) {
            mapView.onSaveInstanceState(outState);
        }
    }
}
