package com.example.tonbo_app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * 用戶管理器
 * 負責管理用戶登入狀態、用戶信息等
 * 為Firebase集成做準備
 */
public class UserManager {
    private static final String TAG = "UserManager";
    private static final String PREFS_NAME = "user_preferences";
    private static final String KEY_USER_LOGGED_IN = "user_logged_in";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_GUEST_MODE = "guest_mode";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_TYPE = "user_type"; // "volunteer" 或 "user"
    
    // 用户类型常量
    public static final String USER_TYPE_VOLUNTEER = "volunteer";
    public static final String USER_TYPE_USER = "user";
    
    private static UserManager instance;
    private Context context;
    private SharedPreferences preferences;
    
    private UserManager(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    public static synchronized UserManager getInstance(Context context) {
        if (instance == null) {
            instance = new UserManager(context);
        }
        return instance;
    }
    
    /**
     * 檢查用戶是否已登入
     */
    public boolean isUserLoggedIn() {
        return preferences.getBoolean(KEY_USER_LOGGED_IN, false);
    }
    
    /**
     * 設置用戶登入狀態
     */
    public void setUserLoggedIn(boolean loggedIn) {
        preferences.edit().putBoolean(KEY_USER_LOGGED_IN, loggedIn).apply();
        Log.d(TAG, "用戶登入狀態設置為: " + loggedIn);
    }
    
    /**
     * 獲取當前用戶郵箱
     */
    public String getCurrentUserEmail() {
        return preferences.getString(KEY_USER_EMAIL, null);
    }
    
    /**
     * 設置當前用戶郵箱
     */
    public void setCurrentUserEmail(String email) {
        preferences.edit().putString(KEY_USER_EMAIL, email).apply();
        Log.d(TAG, "用戶郵箱設置為: " + email);
    }
    
    /**
     * 檢查是否為訪客模式
     */
    public boolean isGuestMode() {
        return preferences.getBoolean(KEY_GUEST_MODE, false);
    }
    
    /**
     * 設置訪客模式
     */
    public void setGuestMode(boolean guestMode) {
        preferences.edit().putBoolean(KEY_GUEST_MODE, guestMode).apply();
        Log.d(TAG, "訪客模式設置為: " + guestMode);
    }
    
    /**
     * 獲取用戶ID
     */
    public String getUserId() {
        return preferences.getString(KEY_USER_ID, null);
    }
    
    /**
     * 設置用戶ID
     */
    public void setUserId(String userId) {
        preferences.edit().putString(KEY_USER_ID, userId).apply();
        Log.d(TAG, "用戶ID設置為: " + userId);
    }
    
    /**
     * 獲取用戶名稱
     */
    public String getUserName() {
        return preferences.getString(KEY_USER_NAME, null);
    }
    
    /**
     * 設置用戶名稱
     */
    public void setUserName(String userName) {
        preferences.edit().putString(KEY_USER_NAME, userName).apply();
        Log.d(TAG, "用戶名稱設置為: " + userName);
    }
    
    /**
     * 獲取用戶類型
     */
    public String getUserType() {
        return preferences.getString(KEY_USER_TYPE, USER_TYPE_USER);
    }
    
    /**
     * 設置用戶類型
     */
    public void setUserType(String userType) {
        preferences.edit().putString(KEY_USER_TYPE, userType).apply();
        Log.d(TAG, "用戶類型設置為: " + userType);
    }
    
    /**
     * 檢查是否為志願者
     */
    public boolean isVolunteer() {
        return USER_TYPE_VOLUNTEER.equals(getUserType());
    }
    
    /**
     * 檢查是否為需要幫助人士
     */
    public boolean isUser() {
        return USER_TYPE_USER.equals(getUserType());
    }
    
    /**
     * 登出用戶
     */
    public void logout() {
        preferences.edit()
                .putBoolean(KEY_USER_LOGGED_IN, false)
                .putString(KEY_USER_EMAIL, null)
                .putBoolean(KEY_GUEST_MODE, false)
                .putString(KEY_USER_ID, null)
                .putString(KEY_USER_NAME, null)
                .putString(KEY_USER_TYPE, null)
                .apply();
        Log.d(TAG, "用戶已登出");
    }
    
    /**
     * 清除所有用戶數據
     */
    public void clearAllUserData() {
        preferences.edit().clear().apply();
        Log.d(TAG, "所有用戶數據已清除");
    }
    
    /**
     * 獲取用戶顯示名稱
     */
    public String getUserDisplayName() {
        String userName = getUserName();
        if (userName != null && !userName.isEmpty()) {
            return userName;
        }
        
        String email = getCurrentUserEmail();
        if (email != null && !email.isEmpty()) {
            // 從郵箱中提取用戶名部分
            int atIndex = email.indexOf("@");
            if (atIndex > 0) {
                return email.substring(0, atIndex);
            }
            return email;
        }
        
        if (isGuestMode()) {
            return "訪客用戶";
        }
        
        return "未知用戶";
    }
    
    /**
     * 檢查是否需要登入
     */
    public boolean needsLogin() {
        return !isUserLoggedIn() && !isGuestMode();
    }
    
    /**
     * 獲取用戶狀態描述
     */
    public String getUserStatusDescription() {
        if (isUserLoggedIn()) {
            return "已登入用戶: " + getUserDisplayName();
        } else if (isGuestMode()) {
            return "訪客模式";
        } else {
            return "未登入";
        }
    }
    
    /**
     * 保存用戶登入信息（為Firebase準備）
     */
    public void saveUserLoginInfo(String userId, String email, String userName) {
        saveUserLoginInfo(userId, email, userName, USER_TYPE_USER);
    }
    
    /**
     * 保存用戶登入信息（包含用戶類型）
     */
    public void saveUserLoginInfo(String userId, String email, String userName, String userType) {
        preferences.edit()
                .putBoolean(KEY_USER_LOGGED_IN, true)
                .putString(KEY_USER_ID, userId)
                .putString(KEY_USER_EMAIL, email)
                .putString(KEY_USER_NAME, userName)
                .putString(KEY_USER_TYPE, userType)
                .putBoolean(KEY_GUEST_MODE, false)
                .apply();
        Log.d(TAG, "用戶登入信息已保存: " + email + ", 類型: " + userType);
    }
    
    /**
     * 檢查用戶數據完整性
     */
    public boolean isUserDataComplete() {
        return isUserLoggedIn() && 
               getCurrentUserEmail() != null && 
               !getCurrentUserEmail().isEmpty();
    }
}

