package com.example.bleanalyzer;

import android.bluetooth.*;
import android.content.Context;
import android.util.Log;
import java.util.UUID;

public class BleWrapper {

    private final BluetoothAdapter adapter;
    private final Callback log;
    private BluetoothGatt gatt;

    interface Callback { void onLog(String s); }

    BleWrapper(BluetoothAdapter adapter, Callback log) {
        this.adapter = adapter;
        this.log = log;
    }

    void connect(String mac) {
        BluetoothDevice dev = adapter.getRemoteDevice(mac);
        if (dev == null) { log.onLog("未找到该 MAC 设备"); return; }
        log.onLog("开始连接 " + mac);
        gatt = dev.connectGatt((Context) log, false, new BleGattCallback(log));
    }

    void close() {
        if (gatt != null) {
            gatt.close();
            gatt = null;
        }
    }

    /* 对外暴露写特征，可扩展 */
    void writeCharacteristic(BluetoothGattCharacteristic ch, byte[] data) {
        if (gatt == null || ch == null) return;
        ch.setValue(data);
        gatt.writeCharacteristic(ch);
    }
}
