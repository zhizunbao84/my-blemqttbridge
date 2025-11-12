package com.example.bleanalyzer;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_BT = 1;          // 权限请求码
    private EditText etMac;
    private TextView tvLog;
    private BleWrapper ble;
    private String pendingMac;                    // 缓存输入的 MAC

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etMac = findViewById(R.id.etMac);
        Button btnConnect = findViewById(R.id.btnConnect);
        tvLog = findViewById(R.id.tvLog);
        tvLog.setMovementMethod(new ScrollingMovementMethod());

        /* 检查蓝牙支持 */
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            toast("请先打开蓝牙");
            finish();
            return;
        }

        ble = new BleWrapper(adapter, this, this::appendLog);

        btnConnect.setOnClickListener(v -> {
            String mac = etMac.getText().toString().trim();
            if (!mac.matches("([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")) {
                toast("MAC 格式错误");
                return;
            }
            pendingMac = mac;
            checkBTPermissionThenConnect(mac);
        });
    }

    /* 统一权限入口：Android 12+ 用 BLUETOOTH_CONNECT，旧版本用定位权限 */
    private void checkBTPermissionThenConnect(String mac) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.BLUETOOTH_CONNECT}, REQ_BT);
                return;
            }
        } else {
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, REQ_BT);
                return;
            }
        }
        doConnect(mac);          // 已有权限直接连接
    }

    /* 真正连接 */
    private void doConnect(String mac) {
        ble.connect(mac);
    }

    /* 权限回调 */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BT && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            doConnect(pendingMac);
        } else {
            toast("需要权限才能连接蓝牙");
        }
    }

    /* 日志输出到 UI */
    private void appendLog(String s) {
        runOnUiThread(() -> tvLog.append(s + "\n"));
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ble.close();   // 释放 GATT
    }
}
