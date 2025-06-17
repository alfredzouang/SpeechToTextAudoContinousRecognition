package com.yourcompany.speechtotext;

import android.util.Log;
import java.io.FileInputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class BufferRecognitionManager {
    private static final String TAG = "BufferRecognitionMgr";
    private static final int BUFFER_SIZE = 4096;
    private static final byte[] END_MARKER = new byte[0];

    private final String wavPath;
    private final AzureTokenManager tokenManager;
    private final String region;
    private final AzureSpeechRecognizer.ResultCallback callback;

    // 限制队列长度，防止 OOM
    private final BlockingQueue<byte[]> bufferQueue = new LinkedBlockingQueue<>(16);
    private volatile boolean fileReadFinished = false;

    public BufferRecognitionManager(String wavPath, AzureTokenManager tokenManager, String region, AzureSpeechRecognizer.ResultCallback callback) {
        this.wavPath = wavPath;
        this.tokenManager = tokenManager;
        this.region = region;
        this.callback = callback;
    }

    public void start() {
        new Thread(this::fileReadThread, "FileReadThread").start();
        new Thread(this::recognitionThread, "RecognitionThread").start();
    }

    private void fileReadThread() {
        long totalBytesRead = 0;
        try {
            while (true) {
                try (FileInputStream fis = new FileInputStream(wavPath)) {
                    byte[] header = new byte[44];
                    int read = fis.read(header);
                    if (read != 44) {
                        Log.e(TAG, "WAV header too short");
                        callback.onResult("Error", "WAV header too short");
                        // 只在 header 错误时退出
                        break;
                    }
                    long offset = 0;
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int len;
                    while ((len = fis.read(buffer)) > 0) {
                        // 只分配实际长度，避免 clone 整个 buffer
                        byte[] actual = new byte[len];
                        System.arraycopy(buffer, 0, actual, 0, len);
                        bufferQueue.put(actual);
                        offset += len;
                        totalBytesRead += len;
                    }
                    Log.i(TAG, "FileReadThread finished one round, bytes read: " + offset + ", total bytes read: " + totalBytesRead);
                    // 读到结尾后，自动重新开始，不发送 END_MARKER
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "FileReadThread exception: " + e.getMessage());
            try { bufferQueue.put(END_MARKER); } catch (Exception ignore) {}
        }
    }

    private void recognitionThread() {
        java.util.LinkedList<byte[]> bufferCache = new java.util.LinkedList<>();
        while (true) {
            AzureSpeechRecognizer.BufferRecognizer recognizer = new AzureSpeechRecognizer.BufferRecognizer(tokenManager, region, callback);
            try {
                while (true) {
                    byte[] buffer;
                    if (!bufferCache.isEmpty()) {
                        buffer = bufferCache.poll();
                    } else {
                        buffer = bufferQueue.take();
                    }
                    if (buffer == END_MARKER) {
                        Log.i(TAG, "RecognitionThread received END_MARKER, closing pushStream and exiting.");
                        try {
                            recognizer.closePushStream();
                            Log.i(TAG, "RecognitionThread: pushStream closed after END_MARKER.");
                        } catch (Exception ex) {
                            Log.e(TAG, "RecognitionThread: pushStream close error: " + ex.getMessage());
                        }
                        recognizer.close();
                        return;
                    }
                    boolean recognized = false;
                    while (!recognized) {
                        try {
                            recognizer.recognizeBuffer(buffer);
                            recognized = true;
                        } catch (AzureSpeechRecognizer.TokenExpiredException e) {
                            Log.i(TAG, "Token expired, refreshing token and recreating recognizer...");
                            tokenManager.forceRefreshToken();
                            // 缓存当前 buffer 及 bufferQueue 中所有未消费 buffer
                            bufferCache.addFirst(buffer);
                            bufferQueue.drainTo(bufferCache);
                            throw new SessionRestartException("Token expired, restart session");
                        } catch (SessionRestartException e) {
                            throw e;
                        } catch (Exception e) {
                            Log.e(TAG, "RecognitionThread exception: " + e.getMessage());
                            callback.onResult("Error", "Recognition exception: " + e.getMessage());
                            // 缓存当前 buffer 及 bufferQueue 中所有未消费 buffer
                            bufferCache.addFirst(buffer);
                            bufferQueue.drainTo(bufferCache);
                            throw new SessionRestartException("Any exception, restart session");
                        }
                    }
                    try { Thread.sleep(10); } catch (InterruptedException ignore) {}
                }
            } catch (SessionRestartException e) {
                Log.i(TAG, "RecognitionThread: session needs restart due to token/session expired, will replay cached buffers.");
                // 彻底销毁 recognizer，外层 while 会新建
            } catch (Exception e) {
                Log.e(TAG, "RecognitionThread fatal exception: " + e.getMessage());
                callback.onResult("Error", "Recognition fatal exception: " + e.getMessage());
                break;
            } finally {
                recognizer.close();
            }
            // sessionStopped 或 401 触发后自动重启 BufferRecognizer，继续消费 bufferQueue
            Log.i(TAG, "RecognitionThread: session ended, restarting BufferRecognizer for continuous recognition.");
        }
    }

    // 自定义异常用于 session 重启
    private static class SessionRestartException extends RuntimeException {
        public SessionRestartException(String msg) { super(msg); }
    }
}
