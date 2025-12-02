package com.bmsedge.iotsensor.controllers;

import com.bmsedge.iotsensor.dto.LocationDTO;
import com.bmsedge.iotsensor.service.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class LocationController {

    private final LocationService locationService;

    // Create or update location
    @PostMapping
    public ResponseEntity<LocationDTO> createLocation(@RequestBody LocationDTO dto) {
        LocationDTO saved = locationService.saveLocation(dto);
        return ResponseEntity.ok(saved);
    }

    // Update location
    @PutMapping("/{id}")
    public ResponseEntity<LocationDTO> updateLocation(@PathVariable Long id, @RequestBody LocationDTO dto) {
        dto.setId(id);
        LocationDTO updated = locationService.saveLocation(dto);
        return ResponseEntity.ok(updated);
    }

    // Get all locations
    @GetMapping
    public ResponseEntity<List<LocationDTO>> getAllLocations() {
        List<LocationDTO> locations = locationService.getAllLocations();
        return ResponseEntity.ok(locations);
    }

    // Get location by ID
    @GetMapping("/{id}")
    public ResponseEntity<LocationDTO> getLocationById(@PathVariable Long id) {
        LocationDTO location = locationService.getLocationById(id);
        return ResponseEntity.ok(location);
    }

    // Get locations by type (IAQ, CAFETERIA, ENERGY, RESTROOM)
    @GetMapping("/type/{type}")
    public ResponseEntity<List<LocationDTO>> getLocationsByType(@PathVariable String type) {
        List<LocationDTO> locations = locationService.getLocationsByType(type);
        return ResponseEntity.ok(locations);
    }

    // Get locations by floor
    @GetMapping("/floor/{floor}")
    public ResponseEntity<List<LocationDTO>> getLocationsByFloor(@PathVariable Integer floor) {
        List<LocationDTO> locations = locationService.getLocationsByFloor(floor);
        return ResponseEntity.ok(locations);
    }

    // Get locations by floor and zone
    @GetMapping("/floor/{floor}/zone/{zone}")
    public ResponseEntity<List<LocationDTO>> getLocationsByFloorAndZone(
            @PathVariable Integer floor,
            @PathVariable String zone) {
        List<LocationDTO> locations = locationService.getLocationsByFloorAndZone(floor, zone);
        return ResponseEntity.ok(locations);
    }

    // Get active locations only
    @GetMapping("/active")
    public ResponseEntity<List<LocationDTO>> getActiveLocations() {
        List<LocationDTO> locations = locationService.getActiveLocations();
        return ResponseEntity.ok(locations);
    }

    // Get all active floors
    @GetMapping("/floors")
    public ResponseEntity<List<Integer>> getAllActiveFloors() {
        List<Integer> floors = locationService.getAllActiveFloors();
        return ResponseEntity.ok(floors);
    }

    // Get active locations by type
    @GetMapping("/active/type/{type}")
    public ResponseEntity<List<LocationDTO>> getActiveLocationsByType(@PathVariable String type) {
        List<LocationDTO> locations = locationService.getActiveLocationsByType(type);
        return ResponseEntity.ok(locations);
    }

    // Deactivate location (soft delete)
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<LocationDTO> deactivateLocation(@PathVariable Long id) {
        LocationDTO deactivated = locationService.deactivateLocation(id);
        return ResponseEntity.ok(deactivated);
    }

    // Delete location
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLocation(@PathVariable Long id) {
        locationService.deleteLocation(id);
        return ResponseEntity.noContent().build();
    }
}