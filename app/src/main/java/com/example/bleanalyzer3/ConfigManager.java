package com.example.bleanalyzer3;

import android.content.Context;
import android.content.res.AssetManager;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.ini4j.Ini;

public class ConfigManager {
    private static final String CONFIG_FILE = "config.ini";
    private static final String EXTERNAL_CONFIG_DIR = "BLEMQTTBridge";
    private static ConfigManager instance;
    private Map<String, String> config = new HashMap<>();
    private Context context;
    private final File externalIni;
    
    private ConfigManager(Context context) {
        this.context = context;
        File externalDir = new File(context.getExternalFilesDir(null), EXTERNAL_CONFIG_DIR);
        externalIni = new File(externalDir, CONFIG_FILE);
        copyFromAssetsOnce(ctx);
        loadConfig();
    }
    
    /* 获取单例（必须在主线程调用一次） */
    public static ConfigManager getInstance(Context ctx) {
        if (INSTANCE == null) {
            synchronized (ConfigManager.class) {
                if (INSTANCE == null) INSTANCE = new ConfigManager(ctx);
            }
        }
        return INSTANCE;
    }
    
    public void loadConfig() {
        try {
            loadFromFile();
            Logger.i("Config reloaded from " + externalConfig.getAbsolutePath());
            
            Logger.i("Config loaded successfully");
            Logger.d("Device MACs: " + getDeviceMacs());
            Logger.d("Scan interval: " + getScanInterval());
            Logger.d("MQTT Broker: " + getMQTTBroker());
            /* 3. 设置日志级别 */
            String level = config.containsKey("general.log_level") ? config.get("general.log_level") : "DEBUG";
            Logger.setLogLevel(level);
        } catch (Exception e) {
            Logger.e("Error loading config", e);
        }
    }

    /* 首次安装：把 assets/config.ini 拷到外部私有目录 */
    private void copyFromAssetsOnce(Context ctx) {
        if (!externalIni.exists()) {
            try (InputStream in = ctx.getAssets().open("config.ini");
                 OutputStream out = new FileOutputStream(externalIni)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                Logger.i("Config file copied to: " + externalIni.getAbsolutePath());
            } catch (IOException e) {
                throw new RuntimeException("首次拷贝 ini 失败", e);
            }
        }
    }
    
    private void copyConfigFromAssets() throws IOException {
        File externalDir = new File(context.getExternalFilesDir(null), EXTERNAL_CONFIG_DIR);
        if (!externalDir.exists()) {
            externalDir.mkdirs();
        }
        
        File externalConfig = new File(externalDir, CONFIG_FILE);
        AssetManager assetManager = context.getAssets();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(assetManager.open(CONFIG_FILE)));
             FileWriter writer = new FileWriter(externalConfig)) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line + "\n");
            }
        }
        
        Logger.i("Config file copied to: " + externalConfig.getAbsolutePath());
    }
    
    private void loadFromFile() throws IOException {
        Properties props = new Properties();
        try (FileReader reader = new FileReader(externalIni)) {
            props.load(reader);
        }
        
        config.clear();
        for (String key : props.stringPropertyNames()) {
            config.put(key, props.getProperty(key));
        }
        
        // 设置日志级别
        String logLevel = config.containsKey("general.log_level") ? config.get("general.log_level") : "DEBUG";
        Logger.setLogLevel(logLevel);
    }
    
    public String[] getDeviceMacs() {
        String macs = config.containsKey("bluetooth.device_macs") ? config.get("bluetooth.device_macs") : "";
        return macs.split(",");
    }
    
    public int getScanInterval() {
        String scaninterval = config.containsKey("bluetooth.scan_interval") ? config.get("bluetooth.scan_interval") : "5000";
        return Integer.parseInt(scaninterval);
    }
    
    public String getMQTTBroker() {
        String mqttbroker = config.containsKey("mqtt.broker") ? config.get("mqtt.broker") : "tcp://127.0.0.2:1883";
        return mqttbroker;
    }
    
    public String getMQTTUsername() {
        String mqttusername = config.containsKey("mqtt.username") ? config.get("mqtt.username") : "";
        return mqttusername;
    }
    
    public String getMQTTPassword() {
        String mqttpass = config.containsKey("mqtt.password") ? config.get("mqtt.password") : "";
        return mqttpass;
    }
    
    public String getMQTTClientId() {
        String mqttclientid = config.containsKey("mqtt.client_id") ? config.get("mqtt.client_id") : "BLEBridgeClient";
        return mqttclientid;
    }
    
    public String getMQTTTopicPrefix() {
        String mqttopicprefix = config.containsKey("mqtt.topic_prefix") ? config.get("mqtt.topic_prefix") : "mi_temp";
        return mqttopicprefix;
    }
    
    public String getConfigFilePath() {
        File externalDir = new File(context.getExternalFilesDir(null), EXTERNAL_CONFIG_DIR);
        File externalConfig = new File(externalDir, CONFIG_FILE);
        return externalConfig.getAbsolutePath();
    }
}
