package com.bmsedge.iotsensor.controllers;

import com.bmsedge.iotsensor.service.AlertService;
import com.bmsedge.iotsensor.service.LocationService;
import com.bmsedge.iotsensor.service.SensorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DashboardController {

    private final SensorService sensorService;
    private final AlertService alertService;
    private final LocationService locationService;

    // Get overview statistics for all modules
    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getOverviewStats() {
        Map<String, Object> overview = new HashMap<>();

        // Cafeteria stats
        Map<String, Object> cafeteria = new HashMap<>();
        cafeteria.put("activeCounters", locationService.getActiveLocationsByType("CAFETERIA").size());
        cafeteria.put("alerts", alertService.countActiveAlertsByType("CAFETERIA"));
        overview.put("cafeteria", cafeteria);

        // IAQ stats
        Map<String, Object> iaq = new HashMap<>();
        iaq.put("totalSensors", locationService.getActiveLocationsByType("IAQ").size());
        iaq.put("alerts", alertService.countActiveAlertsByType("IAQ"));
        overview.put("iaq", iaq);

        // Restroom stats
        Map<String, Object> restroom = new HashMap<>();
        restroom.put("totalRestrooms", locationService.getActiveLocationsByType("RESTROOM").size());
        restroom.put("alerts", alertService.countActiveAlertsByType("RESTROOM"));
        overview.put("restroom", restroom);

        // Energy stats
        Map<String, Object> energy = new HashMap<>();
        energy.put("activeMeters", locationService.getActiveLocationsByType("ENERGY").size());
        energy.put("alerts", alertService.countActiveAlertsByType("ENERGY"));
        overview.put("energy", energy);

        // Total active alerts
        overview.put("totalActiveAlerts", alertService.getAllActiveAlerts().size());

        return ResponseEntity.ok(overview);
    }

    // Get stats by floor
    @GetMapping("/floor/{floor}")
    public ResponseEntity<Map<String, Object>> getFloorStats(@PathVariable Integer floor) {
        Map<String, Object> floorStats = new HashMap<>();

        floorStats.put("floor", floor);
        floorStats.put("locations", locationService.getLocationsByFloor(floor).size());
        floorStats.put("sensors", sensorService.getSensorDataByFloor(floor).size());
        floorStats.put("alerts", alertService.getAlertsByFloor(floor).size());

        // IAQ averages
        Double avgTemp = sensorService.getAverageValue("TEMPERATURE", floor, 24);
        Double avgCO2 = sensorService.getAverageValue("CO2", floor, 24);
        Double avgHumidity = sensorService.getAverageValue("HUMIDITY", floor, 24);

        Map<String, Object> iaqAverages = new HashMap<>();
        iaqAverages.put("temperature", avgTemp != null ? avgTemp : 0.0);
        iaqAverages.put("co2", avgCO2 != null ? avgCO2 : 0.0);
        iaqAverages.put("humidity", avgHumidity != null ? avgHumidity : 0.0);
        floorStats.put("iaqAverages", iaqAverages);

        return ResponseEntity.ok(floorStats);
    }

    // Get stats by type
    @GetMapping("/type/{type}")
    public ResponseEntity<Map<String, Object>> getTypeStats(@PathVariable String type) {
        Map<String, Object> typeStats = new HashMap<>();

        typeStats.put("type", type);
        typeStats.put("locations", locationService.getActiveLocationsByType(type).size());
        typeStats.put("activeAlerts", alertService.countActiveAlertsByType(type));
        typeStats.put("recentData", sensorService.getRecentSensorDataByType(type, 1).size());

        return ResponseEntity.ok(typeStats);
    }

    // Health check endpoint
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getSystemHealth() {
        Map<String, Object> health = new HashMap<>();

        health.put("status", "online");
        health.put("totalLocations", locationService.getActiveLocations().size());
        health.put("activeAlerts", alertService.getAllActiveAlerts().size());
        health.put("floors", locationService.getAllActiveFloors());

        return ResponseEntity.ok(health);
    }
}