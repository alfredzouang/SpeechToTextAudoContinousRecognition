package com.yourcompany.speechtotext;

import android.util.Log;
import com.microsoft.cognitiveservices.speech.*;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;
import com.microsoft.cognitiveservices.speech.audio.AudioInputStream;
import com.microsoft.cognitiveservices.speech.audio.AudioStreamFormat;
import com.microsoft.cognitiveservices.speech.audio.PushAudioInputStream;

public class AzureSpeechRecognizer {
    private static final String TAG = "AzureSpeechRecognizer";

    // Token 失效异常
    public static class TokenExpiredException extends Exception {
        public TokenExpiredException(String msg) { super(msg); }
    }

    // 支持 buffer 识别的内部类
    public static class BufferRecognizer {
        private AzureTokenManager tokenManager;
        private String region;
        private ResultCallback callback;
        private SpeechRecognizer recognizer;
        private SpeechConfig speechConfig;
        private AudioConfig audioConfig;
        private PushAudioInputStream pushStream;
        private volatile boolean sessionShouldRestart = false;

        public BufferRecognizer(AzureTokenManager tokenManager, String region, ResultCallback callback) {
            this.tokenManager = tokenManager;
            this.region = region;
            this.callback = callback;
            recreateRecognizer();
        }

        public void recreateRecognizer() {
            try {
                if (recognizer != null) recognizer.close();
                if (audioConfig != null) audioConfig.close();
                if (speechConfig != null) speechConfig.close();
            } catch (Exception ignore) {}
            try {
                String token = tokenManager.getValidToken();
                speechConfig = SpeechConfig.fromAuthorizationToken(token, region);
                speechConfig.setSpeechRecognitionLanguage("en-US");
                pushStream = AudioInputStream.createPushStream(AudioStreamFormat.getWaveFormatPCM(16000, (short)16, (short)1));
                audioConfig = AudioConfig.fromStreamInput(pushStream);
                recognizer = new SpeechRecognizer(speechConfig, audioConfig);
                recognizer.recognizing.addEventListener((s, e) -> {
                    String text = e.getResult().getText();
                    if (text != null && text.trim().length() > 0) {
                        callback.onResult("Recognizing", text);
                    }
                });

                recognizer.recognized.addEventListener((s, e) -> {
                    Log.i(TAG, "[BufferRecognizer] recognized: " + e.getResult().getText());
                    String text = e.getResult().getText();
                    if (!text.isEmpty()) {
                        callback.onResult("Recognized", text);
                    }
                });
                recognizer.canceled.addEventListener((s, e) -> {
                    Log.w(TAG, "[BufferRecognizer] canceled: " + e.getErrorDetails());
                    sessionShouldRestart = true;
                    if (e.getErrorDetails() != null && e.getErrorDetails().contains("401")) {
                        callback.onResult("TokenExpired", "Token expired during buffer recognition");
                    } else {
                        callback.onResult("Error", "Recognition canceled: " + e.getErrorDetails());
                    }
                });
                recognizer.sessionStopped.addEventListener((s, e) -> {
                    Log.i(TAG, "[BufferRecognizer] sessionStopped: 识别全部完成");
                    sessionShouldRestart = true;
                    callback.onResult("AllRecognized", "识别全部完成");
                });
                recognizer.startContinuousRecognitionAsync().get();
            } catch (Exception e) {
                callback.onResult("Error", "BufferRecognizer init failed: " + e.getMessage());
            }
        }

        public void recognizeBuffer(byte[] buffer) throws TokenExpiredException {
            if (sessionShouldRestart) {
                sessionShouldRestart = false;
                throw new RuntimeException("SessionRestartForStoppedOrCanceled");
            }
            try {
                pushStream.write(buffer);
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("401")) {
                    throw new TokenExpiredException("Token expired during buffer recognition");
                }
                throw new RuntimeException(e);
            }
        }

        public void closePushStream() {
            try {
                if (pushStream != null) {
                    pushStream.close();
                }
            } catch (Exception ignore) {}
        }

        public void close() {
            try {
                if (recognizer != null) recognizer.close();
                if (audioConfig != null) audioConfig.close();
                if (speechConfig != null) speechConfig.close();
            } catch (Exception ignore) {}
        }

    }

    public interface ResultCallback {
        void onResult(String type, String text);
    }
}
