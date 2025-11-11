package com.example.bleanalyzer;

import android.bluetooth.*;
import android.util.Log;
import java.util.List;
import java.util.UUID;

class BleGattCallback extends BluetoothGattCallback {

    private final BleWrapper.Callback log;

    BleGattCallback(BleWrapper.Callback log) { this.log = log; }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            log.onLog("已连接，开始发现服务…");
            gatt.discoverServices();
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            log.onLog("连接断开");
            gatt.close();
        }
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            log.onLog("发现服务失败 status=" + status);
            return;
        }
        List<BluetoothGattService> services = gatt.getServices();
        log.onLog("共发现 " + services.size() + " 个 Service");
        for (BluetoothGattService svc : services) {
            log.onLog("  Service: " + svc.getUuid());
            List<BluetoothGattCharacteristic> chars = svc.getCharacteristics();
            for (BluetoothGattCharacteristic ch : chars) {
                log.onLog("    Characteristic: " + ch.getUuid() +
                        "  Properties=" + ch.getProperties());
                /* 对 NOTIFY 特征使能通知 */
                if ((ch.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                    boolean ok = gatt.setCharacteristicNotification(ch, true);
                    BluetoothGattDescriptor dsc = ch.getDescriptor(
                            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                    if (dsc != null) {
                        dsc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        gatt.writeDescriptor(dsc);
                        log.onLog("      已使能 NOTIFY");
                    }
                }
                /* 打印所有 Descriptor */
                List<BluetoothGattDescriptor> dscs = ch.getDescriptors();
                for (BluetoothGattDescriptor d : dscs) {
                    log.onLog("      Descriptor: " + d.getUuid());
                }
            }
        }
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt,
                                        BluetoothGattCharacteristic characteristic) {
        byte[] data = characteristic.getValue();
        log.onLog("<<< 收到通知 from " + characteristic.getUuid()
                + "  数据：" + bytesToHex(data));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02X ", b));
        return sb.toString();
    }
}
