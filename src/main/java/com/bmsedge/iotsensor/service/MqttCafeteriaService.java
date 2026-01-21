package com.bmsedge.iotsensor.service;

import com.bmsedge.iotsensor.model.CafeteriaAnalytics;
import com.bmsedge.iotsensor.model.CafeteriaLocation;
import com.bmsedge.iotsensor.model.FoodCounter;
import com.bmsedge.iotsensor.repository.CafeteriaAnalyticsRepository;
import com.bmsedge.iotsensor.repository.CafeteriaLocationRepository;
import com.bmsedge.iotsensor.repository.FoodCounterRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class MqttCafeteriaService {

    // ✅ CHANGED: Use constructor injection instead of @RequiredArgsConstructor
    private final CafeteriaAnalyticsRepository analyticsRepository;
    private final FoodCounterRepository counterRepository;
    private final CafeteriaLocationRepository locationRepository;
    private final ObjectMapper objectMapper;
    private final MqttMessageProcessor messageProcessor; // ✅ NEW: Separate processor

    public MqttCafeteriaService(
            CafeteriaAnalyticsRepository analyticsRepository,
            FoodCounterRepository counterRepository,
            CafeteriaLocationRepository locationRepository,
            ObjectMapper objectMapper,
            MqttMessageProcessor messageProcessor) {
        this.analyticsRepository = analyticsRepository;
        this.counterRepository = counterRepository;
        this.locationRepository = locationRepository;
        this.objectMapper = objectMapper;
        this.messageProcessor = messageProcessor;
    }

    @Value("${mqtt.broker.url:ssl://6fdddaf19d614da29c86428142cbe7a2.s1.eu.hivemq.cloud:8883}")
    private String brokerUrl;

    @Value("${mqtt.username:ruthvik}")
    private String username;

    @Value("${mqtt.password:Iotiq369.}")
    private String password;

    @Value("${mqtt.topic:intel-topic}")
    private String baseTopic;

    @Value("${mqtt.client.id:cafeteria-analytics-client}")
    private String clientId;

    @Value("${mqtt.default.cafeteria.code:srr-4a}")
    private String defaultCafeteriaCode;

    private MqttClient mqttClient;
    private final Map<String, Long> topicToCounterIdMap = new HashMap<>();

    @PostConstruct
    public void init() {
        try {
            initializeTopicMappings();
            connectToMqtt();
            subscribeToTopics();
            log.info("✅ MQTT service initialized successfully");
        } catch (Exception e) {
            log.error("❌ Failed to initialize MQTT service", e);
        }
    }

    private void initializeTopicMappings() {
        topicToCounterIdMap.put("intel-topic", 4L);
        topicToCounterIdMap.put("intel-minimeals-topic", 5L);
        topicToCounterIdMap.put("intel-twogood-topic", 6L);

        log.info("✅ Initialized {} topic → counter ID mappings:", topicToCounterIdMap.size());

        topicToCounterIdMap.forEach((topic, counterId) -> {
            try {
                FoodCounter counter = counterRepository.findById(counterId).orElse(null);
                if (counter != null) {
                    log.info("   ✅ '{}' → Counter ID {} ({} - {})",
                            topic, counterId, counter.getCounterName(), counter.getCounterCode());
                } else {
                    log.error("   ❌ '{}' → Counter ID {} NOT FOUND IN DATABASE!", topic, counterId);
                }
            } catch (Exception e) {
                log.error("   ❌ Error verifying counter ID {}: {}", counterId, e.getMessage());
            }
        });
    }

    private void connectToMqtt() throws MqttException {
        mqttClient = new MqttClient(brokerUrl, clientId);

        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(username);
        options.setPassword(password.toCharArray());
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(60);

        mqttClient.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                log.error("❌ MQTT connection lost", cause);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                // ✅ CRITICAL: Call the @Transactional method in the separate Spring bean
                String payload = new String(message.getPayload());
                Long counterId = topicToCounterIdMap.get(topic);

                log.info("📨 Topic: '{}' → Counter ID: {} | Payload length: {}",
                        topic, counterId, payload.length());

                messageProcessor.processMessage(topic, payload, counterId, defaultCafeteriaCode);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // Not used for subscriber
            }
        });

        mqttClient.connect(options);
        log.info("✅ Connected to MQTT broker: {}", brokerUrl);
    }

    private void subscribeToTopics() throws MqttException {
        for (String topic : topicToCounterIdMap.keySet()) {
            mqttClient.subscribe(topic, 1);
            log.info("✅ Subscribed to topic: {}", topic);
        }
    }

    public void publishTestMessage(String deviceId) {
        try {
            String topic = null;

            if (deviceId == null || deviceId.trim().isEmpty()) {
                topic = baseTopic;
            } else {
                String deviceIdLower = deviceId.toLowerCase().trim();

                if (deviceIdLower.contains("healthy") || deviceIdLower.contains("hs-") || deviceIdLower.equals("4")) {
                    topic = "intel-topic";
                } else if (deviceIdLower.contains("mini") || deviceIdLower.contains("meal") || deviceIdLower.contains("mm-") || deviceIdLower.equals("5")) {
                    topic = "intel-minimeals-topic";
                } else if (deviceIdLower.contains("two") || deviceIdLower.contains("good") || deviceIdLower.contains("tg-") || deviceIdLower.equals("6")) {
                    topic = "intel-twogood-topic";
                } else {
                    topic = baseTopic;
                }
            }

            String testPayload = String.format("""
                {
                    "occupancy": %d,
                    "avg_dwell": %.2f,
                    "max_dwell": %.2f,
                    "incount": %d,
                    "estimate_wait_time": %.2f,
                    "waiting_time_min": %.2f,
                    "timestamp": "%s"
                }
                """,
                    (int) (Math.random() * 20) + 5,
                    (Math.random() * 20 + 10) * 60,
                    (Math.random() * 30 + 20) * 60,
                    (int) (Math.random() * 50) + 10,
                    (Math.random() * 15 + 5) * 60,
                    (Math.random() * 10 + 5) * 60,
                    LocalDateTime.now().toString()
            );

            MqttMessage message = new MqttMessage(testPayload.getBytes());
            message.setQos(1);
            mqttClient.publish(topic, message);

            log.info("📤 Published test to topic '{}' for deviceId '{}'", topic, deviceId);

        } catch (Exception e) {
            log.error("❌ Error publishing test message", e);
            throw new RuntimeException("Failed to publish test message: " + e.getMessage(), e);
        }
    }

    public void publishTestMessageCafeteriaLevel(String cafeteriaCode) {
        try {
            String testPayload = String.format("""
                {
                    "occupancy": %d,
                    "avg_dwell": %.2f,
                    "max_dwell": %.2f,
                    "incount": %d,
                    "estimate_wait_time": %.2f,
                    "waiting_time_min": %.2f,
                    "cafeteriaCode": "%s",
                    "timestamp": "%s"
                }
                """,
                    (int) (Math.random() * 200) + 50,
                    (Math.random() * 20 + 10) * 60,
                    (Math.random() * 30 + 20) * 60,
                    (int) (Math.random() * 50) + 10,
                    (Math.random() * 15 + 5) * 60,
                    (Math.random() * 10 + 5) * 60,
                    cafeteriaCode != null ? cafeteriaCode : defaultCafeteriaCode,
                    LocalDateTime.now().toString()
            );

            MqttMessage message = new MqttMessage(testPayload.getBytes());
            message.setQos(1);
            mqttClient.publish(baseTopic, message);

            log.info("📤 Published cafeteria-level test message");
        } catch (Exception e) {
            log.error("❌ Error publishing test message", e);
        }
    }

    public boolean isConnected() {
        return mqttClient != null && mqttClient.isConnected();
    }

    public Map<String, Long> getTopicMappings() {
        return new HashMap<>(topicToCounterIdMap);
    }

    @PreDestroy
    public void cleanup() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
                mqttClient.close();
                log.info("✅ MQTT client disconnected successfully");
            }
        } catch (Exception e) {
            log.error("❌ Error during MQTT cleanup", e);
        }
    }
}