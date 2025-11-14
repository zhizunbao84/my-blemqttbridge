package com.example.bleanalyzer3;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.*;
import android.content.Context;
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

    private static final int REQ_PERMISSION = 1;
    private TextView tvLog;
    private ScrollView scroll;
    private StringBuilder sb = new StringBuilder();

    /* 统一日志输出 */
    private void log(final String txt) {
        runOnUiThread(() -> {
            sb.append(txt).append("\n");
            tvLog.setText(sb);
            scroll.fullScroll(ScrollView.FOCUS_DOWN);
        });
    }

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
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1));

        /* 检查 BLE 支持 */
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            toast("设备不支持 BLE"); finish(); return;
        }

        /* 6.0 动态权限 */
        if (ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQ_PERMISSION);
        } else {
            startScan();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] p,
                                           @NonNull int[] grant) {
        if (requestCode == REQ_PERMISSION) {
            if (grant.length > 0 && grant[0] == PackageManager.PERMISSION_GRANTED) {
                startScan();
            } else {
                toast("权限拒绝，无法扫描"); finish();
            }
        }
    }

    private BluetoothLeScanner scanner;
    private ScanCallback callback;

    private void startScan() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            toast("请先打开蓝牙"); finish(); return;
        }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            toast("获取 scanner 失败"); finish(); return;
        }

        /* 仅被动监听，不请求连接 */
        callback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                parseAdv(result);
            }
        };

        /* 低功耗扫描参数 */
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        log("开始扫描 …");
        scanner.startScan(null, settings, callback);
    }

    /* 解析广播包 */
    private void parseAdv(ScanResult result) {
        byte[] raw = result.getScanRecord().getBytes();
        if (raw == null || raw.length < 15) return;

        /* 遍历 AD Structure */
        int index = 0;
        while (index < raw.length) {
            int len = raw[index++] & 0xFF;
            if (len == 0) break;
            int type = raw[index] & 0xFF;

            /* Service Data – 16-bit UUID = 0xFE95 (小米) */
            if (type == 0x16 && len >= 13) {
                int uuid = (raw[index + 1] & 0xFF) | ((raw[index + 2] & 0xFF) << 8);
                if (uuid == 0xFE95 && (raw[index + 3] & 0xFF) == 0x70) {
                    /* 温度 0x20 类型 */
                    if ((raw[index + 4] & 0xFF) == 0x20) {
                        int tempRaw = (raw[index + 5] & 0xFF) |
                                ((raw[index + 6] & 0xFF) << 8);
                        int humRaw  = raw[index + 7] & 0xFF;
                        float temp = tempRaw * 0.1f;
                        int hum = humRaw;

                        String mac = result.getDevice().getAddress();
                        log(String.format(Locale.CHINA,
                                "%s  ->  %.1f ℃   %d %%",
                                mac, temp, hum));
                        return;
                    }
                }
            }
            index += len;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (scanner != null && callback != null)
            scanner.stopScan(callback);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
