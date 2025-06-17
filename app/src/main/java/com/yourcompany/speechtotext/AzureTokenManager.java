package com.yourcompany.speechtotext;

import android.util.Log;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class AzureTokenManager {
    private static final String TAG = "AzureTokenManager";
    private static final long REFRESH_INTERVAL = 9 * 60 * 1000; // 9分钟

    private String subscriptionKey;
    private String region;
    private String tokenEndpoint;
    private String token;
    private long lastFetchTime; // ms

    private final OkHttpClient client = new OkHttpClient();

    // 新增构造函数，支持 tokenEndpoint
    public AzureTokenManager(String subscriptionKey, String region, String tokenEndpoint) {
        this.subscriptionKey = subscriptionKey;
        this.region = region;
        this.tokenEndpoint = tokenEndpoint;
        this.token = null;
        this.lastFetchTime = 0;
    }

    // 兼容老用法
    public AzureTokenManager(String subscriptionKey, String region) {
        this(subscriptionKey, region, null);
    }

    public synchronized String getValidToken() throws Exception {
        long now = System.currentTimeMillis();
        if (token == null || (now - lastFetchTime) > REFRESH_INTERVAL) {
            fetchToken();
        }
        return token;
    }

    // 强制刷新 token，供 TokenExpired 场景调用
    public synchronized void forceRefreshToken() throws Exception {
        fetchToken();
    }

    private void fetchToken() throws Exception {
        if (tokenEndpoint != null && !tokenEndpoint.isEmpty()) {
            // 通过 tokenEndpoint 获取 token，假设为 GET
            Request request = new Request.Builder()
                    .url(tokenEndpoint)
                    .get()
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Token fetch from tokenEndpoint failed: " + response.code() + " " + response.message());
                    throw new RuntimeException("Token fetch from tokenEndpoint failed: " + response.code() + " " + response.message());
                }
                String body = response.body() != null ? response.body().string() : null;
                if (body == null || body.isEmpty()) {
                    throw new RuntimeException("Token fetch from tokenEndpoint failed: empty body");
                }
                token = body;
                lastFetchTime = System.currentTimeMillis();
                return;
            } catch (IOException e) {
                Log.e(TAG, "Token fetch from tokenEndpoint exception: " + e.getMessage());
                throw new RuntimeException("Token fetch from tokenEndpoint exception: " + e.getMessage());
            }
        }
        // fallback: 通过 subscriptionKey+region 获取
        String url = getTokenUrl();
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create("", MediaType.parse("application/x-www-form-urlencoded")))
                .addHeader("Ocp-Apim-Subscription-Key", subscriptionKey)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                Log.e(TAG, "Token fetch failed: " + response.code() + " " + response.message());
                throw new RuntimeException("Token fetch failed: " + response.code() + " " + response.message());
            }
            String body = response.body() != null ? response.body().string() : null;
            if (body == null || body.isEmpty()) {
                throw new RuntimeException("Token fetch failed: empty body");
            }
            token = body;
            lastFetchTime = System.currentTimeMillis();
        } catch (IOException e) {
            Log.e(TAG, "Token fetch exception: " + e.getMessage());
            throw new RuntimeException("Token fetch exception: " + e.getMessage());
        }
    }

    private String getTokenUrl() {
        return "https://" + region + ".api.cognitive.microsoft.com/sts/v1.0/issueToken";
    }
}
