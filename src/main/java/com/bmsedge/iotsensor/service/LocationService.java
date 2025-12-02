package com.bmsedge.iotsensor.service;

import com.bmsedge.iotsensor.dto.LocationDTO;
import com.bmsedge.iotsensor.model.Location;
import com.bmsedge.iotsensor.repository.LocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository locationRepository;

    // Create or Update Location
    @Transactional
    public LocationDTO saveLocation(LocationDTO dto) {
        Location location = Location.builder()
                .id(dto.getId())
                .name(dto.getName())
                .type(dto.getType())
                .floor(dto.getFloor())
                .zone(dto.getZone())
                .building(dto.getBuilding())
                .description(dto.getDescription())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .active(dto.getActive() != null ? dto.getActive() : true)
                .build();

        Location saved = locationRepository.save(location);
        return convertToDTO(saved);
    }

    // Get all locations
    public List<LocationDTO> getAllLocations() {
        return locationRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // Get location by ID
    public LocationDTO getLocationById(Long id) {
        return locationRepository.findById(id)
                .map(this::convertToDTO)
                .orElseThrow(() -> new RuntimeException("Location not found with id: " + id));
    }

    // Get locations by type (IAQ, CAFETERIA, ENERGY, RESTROOM)
    public List<LocationDTO> getLocationsByType(String type) {
        return locationRepository.findByType(type).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // Get locations by floor
    public List<LocationDTO> getLocationsByFloor(Integer floor) {
        return locationRepository.findByFloor(floor).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // Get locations by floor and zone
    public List<LocationDTO> getLocationsByFloorAndZone(Integer floor, String zone) {
        return locationRepository.findByFloorAndZone(floor, zone).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // Get active locations only
    public List<LocationDTO> getActiveLocations() {
        return locationRepository.findByActive(true).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // Get all active floors
    public List<Integer> getAllActiveFloors() {
        return locationRepository.findAllActiveFloors();
    }

    // Get active locations by type
    public List<LocationDTO> getActiveLocationsByType(String type) {
        return locationRepository.findActiveByType(type).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // Delete location
    @Transactional
    public void deleteLocation(Long id) {
        locationRepository.deleteById(id);
    }

    // Deactivate location (soft delete)
    @Transactional
    public LocationDTO deactivateLocation(Long id) {
        Location location = locationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Location not found with id: " + id));
        location.setActive(false);
        Location updated = locationRepository.save(location);
        return convertToDTO(updated);
    }

    // Helper: Convert Entity to DTO
    private LocationDTO convertToDTO(Location location) {
        return LocationDTO.builder()
                .id(location.getId())
                .name(location.getName())
                .type(location.getType())
                .floor(location.getFloor())
                .zone(location.getZone())
                .building(location.getBuilding())
                .description(location.getDescription())
                .latitude(location.getLatitude())
                .longitude(location.getLongitude())
                .active(location.getActive())
                .build();
    }
}