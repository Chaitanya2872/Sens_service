package com.bmsedge.iotsensor.service;

import com.bmsedge.iotsensor.model.CafeteriaAnalytics;
import com.bmsedge.iotsensor.model.CafeteriaLocation;
import com.bmsedge.iotsensor.model.FoodCounter;
import com.bmsedge.iotsensor.repository.CafeteriaAnalyticsRepository;
import com.bmsedge.iotsensor.repository.CafeteriaLocationRepository;
import com.bmsedge.iotsensor.repository.FoodCounterRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class MqttCafeteriaService {

    private final CafeteriaAnalyticsRepository analyticsRepository;
    private final FoodCounterRepository counterRepository;
    private final CafeteriaLocationRepository locationRepository;
    private final ObjectMapper objectMapper;

    @Value("${mqtt.broker.url:ssl://6fdddaf19d614da29c86428142cbe7a2.s1.eu.hivemq.cloud:8883}")
    private String brokerUrl;

    @Value("${mqtt.username:ruthvik}")
    private String username;

    @Value("${mqtt.password:Iotiq369.}")
    private String password;

    @Value("${mqtt.topic:intel-topic}")
    private String topic;

    @Value("${mqtt.client.id:cafeteria-analytics-client}")
    private String clientId;

    @Value("${mqtt.default.cafeteria.code:srr-4a}")
    private String defaultCafeteriaCode;

    private MqttClient mqttClient;

    /**
     * Initialize MQTT connection on application startup
     */
    @PostConstruct
    public void init() {
        try {
            connectToMqtt();
            subscribeToTopic();
            log.info("MQTT service initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize MQTT service", e);
        }
    }

    /**
     * Connect to HiveMQ broker
     */
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
                log.error("MQTT connection lost", cause);
                // Automatic reconnect is enabled
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                handleIncomingMessage(topic, message);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // Not used for subscriber
            }
        });

        mqttClient.connect(options);
        log.info("Connected to MQTT broker: {}", brokerUrl);
    }

    /**
     * Subscribe to cafeteria analytics topic
     */
    private void subscribeToTopic() throws MqttException {
        mqttClient.subscribe(topic, 1);
        log.info("Subscribed to topic: {}", topic);
    }

    /**
     * Handle incoming MQTT messages
     *
     * Expected payload format (ALL FIELDS OPTIONAL):
     * {
     *   "occupancy": 125,              // Current occupancy count
     *   "avg_dwell": 1110.5,           // Average dwell time in SECONDS (will be converted to minutes)
     *   "max_dwell": 2112.0,           // Maximum dwell time in SECONDS (will be converted to minutes)
     *   "incount": 45,                 // Number of people entering
     *   "estimate_wait_time": 738,     // Estimated wait time in SECONDS (will be converted to minutes)
     *   "waiting_time_min": 600,       // Manual wait time in SECONDS (will be converted to minutes)
     *   "deviceId": "counter-001",     // OPTIONAL - Counter device ID
     *   "cafeteriaCode": "srr-4a",     // OPTIONAL - Cafeteria code
     *   "timestamp": "2024-01-17T14:30:00"  // OPTIONAL - ISO datetime
     * }
     *
     * NOTE: All time values are received in SECONDS and converted to MINUTES for storage
     *
     * Fallback logic:
     * 1. If deviceId provided → Save to specific counter
     * 2. If cafeteriaCode provided → Save to cafeteria level
     * 3. If neither → Use default cafeteria from application.properties
     */
    private void handleIncomingMessage(String topic, MqttMessage message) {
        try {
            String payload = new String(message.getPayload());
            log.debug("📨 Received MQTT message from topic {}: {}", topic, payload);

            // Parse JSON payload
            JsonNode jsonNode = objectMapper.readTree(payload);

            // Extract data from payload (all optional)
            Integer occupancy = jsonNode.has("occupancy") ? jsonNode.get("occupancy").asInt() : null;

            // ============================================
            // FIXED: Convert dwell times from SECONDS to MINUTES
            // ============================================
            Double avgDwellSeconds = jsonNode.has("avg_dwell") ? jsonNode.get("avg_dwell").asDouble() : null;
            Double maxDwellSeconds = jsonNode.has("max_dwell") ? jsonNode.get("max_dwell").asDouble() : null;
            Double estimateWaitTimeSeconds = jsonNode.has("estimate_wait_time") ? jsonNode.get("estimate_wait_time").asDouble() : null;
            Double manualWaitTimeSeconds = jsonNode.has("waiting_time_min") ? jsonNode.get("waiting_time_min").asDouble() : null;

            // Convert to minutes (divide by 60)
            Double avgDwell = avgDwellSeconds != null ? avgDwellSeconds / 60.0 : null;
            Double maxDwell = maxDwellSeconds != null ? maxDwellSeconds / 60.0 : null;
            Double estimateWaitTime = estimateWaitTimeSeconds != null ? estimateWaitTimeSeconds / 60.0 : null;
            Double manualWaitTime = manualWaitTimeSeconds != null ? manualWaitTimeSeconds / 60.0 : null;

            // Log conversion for debugging
            if (avgDwellSeconds != null) {
                log.debug("🔄 Converted avg_dwell: {} seconds → {} minutes",
                        String.format("%.1f", avgDwellSeconds),
                        String.format("%.2f", avgDwell));
            }
            if (estimateWaitTimeSeconds != null) {
                log.debug("🔄 Converted estimate_wait_time: {} seconds → {} minutes",
                        String.format("%.1f", estimateWaitTimeSeconds),
                        String.format("%.2f", estimateWaitTime));
            }
            // ============================================

            Integer inCount = jsonNode.has("incount") ? jsonNode.get("incount").asInt() : null;
            String deviceId = jsonNode.has("deviceId") ? jsonNode.get("deviceId").asText() : null;
            String cafeteriaCode = jsonNode.has("cafeteriaCode") ? jsonNode.get("cafeteriaCode").asText() : null;

            // Parse timestamp or use current time
            LocalDateTime timestamp = LocalDateTime.now();
            if (jsonNode.has("timestamp")) {
                try {
                    timestamp = LocalDateTime.parse(jsonNode.get("timestamp").asText());
                } catch (Exception e) {
                    log.warn("⚠️ Failed to parse timestamp, using current time");
                }
            }

            FoodCounter counter = null;
            CafeteriaLocation location = null;

            // STEP 1: Try to find counter by device ID (if provided)
            if (deviceId != null && !deviceId.trim().isEmpty()) {
                try {
                    counter = counterRepository.findByDeviceId(deviceId).orElse(null);
                    if (counter != null) {
                        location = counter.getCafeteriaLocation();
                        log.debug("✅ Found counter: {} for device: {}", counter.getCounterName(), deviceId);
                    } else {
                        log.warn("⚠️ Counter not found for deviceId: {}. Trying cafeteria-level save...", deviceId);
                    }
                } catch (Exception e) {
                    log.warn("⚠️ Error finding counter by deviceId: {}", e.getMessage());
                }
            }

            // STEP 2: If no counter found, try cafeteria code (if provided)
            if (location == null && cafeteriaCode != null && !cafeteriaCode.trim().isEmpty()) {
                try {
                    location = locationRepository.findByCafeteriaCode(cafeteriaCode).orElse(null);
                    if (location != null) {
                        log.debug("✅ Found cafeteria: {} by code: {}", location.getCafeteriaName(), cafeteriaCode);
                    } else {
                        log.warn("⚠️ Cafeteria not found for code: {}", cafeteriaCode);
                    }
                } catch (Exception e) {
                    log.warn("⚠️ Error finding cafeteria by code: {}", e.getMessage());
                }
            }

            // STEP 3: If still no location, use default cafeteria code
            if (location == null) {
                try {
                    location = locationRepository.findByCafeteriaCode(defaultCafeteriaCode).orElse(null);
                    if (location != null) {
                        log.info("ℹ️ Using default cafeteria: {} (code: {})",
                                location.getCafeteriaName(), defaultCafeteriaCode);
                    } else {
                        log.error("❌ Default cafeteria '{}' not found in database!", defaultCafeteriaCode);
                    }
                } catch (Exception e) {
                    log.error("❌ Error finding default cafeteria: {}", e.getMessage());
                }
            }

            // STEP 4: Last resort - try to find ANY active location
            if (location == null) {
                try {
                    location = locationRepository.findByActive(true)
                            .stream()
                            .findFirst()
                            .orElse(null);

                    if (location != null) {
                        log.warn("⚠️ Using first active cafeteria as fallback: {}", location.getCafeteriaName());
                    } else {
                        log.error("❌ No active cafeteria location found in database. Cannot save data!");
                        log.error("❌ Please ensure at least one cafeteria location exists in the database.");
                        return; // Skip this message
                    }
                } catch (Exception e) {
                    log.error("❌ Error finding fallback cafeteria: {}", e.getMessage());
                    return; // Skip this message
                }
            }

            // Calculate queue length from wait time (assuming 2 min per person)
            // Now using MINUTES (already converted from seconds)
            Integer queueLength = null;
            if (estimateWaitTime != null && estimateWaitTime > 0) {
                queueLength = (int) Math.ceil(estimateWaitTime / 2.0);
            } else if (manualWaitTime != null && manualWaitTime > 0) {
                queueLength = (int) Math.ceil(manualWaitTime / 2.0);
            }

            // Build analytics record - all time values now in MINUTES
            CafeteriaAnalytics.CafeteriaAnalyticsBuilder builder = CafeteriaAnalytics.builder()
                    .cafeteriaLocation(location)
                    .timestamp(timestamp)
                    .currentOccupancy(occupancy)
                    .capacity(location.getCapacity() != null ? location.getCapacity() : 728)
                    .inCount(inCount)
                    .avgDwellTime(avgDwell)       // Already converted to minutes
                    .maxDwellTime(maxDwell)       // Already converted to minutes
                    .estimatedWaitTime(estimateWaitTime)  // Already converted to minutes
                    .manualWaitTime(manualWaitTime)       // Already converted to minutes
                    .queueLength(queueLength);

            // Add counter reference if found (counter-specific data)
            if (counter != null) {
                builder.foodCounter(counter);
            }

            CafeteriaAnalytics analytics = builder.build();

            // Save to database
            CafeteriaAnalytics saved = analyticsRepository.save(analytics);

            // Log success with details
            String savedAs = counter != null
                    ? String.format("counter '%s'", counter.getCounterName())
                    : String.format("cafeteria '%s'", location.getCafeteriaName());

            log.info("✅ Saved analytics for {} | Occupancy: {} | Queue: {} | Dwell: {}min | ID: {}",
                    savedAs,
                    occupancy != null ? occupancy : "N/A",
                    queueLength != null ? queueLength : "N/A",
                    avgDwell != null ? String.format("%.1f", avgDwell) : "N/A",
                    saved.getId());

        } catch (Exception e) {
            log.error("❌ Error processing MQTT message: {}", e.getMessage(), e);
        }
    }

    /**
     * Publish test message (for testing purposes)
     * NOTE: Test values are generated in SECONDS to match real MQTT publisher behavior
     */
    public void publishTestMessage(String deviceId) {
        try {
            String testPayload = String.format("""
                {
                    "occupancy": %d,
                    "avg_dwell": %.2f,
                    "max_dwell": %.2f,
                    "incount": %d,
                    "estimate_wait_time": %.2f,
                    "waiting_time_min": %.2f,
                    "deviceId": "%s",
                    "timestamp": "%s"
                }
                """,
                    (int) (Math.random() * 200) + 50,
                    (Math.random() * 20 + 10) * 60,  // Generate in seconds (10-30 min * 60)
                    (Math.random() * 30 + 20) * 60,  // Generate in seconds (20-50 min * 60)
                    (int) (Math.random() * 50) + 10,
                    (Math.random() * 15 + 5) * 60,   // Generate in seconds (5-20 min * 60)
                    (Math.random() * 10 + 5) * 60,   // Generate in seconds (5-15 min * 60)
                    deviceId != null ? deviceId : "",
                    LocalDateTime.now().toString()
            );

            MqttMessage message = new MqttMessage(testPayload.getBytes());
            message.setQos(1);
            mqttClient.publish(topic, message);

            log.info("📤 Published test message for device: {} (times in seconds)", deviceId);
        } catch (Exception e) {
            log.error("❌ Error publishing test message", e);
        }
    }

    /**
     * Publish test message without deviceId (cafeteria-level)
     * NOTE: Test values are generated in SECONDS to match real MQTT publisher behavior
     */
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
                    (Math.random() * 20 + 10) * 60,  // Generate in seconds
                    (Math.random() * 30 + 20) * 60,  // Generate in seconds
                    (int) (Math.random() * 50) + 10,
                    (Math.random() * 15 + 5) * 60,   // Generate in seconds
                    (Math.random() * 10 + 5) * 60,   // Generate in seconds
                    cafeteriaCode != null ? cafeteriaCode : defaultCafeteriaCode,
                    LocalDateTime.now().toString()
            );

            MqttMessage message = new MqttMessage(testPayload.getBytes());
            message.setQos(1);
            mqttClient.publish(topic, message);

            log.info("📤 Published cafeteria-level test message for: {} (times in seconds)", cafeteriaCode);
        } catch (Exception e) {
            log.error("❌ Error publishing test message", e);
        }
    }

    /**
     * Check MQTT connection status
     */
    public boolean isConnected() {
        return mqttClient != null && mqttClient.isConnected();
    }

    /**
     * Disconnect from MQTT broker on application shutdown
     */
    @PreDestroy
    public void cleanup() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
                mqttClient.close();
                log.info("MQTT client disconnected");
            }
        } catch (Exception e) {
            log.error("Error during MQTT cleanup", e);
        }
    }
}