package com.example.tonbo_app;

import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 出行相關位置服務：周邊 POI 搜索、跨區檢測
 */
public class LocationService {
    private static final String TAG = "LocationService";
    private static final String AMAP_API_KEY = "de266dbe1ed6b4fd37caa431871c5854";
    private static final String AMAP_AROUND_URL = "https://restapi.amap.com/v3/place/around";
    private static final String AMAP_GEOCODE_URL = "https://restapi.amap.com/v3/geocode/geo";
    private static final String AMAP_REGEO_URL = "https://restapi.amap.com/v3/geocode/regeo";

    private final Context context;
    private final FusedLocationProviderClient fusedLocationClient;
    private final OkHttpClient httpClient = new OkHttpClient();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public LocationService(Context context) {
        this.context = context.getApplicationContext();
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(this.context);
    }

    /**
     * POI 候選項
     */
    public static class POICandidate {
        public final String name;
        public final String address;
        public final double lat;
        public final double lng;
        public final int distanceMeters;
        public final String adcode;
        public final String cityname;
        public final String pname;

        public POICandidate(String name, String address, double lat, double lng, int distanceMeters, String adcode, String cityname, String pname) {
            this.name = name;
            this.address = address;
            this.lat = lat;
            this.lng = lng;
            this.distanceMeters = distanceMeters;
            this.adcode = adcode;
            this.cityname = cityname;
            this.pname = pname;
        }
    }

    /**
     * 周邊 POI 搜索（按距離排序）
     * @param keyword 關鍵詞（如「天虹商場」）
     * @param callback 主線程回調
     */
    public void searchNearbyPOIs(String keyword, POISearchCallback callback) {
        if (!hasLocationPermission()) {
            mainHandler.post(() -> callback.onResult(new ArrayList<>(), "需要位置權限"));
            return;
        }
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(executor, location -> {
                    if (location == null) {
                        mainHandler.post(() -> callback.onResult(new ArrayList<>(), "無法獲取位置"));
                        return;
                    }
                    doSearchAround(location.getLongitude(), location.getLatitude(), keyword, callback);
                })
                .addOnFailureListener(executor, e -> {
                    Log.e(TAG, "獲取位置失敗: " + e.getMessage());
                    mainHandler.post(() -> callback.onResult(new ArrayList<>(), "無法獲取位置: " + e.getMessage()));
                });
    }

    private void doSearchAround(double lng, double lat, String keyword, POISearchCallback callback) {
        try {
            String url = String.format(Locale.US, "%s?key=%s&location=%.6f,%.6f&keywords=%s&radius=50000&sortrule=distance&offset=20&page=1",
                    AMAP_AROUND_URL, AMAP_API_KEY, lng, lat, URLEncoder.encode(keyword, "UTF-8"));
            Request request = new Request.Builder().url(url).get().build();
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "周邊搜索失敗: " + e.getMessage());
                    mainHandler.post(() -> callback.onResult(new ArrayList<>(), "搜索失敗: " + e.getMessage()));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        mainHandler.post(() -> callback.onResult(new ArrayList<>(), "HTTP " + response.code()));
                        return;
                    }
                    String body = response.body().string();
                    List<POICandidate> list = parseAroundResponse(body);
                    mainHandler.post(() -> callback.onResult(list, null));
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "周邊搜索異常: " + e.getMessage());
            mainHandler.post(() -> callback.onResult(new ArrayList<>(), e.getMessage()));
        }
    }

    private List<POICandidate> parseAroundResponse(String json) {
        List<POICandidate> result = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(json);
            if (!"1".equals(root.optString("status"))) return result;
            JSONArray pois = root.optJSONArray("pois");
            if (pois == null) return result;
            for (int i = 0; i < pois.length(); i++) {
                JSONObject p = pois.getJSONObject(i);
                String name = p.optString("name");
                String address = p.optString("address");
                String loc = p.optString("location");
                int dist = p.optInt("distance", 0);
                String adcode = p.optString("adcode");
                String cityname = p.optString("cityname");
                String pname = p.optString("pname");
                double lng = 0, lat = 0;
                if (loc != null && loc.contains(",")) {
                    String[] parts = loc.split(",");
                    if (parts.length >= 2) {
                        lng = Double.parseDouble(parts[0].trim());
                        lat = Double.parseDouble(parts[1].trim());
                    }
                }
                result.add(new POICandidate(name, address, lat, lng, dist, adcode, cityname, pname));
            }
        } catch (Exception e) {
            Log.e(TAG, "解析 POI 響應失敗: " + e.getMessage());
        }
        return result;
    }

    public interface POISearchCallback {
        void onResult(List<POICandidate> pois, String error);
    }

    public interface ShortLocationTextCallback {
        void onResult(String shortText, String error);
    }

    /**
     * 當前位置簡短描述（區/市），供播報「在您附近」類提示；失敗時 shortText 為 null。
     */
    public void getCurrentLocationShortText(ShortLocationTextCallback callback) {
        if (!hasLocationPermission()) {
            mainHandler.post(() -> callback.onResult(null, "需要位置權限"));
            return;
        }
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener(executor, location -> {
                    if (location == null) {
                        mainHandler.post(() -> callback.onResult(null, null));
                        return;
                    }
                    try {
                        String url = String.format(Locale.US, "%s?key=%s&location=%.6f,%.6f&extensions=base&radius=1000",
                                AMAP_REGEO_URL, AMAP_API_KEY, location.getLongitude(), location.getLatitude());
                        Request request = new Request.Builder().url(url).get().build();
                        httpClient.newCall(request).enqueue(new Callback() {
                            @Override
                            public void onFailure(Call call, IOException e) {
                                Log.w(TAG, "逆地理失敗: " + e.getMessage());
                                mainHandler.post(() -> callback.onResult(null, null));
                            }

                            @Override
                            public void onResponse(Call call, Response response) throws IOException {
                                String shortText = null;
                                try {
                                    if (response.isSuccessful() && response.body() != null) {
                                        String body = response.body().string();
                                        JSONObject root = new JSONObject(body);
                                        if ("1".equals(root.optString("status"))) {
                                            JSONObject regeo = root.optJSONObject("regeocode");
                                            if (regeo != null) {
                                                JSONObject ac = regeo.optJSONObject("addressComponent");
                                                if (ac != null) {
                                                    String district = ac.optString("district");
                                                    String city = ac.optString("city");
                                                    if (district != null && !district.isEmpty()) {
                                                        shortText = district;
                                                    } else if (city != null && !city.isEmpty()) {
                                                        shortText = city;
                                                    }
                                                }
                                                if (shortText == null || shortText.isEmpty()) {
                                                    String formatted = regeo.optString("formatted_address");
                                                    if (formatted != null && formatted.length() > 24) {
                                                        shortText = formatted.substring(0, 24);
                                                    } else if (formatted != null && !formatted.isEmpty()) {
                                                        shortText = formatted;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    Log.w(TAG, "解析逆地理: " + e.getMessage());
                                }
                                final String out = shortText;
                                mainHandler.post(() -> callback.onResult(out, null));
                            }
                        });
                    } catch (Exception e) {
                        Log.w(TAG, "逆地理請求異常: " + e.getMessage());
                        mainHandler.post(() -> callback.onResult(null, null));
                    }
                })
                .addOnFailureListener(executor, e -> {
                    Log.e(TAG, "獲取位置失敗: " + e.getMessage());
                    mainHandler.post(() -> callback.onResult(null, e.getMessage()));
                });
    }

    /**
     * 檢查用戶位置與目的地是否跨區（內地 vs 港澳台）
     */
    public void checkCrossRegion(String destination, CrossRegionCallback callback) {
        if (!hasLocationPermission()) {
            mainHandler.post(() -> callback.onResult(false, "需要位置權限"));
            return;
        }
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(executor, userLocation -> {
                    if (userLocation == null) {
                        mainHandler.post(() -> callback.onResult(false, "無法獲取位置"));
                        return;
                    }
                    geocodeDestination(destination, (destAdcode, destCity, destProvince, geoError) -> {
                        if (geoError != null) {
                            mainHandler.post(() -> callback.onResult(false, geoError));
                            return;
                        }
                        boolean crossRegion = isCrossRegionFromAdcode(userLocation, destAdcode, destCity, destProvince);
                        mainHandler.post(() -> callback.onResult(crossRegion, null));
                    });
                })
                .addOnFailureListener(executor, e -> {
                    Log.e(TAG, "獲取位置失敗: " + e.getMessage());
                    mainHandler.post(() -> callback.onResult(false, "無法獲取位置"));
                });
    }

    private void geocodeDestination(String address, GeocodeResultCallback callback) {
        try {
            String url = String.format("%s?address=%s&key=%s", AMAP_GEOCODE_URL, URLEncoder.encode(address, "UTF-8"), AMAP_API_KEY);
            Request request = new Request.Builder().url(url).get().build();
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onResult(null, null, null, e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        callback.onResult(null, null, null, "HTTP " + response.code());
                        return;
                    }
                    String body = response.body().string();
                    try {
                        JSONObject root = new JSONObject(body);
                        if (!"1".equals(root.optString("status"))) {
                            callback.onResult(null, null, null, root.optString("info", "未知錯誤"));
                            return;
                        }
                        JSONArray geocodes = root.optJSONArray("geocodes");
                        if (geocodes == null || geocodes.length() == 0) {
                            callback.onResult(null, null, null, "未找到地址");
                            return;
                        }
                        JSONObject g = geocodes.getJSONObject(0);
                        String adcode = g.optString("adcode");
                        String city = g.optString("city");
                        String province = g.optString("province");
                        callback.onResult(adcode, city, province, null);
                    } catch (Exception e) {
                        callback.onResult(null, null, null, e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            callback.onResult(null, null, null, e.getMessage());
        }
    }

    private interface GeocodeResultCallback {
        void onResult(String adcode, String city, String province, String error);
    }

    /**
     * 判斷是否跨區：用戶在內地，目的地在港澳台
     */
    private boolean isCrossRegionFromAdcode(Location userLocation, String destAdcode, String destCity, String destProvince) {
        int userRegion = getRegionFromUserLocation(userLocation);
        boolean userInMainland = (userRegion != 81 && userRegion != 82 && userRegion != 71);
        if (!userInMainland) return false;
        boolean destInHK = isHKRegion(destAdcode, destCity, destProvince);
        boolean destInMacau = isMacauRegion(destAdcode, destCity, destProvince);
        boolean destInTaiwan = isTaiwanRegion(destAdcode, destCity, destProvince);
        return destInHK || destInMacau || destInTaiwan;
    }

    /** 根據經緯度判斷用戶所在區域：81=香港 82=澳門 71=台灣 0=內地/其他 */
    private int getRegionFromUserLocation(Location loc) {
        if (loc == null) return 0;
        double lat = loc.getLatitude();
        double lng = loc.getLongitude();
        if (lat >= 22.1 && lat <= 22.6 && lng >= 113.8 && lng <= 114.4) return 81;
        if (lat >= 22.1 && lat <= 22.25 && lng >= 113.5 && lng <= 113.6) return 82;
        if (lat >= 21.8 && lat <= 25.5 && lng >= 119.3 && lng <= 122.2) return 71;
        return 0;
    }

    private boolean isHKRegion(String adcode, String city, String province) {
        if (adcode != null && (adcode.startsWith("81") || adcode.equals("810000"))) return true;
        if (city != null && city.contains("香港")) return true;
        if (province != null && province.contains("香港")) return true;
        return false;
    }

    private boolean isMacauRegion(String adcode, String city, String province) {
        if (adcode != null && (adcode.startsWith("82") || adcode.equals("820000"))) return true;
        if (city != null && city.contains("澳门")) return true;
        if (province != null && province.contains("澳门")) return true;
        return false;
    }

    private boolean isTaiwanRegion(String adcode, String city, String province) {
        if (adcode != null && adcode.startsWith("71")) return true;
        if (city != null && city.contains("台湾")) return true;
        if (province != null && province.contains("台湾")) return true;
        return false;
    }

    public interface CrossRegionCallback {
        void onResult(boolean isCrossRegion, String error);
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
}
