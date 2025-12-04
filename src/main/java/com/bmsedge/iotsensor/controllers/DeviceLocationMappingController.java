package com.bmsedge.iotsensor.controllers;

import com.bmsedge.iotsensor.dto.DeviceLocationMappingDTO;
import com.bmsedge.iotsensor.service.DeviceLocationMappingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/device-mappings")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DeviceLocationMappingController {

    private final DeviceLocationMappingService mappingService;

    // Create or update device mapping
    @PostMapping
    public ResponseEntity<DeviceLocationMappingDTO> createMapping(@RequestBody DeviceLocationMappingDTO dto) {
        DeviceLocationMappingDTO saved = mappingService.saveMapping(dto);
        return ResponseEntity.ok(saved);
    }

    // Get all mappings
    @GetMapping
    public ResponseEntity<List<DeviceLocationMappingDTO>> getAllMappings() {
        List<DeviceLocationMappingDTO> mappings = mappingService.getAllMappings();
        return ResponseEntity.ok(mappings);
    }

    // Get mapping by device ID
    @GetMapping("/device/{deviceId}")
    public ResponseEntity<DeviceLocationMappingDTO> getMappingByDeviceId(@PathVariable String deviceId) {
        DeviceLocationMappingDTO mapping = mappingService.getMappingByDeviceId(deviceId);
        return ResponseEntity.ok(mapping);
    }

    // Get mappings by location
    @GetMapping("/location/{locationId}")
    public ResponseEntity<List<DeviceLocationMappingDTO>> getMappingsByLocation(@PathVariable Long locationId) {
        List<DeviceLocationMappingDTO> mappings = mappingService.getMappingsByLocation(locationId);
        return ResponseEntity.ok(mappings);
    }

    // Get mappings by device type
    @GetMapping("/type/{deviceType}")
    public ResponseEntity<List<DeviceLocationMappingDTO>> getMappingsByDeviceType(@PathVariable String deviceType) {
        List<DeviceLocationMappingDTO> mappings = mappingService.getMappingsByDeviceType(deviceType);
        return ResponseEntity.ok(mappings);
    }

    // Get active mappings only
    @GetMapping("/active")
    public ResponseEntity<List<DeviceLocationMappingDTO>> getActiveMappings() {
        List<DeviceLocationMappingDTO> mappings = mappingService.getActiveMappings();
        return ResponseEntity.ok(mappings);
    }

    // Update mapping
    @PutMapping("/{id}")
    public ResponseEntity<DeviceLocationMappingDTO> updateMapping(
            @PathVariable Long id,
            @RequestBody DeviceLocationMappingDTO dto) {
        dto.setId(id);
        DeviceLocationMappingDTO updated = mappingService.saveMapping(dto);
        return ResponseEntity.ok(updated);
    }

    // Deactivate mapping
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<DeviceLocationMappingDTO> deactivateMapping(@PathVariable Long id) {
        DeviceLocationMappingDTO deactivated = mappingService.deactivateMapping(id);
        return ResponseEntity.ok(deactivated);
    }

    // Delete mapping
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMapping(@PathVariable Long id) {
        mappingService.deleteMapping(id);
        return ResponseEntity.noContent().build();
    }
}