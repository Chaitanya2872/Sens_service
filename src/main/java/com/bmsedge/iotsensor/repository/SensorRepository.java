package com.bmsedge.iotsensor.repository;

import com.bmsedge.iotsensor.model.SensorData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SensorRepository extends JpaRepository<SensorData, Long> {

    List<SensorData> findByDeviceId(String deviceId);

    List<SensorData> findByType(String type);

    List<SensorData> findByLocationId(Long locationId);

    List<SensorData> findByStatus(String status);

    @Query("SELECT s FROM SensorData s WHERE s.location.floor = :floor ORDER BY s.timestamp DESC")
    List<SensorData> findByFloor(@Param("floor") Integer floor);

    @Query("SELECT s FROM SensorData s WHERE s.location.floor = :floor AND s.type = :type ORDER BY s.timestamp DESC")
    List<SensorData> findByFloorAndType(@Param("floor") Integer floor, @Param("type") String type);

    @Query("SELECT s FROM SensorData s WHERE s.deviceId = :deviceId AND s.timestamp >= :startTime ORDER BY s.timestamp DESC")
    List<SensorData> findRecentByDeviceId(@Param("deviceId") String deviceId,
                                          @Param("startTime") LocalDateTime startTime);

    @Query("SELECT s FROM SensorData s WHERE s.type = :type AND s.timestamp >= :startTime ORDER BY s.timestamp DESC")
    List<SensorData> findRecentByType(@Param("type") String type,
                                      @Param("startTime") LocalDateTime startTime);

    @Query("SELECT s FROM SensorData s WHERE s.location.id = :locationId AND s.timestamp >= :startTime ORDER BY s.timestamp DESC")
    List<SensorData> findRecentByLocation(@Param("locationId") Long locationId,
                                          @Param("startTime") LocalDateTime startTime);

    @Query("SELECT AVG(s.value) FROM SensorData s WHERE s.type = :type AND s.location.floor = :floor AND s.timestamp >= :startTime")
    Double getAverageValueByTypeAndFloor(@Param("type") String type,
                                         @Param("floor") Integer floor,
                                         @Param("startTime") LocalDateTime startTime);

    @Query("SELECT s FROM SensorData s WHERE s.timestamp = (SELECT MAX(s2.timestamp) FROM SensorData s2 WHERE s2.deviceId = s.deviceId)")
    List<SensorData> findLatestForAllDevices();

    @Query("SELECT s FROM SensorData s WHERE s.deviceId = :deviceId ORDER BY s.timestamp DESC LIMIT 1")
    SensorData findLatestByDeviceId(@Param("deviceId") String deviceId);
}