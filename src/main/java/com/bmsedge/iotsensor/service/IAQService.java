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

import java.io.Serializable;
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
public class IAQService {

    private final SensorRepository sensorRepository;
    private final LocationRepository locationRepository;
    private final DeviceLocationMappingRepository deviceLocationMappingRepository;

    /**
     * Save IAQ sensor data from ThingsBoard
     * Converts multi-parameter payload into individual sensor readings
     */
    @Transactional
    public Map<String, Object> saveIAQData(Map<String, Object> payload) {
        String deviceId = extractDeviceId(payload);
        LocalDateTime timestamp = parseTimestamp(payload.get("timestamp"));

        log.info("Processing IAQ data from device: {}", deviceId);

        // Get location from device mapping
        Location location = getLocationForDevice(deviceId);
        if (location == null) {
            throw new RuntimeException("Device not mapped to any location: " + deviceId);
        }

        int savedCount = 0;

        // Save Temperature
        if (payload.containsKey("temp")) {
            Double temp = getDoubleValue(payload, "temp");
            String status = determineTemperatureStatus(temp);
            String quality = determineTemperatureQuality(temp);
            saveSingleReading(deviceId, "TEMPERATURE", temp, "°C", status, quality, location, timestamp);
            savedCount++;
        }

        // Save Humidity
        if (payload.containsKey("humidity")) {
            Double humidity = getDoubleValue(payload, "humidity");
            String status = determineHumidityStatus(humidity);
            String quality = determineHumidityQuality(humidity);
            saveSingleReading(deviceId, "HUMIDITY", humidity, "%", status, quality, location, timestamp);
            savedCount++;
        }

        // Save CO2
        if (payload.containsKey("co2")) {
            Double co2 = getDoubleValue(payload, "co2");
            String status = determineCO2Status(co2);
            String quality = determineCO2Quality(co2);
            saveSingleReading(deviceId, "CO2", co2, "ppm", status, quality, location, timestamp);
            savedCount++;
        }

        // Save PM2.5
        if (payload.containsKey("pm2_5")) {
            Double pm25 = getDoubleValue(payload, "pm2_5");
            String status = determinePM25Status(pm25);
            String quality = determinePM25Quality(pm25);
            saveSingleReading(deviceId, "PM2_5", pm25, "µg/m³", status, quality, location, timestamp);
            savedCount++;
        }

        // Save PM10
        if (payload.containsKey("pm10")) {
            Double pm10 = getDoubleValue(payload, "pm10");
            String status = determinePM10Status(pm10);
            String quality = determinePM10Quality(pm10);
            saveSingleReading(deviceId, "PM10", pm10, "µg/m³", status, quality, location, timestamp);
            savedCount++;
        }

        log.info("✅ Saved {} IAQ readings for device: {}", savedCount, deviceId);

        return Map.of(
                "success", true,
                "message", "IAQ data saved successfully",
                "deviceId", deviceId,
                "locationId", location.getId(),
                "locationName", location.getName(),
                "readingsStored", savedCount,
                "timestamp", timestamp
        );
    }

    /**
     * Get IAQ data for a specific device
     */
    public Map<String, Object> getIAQDataByDevice(String deviceId, Integer hours) {
        LocalDateTime startTime = LocalDateTime.now().minusHours(hours);

        Map<String, Object> result = new HashMap<>();
        result.put("deviceId", deviceId);
        result.put("hours", hours);

        List<String> iaqTypes = Arrays.asList("TEMPERATURE", "HUMIDITY", "CO2", "PM2_5", "PM10");
        Map<String, List<SensorDataDTO>> dataByType = new HashMap<>();

        for (String type : iaqTypes) {
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
     * Get latest IAQ readings for a device
     */
    public Map<String, Object> getLatestIAQData(String deviceId) {
        Map<String, Object> result = new HashMap<>();
        result.put("deviceId", deviceId);

        List<String> iaqTypes = Arrays.asList("TEMPERATURE", "HUMIDITY", "CO2", "PM2_5", "PM10");
        Map<String, SensorDataDTO> latestData = new HashMap<>();

        for (String type : iaqTypes) {
            SensorData latest = sensorRepository.findLatestByDeviceId(deviceId);
            if (latest != null && type.equals(latest.getType())) {
                latestData.put(type.toLowerCase(), convertToDTO(latest));
            }
        }

        result.put("latestReadings", latestData);
        return result;
    }

    /**
     * Get IAQ data by location
     */
    public Map<String, Object> getIAQDataByLocation(Long locationId, Integer hours) {
        LocalDateTime startTime = LocalDateTime.now().minusHours(hours);

        List<SensorData> data = sensorRepository.findRecentByLocation(locationId, startTime)
                .stream()
                .filter(d -> Arrays.asList("TEMPERATURE", "HUMIDITY", "CO2", "PM2_5", "PM10")
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
     * Get IAQ data by floor
     */
    public Map<String, Object> getIAQDataByFloor(Integer floor, Integer hours) {
        LocalDateTime startTime = LocalDateTime.now().minusHours(hours);

        List<SensorData> data = sensorRepository.findByFloor(floor)
                .stream()
                .filter(d -> d.getTimestamp().isAfter(startTime))
                .filter(d -> Arrays.asList("TEMPERATURE", "HUMIDITY", "CO2", "PM2_5", "PM10")
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
     * Get IAQ averages by floor
     */
    public Map<String, Object> getIAQAverages(Integer floor, Integer hours) {
        LocalDateTime startTime = LocalDateTime.now().minusHours(hours);

        Double avgTemp = sensorRepository.getAverageValueByTypeAndFloor("TEMPERATURE", floor, startTime);
        Double avgHumidity = sensorRepository.getAverageValueByTypeAndFloor("HUMIDITY", floor, startTime);
        Double avgCO2 = sensorRepository.getAverageValueByTypeAndFloor("CO2", floor, startTime);
        Double avgPM25 = sensorRepository.getAverageValueByTypeAndFloor("PM2_5", floor, startTime);
        Double avgPM10 = sensorRepository.getAverageValueByTypeAndFloor("PM10", floor, startTime);

        return Map.of(
                "floor", floor,
                "hours", hours,
                "averages", Map.of(
                        "temperature", avgTemp != null ? avgTemp : 0.0,
                        "humidity", avgHumidity != null ? avgHumidity : 0.0,
                        "co2", avgCO2 != null ? avgCO2 : 0.0,
                        "pm2_5", avgPM25 != null ? avgPM25 : 0.0,
                        "pm10", avgPM10 != null ? avgPM10 : 0.0
                )
        );
    }

    /**
     * Get all IAQ sensors status
     */
    public List<Map<String, Object>> getAllIAQSensorsStatus() {
        List<DeviceLocationMapping> mappings = deviceLocationMappingRepository
                .findActiveByDeviceType("IAQ_SENSOR");

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
                status.put("isOnline", isDeviceOnline(latest.getTimestamp()));
            } else {
                status.put("status", "OFFLINE");
                status.put("isOnline", false);
            }

            return status;
        }).collect(Collectors.toList());
    }

    /**
     * Get IAQ quality summary for a location
     */
    public Map<String, Object> getIAQQualitySummary(Long locationId) {
        LocalDateTime startTime = LocalDateTime.now().minusHours(1);

        List<SensorData> recentData = sensorRepository.findRecentByLocation(locationId, startTime);

        Map<String, String> qualityByType = new HashMap<>();
        for (String type : Arrays.asList("TEMPERATURE", "HUMIDITY", "CO2", "PM2_5", "PM10")) {
            recentData.stream()
                    .filter(d -> type.equals(d.getType()))
                    .findFirst()
                    .ifPresent(d -> qualityByType.put(type.toLowerCase(), d.getQuality()));
        }

        String overallQuality = determineOverallIAQQuality(recentData);

        return Map.of(
                "locationId", locationId,
                "qualityByParameter", qualityByType,
                "overallQuality", overallQuality
        );
    }

    /**
     * Get IAQ alerts (values exceeding thresholds)
     */
    public List<Map<String, Object>> getIAQAlerts(Integer hours) {
        LocalDateTime startTime = LocalDateTime.now().minusHours(hours);

        return sensorRepository.findRecentByType("CO2", startTime).stream()
                .filter(d -> "WARNING".equals(d.getStatus()) || "CRITICAL".equals(d.getStatus()))
                .map(d -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("deviceId", d.getDeviceId());
                    map.put("type", d.getType());
                    map.put("value", d.getValue());
                    map.put("status", d.getStatus());
                    map.put("quality", d.getQuality() != null ? d.getQuality() : "N/A");
                    map.put("timestamp", d.getTimestamp());
                    map.put("locationId", d.getLocation() != null ? d.getLocation().getId() : null);
                    map.put("locationName", d.getLocation() != null ? d.getLocation().getName() : "Unknown");
                    return map;
                })
                .collect(Collectors.toList());
    }


    /**
     * Get IAQ trends for visualization
     */
    public Map<String, Object> getIAQTrends(String deviceId, Integer hours, Integer intervalMinutes) {
        // This would require aggregation logic - simplified version
        LocalDateTime startTime = LocalDateTime.now().minusHours(hours);

        List<SensorData> data = sensorRepository.findRecentByDeviceId(deviceId, startTime);

        Map<String, List<Map<String, Object>>> trendsByType = new HashMap<>();

        for (String type : Arrays.asList("TEMPERATURE", "HUMIDITY", "CO2", "PM2_5", "PM10")) {
            List<Map<String, Object>> trends = data.stream()
                    .filter(d -> type.equals(d.getType()))
                    .map(d -> Map.of(
                            "timestamp", (Object) d.getTimestamp(),
                            "value", d.getValue(),
                            "quality", d.getQuality() != null ? d.getQuality() : "N/A"
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
     * Get IAQ comparison across multiple locations
     */
    public Map<String, Object> getIAQComparison(List<Long> locationIds, Integer hours) {
        LocalDateTime startTime = LocalDateTime.now().minusHours(hours);

        Map<String, Object> comparison = new HashMap<>();

        for (Long locationId : locationIds) {
            Location location = locationRepository.findById(locationId).orElse(null);
            if (location == null) continue;

            List<SensorData> data = sensorRepository.findRecentByLocation(locationId, startTime);

            Map<String, Double> averages = new HashMap<>();
            for (String type : Arrays.asList("TEMPERATURE", "HUMIDITY", "CO2", "PM2_5", "PM10")) {
                double avg = data.stream()
                        .filter(d -> type.equals(d.getType()))
                        .mapToDouble(SensorData::getValue)
                        .average()
                        .orElse(0.0);
                averages.put(type.toLowerCase(), avg);
            }

            comparison.put(location.getName(), averages);
        }

        return Map.of(
                "hours", hours,
                "comparison", comparison
        );
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

    private String determineOverallIAQQuality(List<SensorData> recentData) {
        // Simplified: return worst quality found
        List<String> qualities = recentData.stream()
                .map(SensorData::getQuality)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (qualities.contains("SEVERE")) return "SEVERE";
        if (qualities.contains("POOR")) return "POOR";
        if (qualities.contains("MODERATE")) return "MODERATE";
        if (qualities.contains("GOOD")) return "GOOD";
        return "EXCELLENT";
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

    private String determineCO2Status(double co2) {
        if (co2 > 2000) return "CRITICAL";
        if (co2 > 1000) return "WARNING";
        return "NORMAL";
    }

    private String determinePM25Status(double pm25) {
        if (pm25 > 55) return "CRITICAL";
        if (pm25 > 35) return "WARNING";
        return "NORMAL";
    }

    private String determinePM10Status(double pm10) {
        if (pm10 > 150) return "CRITICAL";
        if (pm10 > 50) return "WARNING";
        return "NORMAL";
    }

    // Quality determination methods
    private String determineTemperatureQuality(double temp) {
        if (temp >= 18 && temp <= 24) return "EXCELLENT";
        if (temp >= 15 && temp <= 28) return "GOOD";
        if (temp >= 12 && temp <= 32) return "MODERATE";
        if (temp >= 10 && temp <= 35) return "POOR";
        return "SEVERE";
    }

    private String determineHumidityQuality(double humidity) {
        if (humidity >= 40 && humidity <= 60) return "EXCELLENT";
        if (humidity >= 30 && humidity <= 70) return "GOOD";
        if (humidity >= 25 && humidity <= 75) return "MODERATE";
        if (humidity >= 20 && humidity <= 80) return "POOR";
        return "SEVERE";
    }

    private String determineCO2Quality(double co2) {
        if (co2 <= 400) return "EXCELLENT";
        if (co2 <= 1000) return "GOOD";
        if (co2 <= 1500) return "MODERATE";
        if (co2 <= 2000) return "POOR";
        return "SEVERE";
    }

    private String determinePM25Quality(double pm25) {
        if (pm25 <= 12) return "EXCELLENT";
        if (pm25 <= 35) return "GOOD";
        if (pm25 <= 55) return "MODERATE";
        if (pm25 <= 150) return "POOR";
        return "SEVERE";
    }

    private String determinePM10Quality(double pm10) {
        if (pm10 <= 50) return "EXCELLENT";
        if (pm10 <= 150) return "GOOD";
        if (pm10 <= 250) return "MODERATE";
        if (pm10 <= 350) return "POOR";
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