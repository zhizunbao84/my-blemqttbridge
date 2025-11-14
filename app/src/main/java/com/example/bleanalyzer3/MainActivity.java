package com.example.bleanalyzer3;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.text.method.ScrollingMovementMethod;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.*;

public class MainActivity extends AppCompatActivity {

    /* ===================== 权限常量 ===================== */
    private static final int REQ_PERMISSION = 1;
    /* =================================================== */

    private TextView tvLog;
    private ScrollView scroll;
    private StringBuilder sb = new StringBuilder();
    private BluetoothLeScanner scanner;
    private ScanCallback scanCallback;

    /* 日志打印到界面 */
    private void log(final String txt) {
        runOnUiThread(() -> {
            sb.append(txt).append("\n");
            tvLog.setText(sb);
            scroll.fullScroll(ScrollView.FOCUS_DOWN);
        });
    }

    /* Toast 快捷方法 */
    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    /* ===================== 权限相关 ===================== */
    private boolean hasScanPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            return checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestNeededPermissions() {
        List<String> list = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasScanPermission())
                list.add(android.Manifest.permission.BLUETOOTH_SCAN);
            if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED)
                list.add(android.Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            if (!hasScanPermission())
                list.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (!list.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    list.toArray(new String[0]), REQ_PERMISSION);
        } else {
            startScan(); // 权限已 OK
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMISSION) {
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) {
                    toast("权限被拒绝，无法扫描");
                    finish();
                    return;
                }
            }
            startScan();
        }
    }
    /* =================================================== */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        setContentView(root);

        tvLog = new TextView(this);
        tvLog.setMovementMethod(new ScrollingMovementMethod());
        scroll = new ScrollView(this);
        scroll.addView(tvLog);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        /* 检查 BLE 支持 */
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            toast("设备不支持 BLE");
            finish();
            return;
        }

        /* 蓝牙开关检查 */
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            toast("请先打开蓝牙");
            finish();
            return;
        }

        /* 申请权限 → 成功后开始扫描 */
        requestNeededPermissions();
    }

    /* ===================== 扫描 ===================== */
    private void startScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN)
                        != PackageManager.PERMISSION_GRANTED) {
            return; // 理论上不会走到这里
        }

        scanner = BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner();
        if (scanner == null) {
            toast("获取 BluetoothLeScanner 失败");
            finish();
            return;
        }

        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                parseXiaomiTempHumi(result);
            }
        };

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        log("开始扫描 …");
        scanner.startScan(null, settings, scanCallback);
    }

    /* ===================== 解析米家温湿度计 2 广播 ===================== */
    private void parseXiaomiTempHumi(ScanResult result) {
        byte[] raw = result.getScanRecord().getBytes();
        if (raw == null || raw.length < 15) return;

        int index = 0;
        while (index < raw.length) {
            int len = raw[index++] & 0xFF;
            if (len == 0) break;
            int type = raw[index] & 0xFF;

            /* Service Data - 16-bit UUID = 0xFE95 (小米) */
            if (type == 0x16 && len >= 13) {
                int uuid = (raw[index + 1] & 0xFF) | ((raw[index + 2] & 0xFF) << 8);
                if (uuid == 0xFE95 && (raw[index + 3] & 0xFF) == 0x70) {
                    /* 温度类型 0x20 */
                    if ((raw[index + 4] & 0xFF) == 0x20) {
                        int tempRaw = (raw[index + 5] & 0xFF)
                                | ((raw[index + 6] & 0xFF) << 8);
                        int humRaw = raw[index + 7] & 0xFF;
                        float temp = tempRaw * 0.1f;
                        int hum = humRaw;

                        String mac = result.getDevice().getAddress();
                        log(String.format(Locale.CHINA,
                                "%s  →  %.1f ℃   %d %%", mac, temp, hum));
                        return;
                    }
                }
            }
            index += len;
        }
    }

    /* ===================== 生命周期 ===================== */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (scanner != null && scanCallback != null)
            scanner.stopScan(scanCallback);
    }
}
