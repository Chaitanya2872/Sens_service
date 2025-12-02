package com.bmsedge.iotsensor.service;

import com.bmsedge.iotsensor.dto.AlertDTO;
import com.bmsedge.iotsensor.model.Alert;
import com.bmsedge.iotsensor.model.Location;
import com.bmsedge.iotsensor.model.SensorData;
import com.bmsedge.iotsensor.repository.AlertRepository;
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
public class AlertService {

    private final AlertRepository alertRepository;
    private final LocationRepository locationRepository;
    private final SensorRepository sensorRepository;

    // Create alert
    @Transactional
    public AlertDTO createAlert(AlertDTO dto) {
        Alert alert = Alert.builder()
                .title(dto.getTitle())
                .message(dto.getMessage())
                .severity(dto.getSeverity())
                .type(dto.getType())
                .status("ACTIVE")
                .build();

        // Set location if provided
        if (dto.getLocationId() != null) {
            Location location = locationRepository.findById(dto.getLocationId())
                    .orElseThrow(() -> new RuntimeException("Location not found"));
            alert.setLocation(location);
        }

        // Set sensor data if provided
        if (dto.getSensorDataId() != null) {
            SensorData sensorData = sensorRepository.findById(dto.getSensorDataId())
                    .orElseThrow(() -> new RuntimeException("Sensor data not found"));
            alert.setSensorData(sensorData);
        }

        Alert saved = alertRepository.save(alert);
        return convertToDTO(saved);
    }

    // Get all active alerts
    public List<AlertDTO> getAllActiveAlerts() {
        return alertRepository.findAllActiveAlerts().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // Get active alerts by type
    public List<AlertDTO> getActiveAlertsByType(String type) {
        return alertRepository.findActiveAlertsByType(type).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // Get alerts by floor
    public List<AlertDTO> getAlertsByFloor(Integer floor) {
        return alertRepository.findActiveAlertsByFloor(floor).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // Get alerts by date range
    public List<AlertDTO> getAlertsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return alertRepository.findAlertsByDateRange(startDate, endDate).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // Count active alerts by type
    public Long countActiveAlertsByType(String type) {
        return alertRepository.countActiveAlertsByType(type);
    }

    // Acknowledge alert
    @Transactional
    public AlertDTO acknowledgeAlert(Long alertId, String acknowledgedBy) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new RuntimeException("Alert not found"));

        alert.setStatus("ACKNOWLEDGED");
        alert.setAcknowledgedAt(LocalDateTime.now());
        alert.setAcknowledgedBy(acknowledgedBy);

        Alert updated = alertRepository.save(alert);
        return convertToDTO(updated);
    }

    // Resolve alert
    @Transactional
    public AlertDTO resolveAlert(Long alertId, String resolvedBy) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new RuntimeException("Alert not found"));

        alert.setStatus("RESOLVED");
        alert.setResolvedAt(LocalDateTime.now());
        alert.setResolvedBy(resolvedBy);

        Alert updated = alertRepository.save(alert);
        return convertToDTO(updated);
    }

    // Get alert by ID
    public AlertDTO getAlertById(Long id) {
        return alertRepository.findById(id)
                .map(this::convertToDTO)
                .orElseThrow(() -> new RuntimeException("Alert not found"));
    }

    // Delete alert
    @Transactional
    public void deleteAlert(Long id) {
        alertRepository.deleteById(id);
    }

    // Helper: Convert Entity to DTO
    private AlertDTO convertToDTO(Alert alert) {
        AlertDTO dto = AlertDTO.builder()
                .id(alert.getId())
                .title(alert.getTitle())
                .message(alert.getMessage())
                .severity(alert.getSeverity())
                .type(alert.getType())
                .status(alert.getStatus())
                .createdAt(alert.getCreatedAt())
                .acknowledgedAt(alert.getAcknowledgedAt())
                .resolvedAt(alert.getResolvedAt())
                .build();

        // Add location info if available
        if (alert.getLocation() != null) {
            dto.setLocationId(alert.getLocation().getId());
            dto.setLocationName(alert.getLocation().getName());
            dto.setFloor(alert.getLocation().getFloor());
            dto.setZone(alert.getLocation().getZone());
        }

        // Add sensor info if available
        if (alert.getSensorData() != null) {
            dto.setSensorDataId(alert.getSensorData().getId());
            dto.setDeviceId(alert.getSensorData().getDeviceId());
            dto.setValue(alert.getSensorData().getValue());
            dto.setUnit(alert.getSensorData().getUnit());
        }

        return dto;
    }
}