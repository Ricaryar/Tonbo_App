package com.example.tonbo_app;

import android.content.Context;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 路線規劃器
 * 支持 Google Maps Directions API 和高德地圖 API
 */
public class RoutePlanner {
    private static final String TAG = "RoutePlanner";
    
    // API 配置
    // 注意：請替換為您實際的 API Key
    // Google Maps API Key 獲取：https://console.cloud.google.com/google/maps-apis
    // 高德地圖 API Key 獲取：https://console.amap.com/dev/key/app
    private static final String GOOGLE_MAPS_API_KEY = "YOUR_GOOGLE_MAPS_API_KEY"; // 需要替換為實際的 API Key
    private static final String AMAP_API_KEY = "de266dbe1ed6b4fd37caa431871c5854"; // 高德地圖 Web 服務 Key
    
    // API 端點
    private static final String GOOGLE_DIRECTIONS_API_URL = "https://maps.googleapis.com/maps/api/directions/json";
    private static final String GOOGLE_GEOCODING_API_URL = "https://maps.googleapis.com/maps/api/geocode/json";
    private static final String AMAP_DIRECTIONS_API_URL = "https://restapi.amap.com/v3/direction/driving";
    /** 步行路線規劃（視障人士多為短距離步行） */
    private static final String AMAP_WALKING_API_URL = "https://restapi.amap.com/v3/direction/walking";
    private static final String AMAP_GEOCODING_API_URL = "https://restapi.amap.com/v3/geocode/geo";
    
    // 使用的 API 提供商（google 或 amap）
    private String apiProvider = "google"; // 默認使用 Google Maps
    
    private OkHttpClient httpClient;
    private Gson gson;
    private ExecutorService executorService;
    private Context context;
    
    /** 單一步驟（用於轉向播報：前行100米、左转、右转）；endLat/endLng 用於依定位自動切換下一段 */
    public static class RouteStep {
        public String instruction;
        public String distance;
        public int distanceMeters;
        public String action;
        /** 此段起點緯度（用於偏移檢測，可為 null） */
        public Double startLat;
        /** 此段起點經度 */
        public Double startLng;
        /** 此段終點緯度（步行 API 從 polyline 解析，可為 null） */
        public Double endLat;
        /** 此段終點經度 */
        public Double endLng;

        public RouteStep(String instruction, String distance, int distanceMeters, String action) {
            this(instruction, distance, distanceMeters, action, null, null);
        }

        public RouteStep(String instruction, String distance, int distanceMeters, String action, Double endLat, Double endLng) {
            this(instruction, distance, distanceMeters, action, null, null, endLat, endLng);
        }

        public RouteStep(String instruction, String distance, int distanceMeters, String action, Double startLat, Double startLng, Double endLat, Double endLng) {
            this.instruction = instruction;
            this.distance = distance;
            this.distanceMeters = distanceMeters;
            this.action = action != null ? action : "";
            this.startLat = startLat;
            this.startLng = startLng;
            this.endLat = endLat;
            this.endLng = endLng;
        }
    }

    /**
     * 路線規劃結果
     */
    public static class RouteResult {
        public String distance;      // 距離（公里）
        public String duration;      // 時間（分鐘）
        public String routeSummary;  // 路線摘要
        public String fullRouteInfo; // 完整路線信息
        public List<RouteStep> steps; // 轉向步驟（前行100米/左转/右转）
        /** 完整路線 polyline，高德格式 "lng,lat;lng,lat;..."，供地圖繪製藍線 */
        public String routePolyline;
        public boolean success;
        public String errorMessage;
    }
    
    /**
     * 路線規劃回調
     */
    public interface RoutePlanningCallback {
        void onRoutePlanned(RouteResult result);
        void onError(String error);
    }
    
    private static final int HTTP_CONNECT_TIMEOUT_S = 20;
    private static final int HTTP_READ_TIMEOUT_S = 30;

    public RoutePlanner() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(HTTP_CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
                .readTimeout(HTTP_READ_TIMEOUT_S, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
        this.executorService = Executors.newSingleThreadExecutor();
    }
    
    /**
     * 設置 Context（用於網絡檢查）
     */
    public void setContext(Context context) {
        this.context = context.getApplicationContext();
    }
    
    /**
     * 檢查網絡連接
     */
    private boolean isNetworkAvailable() {
        if (context == null) {
            Log.w(TAG, "Context 未設置，無法檢查網絡連接");
            return true; // 如果沒有 Context，假設網絡可用（避免阻塞）
        }
        
        try {
            ConnectivityManager connectivityManager = 
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager == null) {
                return false;
            }
            
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            boolean isConnected = activeNetworkInfo != null && activeNetworkInfo.isConnected();
            
            Log.d(TAG, "網絡連接狀態: " + (isConnected ? "已連接" : "未連接"));
            return isConnected;
        } catch (Exception e) {
            Log.e(TAG, "檢查網絡連接失敗: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 判斷是否為網絡錯誤
     */
    private boolean isNetworkError(Exception e) {
        if (e == null) {
            return false;
        }
        
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        
        message = message.toLowerCase();
        return message.contains("unable to resolve host") ||
               message.contains("no address associated") ||
               message.contains("network is unreachable") ||
               message.contains("connection refused") ||
               message.contains("timeout") ||
               message.contains("no route to host");
    }
    
    /**
     * 設置 API 提供商
     * @param provider "google" 或 "amap"
     */
    public void setApiProvider(String provider) {
        this.apiProvider = provider;
    }
    
    /**
     * 規劃路線
     * @param origin 起點位置
     * @param destination 終點位置（地址字符串）
     * @param callback 回調接口
     */
    public void planRoute(Location origin, String destination, RoutePlanningCallback callback) {
        if (origin == null) {
            callback.onError("起點位置不可用");
            return;
        }
        
        if (destination == null || destination.trim().isEmpty()) {
            callback.onError("目的地不可用");
            return;
        }
        
        // 檢查網絡連接
        if (!isNetworkAvailable()) {
            String errorMsg = getNetworkErrorMessage();
            Log.e(TAG, errorMsg);
            callback.onError(errorMsg);
            return;
        }
        
        executorService.execute(() -> {
            if ("google".equals(apiProvider)) {
                planRouteWithGoogle(origin, destination, callback);
            } else if ("amap".equals(apiProvider)) {
                planRouteWithAmapWalking(origin, destination, callback);
            } else {
                callback.onError("不支持的 API 提供商: " + apiProvider);
            }
        });
    }
    
    /**
     * 獲取高德地圖API友好的錯誤消息（本地化）
     */
    private String getAmapFriendlyErrorMessage(String errorCode) {
        if (context == null) {
            return "API錯誤: " + errorCode;
        }
        
        String currentLang = LocaleManager.getInstance(context).getCurrentLanguage();
        String friendlyMessage;
        
        // 根據錯誤代碼提供友好的錯誤消息
        switch (errorCode) {
            case "USERKEY_PLAT_NOMATCH":
                if ("english".equals(currentLang)) {
                    friendlyMessage = "Amap API Key type is wrong. Please create a Web service type Key in Amap console and use it in this app.";
                } else if ("mandarin".equals(currentLang)) {
                    friendlyMessage = "高德API密钥类型不对。请在高德开放平台创建一个「Web服务」类型的Key，并在本应用中使用。";
                } else {
                    friendlyMessage = "高德API密鑰類型不對。請在高德開放平台創建一個「Web服務」類型的Key，並在本應用中使用。";
                }
                break;
            case "INVALID_USER_KEY":
                if ("english".equals(currentLang)) {
                    friendlyMessage = "Invalid API Key. Please check your Amap API Key.";
                } else if ("mandarin".equals(currentLang)) {
                    friendlyMessage = "API密钥无效。请检查您的高德地图API密钥。";
                } else {
                    friendlyMessage = "API密鑰無效。請檢查您的高德地圖API密鑰。";
                }
                break;
            case "DAILY_QUERY_OVER_LIMIT":
                if ("english".equals(currentLang)) {
                    friendlyMessage = "Daily API quota exceeded. Please try again tomorrow or upgrade your API plan.";
                } else if ("mandarin".equals(currentLang)) {
                    friendlyMessage = "API调用次数已达上限。请明天再试或升级API套餐。";
                } else {
                    friendlyMessage = "API調用次數已達上限。請明天再試或升級API套餐。";
                }
                break;
            case "ACCESS_TOO_FREQUENT":
                if ("english".equals(currentLang)) {
                    friendlyMessage = "API access too frequent. Please wait a moment and try again.";
                } else if ("mandarin".equals(currentLang)) {
                    friendlyMessage = "API访问过于频繁。请稍候再试。";
                } else {
                    friendlyMessage = "API訪問過於頻繁。請稍候再試。";
                }
                break;
            case "INVALID_PARAMS":
                if ("english".equals(currentLang)) {
                    friendlyMessage = "Invalid address. Please try speaking the destination again more clearly.";
                } else if ("mandarin".equals(currentLang)) {
                    friendlyMessage = "地址无效。请重新清晰地说出目的地。";
                } else {
                    friendlyMessage = "地址無效。請重新清晰地說出目的地。";
                }
                break;
            case "NO_DATA":
                if ("english".equals(currentLang)) {
                    friendlyMessage = "Destination not found. Please try a different address or location name.";
                } else if ("mandarin".equals(currentLang)) {
                    friendlyMessage = "未找到目的地。请尝试其他地址或地点名称。";
                } else {
                    friendlyMessage = "未找到目的地。請嘗試其他地址或地點名稱。";
                }
                break;
            default:
                // 對於未知錯誤，提供通用提示
                if ("english".equals(currentLang)) {
                    friendlyMessage = "Route planning failed: " + errorCode + ". Please check your network connection and try again.";
                } else if ("mandarin".equals(currentLang)) {
                    friendlyMessage = "路線規劃失敗：" + errorCode + "。請檢查網絡連接後重試。";
                } else {
                    friendlyMessage = "路線規劃失敗：" + errorCode + "。請檢查網絡連接後重試。";
                }
                break;
        }
        
        Log.e(TAG, "高德地圖API錯誤: " + errorCode + " -> " + friendlyMessage);
        return friendlyMessage;
    }
    
    /**
     * 獲取網絡錯誤消息（本地化）
     */
    private String getNetworkErrorMessage() {
        if (context == null) {
            return "網絡連接不可用，請檢查網絡設置";
        }
        
        String currentLang = LocaleManager.getInstance(context).getCurrentLanguage();
        if ("english".equals(currentLang)) {
            return "Network connection unavailable. Please check your network settings.";
        } else if ("mandarin".equals(currentLang)) {
            return "网络连接不可用，请检查网络设置";
        } else {
            return "網絡連接不可用，請檢查網絡設置";
        }
    }
    
    /**
     * 獲取網絡錯誤消息（本地化）
     */
    private String getNetworkErrorDetailedMessage(Exception e) {
        if (context == null) {
            return "網絡連接失敗，請檢查網絡設置";
        }
        
        String currentLang = LocaleManager.getInstance(context).getCurrentLanguage();
        String baseMsg;
        if ("english".equals(currentLang)) {
            baseMsg = "Network connection failed. Please check your network settings and try again.";
        } else if ("mandarin".equals(currentLang)) {
            baseMsg = "网络连接失败，请检查网络设置后重试";
        } else {
            baseMsg = "網絡連接失敗，請檢查網絡設置後重試";
        }
        
        return baseMsg;
    }
    
    /**
     * 使用 Google Maps Directions API 規劃路線
     */
    private void planRouteWithGoogle(Location origin, String destination, RoutePlanningCallback callback) {
        try {
            // 構建請求 URL
            // Google Maps 支持直接使用地址字符串作為目的地
            String originStr = origin.getLatitude() + "," + origin.getLongitude();
            String url = String.format(
                "%s?origin=%s&destination=%s&mode=walking&key=%s&language=zh-CN&region=CN",
                GOOGLE_DIRECTIONS_API_URL,
                originStr,
                java.net.URLEncoder.encode(destination, "UTF-8"),
                GOOGLE_MAPS_API_KEY
            );
            
            Log.d(TAG, "Google Maps API 請求 URL: " + url);
            
            Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
            
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Google Maps API 請求失敗: " + e.getMessage(), e);
                    String errorMsg;
                    if (isNetworkError(e)) {
                        errorMsg = getNetworkErrorDetailedMessage(e);
                    } else {
                        errorMsg = "路線規劃失敗: " + e.getMessage();
                    }
                    callback.onError(errorMsg);
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        Log.e(TAG, "Google Maps API 響應失敗: " + response.code());
                        callback.onError("路線規劃失敗: HTTP " + response.code());
                        return;
                    }
                    
                    String responseBody = response.body().string();
                    Log.d(TAG, "Google Maps API 響應: " + responseBody);
                    
                    try {
                        RouteResult result = parseGoogleResponse(responseBody);
                        callback.onRoutePlanned(result);
                    } catch (Exception e) {
                        Log.e(TAG, "解析 Google Maps 響應失敗: " + e.getMessage(), e);
                        callback.onError("解析路線信息失敗: " + e.getMessage());
                    }
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Google Maps API 請求異常: " + e.getMessage(), e);
            callback.onError("路線規劃異常: " + e.getMessage());
        }
    }
    
    /**
     * 使用高德地圖步行 API 規劃路線（視障人士多為短距離步行）
     * 步行結果含每段 polyline，可解析終點座標供依定位自動切換下一段。
     */
    private void planRouteWithAmapWalking(Location origin, String destination, RoutePlanningCallback callback) {
        try {
            String originStr = origin.getLongitude() + "," + origin.getLatitude();
            Log.d(TAG, "高德步行路線規劃 起點: " + originStr + ", 目的地: " + destination);
            geocodeDestinationWithAmap(destination, new GeocodingCallback() {
                @Override
                public void onGeocoded(double lat, double lng) {
                    String destStr = lng + "," + lat;
                    String url = String.format(
                        "%s?origin=%s&destination=%s&key=%s",
                        AMAP_WALKING_API_URL,
                        originStr,
                        destStr,
                        AMAP_API_KEY
                    );
                    Request request = new Request.Builder().url(url).get().build();
                    httpClient.newCall(request).enqueue(new Callback() {
                        @Override
                        public void onFailure(Call call, IOException e) {
                            Log.e(TAG, "高德步行 API 請求失敗: " + e.getMessage(), e);
                            callback.onError(isNetworkError(e) ? getNetworkErrorDetailedMessage(e) : "路線規劃失敗: " + e.getMessage());
                        }
                        @Override
                        public void onResponse(Call call, Response response) throws IOException {
                            if (!response.isSuccessful()) {
                                callback.onError("路線規劃失敗: HTTP " + response.code());
                                return;
                            }
                            String responseBody = response.body().string();
                            try {
                                RouteResult result = parseAmapWalkingResponse(responseBody);
                                callback.onRoutePlanned(result);
                            } catch (Exception e) {
                                Log.e(TAG, "解析高德步行響應失敗: " + e.getMessage(), e);
                                callback.onError("解析路線信息失敗: " + e.getMessage());
                            }
                        }
                    });
                }
                @Override
                public void onError(String error) {
                    callback.onError(error);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "高德步行 API 請求異常: " + e.getMessage(), e);
            callback.onError("路線規劃異常: " + e.getMessage());
        }
    }

    /**
     * 解析高德步行路線 API 響應；從每段 polyline 取最後一點作為該段終點座標（用於依定位自動切換下一段）。
     * 文檔：paths[0].distance/duration，paths[0].steps[].instruction/distance/polyline/action
     */
    private RouteResult parseAmapWalkingResponse(String jsonResponse) {
        RouteResult result = new RouteResult();
        try {
            JsonObject json = JsonParser.parseString(jsonResponse).getAsJsonObject();
            if (!"1".equals(json.get("status").getAsString())) {
                result.success = false;
                result.errorMessage = getAmapFriendlyErrorMessage(json.has("info") ? json.get("info").getAsString() : "未知錯誤");
                return result;
            }
            JsonObject route = json.getAsJsonObject("route");
            if (route == null || !route.has("paths")) {
                result.success = false;
                result.errorMessage = "未找到步行路線";
                return result;
            }
            JsonArray paths = route.getAsJsonArray("paths");
            if (paths.size() == 0) {
                result.success = false;
                result.errorMessage = "路線信息不完整";
                return result;
            }
            JsonObject path = paths.get(0).getAsJsonObject();
            double distanceMeters = jsonElementToDouble(path.get("distance"), 0);
            result.distance = String.format("%.1f", distanceMeters / 1000.0);
            double durationSeconds = jsonElementToDouble(path.get("duration"), 0);
            result.duration = String.valueOf(Math.max(1, (int) (durationSeconds / 60.0)));
            result.steps = new ArrayList<>();
            StringBuilder fullPolyline = new StringBuilder();
            if (path.has("steps") && path.get("steps").isJsonArray()) {
                JsonArray stepsArray = path.getAsJsonArray("steps");
                for (int i = 0; i < stepsArray.size(); i++) {
                    JsonObject step = stepsArray.get(i).getAsJsonObject();
                    String instruction = jsonElementToString(step.get("instruction"), "");
                    int stepDist = (int) jsonElementToDouble(step.get("distance"), 0);
                    String distStr = stepDist >= 1000 ? String.format("%.1f公里", stepDist / 1000.0) : stepDist + "米";
                    String action = jsonElementToString(step.get("action"), "");
                    Double startLat = null, startLng = null, endLat = null, endLng = null;
                    String polyline = jsonElementToString(step.get("polyline"), "");
                    if (polyline != null && !polyline.isEmpty()) {
                        if (fullPolyline.length() > 0) fullPolyline.append(";");
                        fullPolyline.append(polyline.trim());
                        String[] points = polyline.split(";");
                        if (points.length > 0) {
                            String[] first = points[0].trim().split(",");
                            if (first.length >= 2) {
                                try {
                                    startLng = Double.parseDouble(first[0].trim());
                                    startLat = Double.parseDouble(first[1].trim());
                                } catch (NumberFormatException ignored) {}
                            }
                            String[] last = points[points.length - 1].trim().split(",");
                            if (last.length >= 2) {
                                try {
                                    endLng = Double.parseDouble(last[0].trim());
                                    endLat = Double.parseDouble(last[1].trim());
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                    }
                    result.steps.add(new RouteStep(instruction, distStr, stepDist, action, startLat, startLng, endLat, endLng));
                }
            }
            if (fullPolyline.length() > 0) {
                result.routePolyline = fullPolyline.toString();
            }
            result.fullRouteInfo = String.format("距離：%s公里，預計步行時間：%s分鐘", result.distance, result.duration);
            result.success = true;
            Log.d(TAG, "高德步行解析成功: 距離=" + result.distance + "km, 步驟數=" + (result.steps != null ? result.steps.size() : 0));
        } catch (Exception e) {
            Log.e(TAG, "解析高德步行響應異常: " + e.getMessage(), e);
            result.success = false;
            result.errorMessage = "解析響應失敗: " + e.getMessage();
        }
        return result;
    }

    /**
     * 使用高德地圖 API 規劃駕車路線（保留供切換用）
     */
    private void planRouteWithAmap(Location origin, String destination, RoutePlanningCallback callback) {
        try {
            String originStr = origin.getLongitude() + "," + origin.getLatitude();
            Log.d(TAG, "高德路線規劃 起點: " + originStr + ", 目的地文字: " + destination);
            geocodeDestinationWithAmap(destination, new GeocodingCallback() {
                @Override
                public void onGeocoded(double lat, double lng) {
                    String destStr = lng + "," + lat;
                    Log.d(TAG, "高德地理編碼成功 目的地坐標: " + destStr);
                    String url = String.format(
                        "%s?origin=%s&destination=%s&key=%s&extensions=all",
                        AMAP_DIRECTIONS_API_URL,
                        originStr,
                        destStr,
                        AMAP_API_KEY
                    );
                    Log.d(TAG, "高德駕車規劃 請求 URL(已隱藏key): " + AMAP_DIRECTIONS_API_URL + "?origin=...&destination=...&key=***");
                    
                    Request request = new Request.Builder()
                        .url(url)
                        .get()
                        .build();
                    
                    httpClient.newCall(request).enqueue(new Callback() {
                        @Override
                        public void onFailure(Call call, IOException e) {
                            Log.e(TAG, "高德地圖 API 請求失敗: " + e.getMessage(), e);
                            String errorMsg;
                            if (isNetworkError(e)) {
                                errorMsg = getNetworkErrorDetailedMessage(e);
                            } else {
                                errorMsg = "路線規劃失敗: " + e.getMessage();
                            }
                            callback.onError(errorMsg);
                        }
                        
                        @Override
                        public void onResponse(Call call, Response response) throws IOException {
                            if (!response.isSuccessful()) {
                                Log.e(TAG, "高德駕車規劃 HTTP 失敗: " + response.code() + " " + response.message());
                                callback.onError("路線規劃失敗: HTTP " + response.code());
                                return;
                            }
                            String responseBody = response.body().string();
                            if (responseBody.length() > 500) {
                                Log.d(TAG, "高德駕車規劃 響應(前500字): " + responseBody.substring(0, 500) + "...");
                            } else {
                                Log.d(TAG, "高德駕車規劃 響應: " + responseBody);
                            }
                            
                            try {
                                RouteResult result = parseAmapResponse(responseBody);
                                callback.onRoutePlanned(result);
                            } catch (Exception e) {
                                Log.e(TAG, "解析高德地圖響應失敗: " + e.getMessage(), e);
                                callback.onError("解析路線信息失敗: " + e.getMessage());
                            }
                        }
                    });
                }
                
                @Override
                public void onError(String error) {
                    callback.onError(error);
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "高德地圖 API 請求異常: " + e.getMessage(), e);
            callback.onError("路線規劃異常: " + e.getMessage());
        }
    }
    
    /**
     * 地理編碼回調接口
     */
    private interface GeocodingCallback {
        void onGeocoded(double lat, double lng);
        void onError(String error);
    }
    
    /**
     * 使用高德地圖進行地理編碼（地址轉坐標）
     */
    private void geocodeDestinationWithAmap(String address, GeocodingCallback callback) {
        try {
            String url = String.format(
                "%s?address=%s&key=%s",
                AMAP_GEOCODING_API_URL,
                java.net.URLEncoder.encode(address, "UTF-8"),
                AMAP_API_KEY
            );
            
            Log.d(TAG, "高德地理編碼 地址: " + address);
            
            Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
            
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "高德地圖地理編碼請求失敗: " + e.getMessage(), e);
                    String errorMsg;
                    if (isNetworkError(e)) {
                        errorMsg = getNetworkErrorDetailedMessage(e);
                    } else {
                        errorMsg = e.getMessage();
                    }
                    callback.onError(errorMsg);
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        Log.e(TAG, "高德地圖地理編碼響應失敗: " + response.code());
                        callback.onError("HTTP " + response.code());
                        return;
                    }
                    
                    String responseBody = response.body().string();
                    Log.d(TAG, "高德地理編碼響應 status 片段: " + (responseBody.length() > 200 ? responseBody.substring(0, 200) + "..." : responseBody));
                    try {
                        JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
                        String status = json.get("status").getAsString();
                        if (!"1".equals(status)) {
                            String info = json.has("info") ? json.get("info").getAsString() : "未知錯誤";
                            Log.e(TAG, "高德地理編碼失敗 status=" + status + " info=" + info);
                            String friendlyError = getAmapFriendlyErrorMessage(info);
                            callback.onError(friendlyError);
                            return;
                        }
                        
                        JsonArray geocodes = json.getAsJsonArray("geocodes");
                        if (geocodes == null || geocodes.size() == 0) {
                            callback.onError("未找到地址對應的坐標");
                            return;
                        }
                        
                        JsonObject geocode = geocodes.get(0).getAsJsonObject();
                        String location = geocode.get("location").getAsString(); // 格式：經度,緯度
                        String[] coords = location.split(",");
                        
                        if (coords.length != 2) {
                            callback.onError("坐標格式錯誤");
                            return;
                        }
                        
                        double lng = Double.parseDouble(coords[0]);
                        double lat = Double.parseDouble(coords[1]);
                        
                        Log.d(TAG, "地理編碼成功: " + address + " -> " + lat + ", " + lng);
                        callback.onGeocoded(lat, lng);
                        
                    } catch (Exception e) {
                        Log.e(TAG, "解析地理編碼響應失敗: " + e.getMessage(), e);
                        callback.onError("解析響應失敗: " + e.getMessage());
                    }
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "高德地圖地理編碼請求異常: " + e.getMessage(), e);
            callback.onError(e.getMessage());
        }
    }
    
    /**
     * 解析 Google Maps API 響應
     */
    private RouteResult parseGoogleResponse(String jsonResponse) {
        RouteResult result = new RouteResult();
        
        try {
            JsonObject json = JsonParser.parseString(jsonResponse).getAsJsonObject();
            String status = json.get("status").getAsString();
            
            if (!"OK".equals(status)) {
                result.success = false;
                result.errorMessage = "路線規劃失敗: " + status;
                return result;
            }
            
            JsonArray routes = json.getAsJsonArray("routes");
            if (routes == null || routes.size() == 0) {
                result.success = false;
                result.errorMessage = "未找到路線";
                return result;
            }
            
            JsonObject route = routes.get(0).getAsJsonObject();
            JsonArray legs = route.getAsJsonArray("legs");
            
            if (legs == null || legs.size() == 0) {
                result.success = false;
                result.errorMessage = "路線信息不完整";
                return result;
            }
            
            JsonObject leg = legs.get(0).getAsJsonObject();
            
            // 解析距離
            JsonObject distance = leg.getAsJsonObject("distance");
            double distanceMeters = distance.get("value").getAsDouble();
            double distanceKm = distanceMeters / 1000.0;
            result.distance = String.format("%.1f", distanceKm);
            
            // 解析時間
            JsonObject duration = leg.getAsJsonObject("duration");
            int durationSeconds = duration.get("value").getAsInt();
            int durationMinutes = durationSeconds / 60;
            result.duration = String.valueOf(durationMinutes);
            
            // 路線摘要
            String summary = route.has("summary") ? route.get("summary").getAsString() : "";
            result.routeSummary = summary;
            
            // 完整路線信息
            result.fullRouteInfo = String.format(
                "距離：%s公里，預計時間：%s分鐘",
                result.distance,
                result.duration
            );
            
            result.success = true;
            
        } catch (Exception e) {
            Log.e(TAG, "解析 Google Maps 響應異常: " + e.getMessage(), e);
            result.success = false;
            result.errorMessage = "解析響應失敗: " + e.getMessage();
        }
        
        return result;
    }
    
    private static double jsonElementToDouble(JsonElement el, double defaultVal) {
        if (el == null || el.isJsonNull()) return defaultVal;
        try {
            if (el.isJsonPrimitive()) {
                if (el.getAsJsonPrimitive().isNumber()) return el.getAsDouble();
                return Double.parseDouble(el.getAsString());
            }
        } catch (Exception e) {
            Log.w(TAG, "jsonElementToDouble: " + e.getMessage());
        }
        return defaultVal;
    }

    /** 高德有時將 action/instruction 等返回為數組（甚至空數組），安全取字符串 */
    private static String jsonElementToString(JsonElement el, String defaultVal) {
        if (el == null || el.isJsonNull()) return defaultVal;
        try {
            if (el.isJsonPrimitive()) return el.getAsString();
            if (el.isJsonArray()) {
                JsonArray arr = el.getAsJsonArray();
                if (arr.size() > 0 && arr.get(0).isJsonPrimitive()) return arr.get(0).getAsString();
            }
        } catch (Exception e) {
            Log.w(TAG, "jsonElementToString: " + e.getMessage());
        }
        return defaultVal;
    }

    /**
     * 解析高德地圖 API 響應
     */
    private RouteResult parseAmapResponse(String jsonResponse) {
        RouteResult result = new RouteResult();
        
        try {
            JsonObject json = JsonParser.parseString(jsonResponse).getAsJsonObject();
            String status = json.get("status").getAsString();
            
            if (!"1".equals(status)) {
                result.success = false;
                String info = json.has("info") ? json.get("info").getAsString() : "未知錯誤";
                String infocode = json.has("infocode") ? json.get("infocode").getAsString() : "";
                Log.e(TAG, "高德 API 返回非成功 status=" + status + " info=" + info + " infocode=" + infocode);
                result.errorMessage = getAmapFriendlyErrorMessage(info);
                return result;
            }
            
            JsonObject route = json.getAsJsonObject("route");
            if (route == null) {
                result.success = false;
                result.errorMessage = "未找到路線";
                return result;
            }
            
            JsonArray paths = route.getAsJsonArray("paths");
            if (paths == null || paths.size() == 0) {
                result.success = false;
                result.errorMessage = "路線信息不完整";
                return result;
            }
            
            JsonObject path = paths.get(0).getAsJsonObject();
            
            // 解析距離（米）- 高德可能返回 number 或 string
            double distanceMeters = jsonElementToDouble(path.get("distance"), 0);
            double distanceKm = distanceMeters / 1000.0;
            result.distance = String.format("%.1f", distanceKm);
            
            // 解析時間（秒）
            double durationSeconds = jsonElementToDouble(path.get("duration"), 0);
            int durationMinutes = (int)(durationSeconds / 60.0);
            result.duration = String.valueOf(durationMinutes);
            
            // 路線摘要（高德可能返回字串或數組）
            result.routeSummary = jsonElementToString(path.get("strategy"), "");
            
            // 解析轉向步驟（用於播報：前行100米、左转、右转）
            result.steps = new ArrayList<>();
            if (path.has("steps") && path.get("steps").isJsonArray()) {
                JsonArray stepsArray = path.getAsJsonArray("steps");
                for (int i = 0; i < stepsArray.size(); i++) {
                    JsonObject step = stepsArray.get(i).getAsJsonObject();
                    String instruction = jsonElementToString(step.get("instruction"), "");
                    int stepDist = (int) jsonElementToDouble(step.get("distance"), 0);
                    String distStr = stepDist >= 1000 ? String.format("%.1f公里", stepDist / 1000.0) : stepDist + "米";
                    String action = jsonElementToString(step.get("action"), "");
                    result.steps.add(new RouteStep(instruction, distStr, stepDist, action));
                }
            }
            Log.d(TAG, "高德解析成功: 距離=" + result.distance + "km, 步驟數=" + (result.steps != null ? result.steps.size() : 0));
            
            // 完整路線信息
            result.fullRouteInfo = String.format(
                "距離：%s公里，預計時間：%s分鐘",
                result.distance,
                result.duration
            );
            
            result.success = true;
            
        } catch (Exception e) {
            Log.e(TAG, "解析高德地圖響應異常: " + e.getMessage(), e);
            result.success = false;
            result.errorMessage = "解析響應失敗: " + e.getMessage();
        }
        
        return result;
    }
    
    /**
     * 清理資源
     */
    public void cleanup() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
