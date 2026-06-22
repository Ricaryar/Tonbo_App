package com.example.tonbo_app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * 即時協助：兩個大按鈕（視障人士 / 志願者），進入同一聲網頻道以便互相接通。
 */
public class InstantAssistanceActivity extends BaseAccessibleActivity {
    private static final String TAG = "InstantAssistance";
    private static final int PERMISSION_REQUEST_VIDEO = 102;
    private static final String ROLE_BLIND = "blind_user";
    private static final String ROLE_VOLUNTEER = "volunteer";

    /** 兩端必須一致，兩部手機才能進同一房 */
    private static final String SHARED_PAIR_CHANNEL = "tonbo_assist_pair_room";

    private ImageButton backButton;
    private TextView pageTitle;
    private Button btnBlindUser;
    private Button btnVolunteer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_instant_assistance);

        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("language")) {
            currentLanguage = intent.getStringExtra("language");
        }

        initViews();
    }

    @Override
    protected void announcePageTitle() {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (ttsManager == null) {
                return;
            }
            String cantonese = "即時協助。上面大掣係視障人士，下面係義工，撳咗之後再撳加入頻道，兩部電話會入同一間房。";
            String english = "Instant assistance. Top button: blind user. Bottom: volunteer. After opening, tap join channel on both phones to enter the same room.";
            String mandarin = "即时协助。上面大按钮是视障人士，下面是志愿者。进入后双方再点加入频道，两部手机会进同一房间。";
            switch (currentLanguage) {
                case "english":
                    ttsManager.speak(english, english, true);
                    break;
                case "mandarin":
                    ttsManager.speak(mandarin, mandarin, true);
                    break;
                case "cantonese":
                default:
                    ttsManager.speak(cantonese, english, true);
                    break;
            }
        }, 500);
    }

    private void initViews() {
        backButton = findViewById(R.id.backButton);
        pageTitle = findViewById(R.id.pageTitle);
        btnBlindUser = findViewById(R.id.btnBlindUser);
        btnVolunteer = findViewById(R.id.btnVolunteer);

        if (backButton != null) {
            backButton.setOnClickListener(v -> handleBackPressed());
        }

        applyLabels();

        if (btnBlindUser != null) {
            btnBlindUser.setOnClickListener(v -> {
                vibrationManager.vibrateClick();
                openSharedPairVideo(ROLE_BLIND);
            });
        }
        if (btnVolunteer != null) {
            btnVolunteer.setOnClickListener(v -> {
                vibrationManager.vibrateClick();
                openSharedPairVideo(ROLE_VOLUNTEER);
            });
        }
    }

    private void applyLabels() {
        if (pageTitle != null) {
            pageTitle.setText(titleText());
        }
        if (btnBlindUser != null) {
            String t = blindButtonText();
            btnBlindUser.setText(t);
            btnBlindUser.setContentDescription(t);
        }
        if (btnVolunteer != null) {
            String t = volunteerButtonText();
            btnVolunteer.setText(t);
            btnVolunteer.setContentDescription(t);
        }
    }

    private String titleText() {
        if ("english".equals(currentLanguage)) {
            return "Instant Assistance";
        }
        if ("mandarin".equals(currentLanguage)) {
            return "即时协助";
        }
        return "即時協助";
    }

    private String blindButtonText() {
        if ("english".equals(currentLanguage)) {
            return "I am a blind user\nVideo assistance";
        }
        if ("mandarin".equals(currentLanguage)) {
            return "我是视障人士\n视频协助";
        }
        return "我係視障人士\n視像協助";
    }

    private String volunteerButtonText() {
        if ("english".equals(currentLanguage)) {
            return "I am a volunteer\nVideo assistance";
        }
        if ("mandarin".equals(currentLanguage)) {
            return "我是志愿者\n视频协助";
        }
        return "我係義工\n視像協助";
    }

    private void openSharedPairVideo(String role) {
        List<String> need = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.CAMERA);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.RECORD_AUDIO);
        }
        if (!need.isEmpty()) {
            ActivityCompat.requestPermissions(this, need.toArray(new String[0]), PERMISSION_REQUEST_VIDEO);
            pendingRole = role;
            return;
        }
        launchVideoCall(role);
    }

    private String pendingRole = ROLE_BLIND;

    private void launchVideoCall(String role) {
        try {
            Intent videoIntent = new Intent(this, VideoCallActivity.class);
            videoIntent.putExtra("language", currentLanguage);
            videoIntent.putExtra(VideoCallActivity.EXTRA_AGORA_CHANNEL, SHARED_PAIR_CHANNEL);
            videoIntent.putExtra(VideoCallActivity.EXTRA_USER_ROLE, role);
            startActivity(videoIntent);
            vibrationManager.vibrateSuccess();
        } catch (Exception e) {
            Log.e(TAG, "open video", e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != PERMISSION_REQUEST_VIDEO) {
            return;
        }
        boolean allGranted = true;
        for (int r : grantResults) {
            if (r != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }
        if (allGranted) {
            announceInfo(getString(R.string.camera_permission_granted));
            launchVideoCall(pendingRole);
        } else {
            announceError(getString(R.string.camera_permission_denied));
        }
    }
}
