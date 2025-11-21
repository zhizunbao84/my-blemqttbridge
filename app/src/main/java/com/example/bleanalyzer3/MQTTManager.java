package com.example.bleanalyzer3;

import android.content.Context;
import org.eclipse.paho.client.mqttv3.*;

import java.util.UUID;

public class MQTTManager {
    private static MQTTManager instance;
    private MqttClient mqttClient;
    private ConfigManager configManager;
    private boolean isConnected = false;
    
    private MQTTManager(Context context) {
        this.configManager = ConfigManager.getInstance(context);
    }
    
    public static synchronized MQTTManager getInstance(Context context) {
        if (instance == null) {
            instance = new MQTTManager(context.getApplicationContext());
        }
        return instance;
    }
    
    public void connect() {
        try {
            ConfigManager config = ConfigManager.getInstance(null);
            String broker = config.getMQTTBroker();
            String clientId = config.getMQTTClientId() + "_" + UUID.randomUUID().toString().substring(0, 8);
            
            Logger.i("Connecting to MQTT broker: " + broker + " with client ID: " + clientId);
            
            mqttClient = new MqttClient(broker, clientId, new MemoryPersistence());
            
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(10);
            options.setKeepAliveInterval(20);
            
            String username = config.getMQTTUsername();
            String password = config.getMQTTPassword();
            
            if (username != null && !username.isEmpty()) {
                options.setUserName(username);
                if (password != null && !password.isEmpty()) {
                    options.setPassword(password.toCharArray());
                }
            }
            
            mqttClient.connect(options);
            isConnected = true;
            Logger.i("MQTT connected successfully");
            
        } catch (Exception e) {
            Logger.e("MQTT connection failed", e);
            isConnected = false;
        }
    }
    
    public void disconnect() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
                isConnected = false;
                Logger.i("MQTT disconnected");
            }
        } catch (Exception e) {
            Logger.e("MQTT disconnection error", e);
        }
    }
    
    public void publish(String topic, String message) {
        if (!isConnected || mqttClient == null || !mqttClient.isConnected()) {
            Logger.w("MQTT not connected, attempting to reconnect...");
            connect();
            return;
        }
        
        try {
            MqttMessage mqttMessage = new MqttMessage(message.getBytes());
            mqttMessage.setQos(1);
            mqttMessage.setRetained(false);
            
            mqttClient.publish(topic, mqttMessage);
            Logger.d("Published to " + topic + ": " + message);
            
        } catch (Exception e) {
            Logger.e("MQTT publish failed", e);
            isConnected = false;
        }
    }
    
    public boolean isConnected() {
        return isConnected && mqttClient != null && mqttClient.isConnected();
    }
}
