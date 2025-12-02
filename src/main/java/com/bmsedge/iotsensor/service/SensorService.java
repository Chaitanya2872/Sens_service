package com.bmsedge.iotsensor.service;

import com.bmsedge.iotsensor.dto.SensorDataDTO;
import com.bmsedge.iotsensor.model.Location;
import com.bmsedge.iotsensor.model.SensorData;
import com.bmsedge.iotsensor.repository.LocationRepository;
import com.bmsedge.iotsensor.repository.SensorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SensorService {

    private final SensorRepository sensorRepository;
    private final LocationRepository locationRepository;

    // Save sensor data with location
    @Transactional
    public SensorDataDTO saveSensorData(SensorDataDTO dto) {
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