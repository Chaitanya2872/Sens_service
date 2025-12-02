package com.bmsedge.iotsensor.controllers;

import com.bmsedge.iotsensor.dto.AlertDTO;
import com.bmsedge.iotsensor.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AlertController {

    private final AlertService alertService;

    // Create alert
    @PostMapping
    public ResponseEntity<AlertDTO> createAlert(@RequestBody AlertDTO dto) {
        AlertDTO created = alertService.createAlert(dto);
        return ResponseEntity.ok(created);
    }

    // Get all active alerts
    @GetMapping("/active")
    public ResponseEntity<List<AlertDTO>> getAllActiveAlerts() {
        List<AlertDTO> alerts = alertService.getAllActiveAlerts();
        return ResponseEntity.ok(alerts);
    }

    // Get active alerts by type
    @GetMapping("/active/type/{type}")
    public ResponseEntity<List<AlertDTO>> getActiveAlertsByType(@PathVariable String type) {
        List<AlertDTO> alerts = alertService.getActiveAlertsByType(type);
        return ResponseEntity.ok(alerts);
    }

    // Get alerts by floor
    @GetMapping("/floor/{floor}")
    public ResponseEntity<List<AlertDTO>> getAlertsByFloor(@PathVariable Integer floor) {
        List<AlertDTO> alerts = alertService.getAlertsByFloor(floor);
        return ResponseEntity.ok(alerts);
    }

    // Get alerts by date range
    @GetMapping("/range")
    public ResponseEntity<List<AlertDTO>> getAlertsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        List<AlertDTO> alerts = alertService.getAlertsByDateRange(startDate, endDate);
        return ResponseEntity.ok(alerts);
    }

    // Get alert by ID
    @GetMapping("/{id}")
    public ResponseEntity<AlertDTO> getAlertById(@PathVariable Long id) {
        AlertDTO alert = alertService.getAlertById(id);
        return ResponseEntity.ok(alert);
    }

    // Count active alerts by type
    @GetMapping("/count/type/{type}")
    public ResponseEntity<Map<String, Serializable>> countActiveAlertsByType(@PathVariable String type) {
        Long count = alertService.countActiveAlertsByType(type);
        return ResponseEntity.ok(Map.of("count", count, "type", type));
    }

    // Acknowledge alert
    @PatchMapping("/{id}/acknowledge")
    public ResponseEntity<AlertDTO> acknowledgeAlert(
            @PathVariable Long id,
            @RequestParam String acknowledgedBy) {
        AlertDTO acknowledged = alertService.acknowledgeAlert(id, acknowledgedBy);
        return ResponseEntity.ok(acknowledged);
    }

    // Resolve alert
    @PatchMapping("/{id}/resolve")
    public ResponseEntity<AlertDTO> resolveAlert(
            @PathVariable Long id,
            @RequestParam String resolvedBy) {
        AlertDTO resolved = alertService.resolveAlert(id, resolvedBy);
        return ResponseEntity.ok(resolved);
    }

    // Delete alert
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAlert(@PathVariable Long id) {
        alertService.deleteAlert(id);
        return ResponseEntity.noContent().build();
    }
}