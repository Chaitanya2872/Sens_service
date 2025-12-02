package com.bmsedge.iotsensor.repository;

import com.bmsedge.iotsensor.model.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findByStatus(String status);

    List<Alert> findByType(String type);

    List<Alert> findBySeverity(String severity);

    List<Alert> findByLocationId(Long locationId);

    @Query("SELECT a FROM Alert a WHERE a.status = 'ACTIVE' ORDER BY a.severity DESC, a.createdAt DESC")
    List<Alert> findAllActiveAlerts();

    @Query("SELECT a FROM Alert a WHERE a.type = :type AND a.status = 'ACTIVE' ORDER BY a.createdAt DESC")
    List<Alert> findActiveAlertsByType(@Param("type") String type);

    @Query("SELECT a FROM Alert a WHERE a.createdAt BETWEEN :startDate AND :endDate")
    List<Alert> findAlertsByDateRange(@Param("startDate") LocalDateTime startDate,
                                      @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(a) FROM Alert a WHERE a.status = 'ACTIVE' AND a.type = :type")
    Long countActiveAlertsByType(@Param("type") String type);

    @Query("SELECT a FROM Alert a WHERE a.location.floor = :floor AND a.status = 'ACTIVE'")
    List<Alert> findActiveAlertsByFloor(@Param("floor") Integer floor);
}