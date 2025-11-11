package com.example.bleanalyzer;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_PERMISSION = 1;
    private EditText etMac;
    private Button btnConnect;
    private TextView tvLog;
    private BleWrapper ble;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etMac = findViewById(R.id.etMac);
        btnConnect = findViewById(R.id.btnConnect);
        tvLog = findViewById(R.id.tvLog);
        tvLog.setMovementMethod(new ScrollingMovementMethod());

        // 6.0+ 定位权限检查
        if (ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    REQ_PERMISSION);
        }

        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            toast("请先打开蓝牙");
            finish();
            return;
        }

        ble = new BleWrapper(adapter, this::appendLog);

        btnConnect.setOnClickListener(v -> {
            String mac = etMac.getText().toString().trim();
            if (!mac.matches("([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")) {
                toast("MAC 格式错误");
                return;
            }
            ble.connect(mac);
        });
    }

    private void appendLog(String s) {
        runOnUiThread(() -> {
            tvLog.append(s + "\n");
        });
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
