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
    private final SimpMessagingTemplate messagingTemplate; // ✅ WebSocket

    // ✅ Supported counter prefixes
    private static final String[] COUNTER_PREFIXES = {"mini_meals", "two_good", "healthy_station"};


    /**
     * ✅ TRANSACTIONAL message processing with WebSocket broadcast
     */
    @Transactional
    public void processMessage(String topic, String payload, Long counterId, String defaultCafeteriaCode) {
        try {
            log.debug("📨 Processing: topic='{}', counterId={}", topic, counterId);

            JsonNode jsonNode = objectMapper.readTree(payload);

            // ✅ Extract occupancy - handle all counter prefixes
            Integer occupancy = extractIntField(jsonNode, "occupancy", COUNTER_PREFIXES);

            // ✅ Extract times (in seconds, will convert to minutes)
            Double avgDwellSeconds = extractDoubleField(jsonNode, "avg_dwell", COUNTER_PREFIXES);
            Double maxDwellSeconds = extractDoubleField(jsonNode, "max_dwell", COUNTER_PREFIXES);
            Double estimateWaitTimeSeconds = extractDoubleField(jsonNode, "estimate_wait_time", COUNTER_PREFIXES);
            Double manualWaitTimeSeconds = extractDoubleField(jsonNode, "waiting_time_min", COUNTER_PREFIXES);

            // ✅ Convert seconds to minutes
            Double avgDwell = avgDwellSeconds != null ? avgDwellSeconds / 60.0 : null;
            Double maxDwell = maxDwellSeconds != null ? maxDwellSeconds / 60.0 : null;
            Double estimateWaitTime = estimateWaitTimeSeconds != null ? estimateWaitTimeSeconds / 60.0 : null;
            Double manualWaitTime = manualWaitTimeSeconds != null ? manualWaitTimeSeconds / 60.0 : null;

            // Log conversions for debugging
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
            if (manualWaitTimeSeconds != null) {
                log.debug("🔄 Converted manual_wait_time: {} seconds → {} minutes",
                        String.format("%.1f", manualWaitTimeSeconds),
                        String.format("%.2f", manualWaitTime));
            }

            // ✅ Extract inCount
            Integer inCount = extractIntField(jsonNode, "incount", COUNTER_PREFIXES);

            // ✅ Parse timestamp
            LocalDateTime timestamp = LocalDateTime.now();
            if (jsonNode.has("timestamp")) {
                try {
                    timestamp = LocalDateTime.parse(jsonNode.get("timestamp").asText());
                } catch (Exception e) {
                    log.warn("⚠️ Failed to parse timestamp, using current time");
                }
            }

            // ✅ STEP 1: Get counter by ID
            FoodCounter counter = null;
            CafeteriaLocation location = null;

            if (counterId != null) {
                counter = counterRepository.findById(counterId).orElse(null);
                if (counter != null) {
                    location = counter.getCafeteriaLocation();
                    // Force load lazy fields
                    location.getCapacity();
                    location.getCafeteriaName();

                    log.info("✅ Loaded Counter ID {} ('{}') for topic '{}'",
                            counterId, counter.getCounterName(), topic);
                } else {
                    log.error("❌ Counter ID {} NOT FOUND for topic '{}'", counterId, topic);
                }
            }

            // ✅ STEP 2: Fallback to default cafeteria
            if (location == null) {
                location = locationRepository.findByCafeteriaCode(defaultCafeteriaCode).orElse(null);
                if (location != null) {
                    log.info("ℹ️ Using default cafeteria: {}", location.getCafeteriaName());
                } else {
                    log.error("❌ Default cafeteria '{}' not found!", defaultCafeteriaCode);
                    return;
                }
            }

            // ✅ IMPROVED: Calculate queue length with better logic
            Integer queueLength = null;

            // Priority 1: Use wait time to calculate queue (2 min per person)
            if (estimateWaitTime != null && estimateWaitTime > 0) {
                queueLength = (int) Math.ceil(estimateWaitTime / 2.0);
                log.info("✅ Queue calculated from estimateWaitTime: {} min → {} people",
                        String.format("%.2f", estimateWaitTime), queueLength);
            } else if (manualWaitTime != null && manualWaitTime > 0) {
                queueLength = (int) Math.ceil(manualWaitTime / 2.0);
                log.info("✅ Queue calculated from manualWaitTime: {} min → {} people",
                        String.format("%.2f", manualWaitTime), queueLength);
            }
            // Priority 2: Use occupancy as queue length if no wait time
            else if (occupancy != null && occupancy > 0) {
                queueLength = occupancy;
                log.info("ℹ️ Using occupancy as queue length: {} people", occupancy);
            } else {
                log.warn("⚠️ No data available for queue calculation - will save as NULL");
            }

            log.info("📊 Final values: occupancy={}, waitTime={}min, queue={}, inCount={}",
                    occupancy,
                    estimateWaitTime != null ? String.format("%.2f", estimateWaitTime) :
                            manualWaitTime != null ? String.format("%.2f", manualWaitTime) : "null",
                    queueLength,
                    inCount);

            // ✅ BUILD ANALYTICS RECORD
            CafeteriaAnalytics analytics = CafeteriaAnalytics.builder()
                    .cafeteriaLocation(location)
                    .foodCounter(counter)  // ✅ CRITICAL: This sets food_counter_id
                    .timestamp(timestamp)
                    .currentOccupancy(occupancy)
                    .capacity(location.getCapacity() != null ? location.getCapacity() : 728)
                    .inCount(inCount)
                    .avgDwellTime(avgDwell)
                    .maxDwellTime(maxDwell)
                    .estimatedWaitTime(estimateWaitTime)
                    .manualWaitTime(manualWaitTime)
                    .queueLength(queueLength)
                    .build();

            // ✅ SAVE
            CafeteriaAnalytics saved = analyticsRepository.save(analytics);

            // ✅ VERIFY SAVE with detailed logging
            String counterInfo = saved.getFoodCounter() != null
                    ? String.format("Counter ID: %d (%s)", saved.getFoodCounter().getId(), saved.getFoodCounter().getCounterName())
                    : "NULL ❌";

            log.info("✅ SAVED Record ID: {} | {} | Occupancy: {} | Queue: {} | InCount: {} | AvgDwell: {}min | ManualWait: {}min | Topic: '{}'",
                    saved.getId(),
                    counterInfo,
                    occupancy != null ? occupancy : "N/A",
                    queueLength != null ? queueLength : "N/A",
                    inCount != null ? inCount : "N/A",
                    avgDwell != null ? String.format("%.2f", avgDwell) : "N/A",
                    manualWaitTime != null ? String.format("%.2f", manualWaitTime) : "N/A",
                    topic);

            // ✅ DOUBLE CHECK
            if (counter != null && saved.getFoodCounter() == null) {
                log.error("❌❌❌ CRITICAL: Counter was set but NOT saved! Counter ID was: {}", counterId);
            }

            // ✅✅✅ BROADCAST LIVE UPDATE VIA WEBSOCKET
            log.info("🚀 Triggering WebSocket broadcast...");
            broadcastLiveUpdate(location, saved);

        } catch (Exception e) {
            log.error("❌ Error processing message from topic '{}': {}", topic, e.getMessage(), e);
        }
    }


    private void broadcastLiveUpdate(CafeteriaLocation location, CafeteriaAnalytics savedAnalytics) {
        try {
            log.info("🚀 Starting WebSocket broadcast for cafeteria: {}", location.getCafeteriaCode());

            // Get all latest counter statuses
            List<CafeteriaAnalytics> latestAnalytics = analyticsRepository
                    .findLatestForAllCounters(location.getId());

            log.info("📊 Found {} latest analytics records", latestAnalytics.size());

            List<CounterStatusDTO> counters = latestAnalytics.stream()
                    .filter(analytics -> analytics.getFoodCounter() != null)
                    .map(analytics -> {
                        FoodCounter counter = analytics.getFoodCounter();

                        // Determine wait time with COALESCE logic
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

                        log.debug("   Counter: {} | Queue: {} | Wait: {}min",
                                counter.getCounterName(),
                                analytics.getQueueLength(),
                                String.format("%.1f", waitTime));

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

            log.info("✅ Prepared {} counter status DTOs", counters.size());

            // Get occupancy status (using Healthy Station or cafeteria-level)
            CafeteriaAnalytics healthyStationData = latestAnalytics.stream()
                    .filter(a -> a.getFoodCounter() != null)
                    .filter(a -> a.getFoodCounter().getCounterName() != null)
                    .filter(a -> a.getFoodCounter().getCounterName().toLowerCase().contains("healthy station"))
                    .findFirst()
                    .orElseGet(() -> analyticsRepository
                            .findLatestByCafeteriaLocation(location.getId())
                            .orElse(null));

            // Get occupancy status (SUM of all counters)
            OccupancyStatusDTO occupancyStatus = null;
            try {
                // ✅ SUM occupancy from all counters
                int totalOccupancy = latestAnalytics.stream()
                        .filter(a -> a.getFoodCounter() != null)
                        .mapToInt(a -> a.getCurrentOccupancy() != null ? a.getCurrentOccupancy() : 0)
                        .sum();

                Integer capacity = location.getCapacity() != null ? location.getCapacity() : 728;
                Double percentage = capacity > 0 ? (totalOccupancy * 100.0) / capacity : 0.0;

                // Determine congestion level
                String congestionLevel;
                if (percentage < 40) {
                    congestionLevel = "LOW";
                } else if (percentage < 75) {
                    congestionLevel = "MEDIUM";
                } else {
                    congestionLevel = "HIGH";
                }

                occupancyStatus = OccupancyStatusDTO.builder()
                        .currentOccupancy(totalOccupancy)
                        .capacity(capacity)
                        .occupancyPercentage(percentage)
                        .congestionLevel(congestionLevel)
                        .timestamp(LocalDateTime.now())
                        .build();

                log.info("📍 Total Occupancy (all counters): {}/{} ({}%) - {}",
                        totalOccupancy, capacity, String.format("%.1f", percentage), congestionLevel);

            } catch (Exception e) {
                log.error("Error calculating occupancy: {}", e.getMessage());
            }

            // Build update DTO
            LiveCounterUpdateDTO update = LiveCounterUpdateDTO.builder()
                    .cafeteriaCode(location.getCafeteriaCode())
                    .counters(counters)
                    .occupancyStatus(occupancyStatus)
                    .timestamp(LocalDateTime.now())
                    .updateType(savedAnalytics.getFoodCounter() != null ? "counter_update" : "occupancy_update")
                    .build();

            // ✅ BROADCAST to all clients subscribed to this cafeteria
            String destination = "/topic/cafeteria/" + location.getCafeteriaCode();

            log.info("📡 Broadcasting to WebSocket destination: {}", destination);
            log.info("📦 Update Type: {} | Counters: {} | Occupancy: {}",
                    update.getUpdateType(),
                    update.getCounters().size(),
                    update.getOccupancyStatus() != null ? "YES" : "NO");

            messagingTemplate.convertAndSend(destination, update);

            log.info("✅✅✅ WebSocket broadcast SUCCESSFUL to {} | {} counters updated",
                    destination, counters.size());

        } catch (Exception e) {
            log.error("❌ Error broadcasting live update: {}", e.getMessage(), e);
        }
    }

    /**
     * ✅ Helper method to extract integer fields with prefix fallback
     */
    private Integer extractIntField(JsonNode jsonNode, String baseFieldName, String... prefixes) {
        if (jsonNode.has(baseFieldName)) {
            return jsonNode.get(baseFieldName).asInt();
        }

        for (String prefix : prefixes) {
            String fieldName = prefix + "_" + baseFieldName;
            if (jsonNode.has(fieldName)) {
                log.debug("📋 Found field '{}' with value: {}", fieldName, jsonNode.get(fieldName).asInt());
                return jsonNode.get(fieldName).asInt();
            }
        }

        return null;
    }

    /**
     * ✅ Helper method to extract double fields with prefix fallback
     */
    private Double extractDoubleField(JsonNode jsonNode, String baseFieldName, String... prefixes) {
        if (jsonNode.has(baseFieldName)) {
            return jsonNode.get(baseFieldName).asDouble();
        }

        for (String prefix : prefixes) {
            String fieldName = prefix + "_" + baseFieldName;
            if (jsonNode.has(fieldName)) {
                log.debug("📋 Found field '{}' with value: {}", fieldName, jsonNode.get(fieldName).asDouble());
                return jsonNode.get(fieldName).asDouble();
            }
        }

        return null;
    }
}