package com.bmsedge.iotsensor.repository;

import com.bmsedge.iotsensor.model.CafeteriaLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CafeteriaLocationRepository extends JpaRepository<CafeteriaLocation, Long> {

    Optional<CafeteriaLocation> findByCafeteriaCode(String cafeteriaCode);

    List<CafeteriaLocation> findByTenantId(Long tenantId);

    List<CafeteriaLocation> findByActive(Boolean active);

    @Query("SELECT c FROM CafeteriaLocation c WHERE c.tenant.id = :tenantId AND c.active = true ORDER BY c.cafeteriaName")
    List<CafeteriaLocation> findActiveByTenantId(@Param("tenantId") Long tenantId);

    @Query("SELECT c FROM CafeteriaLocation c WHERE c.tenant.tenantCode = :tenantCode AND c.active = true")
    List<CafeteriaLocation> findActiveByTenantCode(@Param("tenantCode") String tenantCode);


    @Query("""
    SELECT ca.timestamp, ca.inCount
    FROM CafeteriaAnalytics ca
    WHERE ca.cafeteriaLocation.id = :locationId
      AND ca.foodCounter.id = :counterId
      AND ca.timestamp BETWEEN :startTime AND :endTime
    ORDER BY ca.timestamp ASC
""")
    List<Object[]> getCounterInCountTimeline(
            @Param("locationId") Long locationId,
            @Param("counterId") Long counterId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );



    @Query("SELECT c FROM CafeteriaLocation c WHERE c.tenant.tenantCode = :tenantCode AND c.cafeteriaCode = :cafeteriaCode")
    Optional<CafeteriaLocation> findByTenantCodeAndCafeteriaCode(
            @Param("tenantCode") String tenantCode,
            @Param("cafeteriaCode") String cafeteriaCode
    );
}