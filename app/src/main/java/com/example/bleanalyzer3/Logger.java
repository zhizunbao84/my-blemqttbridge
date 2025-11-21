package com.example.bleanalyzer3;

import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Logger {
    private static final String TAG = "BLEMQTTBridge";
    private static String logLevel = "DEBUG";
    
    public static void setLogLevel(String level) {
        logLevel = level;
    }
    
    private static boolean shouldLog(String level) {
        int currentLevel = getLevelValue(logLevel);
        int msgLevel = getLevelValue(level);
        return msgLevel >= currentLevel;
    }
    
    private static int getLevelValue(String level) {
        switch (level.toUpperCase()) {
            case "DEBUG": return 0;
            case "INFO": return 1;
            case "WARN": return 2;
            case "ERROR": return 3;
            default: return 0;
        }
    }
    
    private static String formatMessage(String level, String message) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                .format(new Date());
        return String.format("[%s] %s: %s", timestamp, level, message);
    }
    
    public static void d(String message) {
        if (shouldLog("DEBUG")) {
            String formatted = formatMessage("DEBUG", message);
            Log.d(TAG, formatted);
        }
    }
    
    public static void i(String message) {
        if (shouldLog("INFO")) {
            String formatted = formatMessage("INFO", message);
            Log.i(TAG, formatted);
        }
    }
    
    public static void w(String message) {
        if (shouldLog("WARN")) {
            String formatted = formatMessage("WARN", message);
            Log.w(TAG, formatted);
        }
    }
    
    public static void e(String message) {
        if (shouldLog("ERROR")) {
            String formatted = formatMessage("ERROR", message);
            Log.e(TAG, formatted);
        }
    }
    
    public static void e(String message, Throwable throwable) {
        if (shouldLog("ERROR")) {
            String formatted = formatMessage("ERROR", message);
            Log.e(TAG, formatted, throwable);
        }
    }
}
