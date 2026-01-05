package com.bmsedge.iotsensor.controllers;

import com.bmsedge.iotsensor.service.IAQService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/iaq")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class IAQController {

    private final IAQService iaqService;

    /**
     * Save IAQ sensor data (multi-parameter)
     * Expected format from ThingsBoard:
     * {
     *   "device": "Popup IAQ Sensor",
     *   "timestamp": "15:05:10",
     *   "temp": 21.4,
     *   "humidity": 50.5,
     *   "co2": 711,
     *   "pm2_5": 101,
     *   "pm10": 108
     * }
     */
    @PostMapping("/data")
    public ResponseEntity<Map<String, Object>> saveIAQData(@RequestBody Map<String, Object> payload) {
        try {
            log.info("Received IAQ data: {}", payload);
            Map<String, Object> result = iaqService.saveIAQData(payload);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error saving IAQ data", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Error saving IAQ data: " + e.getMessage()
            ));
        }
    }

    /**
     * Get IAQ data for a specific device
     */
    @GetMapping("/device/{deviceId}")
    public ResponseEntity<Map<String, Object>> getIAQDataByDevice(
            @PathVariable String deviceId,
            @RequestParam(defaultValue = "24") Integer hours) {
        Map<String, Object> data = iaqService.getIAQDataByDevice(deviceId, hours);
        return ResponseEntity.ok(data);
    }

    /**
     * Get latest IAQ readings for a device
     */
    @GetMapping("/device/{deviceId}/latest")
    public ResponseEntity<Map<String, Object>> getLatestIAQData(@PathVariable String deviceId) {
        Map<String, Object> data = iaqService.getLatestIAQData(deviceId);
        return ResponseEntity.ok(data);
    }

    /**
     * Get IAQ data by location
     */
    @GetMapping("/location/{locationId}")
    public ResponseEntity<Map<String, Object>> getIAQDataByLocation(
            @PathVariable Long locationId,
            @RequestParam(defaultValue = "24") Integer hours) {
        Map<String, Object> data = iaqService.getIAQDataByLocation(locationId, hours);
        return ResponseEntity.ok(data);
    }

    /**
     * Get IAQ data by floor
     */
    @GetMapping("/floor/{floor}")
    public ResponseEntity<Map<String, Object>> getIAQDataByFloor(
            @PathVariable Integer floor,
            @RequestParam(defaultValue = "24") Integer hours) {
        Map<String, Object> data = iaqService.getIAQDataByFloor(floor, hours);
        return ResponseEntity.ok(data);
    }

    /**
     * Get IAQ averages by floor
     */
    @GetMapping("/floor/{floor}/averages")
    public ResponseEntity<Map<String, Object>> getIAQAverages(
            @PathVariable Integer floor,
            @RequestParam(defaultValue = "24") Integer hours) {
        Map<String, Object> averages = iaqService.getIAQAverages(floor, hours);
        return ResponseEntity.ok(averages);
    }

    /**
     * Get all IAQ sensors status
     */
    @GetMapping("/status")
    public ResponseEntity<List<Map<String, Object>>> getAllIAQSensorsStatus() {
        List<Map<String, Object>> status = iaqService.getAllIAQSensorsStatus();
        return ResponseEntity.ok(status);
    }

    /**
     * Get IAQ quality summary by location
     */
    @GetMapping("/location/{locationId}/quality")
    public ResponseEntity<Map<String, Object>> getIAQQualitySummary(@PathVariable Long locationId) {
        Map<String, Object> quality = iaqService.getIAQQualitySummary(locationId);
        return ResponseEntity.ok(quality);
    }

    /**
     * Get IAQ alerts (values exceeding thresholds)
     */
    @GetMapping("/alerts")
    public ResponseEntity<List<Map<String, Object>>> getIAQAlerts(
            @RequestParam(defaultValue = "24") Integer hours) {
        List<Map<String, Object>> alerts = iaqService.getIAQAlerts(hours);
        return ResponseEntity.ok(alerts);
    }

    /**
     * Get IAQ trend data for visualization
     */
    @GetMapping("/device/{deviceId}/trends")
    public ResponseEntity<Map<String, Object>> getIAQTrends(
            @PathVariable String deviceId,
            @RequestParam(defaultValue = "24") Integer hours,
            @RequestParam(defaultValue = "60") Integer intervalMinutes) {
        Map<String, Object> trends = iaqService.getIAQTrends(deviceId, hours, intervalMinutes);
        return ResponseEntity.ok(trends);
    }

    /**
     * Get IAQ comparison across multiple locations
     */
    @GetMapping("/comparison")
    public ResponseEntity<Map<String, Object>> getIAQComparison(
            @RequestParam List<Long> locationIds,
            @RequestParam(defaultValue = "24") Integer hours) {
        Map<String, Object> comparison = iaqService.getIAQComparison(locationIds, hours);
        return ResponseEntity.ok(comparison);
    }
}