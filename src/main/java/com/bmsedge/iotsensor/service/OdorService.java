package com.bmsedge.iotsensor.service;

import com.bmsedge.iotsensor.dto.SensorDataDTO;
import com.bmsedge.iotsensor.model.DeviceLocationMapping;
import com.bmsedge.iotsensor.model.Location;
import com.bmsedge.iotsensor.model.SensorData;
import com.bmsedge.iotsensor.repository.DeviceLocationMappingRepository;
import com.bmsedge.iotsensor.repository.LocationRepository;
import com.bmsedge.iotsensor.repository.SensorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OdorService {

    private final SensorRepository sensorRepository;
    private final LocationRepository locationRepository;
    private final DeviceLocationMappingRepository deviceLocationMappingRepository;


    @Transactional
    public Map<String, Object> saveOdorData(Map<String, Object> payload) {
        String deviceId = extractDeviceId(payload);
        LocalDateTime timestamp = parseTimestamp(payload.get("timestamp"));

        log.info("Processing odor sensor data from device: {}", deviceId);

        // Get location from device mapping
        Location location = getLocationForDevice(deviceId);
        if (location == null) {
            throw new RuntimeException("Device not mapped to any location: " + deviceId);
        }

        int savedCount = 0;

        // Save Battery data
        if (payload.containsKey("odorbattery") || payload.containsKey("battery")) {
            Double battery = getDoubleValue(payload, payload.containsKey("odorbattery") ? "odorbattery" : "battery");
            saveSingleReading(deviceId, "BATTERY", battery, "%", "NORMAL", null, location, timestamp);
            savedCount++;
        }

        // Save Temperature data
        if (payload.containsKey("odortemperature") || payload.containsKey("temperature")) {
            Double temp = getDoubleValue(payload, payload.containsKey("odortemperature") ? "odortemperature" : "temperature");
            String tempStatus = determineTemperatureStatus(temp);
            saveSingleReading(deviceId, "TEMPERATURE", temp, "°C", tempStatus, null, location, timestamp);
            savedCount++;
        }

        // Save Humidity data
        if (payload.containsKey("odorhumidity") || payload.containsKey("humidity")) {
            Double humidity = getDoubleValue(payload, payload.containsKey("odorhumidity") ? "odorhumidity" : "humidity");
            String humidityStatus = determineHumidityStatus(humidity);
            saveSingleReading(deviceId, "HUMIDITY", humidity, "%", humidityStatus, null, location, timestamp);
            savedCount++;
        }

        // Save NH3 (Ammonia) data
        Double nh3 = null;
        if (payload.containsKey("odornh3")) {
            nh3 = getDoubleValue(payload, "odornh3");
            String nh3Status = determineNH3Status(nh3);
            String nh3Quality = determineNH3Quality(nh3);
            saveSingleReading(deviceId, "NH3", nh3, "ppm", nh3Status, nh3Quality, location, timestamp);
            savedCount++;
        }

        // Save H2S (Hydrogen Sulfide) data
        Double h2s = null;
        if (payload.containsKey("odorh2s_high_precision")) {
            h2s = getDoubleValue(payload, "odorh2s_high_precision");
            String h2sStatus = determineH2SStatus(h2s);
            String h2sQuality = determineH2SQuality(h2s);
            saveSingleReading(deviceId, "H2S", h2s, "ppm", h2sStatus, h2sQuality, location, timestamp);
            savedCount++;
        }

        // Calculate and save overall Odor Index
        if (nh3 != null && h2s != null) {
            double odorIndex = calculateOdorIndex(nh3, h2s);
            String odorStatus = determineOdorStatus(odorIndex);
            String odorQuality = determineOdorQuality(odorIndex);
            saveSingleReading(deviceId, "ODOR_INDEX", odorIndex, "index", odorStatus, odorQuality, location, timestamp);
            savedCount++;
        }

        log.info("✅ Saved {} odor readings for device: {}", savedCount, deviceId);

        return Map.of(
                "success", true,
                "message", "Odor sensor data saved successfully",
                "deviceId", deviceId,
                "locationId", location.getId(),
                "locationName", location.getName(),
                "readingsStored", savedCount,
                "timestamp", timestamp
        );
    }

    /**
     * Get odor sensor data for a specific device
     */
    public Map<String, Object> getOdorSensorData(String deviceId, Integer hours) {
        LocalDateTime startTime = LocalDateTime.now().minusHours(hours);

        Map<String, Object> result = new HashMap<>();
        result.put("deviceId", deviceId);
        result.put("hours", hours);

        List<String> odorTypes = Arrays.asList("ODOR_INDEX", "NH3", "H2S", "TEMPERATURE", "HUMIDITY", "BATTERY");
        Map<String, List<SensorDataDTO>> dataByType = new HashMap<>();

        for (String type : odorTypes) {
            List<SensorData> data = sensorRepository.findRecentByDeviceId(deviceId, startTime)
                    .stream()
                    .filter(d -> type.equals(d.getType()))
                    .collect(Collectors.toList());

            if (!data.isEmpty()) {
                dataByType.put(type.toLowerCase(), data.stream()
                        .map(this::convertToDTO)
                        .collect(Collectors.toList()));
            }
        }

        result.put("data", dataByType);
        return result;
    }

    /**
     * Get latest odor data for a device
     */
    public Map<String, Object> getLatestOdorData(String deviceId) {
        Map<String, Object> result = new HashMap<>();
        result.put("deviceId", deviceId);

        List<String> odorTypes = Arrays.asList("ODOR_INDEX", "NH3", "H2S", "TEMPERATURE", "HUMIDITY", "BATTERY");
        Map<String, SensorDataDTO> latestData = new HashMap<>();

        for (String type : odorTypes) {
            List<SensorData> typeData = sensorRepository.findByDeviceId(deviceId).stream()
                    .filter(d -> type.equals(d.getType()))
                    .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                    .limit(1)
                    .collect(Collectors.toList());

            if (!typeData.isEmpty()) {
                latestData.put(type.toLowerCase(), convertToDTO(typeData.get(0)));
            }
        }

        result.put("latestReadings", latestData);

        // Add device status
        if (latestData.containsKey("odor_index")) {
            SensorDataDTO odorIndex = latestData.get("odor_index");
            result.put("overallStatus", odorIndex.getStatus());
            result.put("overallQuality", odorIndex.getQuality());
        }

        return result;
    }

    /**
     * Get odor data by location (restroom)
     */
    public Map<String, Object> getOdorDataByLocation(Long locationId, Integer hours) {
        LocalDateTime startTime = LocalDateTime.now().minusHours(hours);

        List<SensorData> data = sensorRepository.findRecentByLocation(locationId, startTime)
                .stream()
                .filter(d -> Arrays.asList("ODOR_INDEX", "NH3", "H2S", "TEMPERATURE", "HUMIDITY", "BATTERY")
                        .contains(d.getType()))
                .collect(Collectors.toList());

        Map<String, List<SensorDataDTO>> dataByType = data.stream()
                .collect(Collectors.groupingBy(
                        SensorData::getType,
                        Collectors.mapping(this::convertToDTO, Collectors.toList())
                ));

        return Map.of(
                "locationId", locationId,
                "hours", hours,
                "data", dataByType
        );
    }

    /**
     * Get odor data by floor
     */
    public Map<String, Object> getOdorDataByFloor(Integer floor, Integer hours) {
        LocalDateTime startTime = LocalDateTime.now().minusHours(hours);

        List<SensorData> data = sensorRepository.findByFloor(floor)
                .stream()
                .filter(d -> d.getTimestamp().isAfter(startTime))
                .filter(d -> Arrays.asList("ODOR_INDEX", "NH3", "H2S")
                        .contains(d.getType()))
                .collect(Collectors.toList());

        Map<String, List<SensorDataDTO>> dataByType = data.stream()
                .collect(Collectors.groupingBy(
                        SensorData::getType,
                        Collectors.mapping(this::convertToDTO, Collectors.toList())
                ));

        return Map.of(
                "floor", floor,
                "hours", hours,
                "data", dataByType
        );
    }

    /**
     * Get all odor sensors status
     */
    public List<Map<String, Object>> getAllOdorSensorsStatus() {
        List<DeviceLocationMapping> mappings = deviceLocationMappingRepository
                .findActiveByDeviceType("ODOR_SENSOR");

        return mappings.stream().map(mapping -> {
            String deviceId = mapping.getDeviceId();
            SensorData latest = sensorRepository.findLatestByDeviceId(deviceId);

            Map<String, Object> status = new HashMap<>();
            status.put("deviceId", deviceId);
            status.put("locationId", mapping.getLocation().getId());
            status.put("locationName", mapping.getLocation().getName());
            status.put("floor", mapping.getLocation().getFloor());

            if (latest != null) {
                status.put("lastUpdate", latest.getTimestamp());
                status.put("status", latest.getStatus());
                status.put("quality", latest.getQuality());
                status.put("isOnline", isDeviceOnline(latest.getTimestamp()));
            } else {
                status.put("status", "OFFLINE");
                status.put("isOnline", false);
            }

            return status;
        }).collect(Collectors.toList());
    }

    /**
     * Get odor quality summary for a restroom
     */
    public Map<String, Object> getOdorQualitySummary(Long locationId) {
        LocalDateTime startTime = LocalDateTime.now().minusHours(1);

        List<SensorData> recentData = sensorRepository.findRecentByLocation(locationId, startTime);

        Map<String, String> qualityByType = new HashMap<>();
        for (String type : Arrays.asList("ODOR_INDEX", "NH3", "H2S")) {
            recentData.stream()
                    .filter(d -> type.equals(d.getType()))
                    .findFirst()
                    .ifPresent(d -> qualityByType.put(type.toLowerCase(), d.getQuality()));
        }

        String overallQuality = qualityByType.getOrDefault("odor_index", "UNKNOWN");

        return Map.of(
                "locationId", locationId,
                "qualityByParameter", qualityByType,
                "overallQuality", overallQuality
        );
    }

    /**
     * Get odor alerts (high NH3/H2S levels)
     */
    public List<Map<String, Object>> getOdorAlerts(Integer hours) {
        LocalDateTime startTime = LocalDateTime.now().minusHours(hours);

        List<Map<String, Object>> alerts = new ArrayList<>();

        // Check ODOR_INDEX alerts
        alerts.addAll(sensorRepository.findRecentByType("ODOR_INDEX", startTime).stream()
                .filter(d -> "WARNING".equals(d.getStatus()) || "CRITICAL".equals(d.getStatus()))
                .map(this::mapToAlert)
                .collect(Collectors.toList()));

        // Check NH3 alerts
        alerts.addAll(sensorRepository.findRecentByType("NH3", startTime).stream()
                .filter(d -> "WARNING".equals(d.getStatus()) || "CRITICAL".equals(d.getStatus()))
                .map(this::mapToAlert)
                .collect(Collectors.toList()));

        // Check H2S alerts
        alerts.addAll(sensorRepository.findRecentByType("H2S", startTime).stream()
                .filter(d -> "WARNING".equals(d.getStatus()) || "CRITICAL".equals(d.getStatus()))
                .map(this::mapToAlert)
                .collect(Collectors.toList()));

        return alerts;
    }

    /**
     * Get odor index trends for visualization
     */
    public Map<String, Object> getOdorTrends(String deviceId, Integer hours, Integer intervalMinutes) {
        LocalDateTime startTime = LocalDateTime.now().minusHours(hours);

        List<SensorData> data = sensorRepository.findRecentByDeviceId(deviceId, startTime);

        Map<String, List<Map<String, Object>>> trendsByType = new HashMap<>();

        for (String type : Arrays.asList("ODOR_INDEX", "NH3", "H2S")) {
            List<Map<String, Object>> trends = data.stream()
                    .filter(d -> type.equals(d.getType()))
                    .map(d -> Map.of(
                            "timestamp", (Object) d.getTimestamp(),
                            "value", d.getValue(),
                            "quality", d.getQuality() != null ? d.getQuality() : "N/A",
                            "status", d.getStatus()
                    ))
                    .collect(Collectors.toList());

            if (!trends.isEmpty()) {
                trendsByType.put(type.toLowerCase(), trends);
            }
        }

        return Map.of(
                "deviceId", deviceId,
                "hours", hours,
                "trends", trendsByType
        );
    }

    /**
     * Get restroom comparison by odor levels
     */
    public Map<String, Object> getRestroomComparison(List<Long> locationIds, Integer hours) {
        LocalDateTime startTime = LocalDateTime.now().minusHours(hours);

        Map<String, Object> comparison = new HashMap<>();

        for (Long locationId : locationIds) {
            Location location = locationRepository.findById(locationId).orElse(null);
            if (location == null) continue;

            List<SensorData> data = sensorRepository.findRecentByLocation(locationId, startTime);

            Map<String, Double> averages = new HashMap<>();
            for (String type : Arrays.asList("ODOR_INDEX", "NH3", "H2S")) {
                double avg = data.stream()
                        .filter(d -> type.equals(d.getType()))
                        .mapToDouble(SensorData::getValue)
                        .average()
                        .orElse(0.0);
                averages.put(type.toLowerCase(), avg);
            }

            // Get latest quality
            String quality = data.stream()
                    .filter(d -> "ODOR_INDEX".equals(d.getType()))
                    .findFirst()
                    .map(SensorData::getQuality)
                    .orElse("UNKNOWN");

            comparison.put(location.getName(), Map.of(
                    "averages", averages,
                    "quality", quality
            ));
        }

        return Map.of(
                "hours", hours,
                "comparison", comparison
        );
    }

    /**
     * Get NH3 and H2S breakdown
     */
    public Map<String, Object> getOdorBreakdown(String deviceId, Integer hours) {
        LocalDateTime startTime = LocalDateTime.now().minusHours(hours);

        List<SensorData> nh3Data = sensorRepository.findRecentByDeviceId(deviceId, startTime)
                .stream()
                .filter(d -> "NH3".equals(d.getType()))
                .collect(Collectors.toList());

        List<SensorData> h2sData = sensorRepository.findRecentByDeviceId(deviceId, startTime)
                .stream()
                .filter(d -> "H2S".equals(d.getType()))
                .collect(Collectors.toList());

        double avgNH3 = nh3Data.stream().mapToDouble(SensorData::getValue).average().orElse(0.0);
        double maxNH3 = nh3Data.stream().mapToDouble(SensorData::getValue).max().orElse(0.0);

        double avgH2S = h2sData.stream().mapToDouble(SensorData::getValue).average().orElse(0.0);
        double maxH2S = h2sData.stream().mapToDouble(SensorData::getValue).max().orElse(0.0);

        return Map.of(
                "deviceId", deviceId,
                "hours", hours,
                "nh3", Map.of(
                        "average", avgNH3,
                        "max", maxNH3,
                        "unit", "ppm",
                        "data", nh3Data.stream().map(this::convertToDTO).collect(Collectors.toList())
                ),
                "h2s", Map.of(
                        "average", avgH2S,
                        "max", maxH2S,
                        "unit", "ppm",
                        "data", h2sData.stream().map(this::convertToDTO).collect(Collectors.toList())
                )
        );
    }

    /**
     * Get battery status for all odor sensors
     */
    public List<Map<String, Object>> getBatteryStatus() {
        List<DeviceLocationMapping> mappings = deviceLocationMappingRepository
                .findActiveByDeviceType("ODOR_SENSOR");

        return mappings.stream().map(mapping -> {
            String deviceId = mapping.getDeviceId();

            List<SensorData> batteryData = sensorRepository.findByDeviceId(deviceId).stream()
                    .filter(d -> "BATTERY".equals(d.getType()))
                    .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                    .limit(1)
                    .collect(Collectors.toList());

            Map<String, Object> status = new HashMap<>();
            status.put("deviceId", deviceId);
            status.put("locationName", mapping.getLocation().getName());

            if (!batteryData.isEmpty()) {
                SensorData battery = batteryData.get(0);
                status.put("batteryLevel", battery.getValue());
                status.put("lastUpdate", battery.getTimestamp());
                status.put("status", battery.getValue() < 20 ? "LOW" : "NORMAL");
            } else {
                status.put("status", "UNKNOWN");
            }

            return status;
        }).collect(Collectors.toList());
    }

    // ===== HELPER METHODS =====

    private void saveSingleReading(String deviceId, String type, Double value, String unit,
                                   String status, String quality, Location location, LocalDateTime timestamp) {
        SensorData sensorData = SensorData.builder()
                .deviceId(deviceId)
                .type(type)
                .value(value)
                .unit(unit)
                .status(status)
                .quality(quality)
                .location(location)
                .timestamp(timestamp)
                .build();

        sensorRepository.save(sensorData);
    }

    private Location getLocationForDevice(String deviceId) {
        return deviceLocationMappingRepository.findByDeviceId(deviceId)
                .map(DeviceLocationMapping::getLocation)
                .orElse(null);
    }

    private String extractDeviceId(Map<String, Object> payload) {
        if (payload.containsKey("deviceId")) {
            return (String) payload.get("deviceId");
        }
        if (payload.containsKey("device")) {
            return (String) payload.get("device");
        }
        throw new IllegalArgumentException("No device identifier found in payload");
    }

    private Double getDoubleValue(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return Double.parseDouble(value.toString());
    }

    private LocalDateTime parseTimestamp(Object timestampObj) {
        if (timestampObj == null) {
            return LocalDateTime.now();
        }

        String timestamp = timestampObj.toString();

        try {
            // Try parsing HH:mm:ss format
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
            LocalTime time = LocalTime.parse(timestamp, timeFormatter);
            return LocalDateTime.of(LocalDate.now(), time);
        } catch (DateTimeParseException e) {
            try {
                // Try parsing as ISO datetime
                return LocalDateTime.parse(timestamp);
            } catch (DateTimeParseException e2) {
                log.warn("Failed to parse timestamp: {}, using current time", timestamp);
                return LocalDateTime.now();
            }
        }
    }

    private boolean isDeviceOnline(LocalDateTime lastUpdate) {
        return lastUpdate != null && lastUpdate.isAfter(LocalDateTime.now().minusMinutes(10));
    }

    private Map<String, Object> mapToAlert(SensorData d) {
        return Map.of(
                "deviceId", d.getDeviceId(),
                "type", d.getType(),
                "value", d.getValue(),
                "unit", d.getUnit(),
                "status", d.getStatus(),
                "quality", d.getQuality() != null ? d.getQuality() : "N/A",
                "timestamp", d.getTimestamp(),
                "locationId", d.getLocation() != null ? d.getLocation().getId() : null,
                "locationName", d.getLocation() != null ? d.getLocation().getName() : "Unknown"
        );
    }

    // Calculate odor index from NH3 and H2S levels
    private double calculateOdorIndex(double nh3, double h2s) {
        // NH3 thresholds (ppm): 0-0.5 (20), 0.5-2 (40), 2-5 (60), 5-25 (80), >25 (100)
        double nh3Score;
        if (nh3 <= 0.5) nh3Score = 20;
        else if (nh3 <= 2) nh3Score = 40;
        else if (nh3 <= 5) nh3Score = 60;
        else if (nh3 <= 25) nh3Score = 80;
        else nh3Score = 100;

        // H2S thresholds (ppm): 0-0.01 (20), 0.01-0.03 (40), 0.03-0.1 (60), 0.1-0.5 (80), >0.5 (100)
        double h2sScore;
        if (h2s <= 0.01) h2sScore = 20;
        else if (h2s <= 0.03) h2sScore = 40;
        else if (h2s <= 0.1) h2sScore = 60;
        else if (h2s <= 0.5) h2sScore = 80;
        else h2sScore = 100;

        // Weighted average (H2S is more critical for odor)
        return (nh3Score * 0.4) + (h2sScore * 0.6);
    }

    // Status determination methods
    private String determineTemperatureStatus(double temp) {
        if (temp < 10 || temp > 35) return "CRITICAL";
        if (temp < 15 || temp > 30) return "WARNING";
        return "NORMAL";
    }

    private String determineHumidityStatus(double humidity) {
        if (humidity < 20 || humidity > 80) return "CRITICAL";
        if (humidity < 30 || humidity > 70) return "WARNING";
        return "NORMAL";
    }

    private String determineNH3Status(double nh3) {
        if (nh3 > 25) return "CRITICAL";
        if (nh3 > 5) return "WARNING";
        return "NORMAL";
    }

    private String determineH2SStatus(double h2s) {
        if (h2s > 0.5) return "CRITICAL";
        if (h2s > 0.1) return "WARNING";
        return "NORMAL";
    }

    private String determineOdorStatus(double odorIndex) {
        if (odorIndex > 80) return "CRITICAL";
        if (odorIndex > 60) return "WARNING";
        return "NORMAL";
    }

    // Quality determination methods
    private String determineNH3Quality(double nh3) {
        if (nh3 <= 0.5) return "EXCELLENT";
        if (nh3 <= 2) return "GOOD";
        if (nh3 <= 5) return "MODERATE";
        if (nh3 <= 25) return "POOR";
        return "SEVERE";
    }

    private String determineH2SQuality(double h2s) {
        if (h2s <= 0.01) return "EXCELLENT";
        if (h2s <= 0.03) return "GOOD";
        if (h2s <= 0.1) return "MODERATE";
        if (h2s <= 0.5) return "POOR";
        return "SEVERE";
    }

    private String determineOdorQuality(double odorIndex) {
        if (odorIndex <= 20) return "EXCELLENT";
        if (odorIndex <= 40) return "GOOD";
        if (odorIndex <= 60) return "MODERATE";
        if (odorIndex <= 80) return "POOR";
        return "SEVERE";
    }

    private SensorDataDTO convertToDTO(SensorData sensorData) {
        SensorDataDTO dto = SensorDataDTO.builder()
                .id(sensorData.getId())
                .deviceId(sensorData.getDeviceId())
                .type(sensorData.getType())
                .value(sensorData.getValue())
                .unit(sensorData.getUnit())
                .status(sensorData.getStatus())
                .quality(sensorData.getQuality())
                .timestamp(sensorData.getTimestamp())
                .build();

        if (sensorData.getLocation() != null) {
            dto.setLocationId(sensorData.getLocation().getId());
            dto.setLocationName(sensorData.getLocation().getName());
            dto.setFloor(sensorData.getLocation().getFloor());
            dto.setZone(sensorData.getLocation().getZone());
        }

        return dto;
    }
}