package com.example.bleanalyzer3;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class MainActivity extends AppCompatActivity {
    private TextView logTextView;
    private ScrollView scrollView;
    private Button startButton;
    private Button stopButton;
    private Button refreshLogButton;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createUI();
        
        Logger.i("MainActivity created");
        
        // 检查权限
        checkPermissions();
        
        // 显示配置文件路径
        String configPath = ConfigManager.getInstance(this).getConfigFilePath();
        Logger.i("Config file path: " + configPath);
    }
    
    private void createUI() {
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(16, 16, 16, 16);
        
        // 创建按钮布局
        LinearLayout buttonLayout = new LinearLayout(this);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        
        startButton = new Button(this);
        startButton.setText("Start Service");
        startButton.setOnClickListener(v -> startService());
        
        stopButton = new Button(this);
        stopButton.setText("Stop Service");
        stopButton.setOnClickListener(v -> stopService());
        
        refreshLogButton = new Button(this);
        refreshLogButton.setText("Refresh Log");
        refreshLogButton.setOnClickListener(v -> refreshLog());
        
        buttonLayout.addView(startButton);
        buttonLayout.addView(stopButton);
        buttonLayout.addView(refreshLogButton);
        
        // 创建日志显示区域
        logTextView = new TextView(this);
        logTextView.setTextSize(10);
        logTextView.setPadding(8, 8, 8, 8);
        logTextView.setTypeface(android.graphics.Typeface.MONOSPACE);
        
        scrollView = new ScrollView(this);
        scrollView.addView(logTextView);
        
        mainLayout.addView(buttonLayout);
        mainLayout.addView(scrollView, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1.0f));
        
        setContentView(mainLayout);
    }
    
    private void checkPermissions() {
        if (!PermissionManager.hasAllPermissions(this)) {
            Logger.w("Requesting permissions...");
            PermissionManager.requestPermissions(this);
        } else {
            Logger.i("All permissions granted");
            startService();
        }
        
        // Android 11+ 需要特殊处理后台位置权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!ActivityCompat.shouldShowRequestPermissionRationale(this, 
                    android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                try {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                } catch (Exception e) {
                    Logger.e("Error opening app settings", e);
                }
            }
        }
    }
    
    private void startService() {
        try {
            Intent serviceIntent = new Intent(this, BLEService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            Logger.i("BLE Service started");
        } catch (Exception e) {
            Logger.e("Error starting service", e);
        }
    }
    
    private void stopService() {
        try {
            Intent serviceIntent = new Intent(this, BLEService.class);
            stopService(serviceIntent);
            Logger.i("BLE Service stopped");
        } catch (Exception e) {
            Logger.e("Error stopping service", e);
        }
    }
    
    private void refreshLog() {
        new Thread(() -> {
            try {
                Process process = Runtime.getRuntime().exec("logcat -d -s BLEMQTTBridge:*");
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                
                StringBuilder log = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    log.append(line).append("\n");
                }
                
                runOnUiThread(() -> {
                    logTextView.setText(log.toString());
                    scrollView.fullScroll(ScrollView.FOCUS_DOWN);
                });
                
            } catch (Exception e) {
                Logger.e("Error reading logcat", e);
            }
        }).start();
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (PermissionManager.isPermissionGranted(requestCode, permissions, grantResults)) {
            Logger.i("All permissions granted");
            startService();
        } else {
            Logger.e("Some permissions denied");
        }
    }
}
