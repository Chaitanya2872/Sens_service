package com.bmsedge.iotsensor.service;

import com.bmsedge.iotsensor.dto.CounterStatusDTO;
import com.bmsedge.iotsensor.dto.LiveCounterUpdateDTO;
import com.bmsedge.iotsensor.dto.OccupancyStatusDTO;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MqttMessageProcessor {

    private final CafeteriaAnalyticsRepository analyticsRepository;
    private final FoodCounterRepository counterRepository;
    private final CafeteriaLocationRepository locationRepository;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    // ✅ India Standard Time timezone
    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    // ✅ Supported counter prefixes
    private static final String[] COUNTER_PREFIXES = {"two_good", "mini_meals", "healthy_station"};

    /**
     * ✅ FIXED: Transactional message processing with IST timezone support
     */
    @Transactional
    public void processMessage(String topic, String payload, Long counterId, String defaultCafeteriaCode) {
        try {
            log.info("📨 Processing: topic='{}', counterId={}, payload length={}",
                    topic, counterId, payload.length());
            log.debug("📄 Raw payload: {}", payload);

            JsonNode jsonNode = objectMapper.readTree(payload);

            // ✅ Extract occupancy - handle all counter prefixes
            Integer occupancy = extractIntFieldSafe(jsonNode, "occupancy", COUNTER_PREFIXES);

            // ✅ Extract times (in seconds, will convert to minutes)
            Double avgDwellSeconds = extractDoubleFieldSafe(jsonNode, "avg_dwell", COUNTER_PREFIXES);
            Double maxDwellSeconds = extractDoubleFieldSafe(jsonNode, "max_dwell", COUNTER_PREFIXES);
            Double estimateWaitTimeSeconds = extractDoubleFieldSafe(jsonNode, "estimate_wait_time", COUNTER_PREFIXES);
            Double manualWaitTimeSeconds = extractDoubleFieldSafe(jsonNode, "waiting_time_min", COUNTER_PREFIXES);

            // ✅ Convert seconds to minutes (only if value is valid)
            Double avgDwell = avgDwellSeconds != null && avgDwellSeconds > 0 ? avgDwellSeconds / 60.0 : null;
            Double maxDwell = maxDwellSeconds != null && maxDwellSeconds > 0 ? maxDwellSeconds / 60.0 : null;
            Double estimateWaitTime = estimateWaitTimeSeconds != null && estimateWaitTimeSeconds > 0 ? estimateWaitTimeSeconds / 60.0 : null;
            Double manualWaitTime = manualWaitTimeSeconds != null && manualWaitTimeSeconds > 0 ? manualWaitTimeSeconds / 60.0 : null;

            // ✅ Extract inCount
            Integer inCount = extractIntFieldSafe(jsonNode, "incount", COUNTER_PREFIXES);

            log.info("📊 Extracted: occupancy={}, inCount={}, avgDwell={}min, estimateWait={}min",
                    occupancy, inCount,
                    avgDwell != null ? String.format("%.2f", avgDwell) : "NULL",
                    estimateWaitTime != null ? String.format("%.2f", estimateWaitTime) : "NULL");

            // ✅ FIXED: Parse timestamp with IST timezone
            LocalDateTime timestamp = parseTimestampWithIST(jsonNode);

            // ✅ STEP 1: Get counter by ID
            FoodCounter counter = null;
            CafeteriaLocation location = null;

            if (counterId != null) {
                counter = counterRepository.findById(counterId).orElse(null);
                if (counter != null) {
                    location = counter.getCafeteriaLocation();
                    log.info("✅ Loaded Counter ID {} ('{}') in cafeteria '{}'",
                            counterId, counter.getCounterName(), location.getCafeteriaName());
                } else {
                    log.error("❌ Counter ID {} NOT FOUND in database!", counterId);
                }
            }

            // ✅ STEP 2: Fallback to default cafeteria
            if (location == null) {
                location = locationRepository.findByCafeteriaCode(defaultCafeteriaCode).orElse(null);
                if (location != null) {
                    log.info("ℹ️ Using default cafeteria: {} (no counter found for ID: {})",
                            location.getCafeteriaName(), counterId);
                } else {
                    log.error("❌ Default cafeteria '{}' not found! Cannot save data.", defaultCafeteriaCode);
                    return;
                }
            }

            // ✅ Calculate queue length
            Integer queueLength = null;
            if (estimateWaitTime != null && estimateWaitTime > 0) {
                queueLength = (int) Math.ceil(estimateWaitTime / 2.0);
                log.info("✅ Queue calculated from estimateWaitTime: {} min → {} people",
                        String.format("%.2f", estimateWaitTime), queueLength);
            } else if (manualWaitTime != null && manualWaitTime > 0) {
                queueLength = (int) Math.ceil(manualWaitTime / 2.0);
                log.info("✅ Queue calculated from manualWaitTime: {} min → {} people",
                        String.format("%.2f", manualWaitTime), queueLength);
            } else if (occupancy != null && occupancy > 0) {
                queueLength = occupancy;
                log.info("ℹ️ Using occupancy as queue length: {} people", occupancy);
            }

            // ✅ BUILD ANALYTICS RECORD
            CafeteriaAnalytics analytics = CafeteriaAnalytics.builder()
                    .cafeteriaLocation(location)
                    .foodCounter(counter)
                    .timestamp(timestamp)  // ✅ Now using IST timestamp
                    .currentOccupancy(occupancy)
                    .capacity(location.getCapacity() != null ? location.getCapacity() : 728)
                    .inCount(inCount)
                    .avgDwellTime(avgDwell)
                    .maxDwellTime(maxDwell)
                    .estimatedWaitTime(estimateWaitTime)
                    .manualWaitTime(manualWaitTime)
                    .queueLength(queueLength)
                    .build();

            // ✅ LOG BEFORE SAVE
            log.info("💾 Saving with IST timestamp: {}", timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            // ✅ SAVE
            CafeteriaAnalytics saved = analyticsRepository.save(analytics);

            // ✅ VERIFY SAVE
            String counterInfo = saved.getFoodCounter() != null
                    ? String.format("Counter ID: %d (%s)", saved.getFoodCounter().getId(), saved.getFoodCounter().getCounterName())
                    : "Cafeteria-level (counter=NULL)";

            log.info("✅✅✅ SAVED SUCCESSFULLY!");
            log.info("    Record ID: {}", saved.getId());
            log.info("    {}", counterInfo);
            log.info("    Timestamp (IST): {}", saved.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            log.info("    Occupancy: {}", occupancy != null ? occupancy : "NULL");
            log.info("    In Count: {}", inCount != null ? inCount : "NULL");
            log.info("    Queue Length: {}", queueLength != null ? queueLength : "NULL");

            // ✅ Verify in database
            CafeteriaAnalytics verified = analyticsRepository.findById(saved.getId()).orElse(null);
            if (verified != null) {
                log.info("✅ Verified: Record {} exists in database", verified.getId());
                if (verified.getFoodCounter() != null) {
                    log.info("✅ Counter link verified: food_counter_id = {}", verified.getFoodCounter().getId());
                }
            } else {
                log.error("❌❌❌ CRITICAL: Record was saved but cannot be found in database!");
            }

            // ✅ BROADCAST VIA WEBSOCKET
            broadcastLiveUpdate(location, saved);

        } catch (Exception e) {
            log.error("❌❌❌ CRITICAL ERROR processing message from topic '{}': {}", topic, e.getMessage());
            log.error("Stack trace:", e);
            throw new RuntimeException("Failed to process MQTT message", e);
        }
    }

    /**
     * ✅ NEW: Parse timestamp from MQTT payload with IST timezone support
     */
    private LocalDateTime parseTimestampWithIST(JsonNode jsonNode) {
        // Default to current IST time
        LocalDateTime istNow = ZonedDateTime.now(IST_ZONE).toLocalDateTime();

        if (!jsonNode.has("timestamp")) {
            log.debug("ℹ️ No timestamp in payload, using current IST time: {}",
                    istNow.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            return istNow;
        }

        try {
            String timestampStr = jsonNode.get("timestamp").asText();

            // Try parsing as ISO LocalDateTime (no timezone)
            try {
                LocalDateTime parsedTime = LocalDateTime.parse(timestampStr);
                log.debug("✅ Parsed timestamp from payload: {} (treated as IST)",
                        parsedTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                return parsedTime;
            } catch (Exception e1) {
                // Try other common formats
                try {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    LocalDateTime parsedTime = LocalDateTime.parse(timestampStr, formatter);
                    log.debug("✅ Parsed timestamp with custom format: {}",
                            parsedTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    return parsedTime;
                } catch (Exception e2) {
                    log.warn("⚠️ Failed to parse timestamp '{}', using current IST time", timestampStr);
                    return istNow;
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ Error reading timestamp field, using current IST time");
            return istNow;
        }
    }

    /**
     * ✅ SAFE integer field extraction - handles invalid values gracefully
     */
    private Integer extractIntFieldSafe(JsonNode jsonNode, String baseFieldName, String... prefixes) {
        try {
            if (jsonNode.has(baseFieldName)) {
                JsonNode node = jsonNode.get(baseFieldName);
                if (node.isNumber()) {
                    return node.asInt();
                } else if (node.isTextual()) {
                    try {
                        return Integer.parseInt(node.asText());
                    } catch (NumberFormatException e) {
                        log.warn("⚠️ Cannot parse '{}' as integer: {}", baseFieldName, node.asText());
                        return null;
                    }
                }
            }

            for (String prefix : prefixes) {
                String fieldName = prefix + "_" + baseFieldName;
                if (jsonNode.has(fieldName)) {
                    JsonNode node = jsonNode.get(fieldName);
                    if (node.isNumber()) {
                        return node.asInt();
                    } else if (node.isTextual()) {
                        try {
                            return Integer.parseInt(node.asText());
                        } catch (NumberFormatException e) {
                            log.warn("⚠️ Cannot parse '{}' as integer: {}", fieldName, node.asText());
                            continue;
                        }
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.error("❌ Error extracting integer field '{}': {}", baseFieldName, e.getMessage());
            return null;
        }
    }

    /**
     * ✅ SAFE double field extraction - handles invalid values gracefully
     */
    private Double extractDoubleFieldSafe(JsonNode jsonNode, String baseFieldName, String... prefixes) {
        try {
            if (jsonNode.has(baseFieldName)) {
                JsonNode node = jsonNode.get(baseFieldName);
                if (node.isNumber()) {
                    return node.asDouble();
                } else if (node.isTextual()) {
                    String text = node.asText();
                    if (text.toLowerCase().contains("ready") ||
                            text.toLowerCase().contains("serve") ||
                            text.toLowerCase().contains("wait")) {
                        log.warn("⚠️ Field '{}' contains non-numeric string: '{}' - returning NULL",
                                baseFieldName, text);
                        return null;
                    }
                    try {
                        return Double.parseDouble(text);
                    } catch (NumberFormatException e) {
                        log.warn("⚠️ Cannot parse '{}' as double: {}", baseFieldName, text);
                        return null;
                    }
                }
            }

            for (String prefix : prefixes) {
                String fieldName = prefix + "_" + baseFieldName;
                if (jsonNode.has(fieldName)) {
                    JsonNode node = jsonNode.get(fieldName);
                    if (node.isNumber()) {
                        return node.asDouble();
                    } else if (node.isTextual()) {
                        String text = node.asText();
                        if (text.toLowerCase().contains("ready") ||
                                text.toLowerCase().contains("serve") ||
                                text.toLowerCase().contains("wait")) {
                            log.warn("⚠️ Field '{}' contains non-numeric string: '{}' - returning NULL",
                                    fieldName, text);
                            return null;
                        }
                        try {
                            return Double.parseDouble(text);
                        } catch (NumberFormatException e) {
                            log.warn("⚠️ Cannot parse '{}' as double: {}", fieldName, text);
                            continue;
                        }
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.error("❌ Error extracting double field '{}': {}", baseFieldName, e.getMessage());
            return null;
        }
    }

    private void broadcastLiveUpdate(CafeteriaLocation location, CafeteriaAnalytics savedAnalytics) {
        try {
            log.info("🚀 Starting WebSocket broadcast for cafeteria: {}", location.getCafeteriaCode());

            List<CafeteriaAnalytics> latestAnalytics = analyticsRepository
                    .findLatestForAllCounters(location.getId());

            List<CounterStatusDTO> counters = latestAnalytics.stream()
                    .filter(analytics -> analytics.getFoodCounter() != null)
                    .map(analytics -> {
                        FoodCounter counter = analytics.getFoodCounter();
                        Double waitTime = analytics.getEstimatedWaitTime();
                        if (waitTime == null || waitTime == 0) {
                            waitTime = analytics.getManualWaitTime();
                        }
                        if (waitTime == null || waitTime == 0) {
                            waitTime = analytics.getAvgDwellTime();
                        }
                        if (waitTime == null) {
                            waitTime = 0.0;
                        }

                        return CounterStatusDTO.builder()
                                .counterName(counter.getCounterName())
                                .queueLength(analytics.getQueueLength() != null ? analytics.getQueueLength() : 0)
                                .waitTime(waitTime)
                                .congestionLevel(analytics.getCongestionLevel() != null ? analytics.getCongestionLevel() : "LOW")
                                .serviceStatus(analytics.getServiceStatus() != null ? analytics.getServiceStatus() : "UNKNOWN")
                                .lastUpdated(analytics.getTimestamp())
                                .build();
                    })
                    .collect(Collectors.toList());

            OccupancyStatusDTO occupancyStatus = null;
            try {
                int totalOccupancy = latestAnalytics.stream()
                        .filter(a -> a.getFoodCounter() != null)
                        .mapToInt(a -> a.getCurrentOccupancy() != null ? a.getCurrentOccupancy() : 0)
                        .sum();

                Integer capacity = location.getCapacity() != null ? location.getCapacity() : 728;
                Double percentage = capacity > 0 ? (totalOccupancy * 100.0) / capacity : 0.0;

                String congestionLevel = percentage < 40 ? "LOW" : (percentage < 75 ? "MEDIUM" : "HIGH");

                occupancyStatus = OccupancyStatusDTO.builder()
                        .currentOccupancy(totalOccupancy)
                        .capacity(capacity)
                        .occupancyPercentage(percentage)
                        .congestionLevel(congestionLevel)
                        .timestamp(ZonedDateTime.now(IST_ZONE).toLocalDateTime())
                        .build();
            } catch (Exception e) {
                log.error("Error calculating occupancy: {}", e.getMessage());
            }

            LiveCounterUpdateDTO update = LiveCounterUpdateDTO.builder()
                    .cafeteriaCode(location.getCafeteriaCode())
                    .counters(counters)
                    .occupancyStatus(occupancyStatus)
                    .timestamp(ZonedDateTime.now(IST_ZONE).toLocalDateTime())
                    .updateType(savedAnalytics.getFoodCounter() != null ? "counter_update" : "occupancy_update")
                    .build();

            String destination = "/topic/cafeteria/" + location.getCafeteriaCode();
            messagingTemplate.convertAndSend(destination, update);

            log.info("✅✅✅ WebSocket broadcast SUCCESSFUL to {} | {} counters updated",
                    destination, counters.size());

        } catch (Exception e) {
            log.error("❌ Error broadcasting live update: {}", e.getMessage(), e);
        }
    }
}