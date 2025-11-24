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
        
        /* 1. 启动 BLE 扫描（无论 MQTT 是否连上） */
        initializeBluetooth();
        loadTargetDevices();
        startScanning();          // ← 先扫，MQTT后启
    
        /* 2. MQTT 异步连接（失败不影响 BLE） */
        new Thread(() -> {
            int retry = 0;
            while (!mqttManager.isConnected() && retry < 10) {
                Logger.i("MQTT connect attempt " + (retry + 1));
                mqttManager.connect();
                if (!mqttManager.isConnected()) {
                    try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                    retry++;
                }
            }
        }).start();
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
        byte[] raw = result.getScanRecord().getBytes();
        if (raw == null || raw.length < 15) return;

    /* 1. 无脑打印完整广播包 ＋ MAC */
        StringBuilder hex = new StringBuilder("收到广播  MAC=");
        hex.append(deviceAddress).append("  Len=")
           .append(raw.length).append("  Data=");
        for (byte b : raw) {
            hex.append(String.format("%02X ", b & 0xFF));
        }
        Logger.d(hex.toString());

        /* 1. 定位 BTHome v2 头  0x16D2FC40 */
        int idx = 0;
        while (idx < raw.length - 6) {
            if (raw[idx] == 0x16 &&
                raw[idx + 1] == (byte) 0xD2 &&
                raw[idx + 2] == (byte) 0xFC &&
                raw[idx + 3] == 0x40) break;
            idx++;
        }
        if (idx > raw.length - 6) return;
    
        /* 2. 剥离 payload（头之后所有字节） */
        int offset = idx + 4;                 // 跳过 16 D2 FC 40
        int payloadLen = raw.length - offset;
        if (payloadLen < 3) return;
    
        /* 3. 逐字段解析（与 Python 完全一致） */
        int i = offset;
        float temperature = 0, humidity = 0, voltage = 0;
        int battery = 0;
    
        while (i < raw.length - 1) {
            int typeId = raw[i] & 0xFF;
            i++;                                    // 跳过 type 字节
    
            switch (typeId) {
                case 0x01:                          // 电池 %
                    battery = raw[i] & 0xFF;
                    i += 1;
                    break;
    
                case 0x02:                          // 温度  sint16  ×0.01 ℃
                    int tempRaw = (raw[i] & 0xFF) | ((raw[i + 1] & 0xFF) << 8);
                    temperature = tempRaw / 100.0f;
                    i += 2;
                    break;
    
                case 0x03:                          // 湿度  uint16  ×0.01 %
                    int humRaw = (raw[i] & 0xFF) | ((raw[i + 1] & 0xFF) << 8);
                    humidity = humRaw / 100.0f;
                    i += 2;
                    break;
    
                case 0x0C:                          // 电压  uint16  ×0.001 V
                    int voltRaw = (raw[i] & 0xFF) | ((raw[i + 1] & 0xFF) << 8);
                    voltage = voltRaw / 1000.0f;
                    i += 2;
                    break;
    
                default:                            // 未知 type，跳过 1 字节
                    i += 1;
                    break;
            }
        }
    
        /* 4. 打印结果（与 Python 完全一致） */
        Logger.d("★ BTHome明文  温度=" + temperature +
            "℃  湿度=" + humidity +
            "%  电池=" + battery +
            "%  电压=" + voltage + "V");
        // 将数据发送到MQTT
        sendToMQTT(deviceAddress, deviceName, scanRecord, rssi);
    }
    
    private void sendToMQTT(String macAddress, float temperature, float humidity, int battery) {
        try {
            String topic = configManager.getMQTTTopicPrefix() + "/" + macAddress.replace(":", "")+"/state";
            
            // 构建JSON格式的消息
            String message = String.format(
                "{\"temperature\":\"%.1f\",\"humidity\":\"%.1f\",\"battery\":%d}",
                temperature,
                humidity,
                battery
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
