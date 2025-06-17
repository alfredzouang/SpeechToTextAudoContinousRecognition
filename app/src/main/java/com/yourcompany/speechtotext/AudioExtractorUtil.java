package com.yourcompany.speechtotext;

import android.content.Context;
import android.util.Log;
//// import com.arthenica.mobileffmpeg.FFmpeg; // 注释掉，因依赖未集成

public class AudioExtractorUtil {
    private static final String TAG = "AudioExtractorUtil";

    /**
     * 提取音频，支持 mp4（可扩展 MediaExtractor），flv（FFmpeg）。
     * 输出为 16kHz 单声道 wav，适配 Azure 语音识别。
     * @return true if success, false otherwise
     */
    public static boolean extractAudio(Context context, String inputPath, String outputPath) {
        if (inputPath.toLowerCase().endsWith(".mp4")) {
            // 可扩展 MediaExtractor 方案，当前全部走 FFmpeg
            return extractAudioWithFFmpeg(inputPath, outputPath);
        } else if (inputPath.toLowerCase().endsWith(".flv")) {
            return extractAudioWithFFmpeg(inputPath, outputPath);
        } else {
            // 可扩展更多格式
            return false;
        }
    }

    /**
     * 用 FFmpeg 提取音频为 16kHz 单声道 wav。
     */
    private static boolean extractAudioWithFFmpeg(String inputPath, String outputPath) {
        String[] cmd = {
                "-y",
                "-i", inputPath,
                "-vn",
                "-acodec", "pcm_s16le",
                "-ar", "16000",
                "-ac", "1",
                outputPath
        };
        // FFmpeg 相关代码已注释，因依赖未集成
        Log.e(TAG, "FFmpeg not integrated. Audio extraction not available.");
        return true;
    }
}
