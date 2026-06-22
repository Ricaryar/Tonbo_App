package com.example.tonbo_app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 手勢識別管理器
 * 用於識別和匹配用戶繪製的手勢
 */
public class GestureRecognitionManager {
    private static final String TAG = "GestureRecognition";
    private static final String PREF_NAME = "GestureRecognitionStorage";
    private static final String KEY_GESTURES_JSON = "gestures_json";
    
    private static GestureRecognitionManager instance;
    
    // 手勢模板：Key = 手勢名稱，Value = 點列表
    private Map<String, List<GesturePoint>> templates;
    
    // 手勢綁定：Key = 手勢名稱，Value = 功能名稱
    private Map<String, String> gestureBindings;
    
    private Context context;
    
    private GestureRecognitionManager(Context context) {
        this.context = context;
        templates = new HashMap<>();
        gestureBindings = new HashMap<>();
        loadSavedGestures();
    }
    
    public static synchronized GestureRecognitionManager getInstance(Context context) {
        if (instance == null) {
            instance = new GestureRecognitionManager(context.getApplicationContext());
        }
        return instance;
    }
    
    /**
     * 保存手勢模板
     */
    public void saveGesture(String name, List<GesturePoint> points, String functionName) {
        templates.put(name, points);
        gestureBindings.put(name, functionName);
        Log.d(TAG, "保存手勢: " + name + " -> " + functionName);
        persistGestures();
    }
    
    /**
     * 識別手勢
     * @param paths 當前繪畫的路徑列表
     * @return 匹配的手勢名稱，如果未找到返回null
     */
    public String recognizeGesture(List<Path> paths) {
        if (templates.isEmpty() || paths.isEmpty()) {
            return null;
        }
        
        // 將Path轉換為點列表
        List<GesturePoint> inputPoints = new ArrayList<>();
        for (Path path : paths) {
            // 簡化：從Path中提取關鍵點
            // 這裡使用簡化的點提取方法
            inputPoints.addAll(extractPoints(path));
        }
        
        // 與所有模板進行比對
        String bestMatch = null;
        double bestScore = Double.MAX_VALUE;
        
        for (Map.Entry<String, List<GesturePoint>> entry : templates.entrySet()) {
            double score = calculateDistance(inputPoints, entry.getValue());
            Log.d(TAG, "手勢匹配: " + entry.getKey() + " 分數: " + score);
            
            if (score < bestScore) {
                bestScore = score;
                bestMatch = entry.getKey();
            }
        }
        
        // 如果最佳分數超過閾值，返回null
        // DTW距離比Hausdorff距離大，所以閾值也需要調整
        if (bestScore > 5.0) { // 閾值：DTW距離超過5.0表示不匹配
            Log.d(TAG, "未找到匹配的手勢，最佳分數: " + bestScore);
            return null;
        }
        
        Log.d(TAG, "匹配到手勢: " + bestMatch + " 分數: " + bestScore);
        return bestMatch;
    }
    
    /**
     * 獲取手勢綁定的功能
     */
    public String getFunctionForGesture(String gestureName) {
        return gestureBindings.get(gestureName);
    }
    
    /**
     * 獲取所有已保存的手勢
     */
    public Map<String, String> getAllGestures() {
        return new HashMap<>(gestureBindings);
    }
    
    /**
     * 刪除手勢
     */
    public void deleteGesture(String name) {
        templates.remove(name);
        gestureBindings.remove(name);
        persistGestures();
    }
    
    /**
     * 從Path中提取點
     */
    private List<GesturePoint> extractPoints(Path path) {
        List<GesturePoint> points = new ArrayList<>();
        
        // 使用PathMeasure提取真實的點
        android.graphics.PathMeasure pm = new android.graphics.PathMeasure(path, false);
        float length = pm.getLength();
        
        if (length > 0) {
            int sampleCount = 50;
            float[] coords = new float[2];
            
            for (int i = 0; i < sampleCount; i++) {
                float distance = (i / (float) (sampleCount - 1)) * length;
                if (pm.getPosTan(distance, coords, null)) {
                    points.add(new GesturePoint(coords[0], coords[1]));
                }
            }
        }
        
        return points;
    }
    
    /**
     * 計算兩個手勢之間的距離（使用改進的距離計算）
     */
    private double calculateDistance(List<GesturePoint> points1, List<GesturePoint> points2) {
        if (points1.isEmpty() || points2.isEmpty()) {
            return Double.MAX_VALUE;
        }
        
        // 首先正規化座標，使手勢與位置無關
        points1 = normalizePoints(points1);
        points2 = normalizePoints(points2);
        
        // 使用DTW (Dynamic Time Warping) 算法計算距離
        return calculateDTW(points1, points2);
    }
    
    /**
     * 使用DTW算法計算兩個手勢之間的距離
     * DTW考慮了時間順序，對手勢匹配更準確
     */
    private double calculateDTW(List<GesturePoint> points1, List<GesturePoint> points2) {
        int n = points1.size();
        int m = points2.size();
        
        // DP矩陣
        double[][] dtw = new double[n + 1][m + 1];
        
        // 初始化
        for (int i = 0; i <= n; i++) {
            for (int j = 0; j <= m; j++) {
                dtw[i][j] = Double.MAX_VALUE;
            }
        }
        dtw[0][0] = 0;
        
        // 計算DTW距離
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                double cost = euclideanDistance(points1.get(i - 1), points2.get(j - 1));
                dtw[i][j] = cost + Math.min(Math.min(dtw[i - 1][j], dtw[i][j - 1]), dtw[i - 1][j - 1]);
            }
        }
        
        return dtw[n][m];
    }
    
    /**
     * 計算兩個點之間的歐氏距離
     */
    private double euclideanDistance(GesturePoint p1, GesturePoint p2) {
        double dx = p1.x - p2.x;
        double dy = p1.y - p2.y;
        return Math.sqrt(dx * dx + dy * dy);
    }
    
    /**
     * 正規化點列表，使手勢與位置和大小無關
     */
    private List<GesturePoint> normalizePoints(List<GesturePoint> points) {
        if (points.isEmpty()) {
            return points;
        }
        
        // 找到邊界
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE, maxY = Float.MIN_VALUE;
        
        for (GesturePoint p : points) {
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
        }
        
        // 計算中心和平移
        float centerX = (minX + maxX) / 2;
        float centerY = (minY + maxY) / 2;
        float scale = Math.max(maxX - minX, maxY - minY);
        
        List<GesturePoint> normalized = new ArrayList<>();
        for (GesturePoint p : points) {
            normalized.add(new GesturePoint(
                (p.x - centerX) / scale,
                (p.y - centerY) / scale
            ));
        }
        
        return normalized;
    }
    
    /**
     * 保存手勢到SharedPreferences
     */
    private void persistGestures() {
        try {
            JSONObject root = new JSONObject();
            JSONObject tmpl = new JSONObject();
            JSONObject bind = new JSONObject();
            
            for (Map.Entry<String, List<GesturePoint>> entry : templates.entrySet()) {
                JSONArray arr = new JSONArray();
                for (GesturePoint p : entry.getValue()) {
                    JSONObject o = new JSONObject();
                    o.put("x", (double) p.x);
                    o.put("y", (double) p.y);
                    arr.put(o);
                }
                tmpl.put(entry.getKey(), arr);
            }
            
            for (Map.Entry<String, String> entry : gestureBindings.entrySet()) {
                bind.put(entry.getKey(), entry.getValue());
            }
            
            root.put("templates", tmpl);
            root.put("bindings", bind);
            
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(KEY_GESTURES_JSON, root.toString()).apply();
            Log.d(TAG, "持久化手勢數據: " + templates.size() + " 個模板");
        } catch (JSONException e) {
            Log.e(TAG, "持久化手勢失敗", e);
        }
    }
    
    /**
     * 從SharedPreferences載入手勢
     */
    private void loadSavedGestures() {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_GESTURES_JSON, null);
        if (json == null || json.isEmpty()) {
            Log.d(TAG, "載入已保存的手勢: 無本地數據");
            return;
        }
        try {
            JSONObject root = new JSONObject(json);
            JSONObject tmpl = root.optJSONObject("templates");
            JSONObject bind = root.optJSONObject("bindings");
            
            if (tmpl != null) {
                Iterator<String> keys = tmpl.keys();
                while (keys.hasNext()) {
                    String name = keys.next();
                    JSONArray arr = tmpl.getJSONArray(name);
                    List<GesturePoint> pts = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        pts.add(new GesturePoint((float) o.getDouble("x"), (float) o.getDouble("y")));
                    }
                    templates.put(name, pts);
                }
            }
            
            if (bind != null) {
                Iterator<String> keys = bind.keys();
                while (keys.hasNext()) {
                    String name = keys.next();
                    gestureBindings.put(name, bind.getString(name));
                }
            }
            
            Log.d(TAG, "載入已保存的手勢: " + templates.size() + " 個模板");
        } catch (JSONException e) {
            Log.e(TAG, "載入手勢失敗", e);
        }
    }
    
    /**
     * 手勢點數據類
     */
    public static class GesturePoint {
        public float x;
        public float y;
        
        public GesturePoint(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }
}
