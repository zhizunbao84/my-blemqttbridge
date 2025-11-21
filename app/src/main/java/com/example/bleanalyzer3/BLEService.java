package com.example.bleanalyzer3;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.annotation.Nullable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

public class BLEService extends Service {
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private Handler handler = new Handler(Looper.getMainLooper());
    private ConfigManager configManager;
    private MQTTManager mqttManager;
    
    private boolean isScanning = false;
    private Runnable scanRunnable;
    private Set<String> targetMacs = new HashSet<>();
    
    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            processScanResult(result);
        }
        
        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            for (ScanResult result : results) {
                processScanResult(result);
            }
        }
        
        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Logger.e("BLE scan failed with error code: " + errorCode);
        }
    };
    
    @Override
    public void onCreate() {
        super.onCreate();
        Logger.i("BLEService onCreate");
        
        configManager = ConfigManager.getInstance(this);
        mqttManager = MQTTManager.getInstance(this);
        
        initializeBluetooth();
        loadTargetDevices();
        
        // 连接MQTT
        mqttManager.connect();
        
        // 开始扫描循环
        startScanning();
    }
    
    private void initializeBluetooth() {
        try {
            BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
            if (bluetoothManager != null) {
                bluetoothAdapter = bluetoothManager.getAdapter();
                if (bluetoothAdapter != null) {
                    bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
                    Logger.i("Bluetooth initialized successfully");
                } else {
                    Logger.e("Bluetooth adapter is null");
                }
            }
        } catch (Exception e) {
            Logger.e("Error initializing Bluetooth", e);
        }
    }
    
    private void loadTargetDevices() {
        String[] macs = configManager.getDeviceMacs();
        targetMacs.clear();
        targetMacs.addAll(Arrays.asList(macs));
        Logger.i("Loaded " + targetMacs.size() + " target devices");
        Logger.i("Target MAC list: " + targetMacs);
    }
    
    private void startScanning() {
        if (scanRunnable == null) {
            scanRunnable = new Runnable() {
                @Override
                public void run() {
                    if (isScanning) {
                        stopScan();
                    } else {
                        startScan();
                    }
                    handler.postDelayed(this, configManager.getScanInterval());
                }
            };
        }
        handler.post(scanRunnable);
    }
    
    private void startScan() {
        if (bluetoothLeScanner == null) {
            Logger.e("BluetoothLeScanner is null");
            return;
        }
        
        try {
            bluetoothLeScanner.startScan(scanCallback);
            isScanning = true;
            Logger.d("BLE scan started");
        } catch (Exception e) {
            Logger.e("Error starting BLE scan", e);
            isScanning = false;
        }
    }
    
    private void stopScan() {
        if (bluetoothLeScanner == null) {
            return;
        }
        
        try {
            bluetoothLeScanner.stopScan(scanCallback);
            isScanning = false;
            Logger.d("BLE scan stopped");
        } catch (Exception e) {
            Logger.e("Error stopping BLE scan", e);
        }
    }
    
    private void processScanResult(ScanResult result) {
        BluetoothDevice device = result.getDevice();
        if (device == null) {
            return;
        }
        
        String deviceAddress = device.getAddress();
        String deviceName = device.getName();
        

        
        byte[] scanRecord = result.getScanRecord().getBytes();
        int rssi = result.getRssi();
        
        Logger.d("Found target device: " + deviceAddress + " (Name: " + deviceName + "), RSSI: " + rssi);
        
        // 将数据发送到MQTT
        sendToMQTT(deviceAddress, deviceName, scanRecord, rssi);
    }
    
    private void sendToMQTT(String macAddress, String deviceName, byte[] scanRecord, int rssi) {
        try {
            String topic = configManager.getMQTTTopicPrefix() + "/" + macAddress.replace(":", "");
            
            // 构建JSON格式的消息
            String message = String.format(
                "{\"mac\":\"%s\",\"name\":\"%s\",\"rssi\":%d,\"data\":\"%s\",\"timestamp\":%d}",
                macAddress,
                deviceName != null ? deviceName : "",
                rssi,
                bytesToHex(scanRecord),
                System.currentTimeMillis()
            );
            
            mqttManager.publish(topic, message);
            
        } catch (Exception e) {
            Logger.e("Error sending to MQTT", e);
        }
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Logger.i("BLEService onDestroy");
        
        if (handler != null && scanRunnable != null) {
            handler.removeCallbacks(scanRunnable);
        }
        
        stopScan();
        mqttManager.disconnect();
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
