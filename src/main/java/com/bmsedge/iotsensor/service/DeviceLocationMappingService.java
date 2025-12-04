package com.bmsedge.iotsensor.service;

import com.bmsedge.iotsensor.dto.DeviceLocationMappingDTO;
import com.bmsedge.iotsensor.model.DeviceLocationMapping;
import com.bmsedge.iotsensor.model.Location;
import com.bmsedge.iotsensor.repository.DeviceLocationMappingRepository;
import com.bmsedge.iotsensor.repository.LocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeviceLocationMappingService {

    private final DeviceLocationMappingRepository mappingRepository;
    private final LocationRepository locationRepository;

    @Transactional
    public DeviceLocationMappingDTO saveMapping(DeviceLocationMappingDTO dto) {
        Location location = locationRepository.findById(dto.getLocationId())
                .orElseThrow(() -> new RuntimeException("Location not found with id: " + dto.getLocationId()));

        DeviceLocationMapping mapping = DeviceLocationMapping.builder()
                .id(dto.getId())
                .deviceId(dto.getDeviceId())
                .location(location)
                .deviceType(dto.getDeviceType())
                .description(dto.getDescription())
                .active(dto.getActive() != null ? dto.getActive() : true)
                .build();

        DeviceLocationMapping saved = mappingRepository.save(mapping);
        return convertToDTO(saved);
    }

    public List<DeviceLocationMappingDTO> getAllMappings() {
        return mappingRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public DeviceLocationMappingDTO getMappingByDeviceId(String deviceId) {
        return mappingRepository.findByDeviceId(deviceId)
                .map(this::convertToDTO)
                .orElseThrow(() -> new RuntimeException("Mapping not found for device: " + deviceId));
    }

    public List<DeviceLocationMappingDTO> getMappingsByLocation(Long locationId) {
        return mappingRepository.findByLocationId(locationId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<DeviceLocationMappingDTO> getMappingsByDeviceType(String deviceType) {
        return mappingRepository.findByDeviceType(deviceType).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<DeviceLocationMappingDTO> getActiveMappings() {
        return mappingRepository.findByActive(true).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public DeviceLocationMappingDTO deactivateMapping(Long id) {
        DeviceLocationMapping mapping = mappingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Mapping not found with id: " + id));
        mapping.setActive(false);
        DeviceLocationMapping updated = mappingRepository.save(mapping);
        return convertToDTO(updated);
    }

    @Transactional
    public void deleteMapping(Long id) {
        mappingRepository.deleteById(id);
    }

    private DeviceLocationMappingDTO convertToDTO(DeviceLocationMapping mapping) {
        DeviceLocationMappingDTO dto = DeviceLocationMappingDTO.builder()
                .id(mapping.getId())
                .deviceId(mapping.getDeviceId())
                .deviceType(mapping.getDeviceType())
                .description(mapping.getDescription())
                .active(mapping.getActive())
                .build();

        if (mapping.getLocation() != null) {
            dto.setLocationId(mapping.getLocation().getId());
            dto.setLocationName(mapping.getLocation().getName());
            dto.setFloor(mapping.getLocation().getFloor());
            dto.setZone(mapping.getLocation().getZone());
        }

        return dto;
    }
}