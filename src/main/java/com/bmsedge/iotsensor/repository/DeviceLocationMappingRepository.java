package com.bmsedge.iotsensor.repository;

import com.bmsedge.iotsensor.model.DeviceLocationMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeviceLocationMappingRepository extends JpaRepository<DeviceLocationMapping, Long> {

    Optional<DeviceLocationMapping> findByDeviceId(String deviceId);

    List<DeviceLocationMapping> findByLocationId(Long locationId);

    List<DeviceLocationMapping> findByDeviceType(String deviceType);

    List<DeviceLocationMapping> findByActive(Boolean active);

    @Query("SELECT d FROM DeviceLocationMapping d WHERE d.deviceType = :deviceType AND d.active = true")
    List<DeviceLocationMapping> findActiveByDeviceType(@Param("deviceType") String deviceType);

    @Query("SELECT d FROM DeviceLocationMapping d WHERE d.location.id = :locationId AND d.active = true")
    List<DeviceLocationMapping> findActiveByLocationId(@Param("locationId") Long locationId);
}