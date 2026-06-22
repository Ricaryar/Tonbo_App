package com.example.tonbo_app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

public class LoginActivity extends BaseAccessibleActivity {
    private EditText emailEditText;
    private EditText passwordEditText;
    private Button loginButton;
    private Button volunteerButton; // 志願者角色按鈕
    private Button userButton;      // 視障人士角色按鈕

    private UserManager userManager;
    private String selectedUserType = UserManager.USER_TYPE_USER; // 默認為視障人士

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        userManager = UserManager.getInstance(this); //
        initViews();
    }

    private void initViews() {
        emailEditText = findViewById(R.id.email_edit_text);
        passwordEditText = findViewById(R.id.password_edit_text);
        loginButton = findViewById(R.id.login_button);
        volunteerButton = findViewById(R.id.volunteer_button);
        userButton = findViewById(R.id.user_button);

        // 身份選擇邏輯
        volunteerButton.setOnClickListener(v -> {
            selectedUserType = UserManager.USER_TYPE_VOLUNTEER;
            announceInfo("已選擇志願者模式"); //
        });

        userButton.setOnClickListener(v -> {
            selectedUserType = UserManager.USER_TYPE_USER;
            announceInfo("已選擇用戶模式");
        });

        // 登錄邏輯（應急 Mock 版）
        loginButton.setOnClickListener(v -> {
            String email = emailEditText.getText().toString().trim();
            if (!email.isEmpty()) {
                // 保存狀態到數據庫
                userManager.setUserLoggedIn(true);
                userManager.setCurrentUserEmail(email);
                userManager.setUserType(selectedUserType);

                ttsManager.speak("登錄成功，進入系統", "Login success", true);
                navigateToMainActivity();
            }
        });
    }

    private void navigateToMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    protected void announcePageTitle() {
        ttsManager.speak("登錄頁面。請選擇身份並輸入賬號。", "Login page.", true);
    }
}