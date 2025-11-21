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

public class ConfigManager {
    private static final String CONFIG_FILE = "config.ini";
    private static final String EXTERNAL_CONFIG_DIR = "BLEMQTTBridge";
    private static ConfigManager instance;
    private Map<String, String> config = new HashMap<>();
    private Context context;
    
    private ConfigManager(Context context) {
        this.context = context;
        loadConfig();
    }
    
    public static synchronized ConfigManager getInstance(Context context) {
        if (instance == null) {
            instance = new ConfigManager(context.getApplicationContext());
        }
        return instance;
    }
    
    public void loadConfig() {
        try {
            // 首先尝试从外部存储加载配置文件
            File externalDir = new File(context.getExternalFilesDir(null), EXTERNAL_CONFIG_DIR);
            File externalConfig = new File(externalDir, CONFIG_FILE);
            
            if (externalConfig.exists()) {
                Logger.i("Loading config from external storage: " + externalConfig.getAbsolutePath());
                loadFromFile(externalConfig);
            } else {
                // 如果不存在，则从assets复制到外部存储
                Logger.i("External config not found, copying from assets");
                copyConfigFromAssets();
                if (externalConfig.exists()) {
                    loadFromFile(externalConfig);
                }
            }
            
            Logger.i("Config loaded successfully");
            Logger.d("Device MACs: " + getDeviceMacs());
            Logger.d("Scan interval: " + getScanInterval());
            Logger.d("MQTT Broker: " + getMQTTBroker());
        } catch (Exception e) {
            Logger.e("Error loading config", e);
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
    
    private void loadFromFile(File file) throws IOException {
        Properties props = new Properties();
        try (FileReader reader = new FileReader(file)) {
            props.load(reader);
        }
        
        config.clear();
        for (String key : props.stringPropertyNames()) {
            config.put(key, props.getProperty(key));
        }
        
        // 设置日志级别
        String logLevel = config.getOrDefault("general.log_level", "DEBUG");
        Logger.setLogLevel(logLevel);
    }
    
    public String[] getDeviceMacs() {
        String macs = config.getOrDefault("bluetooth.device_macs", "");
        return macs.split(",");
    }
    
    public int getScanInterval() {
        return Integer.parseInt(config.getOrDefault("bluetooth.scan_interval", "5000"));
    }
    
    public String getMQTTBroker() {
        return config.getOrDefault("mqtt.broker", "tcp://broker.hivemq.com:1883");
    }
    
    public String getMQTTUsername() {
        return config.getOrDefault("mqtt.username", "");
    }
    
    public String getMQTTPassword() {
        return config.getOrDefault("mqtt.password", "");
    }
    
    public String getMQTTClientId() {
        return config.getOrDefault("mqtt.client_id", "BLEBridgeClient");
    }
    
    public String getMQTTTopicPrefix() {
        return config.getOrDefault("mqtt.topic_prefix", "ble/data");
    }
    
    public String getConfigFilePath() {
        File externalDir = new File(context.getExternalFilesDir(null), EXTERNAL_CONFIG_DIR);
        File externalConfig = new File(externalDir, CONFIG_FILE);
        return externalConfig.getAbsolutePath();
    }
}
