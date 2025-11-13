package com.example.bleanalyzer;

import android.bluetooth.*;
import android.util.Log;
import java.util.Locale;
import java.util.UUID;

class BleGattCallback extends BluetoothGattCallback {

    private final BleWrapper.Callback log;

    BleGattCallback(BleWrapper.Callback log) {
        this.log = log;
    }

    /* ---------- 连接 ---------- */
    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            log.onLog("已连接，开始发现服务...");
            gatt.discoverServices();
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            log.onLog("连接断开");
            gatt.close();
        }
    }

    /* ---------- 发现服务 ---------- */
    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            log.onLog("发现服务失败 status=" + status);
            return;
        }

        for (BluetoothGattService svc : gatt.getServices()) {
            log.onLog(String.format(Locale.US, "Service  %s", svc.getUuid()));
            /* 逐个 characteristic 处理 */
            for (BluetoothGattCharacteristic ch : svc.getCharacteristics()) {
                int props = ch.getProperties();
                log.onLog(String.format(Locale.US, "  Characteristic  %s  props=0x%02X", ch.getUuid(), props));

                /* 1. 能通知/指示 → 逐个打开（串行，等回调完成再下一个） */
                if ((props & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ||
                    (props & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
                    boolean ok = gatt.setCharacteristicNotification(ch, true);
                    if (!ok) {
                        log.onLog("    setCharacteristicNotification 失败");
                        continue;
                    }
                    /* 把 CCC 写队列里，写完一条再写第二条 */
                    BluetoothGattDescriptor desc = ch.getDescriptor(
                            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                    if (desc != null) {
                        desc.setValue((props & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                                ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                                : BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        /* 关键：把 characteristic 存到 tag，方便在 onDescriptorWrite 里继续 */
                        desc.setCharacteristic(ch);
                        gatt.writeDescriptor(desc);
                        /* 先不写下一条，等 onDescriptorWrite 回来再 continue */
                        return;
                    }
                }

                /* 2. 没有通知属性 → 直接主动读一次（可能设备只支持读） */
                else if ((props & BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                    log.onLog("    主动读取 " + ch.getUuid());
                    gatt.readCharacteristic(ch);
                    /* 同样等 onCharacteristicRead 回来再继续 */
                    return;
                }
            }
        }
    }

    /* ---------- CCC 写完成 ---------- */
    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        BluetoothGattCharacteristic ch = descriptor.getCharacteristic();
        log.onLog(String.format(Locale.US,
                "    CCC 写入 %s → %s", ch.getUuid(), status == BluetoothGatt.GATT_SUCCESS ? "OK" : "失败"));
        /* 继续开下一条 characteristic */
        continueOpenNext(gatt, ch);
    }

    /* ---------- 主动读完成 ---------- */
    @Override
    public void onCharacteristicRead(BluetoothGatt gatt,
                                     BluetoothGattCharacteristic characteristic, int status) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            log.onLog(String.format(Locale.US, ">>> 主动读取  %s  数据=%s",
                    characteristic.getUuid(), bytesToHex(characteristic.getValue())));
        }
        /* 继续处理下一条 */
        continueOpenNext(gatt, characteristic);
    }

    /* ---------- 通知到达 ---------- */
    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt,
                                        BluetoothGattCharacteristic characteristic) {
        byte[] data = characteristic.getValue();
        log.onLog(String.format(Locale.US, "<<< 收到通知  %s  数据=%s",
                characteristic.getUuid(), bytesToHex(data)));
        /* TODO: 如果是一条 characteristic 里含多种数据，在此按字段拆包 */
    }

    /* ---------- 工具 ---------- */
    private static String bytesToHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        StringBuilder sb = new StringBuilder(bytes.length * 3);
        for (byte b : bytes) sb.append(String.format("%02X ", b));
        return sb.toString().trim();
    }

    /* ---------- 关键：串行继续 ---------- */
    private void continueOpenNext(BluetoothGatt gatt, BluetoothGattCharacteristic justDone) {
        BluetoothGattService svc = justDone.getService();
        boolean found = false;          // 是否找到下一条
        boolean startFrom = justDone.getUuid().equals(svc.getCharacteristics()
                .get(svc.getCharacteristics().size() - 1).getUuid()); // 已经是最后一条
        for (BluetoothGattCharacteristic ch : svc.getCharacteristics()) {
            if (found) {   // 继续处理下一条
                handleCharacteristic(gatt, ch);
                return;
            }
            if (ch.equals(justDone)) found = true;
        }
        /* 当前 service 已完，跳到下一个 service */
        for (BluetoothGattService nextSvc : gatt.getServices()) {
            if (nextSvc.equals(svc)) {
                found = true;
                continue;
            }
            if (found) {
                for (BluetoothGattCharacteristic ch : nextSvc.getCharacteristics()) {
                    handleCharacteristic(gatt, ch);
                    return;
                }
            }
        }
        /* 全部处理完毕 */
        log.onLog("=== 所有 characteristic 处理完成 ===");
    }

    /* 对单条 characteristic 执行 打开通知 / 主动读 */
    private void handleCharacteristic(BluetoothGatt gatt, BluetoothGattCharacteristic ch) {
        int props = ch.getProperties();
        log.onLog(String.format(Locale.US, "  Characteristic  %s  props=0x%02X", ch.getUuid(), props));

        if ((props & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ||
            (props & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
            boolean ok = gatt.setCharacteristicNotification(ch, true);
            if (!ok) {
                log.onLog("    setCharacteristicNotification 失败");
                continueOpenNext(gatt, ch);   // 失败也继续
                return;
            }
            BluetoothGattDescriptor desc = ch.getDescriptor(
                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
            if (desc != null) {
                desc.setValue((props & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                        ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                        : BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);

                gatt.writeDescriptor(desc);
                /* 等待 onDescriptorWrite 再继续 */
                return;
            }
        }
        if ((props & BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
            log.onLog("    主动读取 " + ch.getUuid());
            gatt.readCharacteristic(ch);
            /* 等待 onCharacteristicRead 再继续 */
            return;
        }
        /* 既不可通知也不可读，直接跳过 */
        continueOpenNext(gatt, ch);
    }
}
