package com.example.tonbo_app;

import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.ContextCompat;

/**
 * 導航控制器
 * 使用 LocationManager 作為主定位方式，不依賴 Google Play Services，適用於中國大陸/港澳台環境。
 */
public class NavigationController {
    private static final String TAG = "NavigationController";

    private Context context;
    private LocationManager locationManager;
    private TTSManager ttsManager;
    private NavigationListener listener;
    private RoutePlanner routePlanner;

    // 導航狀態
    private NavigationState currentState = NavigationState.IDLE;
    private Location currentLocation;
    private String destination;
    /** 由 Activity 設置的規劃起點（地圖實時定位），優先於 getSafeLocation 結果 */
    private Location planningOriginFromActivity;

    /**
     * 導航狀態枚舉
     */
    public enum NavigationState {
        IDLE,                    // 空閒
        GETTING_LOCATION,        // 正在獲取位置
        PLANNING_ROUTE,          // 正在規劃路線
        NAVIGATING,              // 導航中
        ARRIVED,                 // 已到達
        ERROR                    // 錯誤
    }

    /**
     * 導航監聽器接口
     */
    public interface NavigationListener {
        void onLocationObtained(Location location);
        void onRoutePlanned(String routeInfo);
        /** 路線總距離（公里字串，如 "0.8"）、總時長（分鐘字串），用於剩餘距離顯示 */
        void onRouteSummary(String totalDistanceKm, String totalDurationMin);
        /** 轉向步驟（前行100米、左转、右转），可為空 */
        void onRouteSteps(java.util.List<RoutePlanner.RouteStep> steps);
        /** 完整路線 polyline 字串（高德格式 "lng,lat;lng,lat;..."），供地圖繪製藍線，可為空 */
        void onRoutePolyline(String polyline);
        void onNavigationStarted();
        void onNavigationStopped();
        /** 定位超時，未進入路線規劃，僅輔助模式；用於更新 UI 避免一直顯示「路線規劃中」 */
        void onLocationTimeout();
        void onNavigationStateChanged(NavigationState state);
        void onError(String error);
        /** 導航中週期性定位更新，用於依位置自動切換下一段 */
        void onLocationUpdate(Location location);
    }

    /**
     * 構造函數
     */
    public NavigationController(Context context) {
        this.context = context.getApplicationContext();
        this.locationManager = (LocationManager) this.context.getSystemService(Context.LOCATION_SERVICE);
        this.ttsManager = TTSManager.getInstance(this.context);
        this.routePlanner = new RoutePlanner();
        routePlanner.setContext(this.context);
        routePlanner.setApiProvider("amap");
    }

    /**
     * 設置導航監聽器
     */
    public void setNavigationListener(NavigationListener listener) {
        this.listener = listener;
    }

    /** 設置路線規劃起點（由 Activity 從地圖實時定位傳入），格式為當前實時坐標 */
    public void setPlanningOrigin(Location origin) {
        this.planningOriginFromActivity = origin;
    }

    /** 偏離路線後觸發重新規劃：以給定位置為起點、當前目的地不變，重新調用路線規劃 */
    public void replanRoute(Location origin) {
        if (origin == null || destination == null || destination.isEmpty()) return;
        this.planningOriginFromActivity = origin;
        this.currentLocation = origin;
        planRoute();
    }

    private static final long LOCATION_REQUEST_TIMEOUT_MS = 8000;
    private static final long ROUTE_PLANNING_TIMEOUT_MS = 20000;

    private volatile boolean isGettingLocation = false;
    private volatile boolean locationResolved = false;
    private LocationSuccessCallback pendingLocationSuccessCallback;
    private Handler locationRequestHandler;
    private Runnable locationRequestTimeoutRunnable;
    private volatile boolean routePlanningCompleted = false;
    private Runnable routePlanningTimeoutRunnable;
    private Handler routePlanningHandler;

    /** 單次定位請求的 LocationListener：收到首個位置後 removeUpdates 並回調 */
    private final LocationListener oneShotLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            if (location == null) return;
            onOneShotLocationResult(location);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {}

        @Override
        public void onProviderEnabled(String provider) {}

        @Override
        public void onProviderDisabled(String provider) {}
    };

    /** 導航中週期定位的 LocationListener */
    private LocationListener navigationLocationListener;

    /** 定位成功回調（僅在 getSafeLocation 成功時調用） */
    private interface LocationSuccessCallback {
        void onSuccess(Location location);
    }

    /**
     * 開始導航：僅通過 getSafeLocation 獲取位置，成功後規劃路線，失敗則播報並停止。
     */
    public void startNavigation(String destination) {
        Log.d(TAG, "startNavigation()");
        if (destination == null || destination.trim().isEmpty()) {
            String errorMsg = getLocalizedString("navigation_error_no_destination");
            Log.e(TAG, errorMsg);
            if (listener != null) listener.onError(errorMsg);
            return;
        }
        this.destination = destination.trim();
        Log.d(TAG, "開始導航到: " + this.destination);
        setState(NavigationState.GETTING_LOCATION);
        getSafeLocation(loc -> {
            currentLocation = loc;
            if (listener != null) listener.onLocationObtained(loc);
            planRoute();
        });
    }

    /**
     * 使用 LocationManager 的定位流程：
     * 1. 優先 getLastKnownLocation(GPS_PROVIDER)
     * 2. 為空則 getLastKnownLocation(NETWORK_PROVIDER)
     * 3. 仍為空則 requestLocationUpdates(GPS_PROVIDER)，8 秒超時；成功則 removeUpdates，超時則 speakLocationFailedAndStop()
     */
    private void getSafeLocation(LocationSuccessCallback onSuccess) {
        Log.d(TAG, "getSafeLocation()");
        if (onSuccess == null) return;
        if (isGettingLocation) {
            Log.w(TAG, "已有定位請求進行中，忽略重複請求");
            return;
        }
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "PERMISSION not GRANTED");
            speakLocationFailedAndStop();
            return;
        }
        if (locationManager == null) {
            Log.w(TAG, "LocationManager is null");
            speakLocationFailedAndStop();
            return;
        }
        isGettingLocation = true;
        locationResolved = false;

        // 1. 優先 GPS 緩存
        try {
            Location loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (loc != null) {
                locationResolved = true;
                isGettingLocation = false;
                Log.d(TAG, "位置獲取成功(lastKnown GPS): " + loc.getLatitude() + ", " + loc.getLongitude());
                onSuccess.onSuccess(loc);
                return;
            }
        } catch (SecurityException ignored) {}

        // 2. 再試網絡緩存
        try {
            Location loc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (loc != null) {
                locationResolved = true;
                isGettingLocation = false;
                Log.d(TAG, "位置獲取成功(lastKnown NETWORK): " + loc.getLatitude() + ", " + loc.getLongitude());
                onSuccess.onSuccess(loc);
                return;
            }
        } catch (SecurityException ignored) {}

        // 3. 請求一次實時定位，8 秒超時
        pendingLocationSuccessCallback = onSuccess;
        locationRequestHandler = new Handler(Looper.getMainLooper());
        locationRequestTimeoutRunnable = () -> {
            if (locationResolved) return;
            locationResolved = true;
            isGettingLocation = false;
            pendingLocationSuccessCallback = null;
            try {
                locationManager.removeUpdates(oneShotLocationListener);
            } catch (SecurityException ignored) {}
            Log.w(TAG, "8 秒內未獲取到位置，停止導航啟動");
            speakLocationFailedAndStop();
        };
        locationRequestHandler.postDelayed(locationRequestTimeoutRunnable, LOCATION_REQUEST_TIMEOUT_MS);
        try {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    0,
                    0,
                    oneShotLocationListener,
                    Looper.getMainLooper()
            );
        } catch (SecurityException e) {
            if (locationRequestHandler != null && locationRequestTimeoutRunnable != null)
                locationRequestHandler.removeCallbacks(locationRequestTimeoutRunnable);
            pendingLocationSuccessCallback = null;
            isGettingLocation = false;
            speakLocationFailedAndStop();
        }
    }

    /** 收到單次 requestLocationUpdates 的位置：移除訂閱、取消超時、回調 onSuccess */
    private void onOneShotLocationResult(Location loc) {
        if (locationResolved) return;
        locationResolved = true;
        isGettingLocation = false;
        if (locationRequestHandler != null && locationRequestTimeoutRunnable != null) {
            locationRequestHandler.removeCallbacks(locationRequestTimeoutRunnable);
            locationRequestTimeoutRunnable = null;
        }
        try {
            locationManager.removeUpdates(oneShotLocationListener);
        } catch (SecurityException ignored) {}
        Log.d(TAG, "位置獲取成功(requestLocationUpdates): " + loc.getLatitude() + ", " + loc.getLongitude());
        LocationSuccessCallback cb = pendingLocationSuccessCallback;
        pendingLocationSuccessCallback = null;
        if (cb != null) cb.onSuccess(loc);
    }

    /** 定位失敗：播報並停止導航啟動流程 */
    private void speakLocationFailedAndStop() {
        String msg = getLocalizedString("navigation_location_failed_check_permission");
        if (ttsManager != null) ttsManager.speak(msg, msg, true);
        setState(NavigationState.IDLE);
        if (listener != null) listener.onLocationTimeout();
    }

    /**
     * 規劃路線（使用真實 API）
     */
    private void planRoute() {
        Log.w(TAG, "planRoute()");
        Location origin = (planningOriginFromActivity != null) ? planningOriginFromActivity : currentLocation;
        planningOriginFromActivity = null;
        if (origin == null) {
            Log.w(TAG, "planRoute: 無位置，跳過路線規劃，保持輔助模式");
            return;
        }

        setState(NavigationState.PLANNING_ROUTE);
        String statusMsg = getLocalizedString("navigation_planning_route");
        announceStatus(statusMsg);

        routePlanningCompleted = false;
        routePlanningHandler = new Handler(Looper.getMainLooper());
        routePlanningTimeoutRunnable = () -> {
            if (routePlanningCompleted) return;
            routePlanningCompleted = true;
            Log.w(TAG, "路線規劃 " + (ROUTE_PLANNING_TIMEOUT_MS / 1000) + " 秒超時，保持輔助模式");
            setState(NavigationState.NAVIGATING);
            String timeoutMsg = getLocalizedString("navigation_route_planning_timeout");
            announceStatus(timeoutMsg);
            if (listener != null) listener.onError(timeoutMsg);
        };
        routePlanningHandler.postDelayed(routePlanningTimeoutRunnable, ROUTE_PLANNING_TIMEOUT_MS);

        Handler mainHandler = new Handler(Looper.getMainLooper());
        routePlanner.planRoute(origin, destination, new RoutePlanner.RoutePlanningCallback() {
            @Override
            public void onRoutePlanned(RoutePlanner.RouteResult result) {
                mainHandler.post(() -> {
                    if (routePlanningCompleted) return;
                    routePlanningCompleted = true;
                    if (routePlanningHandler != null && routePlanningTimeoutRunnable != null)
                        routePlanningHandler.removeCallbacks(routePlanningTimeoutRunnable);
                    if (result.success) {
                        String routeInfo = String.format(
                                getLocalizedString("navigation_route_info"),
                                destination,
                                result.distance != null ? result.distance : "—",
                                result.duration != null ? result.duration : "—"
                        );
                        Log.d(TAG, "路線規劃完成: " + routeInfo);
                        if (listener != null) listener.onRoutePlanned(routeInfo);
                        if (listener != null) {
                            listener.onRouteSummary(
                                    result.distance != null ? result.distance : null,
                                    result.duration != null ? result.duration : null
                            );
                        }
                        if (listener != null && result.steps != null && !result.steps.isEmpty()) {
                            listener.onRouteSteps(result.steps);
                        }
                        if (listener != null && result.routePolyline != null && !result.routePolyline.isEmpty()) {
                            listener.onRoutePolyline(result.routePolyline);
                        }
                        startNavigating();
                    } else {
                        String errorMsg = result.errorMessage != null ? result.errorMessage : "路線規劃失敗";
                        Log.w(TAG, "路線規劃失敗（保持輔助模式）: " + errorMsg);
                        setState(NavigationState.NAVIGATING);
                        if (listener != null) listener.onError(errorMsg);
                    }
                });
            }

            @Override
            public void onError(String error) {
                Log.w(TAG, "路線規劃錯誤（保持輔助模式）: " + error);
                mainHandler.post(() -> {
                    if (routePlanningCompleted) return;
                    routePlanningCompleted = true;
                    if (routePlanningHandler != null && routePlanningTimeoutRunnable != null)
                        routePlanningHandler.removeCallbacks(routePlanningTimeoutRunnable);
                    setState(NavigationState.NAVIGATING);
                    if (listener != null) listener.onError(error);
                });
            }
        });
    }

    /**
     * 路線規劃成功後播報導航開始，並啟動週期性定位
     */
    private void startNavigating() {
        String statusMsg = getLocalizedString("navigation_started");
        announceStatus(statusMsg);
        setState(NavigationState.NAVIGATING);
        if (listener != null) listener.onNavigationStarted();
        Log.d(TAG, "路線已規劃，導航播報開始，目的地: " + destination);
        startNavigationLocationUpdates();
    }

    /** 導航中約每 10 秒更新一次位置（使用 LocationManager） */
    private void startNavigationLocationUpdates() {
        Log.d(TAG, "startNavigationLocationUpdates");
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "PERMISSION not GRANTED");
            return;
        }
        if (locationManager == null || navigationLocationListener != null) {
            Log.d(TAG, "locationManager null or already requesting updates");
            return;
        }
        navigationLocationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (location == null || currentState != NavigationState.NAVIGATING) return;
                if (listener != null) {
                    Log.d(TAG, "onLocationUpdate " + location);
                    listener.onLocationUpdate(location);
                }
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}

            @Override
            public void onProviderEnabled(String provider) {}

            @Override
            public void onProviderDisabled(String provider) {}
        };
        try {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    10_000,
                    5f,
                    navigationLocationListener,
                    Looper.getMainLooper()
            );
            Log.d(TAG, "導航中定位更新已啟動 (LocationManager)");
        } catch (SecurityException e) {
            Log.w(TAG, "導航定位更新權限異常: " + e.getMessage());
            navigationLocationListener = null;
        }
    }

    private void stopNavigationLocationUpdates() {
        if (navigationLocationListener != null && locationManager != null) {
            try {
                locationManager.removeUpdates(navigationLocationListener);
            } catch (SecurityException ignored) {}
            navigationLocationListener = null;
            Log.d(TAG, "導航中定位更新已停止");
        }
    }

    /**
     * 停止導航
     */
    public void stopNavigation() {
        if (currentState == NavigationState.NAVIGATING) {
            stopNavigationLocationUpdates();
            setState(NavigationState.IDLE);
            String statusMsg = getLocalizedString("navigation_stopped");
            announceStatus(statusMsg);
            if (listener != null) listener.onNavigationStopped();
            Log.d(TAG, "導航已停止");
        }
    }

    private void setState(NavigationState state) {
        if (currentState != state) {
            NavigationState oldState = currentState;
            currentState = state;
            Log.d(TAG, "導航狀態變化: " + oldState + " -> " + state);
            if (listener != null) {
                listener.onNavigationStateChanged(state);
            }
        }
    }

    public NavigationState getCurrentState() {
        return currentState;
    }

    public Location getCurrentLocationSync() {
        return currentLocation;
    }

    public String getDestination() {
        return destination;
    }

    private void announceStatus(String message) {
        if (ttsManager != null) {
            String currentLang = LocaleManager.getInstance(context).getCurrentLanguage();
            if ("english".equals(currentLang)) {
                ttsManager.speak(null, message, true);
            } else if ("mandarin".equals(currentLang)) {
                ttsManager.speak(message, null, true);
            } else {
                ttsManager.speak(message, message, true);
            }
        }
    }

    private String getLocalizedString(String key) {
        String currentLang = LocaleManager.getInstance(context).getCurrentLanguage();

        switch (key) {
            case "navigation_getting_location":
                if ("english".equals(currentLang)) {
                    return "Getting current location";
                } else if ("mandarin".equals(currentLang)) {
                    return "正在获取当前位置";
                } else {
                    return "正在獲取當前位置";
                }
            case "navigation_planning_route":
                if ("english".equals(currentLang)) {
                    return "Planning route";
                } else if ("mandarin".equals(currentLang)) {
                    return "正在规划路线";
                } else {
                    return "正在規劃路線";
                }
            case "navigation_started":
                if ("english".equals(currentLang)) {
                    return "Navigation started";
                } else if ("mandarin".equals(currentLang)) {
                    return "导航开始";
                } else {
                    return "導航開始";
                }
            case "navigation_stopped":
                if ("english".equals(currentLang)) {
                    return "Navigation stopped";
                } else if ("mandarin".equals(currentLang)) {
                    return "导航已停止";
                } else {
                    return "導航已停止";
                }
            case "navigation_route_info":
                if ("english".equals(currentLang)) {
                    return "Route to %s planned. Distance: %s km, Estimated time: %s minutes";
                } else if ("mandarin".equals(currentLang)) {
                    return "前往%s的路线已规划。距离：%s公里，预计时间：%s分钟";
                } else {
                    return "前往%s的路線已規劃。距離：%s公里，預計時間：%s分鐘";
                }
            case "navigation_error_no_destination":
                if ("english".equals(currentLang)) {
                    return "Destination not specified";
                } else if ("mandarin".equals(currentLang)) {
                    return "未指定目的地";
                } else {
                    return "未指定目的地";
                }
            case "navigation_error_location_unavailable":
                if ("english".equals(currentLang)) {
                    return "Location unavailable";
                } else if ("mandarin".equals(currentLang)) {
                    return "位置不可用";
                } else {
                    return "位置不可用";
                }
            case "navigation_location_slow_assist":
                if ("english".equals(currentLang)) {
                    return "Location is slow, assist mode is on";
                } else if ("mandarin".equals(currentLang)) {
                    return "定位较慢，已进入辅助模式";
                } else {
                    return "定位較慢，已進入輔助模式";
                }
            case "navigation_route_planning_timeout":
                if ("english".equals(currentLang)) {
                    return "Route planning timed out, assist mode is on";
                } else if ("mandarin".equals(currentLang)) {
                    return "路线规划超时，已保持辅助模式";
                } else {
                    return "路線規劃逾時，已保持輔助模式";
                }
            case "navigation_using_cached_location":
                if ("english".equals(currentLang)) {
                    return "Using cached location for route planning";
                } else if ("mandarin".equals(currentLang)) {
                    return "使用缓存位置进行路线规划";
                } else {
                    return "使用緩存位置進行路線規劃";
                }
            case "navigation_demo_origin":
                if ("english".equals(currentLang)) {
                    return "Demo start point in use. Route does not match your real location. Please enable device location for accurate navigation.";
                } else if ("mandarin".equals(currentLang)) {
                    return "当前使用演示起点，路线与您实际位置不符，请开启定位以获取真实导航";
                } else {
                    return "目前使用演示起點，路線與您實際位置不符，請開啟定位以取得真實導航";
                }
            case "navigation_location_off_hint":
                if ("english".equals(currentLang)) {
                    return "Please turn on location in system settings so we can use your position.";
                } else if ("mandarin".equals(currentLang)) {
                    return "请打开手机设置中的定位服务，以便获取您的位置";
                } else {
                    return "請開啟手機設定中的定位服務，以便取得您的位置";
                }
            case "navigation_demo_long_route_hint":
                if ("english".equals(currentLang)) {
                    return "This route is from a demo start point. For navigation from your actual location, please enable location.";
                } else if ("mandarin".equals(currentLang)) {
                    return "当前为演示起点，路线较远；请开启定位以从您的位置导航";
                } else {
                    return "目前為演示起點，路線較遠；請開啟定位以從您的位置導航";
                }
            case "navigation_error_location_failed":
                if ("english".equals(currentLang)) {
                    return "Failed to get location";
                } else if ("mandarin".equals(currentLang)) {
                    return "获取位置失败";
                } else {
                    return "獲取位置失敗";
                }
            case "navigation_error_permission":
                if ("english".equals(currentLang)) {
                    return "Location permission denied";
                } else if ("mandarin".equals(currentLang)) {
                    return "位置权限被拒绝";
                } else {
                    return "位置權限被拒絕";
                }
            case "navigation_error_unknown":
                if ("english".equals(currentLang)) {
                    return "Unknown error";
                } else if ("mandarin".equals(currentLang)) {
                    return "未知错误";
                } else {
                    return "未知錯誤";
                }
            case "navigation_location_failed_check_permission":
                if ("english".equals(currentLang)) {
                    return "Unable to get location. Please check location permission.";
                } else if ("mandarin".equals(currentLang)) {
                    return "无法获取定位，请检查定位权限";
                } else {
                    return "無法取得定位，請檢查定位權限";
                }
            default:
                return "";
        }
    }

    /**
     * 設置路線規劃 API 提供商
     * @param provider "google" 或 "amap"
     */
    public void setRouteApiProvider(String provider) {
        if (routePlanner != null) {
            routePlanner.setApiProvider(provider);
            Log.d(TAG, "路線規劃 API 提供商設置為: " + provider);
        }
    }

    /**
     * 清理資源
     */
    public void cleanup() {
        if (routePlanner != null) {
            routePlanner.cleanup();
            routePlanner = null;
        }
        listener = null;
        Log.d(TAG, "導航控制器資源已清理");
    }
}
