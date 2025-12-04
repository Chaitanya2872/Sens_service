package com.bmsedge.iotsensor.service;

import com.bmsedge.iotsensor.dto.SensorDataDTO;
import com.bmsedge.iotsensor.model.Location;
import com.bmsedge.iotsensor.model.SensorData;
import com.bmsedge.iotsensor.repository.LocationRepository;
import com.bmsedge.iotsensor.repository.SensorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SensorService {

    private final SensorRepository sensorRepository;
    private final LocationRepository locationRepository;

    // Save sensor data with location
    @Transactional
    public SensorDataDTO saveSensorData(SensorDataDTO dto) {
        // Check if this is odor sensor data from ThingsBoard
        if (isOdorSensorData(dto)) {
            return saveOdorSensorData(dto);
        }

        // Standard sensor data processing
        SensorData sensorData = SensorData.builder()
                .deviceId(dto.getDeviceId())
                .type(dto.getType())
                .value(dto.getValue())
                .unit(dto.getUnit())
                .status(dto.getStatus() != null ? dto.getStatus() : "NORMAL")
                .quality(dto.getQuality())
                .threshold(dto.getThreshold())
                .timestamp(LocalDateTime.now())
                .build();

        // Set location if provided
        if (dto.getLocationId() != null) {
            Location location = locationRepository.findById(dto.getLocationId())
                    .orElseThrow(() -> new RuntimeException("Location not found"));
            sensorData.setLocation(location);
        }

        SensorData saved = sensorRepository.save(sensorData);
        return convertToDTO(saved);
    }

    // Check if incoming data is from odor sensor
    private boolean isOdorSensorData(SensorDataDTO dto) {
        return dto.getDevice() != null &&
                (dto.getOdornh3() != null || dto.getOdorh2s_high_precision() != null);
    }

    // Save odor sensor data by converting to multiple sensor records
    @Transactional
    private SensorDataDTO saveOdorSensorData(SensorDataDTO dto) {
        log.info("Processing odor sensor data from device: {}", dto.getDevice());

        String deviceId = dto.getDevice();
        LocalDateTime timestamp = parseTimestamp(String.valueOf(dto.getTimestamp()));
        Location location = null;

        // Get location if provided
        if (dto.getLocationId() != null) {
            location = locationRepository.findById(dto.getLocationId())
                    .orElse(null);
        }

        // Save Battery data
        if (dto.getOdorbattery() != null) {
            saveSingleReading(deviceId, "BATTERY", dto.getOdorbattery(), "%",
                    "NORMAL", null, location, timestamp);
        }

        // Save Temperature data
        if (dto.getOdortemperature() != null) {
            String tempStatus = determineTemperatureStatus(dto.getOdortemperature());
            saveSingleReading(deviceId, "TEMPERATURE", dto.getOdortemperature(), "°C",
                    tempStatus, null, location, timestamp);
        }

        // Save Humidity data
        if (dto.getOdorhumidity() != null) {
            String humidityStatus = determineHumidityStatus(dto.getOdorhumidity());
            saveSingleReading(deviceId, "HUMIDITY", dto.getOdorhumidity(), "%",
                    humidityStatus, null, location, timestamp);
        }

        // Save NH3 (Ammonia) data
        if (dto.getOdornh3() != null) {
            String nh3Status = determineNH3Status(dto.getOdornh3());
            String nh3Quality = determineNH3Quality(dto.getOdornh3());
            saveSingleReading(deviceId, "NH3", dto.getOdornh3(), "ppm",
                    nh3Status, nh3Quality, location, timestamp);
        }

        // Save H2S (Hydrogen Sulfide) data
        if (dto.getOdorh2s_high_precision() != null) {
            String h2sStatus = determineH2SStatus(dto.getOdorh2s_high_precision());
            String h2sQuality = determineH2SQuality(dto.getOdorh2s_high_precision());
            saveSingleReading(deviceId, "H2S", dto.getOdorh2s_high_precision(), "ppm",
                    h2sStatus, h2sQuality, location, timestamp);
        }

        // Calculate and save overall Odor Index
        if (dto.getOdornh3() != null && dto.getOdorh2s_high_precision() != null) {
            double odorIndex = calculateOdorIndex(dto.getOdornh3(), dto.getOdorh2s_high_precision());
            String odorStatus = determineOdorStatus(odorIndex);
            String odorQuality = determineOdorQuality(odorIndex);
            SensorData odorData = saveSingleReading(deviceId, "ODOR_INDEX", odorIndex, "index",
                    odorStatus, odorQuality, location, timestamp);

            // Return the odor index record as the response
            return convertToDTO(odorData);
        }

        // If no odor index calculated, return a basic response
        return SensorDataDTO.builder()
                .deviceId(deviceId)
                .type("ODOR")
                .status("NORMAL")
                .timestamp(timestamp)
                .build();
    }

    // Helper method to save a single sensor reading
    private SensorData saveSingleReading(String deviceId, String type, Double value, String unit,
                                         String status, String quality, Location location,
                                         LocalDateTime timestamp) {
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

        return sensorRepository.save(sensorData);
    }

    // === Odor Calculation Methods ===

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
        if (temp < 15 || temp > 30) return "WARNING";
        if (temp < 10 || temp > 35) return "CRITICAL";
        return "NORMAL";
    }

    private String determineHumidityStatus(double humidity) {
        if (humidity < 30 || humidity > 70) return "WARNING";
        if (humidity < 20 || humidity > 80) return "CRITICAL";
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

    // Parse timestamp from ThingsBoard format
    private LocalDateTime parseTimestamp(String timestamp) {
        if (timestamp == null) {
            return LocalDateTime.now();
        }

        try {
            // Try parsing HH:mm:ss format
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
            return LocalDateTime.of(
                    LocalDateTime.now().toLocalDate(),
                    java.time.LocalTime.parse(timestamp, timeFormatter)
            );
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse timestamp: {}, using current time", timestamp);
            return LocalDateTime.now();
        }
    }

    // Get all sensor data for a device
    public List<SensorDataDTO> getSensorDataByDevice(String deviceId) {
        return sensorRepository.findByDeviceId(deviceId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // Get sensor data by location
    public List<SensorDataDTO> getSensorDataByLocation(Long locationId) {
        return sensorRepository.findByLocationId(locationId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // Get sensor data by floor
    public List<SensorDataDTO> getSensorDataByFloor(Integer floor) {
        return sensorRepository.findByFloor(floor).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // Get sensor data by floor and type
    public List<SensorDataDTO> getSensorDataByFloorAndType(Integer floor, String type) {
        return sensorRepository.findByFloorAndType(floor, type).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // Get sensor data by type
    public List<SensorDataDTO> getSensorDataByType(String type) {
        return sensorRepository.findByType(type).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // Get recent sensor data for a device (last 24 hours)
    public List<SensorDataDTO> getRecentSensorData(String deviceId, Integer hours) {
        LocalDateTime startTime = LocalDateTime.now().minusHours(hours);
        return sensorRepository.findRecentByDeviceId(deviceId, startTime).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // Get recent sensor data by type
    public List<SensorDataDTO> getRecentSensorDataByType(String type, Integer hours) {
        LocalDateTime startTime = LocalDateTime.now().minusHours(hours);
        return sensorRepository.findRecentByType(type, startTime).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // Get recent sensor data by location
    public List<SensorDataDTO> getRecentSensorDataByLocation(Long locationId, Integer hours) {
        LocalDateTime startTime = LocalDateTime.now().minusHours(hours);
        return sensorRepository.findRecentByLocation(locationId, startTime).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // Get average value by type and floor
    public Double getAverageValue(String type, Integer floor, Integer hours) {
        LocalDateTime startTime = LocalDateTime.now().minusHours(hours);
        return sensorRepository.getAverageValueByTypeAndFloor(type, floor, startTime);
    }

    // Get latest sensor data for all devices
    public List<SensorDataDTO> getLatestForAllDevices() {
        return sensorRepository.findLatestForAllDevices().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // Get latest sensor data for a device
    public SensorDataDTO getLatestByDeviceId(String deviceId) {
        SensorData data = sensorRepository.findLatestByDeviceId(deviceId);
        return data != null ? convertToDTO(data) : null;
    }

    // Helper: Convert Entity to DTO
    private SensorDataDTO convertToDTO(SensorData sensorData) {
        SensorDataDTO dto = SensorDataDTO.builder()
                .id(sensorData.getId())
                .deviceId(sensorData.getDeviceId())
                .type(sensorData.getType())
                .value(sensorData.getValue())
                .unit(sensorData.getUnit())
                .status(sensorData.getStatus())
                .quality(sensorData.getQuality())
                .threshold(sensorData.getThreshold())
                .timestamp(sensorData.getTimestamp())
                .build();

        // Add location info if available
        if (sensorData.getLocation() != null) {
            dto.setLocationId(sensorData.getLocation().getId());
            dto.setLocationName(sensorData.getLocation().getName());
            dto.setFloor(sensorData.getLocation().getFloor());
            dto.setZone(sensorData.getLocation().getZone());
            dto.setBuilding(sensorData.getLocation().getBuilding());
        }

        return dto;
    }
}