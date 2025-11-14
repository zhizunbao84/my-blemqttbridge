package com.example.bleanalyzer3;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.*;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.*;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_PERMISSION = 1;
    private static final int MAX_RETRY     = 2;          // 最多连续申请次数
    private int retryCount = 0;

    private TextView tvLog;
    private ScrollView scroll;
    private StringBuilder sb = new StringBuilder();
    private BluetoothLeScanner scanner;
    private ScanCallback scanCallback;

    /* ===================== 日志 ===================== */
    private void log(final String txt) {
        runOnUiThread(() -> {
            sb.append(txt).append("\n");
            tvLog.setText(sb);
            scroll.fullScroll(ScrollView.FOCUS_DOWN);
        });
    }
    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    /* ===================== 权限：只认“是否授予” ===================== */
    private boolean hasScanPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            return checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    /* 真正缺少的权限列表 */
    private List<String> missingPerms() {
        List<String> list = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED)
                list.add(android.Manifest.permission.BLUETOOTH_SCAN);
            if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED)
                list.add(android.Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED)
                list.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
        }
        return list;
    }

    /* 申请权限入口 */
    private void requestPerms() {
        List<String> need = missingPerms();
        if (need.isEmpty()) {
            startScan();
            return;
        }
        /* 连续申请记录 */
        retryCount++;
        ActivityCompat.requestPermissions(this,
                need.toArray(new String[0]), REQ_PERMISSION);
    }

    /* 结果回调：简单暴力，只要没全给就再申，超次进设置 */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMISSION) {
            /* 只要有一个拒绝就算失败 */
            boolean allGranted = true;
            for (int g : grantResults) {
                if (g != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                retryCount = 0;
                startScan();
                return;
            }
            /* 未全授予 */
            if (retryCount < MAX_RETRY) {
                toast("需要权限才能扫描蓝牙");
                requestPerms();          // 再试一次
            } else {
                /* 两次都拒绝，带用户进设置页 */
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
                toast("请手动授予权限");
                finish();
            }
        }
    }
    /* =================================================== */

    /* ===================== 扫描 ===================== */
    private void startScan() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            toast("请先打开蓝牙");
            finish();
            return;
        }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            toast("BluetoothLeScanner 为空");
            finish();
            return;
        }
        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {

                /* 先无脑打印所有广播包长度，确认回调 alive */
                log("收到广播，长度=" + result.getScanRecord().getBytes().length + "  MAC=" + result.getDevice().getAddress());
                parseXiaomiTempHumi(result);
            }
        };
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        log("开始扫描 …");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // 再次保护，防止 ROM 异常
            if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) return;
        }
        scanner.startScan(null, settings, scanCallback);
    }
    /* =================================================== */

    /* ============ 1. 配置你的 Token（32 位小写） ============ */
    private static final String TOKEN_HEX = "5d0836c47ebe0c99bbd7474737bbadfd";
    /* ===================== 解析 ===================== */
    private void parseXiaomiTempHumi(ScanResult result) {
        /* 1. 只打印指定 MAC 的广播包 */
        String mac = result.getDevice().getAddress();
        if (!"A4:C1:38:25:F4:AE".equalsIgnoreCase(mac)) return;
        byte[] raw = result.getScanRecord().getBytes();
        if (raw == null || raw.length < 15) return;

    /* 1. 无脑打印完整广播包 ＋ MAC */
        StringBuilder hex = new StringBuilder("收到广播  MAC=");
        hex.append(result.getDevice().getAddress()).append("  Len=")
           .append(raw.length).append("  Data=");
        for (byte b : raw) {
            hex.append(String.format("%02X ", b & 0xFF));
        }
        log(hex.toString());
        
        int idx = 0;
        while (idx < raw.length) {
            int len = raw[idx++] & 0xFF;
            if (len == 0) break;
            int type = raw[idx] & 0xFF;
            if (type == 0x16 && len >= 13) {
                int uuid = (raw[idx + 1] & 0xFF) | ((raw[idx + 2] & 0xFF) << 8);
                if (uuid == 0xFE95) {
                    int frameType = raw[idx + 3] & 0xFF;
                    if (frameType == 0x5B) {
                        decrypt0x5B(mac, raw, idx + 4, len - 4);
                        return;
                    }
                    if (frameType == 0x20) {   // 明文备份
                        int tempRaw = (raw[idx + 5] & 0xFF) | ((raw[idx + 6] & 0xFF) << 8);
                        int humRaw  = raw[idx + 7] & 0xFF;
                        log("★ 明文解析  温度=" + (tempRaw * 0.1f) +
                            "℃  湿度=" + humRaw + "%");
                        return;
                    }
                }
            }
            idx += len;
        }
    }
    /* =================================================== */
    /* ============ 3. 0x5B 解密实现 ============ */
    private void decrypt0x5B(String mac, byte[] raw, int offset, int dataLen) {
        try {
            /* --- 1. 提取字段 --- */
            int payloadOff = offset;
            byte[] enc  = new byte[8];   // 8 字节 密文(含 4 字节 tag)
            System.arraycopy(raw, payloadOff, enc, 0, 8);
            byte[] nonce = new byte[8];
            System.arraycopy(enc, 0, nonce, 0, 5);        // enc[0:5]
            byte[] macBytes = macToBytes(mac);
            System.arraycopy(macBytes, 3, nonce, 5, 3);   // MAC 后 3 字节
    
            byte[] key = hexToBytes(TOKEN_HEX);
            byte[] cipherText = new byte[5];              // 前 5 字节是密文+tag
            System.arraycopy(enc, 0, cipherText, 0, 5);
    
            /* --- 2. AES-128-CCM 解密 --- */
            Cipher cipher = Cipher.getInstance("AES/CCM/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            cipher.init(Cipher.DECRYPT_MODE, keySpec,
                        new IvParameterSpec(nonce));
            byte[] plain = cipher.doFinal(cipherText);    // 5 字节
    
            /* --- 3. 提取数值 --- */
            int humidity = plain[0] & 0xFF;
            int tempRaw  = (plain[1] & 0xFF) | ((plain[2] & 0xFF) << 8);
            float temp   = tempRaw * 0.1f;
            int battery  = (plain[3] & 0xFF) | ((plain[4] & 0xFF) << 8);
    
            log("★ 解密成功  温度=" + temp + "℃  湿度=" + humidity +
                "%  电池=" + battery + " mV");
    
        } catch (Exception e) {
            log("解密失败: " + e.getMessage());
        }
    }
    
    /* ============ 4. 工具 ============ */
    private byte[] macToBytes(String mac) {
        String[] hex = mac.split(":");
        byte[] b = new byte[6];
        for (int i = 0; i < 6; i++) b[i] = (byte) Integer.parseInt(hex[i], 16);
        return b;
    }
    
    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] out = new byte[len >> 1];
        for (int i = 0; i < len; i += 2) {
            out[i >> 1] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return out;
    }

    
    /* ===================== 界面 ===================== */
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

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            toast("设备不支持 BLE");
            finish();
            return;
        }

        /* 6.0-11 额外检查定位开关 */
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (lm != null &&
                    !lm.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
                    !lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                toast("请打开定位开关");
                finish();
                return;
            }
        }

        requestPerms();
    }
    /* =================================================== */

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (scanner != null && scanCallback != null)
            scanner.stopScan(scanCallback);
    }
}
