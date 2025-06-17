package com.yourcompany.speechtotext;

import android.content.Context;
import org.json.JSONObject;
import java.io.IOException;
import java.io.InputStream;

public class ConfigManager {
    private static AzureConfig config = null;

    public static AzureConfig loadConfig(Context context) {
        if (config != null) return config;
        try {
            InputStream inputStream = context.getAssets().open("config.json");
            int size = inputStream.available();
            byte[] buffer = new byte[size];
            inputStream.read(buffer);
            inputStream.close();
            String jsonStr = new String(buffer, "UTF-8");
            JSONObject json = new JSONObject(jsonStr);
            String subscriptionKey = json.optString("subscriptionKey", null);
            String region = json.optString("region", null);
            String tokenEndpoint = json.optString("tokenEndpoint", null);
            config = new AzureConfig(subscriptionKey, region, tokenEndpoint);
            return config;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config.json: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("Invalid config.json format: " + e.getMessage());
        }
    }

    public static class AzureConfig {
        public final String subscriptionKey;
        public final String region;
        public final String tokenEndpoint;

        public AzureConfig(String subscriptionKey, String region, String tokenEndpoint) {
            this.subscriptionKey = subscriptionKey;
            this.region = region;
            this.tokenEndpoint = tokenEndpoint;
        }
    }
}
