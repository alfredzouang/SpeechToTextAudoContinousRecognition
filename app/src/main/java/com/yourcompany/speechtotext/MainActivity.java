package com.yourcompany.speechtotext;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ScrollView;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final String VIDEO_PATH = "/sdcard/Download/input.flv"; // 可改为 input.mp4
    private static final String AUDIO_PATH = "/sdcard/Android/data/com.yourcompany.speechtotext/files/output.wav";

    private TextView textView;
    private Button btnStart;
    private ScrollView scrollView;
    private Handler uiHandler;

    // 新增：识别终稿和草稿缓存
    private final java.util.List<String> recognizedList = new java.util.ArrayList<>();
    private String recognizingDraft = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView = findViewById(R.id.textView);
        btnStart = findViewById(R.id.btnStart);
        scrollView = findViewById(R.id.scrollView);
        uiHandler = new Handler(Looper.getMainLooper());

        btnStart.setOnClickListener(v -> {
            textView.setText("");
            startRecognition();
        });

        checkAndRequestPermissions();
    }

    private void checkAndRequestPermissions() {
        String[] permissions = new String[] {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[] {
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
            };
        }
        boolean needRequest = false;
        for (String perm : permissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                needRequest = true;
                break;
            }
        }
        if (needRequest) {
            ActivityCompat.requestPermissions(this, permissions, 100);
        }
    }

    private void startRecognition() {
        btnStart.setEnabled(false);
        appendText("开始提取音频...\n");
        // 清空历史识别内容
        uiHandler.post(() -> {
            recognizedList.clear();
            recognizingDraft = "";
            textView.setText("");
        });
        new Thread(() -> {
            try {
                // 1. 读取配置
                ConfigManager.AzureConfig config = ConfigManager.loadConfig(MainActivity.this);
                // 2. 提取音频
                boolean extractOk = AudioExtractorUtil.extractAudio(MainActivity.this, VIDEO_PATH, AUDIO_PATH);
                if (!extractOk) {
                    appendText("音频提取失败\n");
                    enableButton();
                    return;
                }
                appendText("音频提取完成，开始获取Token...\n");
                // 3. 获取Token
                AzureTokenManager tokenManager = new AzureTokenManager(config.subscriptionKey, config.region, config.tokenEndpoint);
                String token = tokenManager.getValidToken();
                appendText("Token获取成功，开始识别...\n");
                // 4. 识别音频（使用 BufferRecognitionManager 方案）
                AzureSpeechRecognizer.ResultCallback callback = (type, text) -> {
                    Log.i("BufferRecogDemo", type + ": " + text);
                    if ("Recognizing".equals(type)) {
                        updateRecognizingDraft(text);
                    } else if ("Recognized".equals(type)) {
                        commitRecognized(text);
                    } else if ("Error".equals(type)) {
                        appendText("识别异常: " + text + "\n");
                    } else if ("TokenExpired".equals(type)) {
                        appendText("Token 失效，已自动刷新并重试\n");
                    }
                };
                BufferRecognitionManager manager = new BufferRecognitionManager(AUDIO_PATH, tokenManager, config.region, callback);
                manager.start();
                appendText("识别已启动（BufferRecognitionManager）\n");
            } catch (Exception e) {
                appendText("发生异常: " + e.getMessage() + "\n");
            } finally {
                enableButton();
            }
        }).start();
    }

    // recognizing 事件：只更新草稿
    private void updateRecognizingDraft(String draft) {
        uiHandler.post(() -> {
            recognizingDraft = draft;
            renderRecognitionText();
        });
    }

    // recognized 事件：将草稿变为终稿
    private void commitRecognized(String recognized) {
        uiHandler.post(() -> {
            if (!recognized.trim().isEmpty()) {
                recognizedList.add(recognized.trim());
            }
            recognizingDraft = "";
            renderRecognitionText();
        });
    }

    // 渲染所有 recognized + 当前草稿
    private void renderRecognitionText() {
        StringBuilder sb = new StringBuilder();
        for (String line : recognizedList) {
            sb.append(line).append("\n");
        }
        if (!recognizingDraft.isEmpty()) {
            sb.append(recognizingDraft);
        }
        textView.setText(sb.toString());
        scrollToBottom();
    }

    private void appendText(String text) {
        uiHandler.post(() -> {
            textView.append(text);
            scrollToBottom();
        });
    }

    private void scrollToBottom() {
        if (scrollView != null) {
            // 先立即滚动，再延迟滚动，确保内容刷新后能滚到底
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
            scrollView.postDelayed(() -> scrollView.fullScroll(View.FOCUS_DOWN), 50);
        }
    }

    private void enableButton() {
        uiHandler.post(() -> btnStart.setEnabled(true));
    }
}
