package com.bmsedge.iotsensor.controllers;

import com.bmsedge.iotsensor.dto.SensorDataDTO;
import com.bmsedge.iotsensor.service.SensorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sensors")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SensorController {

    private final SensorService sensorService;

    // === POST FROM THINGSBOARD ===
    @PostMapping("/data")
    public ResponseEntity<SensorDataDTO> saveSensorData(@RequestBody SensorDataDTO dto) {
        SensorDataDTO saved = sensorService.saveSensorData(dto);
        return ResponseEntity.ok(saved);
    }

    // Get sensor data by device ID
    @GetMapping("/device/{deviceId}")
    public ResponseEntity<List<SensorDataDTO>> getSensorDataByDevice(@PathVariable String deviceId) {
        List<SensorDataDTO> data = sensorService.getSensorDataByDevice(deviceId);
        return ResponseEntity.ok(data);
    }

    // Get latest sensor data for a device
    @GetMapping("/device/{deviceId}/latest")
    public ResponseEntity<SensorDataDTO> getLatestByDeviceId(@PathVariable String deviceId) {
        SensorDataDTO data = sensorService.getLatestByDeviceId(deviceId);
        return ResponseEntity.ok(data);
    }

    // Get sensor data by location
    @GetMapping("/location/{locationId}")
    public ResponseEntity<List<SensorDataDTO>> getSensorDataByLocation(@PathVariable Long locationId) {
        List<SensorDataDTO> data = sensorService.getSensorDataByLocation(locationId);
        return ResponseEntity.ok(data);
    }

    // Get sensor data by floor
    @GetMapping("/floor/{floor}")
    public ResponseEntity<List<SensorDataDTO>> getSensorDataByFloor(@PathVariable Integer floor) {
        List<SensorDataDTO> data = sensorService.getSensorDataByFloor(floor);
        return ResponseEntity.ok(data);
    }

    // Get sensor data by floor and type
    @GetMapping("/floor/{floor}/type/{type}")
    public ResponseEntity<List<SensorDataDTO>> getSensorDataByFloorAndType(
            @PathVariable Integer floor,
            @PathVariable String type) {
        List<SensorDataDTO> data = sensorService.getSensorDataByFloorAndType(floor, type);
        return ResponseEntity.ok(data);
    }

    // Get sensor data by type
    @GetMapping("/type/{type}")
    public ResponseEntity<List<SensorDataDTO>> getSensorDataByType(@PathVariable String type) {
        List<SensorDataDTO> data = sensorService.getSensorDataByType(type);
        return ResponseEntity.ok(data);
    }

    // Get recent sensor data for a device (last N hours)
    @GetMapping("/device/{deviceId}/recent")
    public ResponseEntity<List<SensorDataDTO>> getRecentSensorData(
            @PathVariable String deviceId,
            @RequestParam(defaultValue = "24") Integer hours) {
        List<SensorDataDTO> data = sensorService.getRecentSensorData(deviceId, hours);
        return ResponseEntity.ok(data);
    }

    // Get recent sensor data by type
    @GetMapping("/type/{type}/recent")
    public ResponseEntity<List<SensorDataDTO>> getRecentSensorDataByType(
            @PathVariable String type,
            @RequestParam(defaultValue = "24") Integer hours) {
        List<SensorDataDTO> data = sensorService.getRecentSensorDataByType(type, hours);
        return ResponseEntity.ok(data);
    }

    // Get recent sensor data by location
    @GetMapping("/location/{locationId}/recent")
    public ResponseEntity<List<SensorDataDTO>> getRecentSensorDataByLocation(
            @PathVariable Long locationId,
            @RequestParam(defaultValue = "24") Integer hours) {
        List<SensorDataDTO> data = sensorService.getRecentSensorDataByLocation(locationId, hours);
        return ResponseEntity.ok(data);
    }

    // Get average value by type and floor
    @GetMapping("/average")
    public ResponseEntity<Map<String, Object>> getAverageValue(
            @RequestParam String type,
            @RequestParam Integer floor,
            @RequestParam(defaultValue = "24") Integer hours) {
        Double average = sensorService.getAverageValue(type, floor, hours);
        return ResponseEntity.ok(Map.of(
                "type", type,
                "floor", floor,
                "hours", hours,
                "average", average != null ? average : 0.0
        ));
    }

    // Get latest sensor data for all devices
    @GetMapping("/latest/all")
    public ResponseEntity<List<SensorDataDTO>> getLatestForAllDevices() {
        List<SensorDataDTO> data = sensorService.getLatestForAllDevices();
        return ResponseEntity.ok(data);
    }
}