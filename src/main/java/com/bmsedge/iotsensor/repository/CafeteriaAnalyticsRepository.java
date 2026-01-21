package com.bmsedge.iotsensor.repository;

import com.bmsedge.iotsensor.model.CafeteriaAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CafeteriaAnalyticsRepository extends JpaRepository<CafeteriaAnalytics, Long> {

    // ==================== BASIC QUERIES ====================

    List<CafeteriaAnalytics> findByCafeteriaLocationId(Long cafeteriaLocationId);

    List<CafeteriaAnalytics> findByFoodCounterId(Long foodCounterId);

    @Query("SELECT c FROM CafeteriaAnalytics c WHERE c.timestamp >= :startTime ORDER BY c.timestamp DESC")
    List<CafeteriaAnalytics> findRecent(@Param("startTime") LocalDateTime startTime);

    // ==================== LATEST DATA ====================

    @Query("""
SELECT c FROM CafeteriaAnalytics c
WHERE c.cafeteriaLocation.id = :cafeteriaLocationId
AND c.id = (
    SELECT MAX(c2.id)
    FROM CafeteriaAnalytics c2
    WHERE c2.cafeteriaLocation.id = :cafeteriaLocationId
)
""")
    Optional<CafeteriaAnalytics> findLatestByCafeteriaLocation(
            @Param("cafeteriaLocationId") Long cafeteriaLocationId
    );


    @Query("""
SELECT c FROM CafeteriaAnalytics c
WHERE c.foodCounter.id = :foodCounterId
AND c.id = (
    SELECT MAX(c2.id)
    FROM CafeteriaAnalytics c2
    WHERE c2.foodCounter.id = :foodCounterId
)
""")
    Optional<CafeteriaAnalytics> findLatestByFoodCounter(
            @Param("foodCounterId") Long foodCounterId
    );


    @Query("""
SELECT c FROM CafeteriaAnalytics c
JOIN FETCH c.foodCounter fc
WHERE fc.cafeteriaLocation.id = :cafeteriaLocationId
AND c.id = (
    SELECT MAX(c2.id)
    FROM CafeteriaAnalytics c2
    WHERE c2.foodCounter.id = fc.id
)
ORDER BY fc.counterName
""")
    List<CafeteriaAnalytics> findLatestForAllCounters(
            @Param("cafeteriaLocationId") Long cafeteriaLocationId
    );


    // ==================== TIME RANGE QUERIES ====================

    @Query("SELECT c FROM CafeteriaAnalytics c WHERE c.cafeteriaLocation.id = :cafeteriaLocationId AND c.timestamp >= :startTime AND c.timestamp <= :endTime ORDER BY c.timestamp ASC")
    List<CafeteriaAnalytics> findByCafeteriaLocationAndTimeRange(
            @Param("cafeteriaLocationId") Long cafeteriaLocationId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    @Query("SELECT c FROM CafeteriaAnalytics c WHERE c.foodCounter.id = :foodCounterId AND c.timestamp >= :startTime AND c.timestamp <= :endTime ORDER BY c.timestamp ASC")
    List<CafeteriaAnalytics> findByFoodCounterAndTimeRange(
            @Param("foodCounterId") Long foodCounterId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    // ==================== HOURLY ANALYTICS ====================

    @Query("""
        SELECT
          EXTRACT(HOUR FROM c.timestamp) as hour,
          c.foodCounter.counterName,
          AVG(c.currentOccupancy) as avgOccupancy,
          MAX(c.currentOccupancy) as maxOccupancy,
          AVG(c.queueLength) as avgQueue,
          MAX(c.queueLength) as maxQueue,
          AVG(c.avgDwellTime) as avgDwell,
          AVG(c.estimatedWaitTime) as avgWait,
          SUM(c.inCount) as totalInflow,
          COUNT(c) as recordCount
        FROM CafeteriaAnalytics c
        WHERE c.cafeteriaLocation.id = :cafeteriaLocationId
        AND c.timestamp >= :startOfDay
        AND c.timestamp < :endOfDay
        GROUP BY EXTRACT(HOUR FROM c.timestamp), c.foodCounter.counterName
        ORDER BY hour, c.foodCounter.counterName
    """)
    List<Object[]> getHourlyAnalytics(
            @Param("cafeteriaLocationId") Long cafeteriaLocationId,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay
    );

    /**
     * Get cafeteria-level hourly flow (cafeteria entrance/exit)
     */
    @Query("""
        SELECT
          EXTRACT(HOUR FROM c.timestamp) as hour,
          SUM(c.inCount) as totalInflow,
          AVG(c.currentOccupancy) as avgOccupancy,
          MAX(c.currentOccupancy) as maxOccupancy
        FROM CafeteriaAnalytics c
        WHERE c.cafeteriaLocation.id = :cafeteriaLocationId
        AND c.foodCounter IS NULL
        AND c.timestamp >= :startTime
        AND c.timestamp <= :endTime
        GROUP BY EXTRACT(HOUR FROM c.timestamp)
        ORDER BY hour
    """)
    List<Object[]> getCafeteriaHourlyFlow(
            @Param("cafeteriaLocationId") Long cafeteriaLocationId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * ✅ NEW: Get counter-level hourly flow (sum of all food counters)
     * Used for real footfall comparison instead of Math.random()
     */
    @Query("""
        SELECT
          EXTRACT(HOUR FROM c.timestamp) as hour,
          SUM(c.inCount) as totalCounterInflow
        FROM CafeteriaAnalytics c
        WHERE c.cafeteriaLocation.id = :cafeteriaLocationId
        AND c.foodCounter IS NOT NULL
        AND c.timestamp >= :startTime
        AND c.timestamp <= :endTime
        GROUP BY EXTRACT(HOUR FROM c.timestamp)
        ORDER BY hour
    """)
    List<Object[]> getCounterHourlyFlow(
            @Param("cafeteriaLocationId") Long cafeteriaLocationId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    // ==================== DAILY ANALYTICS ====================

    @Query("""
        SELECT
          DATE(c.timestamp) as date,
          c.foodCounter.counterName,
          AVG(c.currentOccupancy) as avgOccupancy,
          MAX(c.currentOccupancy) as maxOccupancy,
          AVG(c.queueLength) as avgQueue,
          AVG(c.avgDwellTime) as avgDwell,
          SUM(c.inCount) as totalInflow,
          COUNT(c) as recordCount
        FROM CafeteriaAnalytics c
        WHERE c.cafeteriaLocation.id = :cafeteriaLocationId
        AND c.timestamp >= :startDateTime
        AND c.timestamp <= :endDateTime
        GROUP BY DATE(c.timestamp), c.foodCounter.counterName
        ORDER BY date, c.foodCounter.counterName
    """)
    List<Object[]> getDailyAnalytics(
            @Param("cafeteriaLocationId") Long cafeteriaLocationId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );

    // ==================== AGGREGATIONS ====================

    @Query("SELECT AVG(c.currentOccupancy) FROM CafeteriaAnalytics c WHERE c.cafeteriaLocation.id = :cafeteriaLocationId AND c.timestamp >= :startTime")
    Double getAverageOccupancy(
            @Param("cafeteriaLocationId") Long cafeteriaLocationId,
            @Param("startTime") LocalDateTime startTime
    );

    @Query("SELECT AVG(c.avgDwellTime) FROM CafeteriaAnalytics c WHERE c.cafeteriaLocation.id = :cafeteriaLocationId AND c.timestamp >= :startTime")
    Double getAverageDwellTime(
            @Param("cafeteriaLocationId") Long cafeteriaLocationId,
            @Param("startTime") LocalDateTime startTime
    );

    @Query("SELECT SUM(c.inCount) FROM CafeteriaAnalytics c WHERE c.cafeteriaLocation.id = :cafeteriaLocationId AND c.timestamp >= :startTime")
    Long getTotalVisitors(
            @Param("cafeteriaLocationId") Long cafeteriaLocationId,
            @Param("startTime") LocalDateTime startTime
    );


    /**
     * Get total visitors by summing in_count field
     * Only counts cafeteria-level analytics (food_counter IS NULL) to avoid double-counting
     */
    @Query("SELECT COALESCE(SUM(ca.inCount), 0) FROM CafeteriaAnalytics ca " +
            "WHERE ca.cafeteriaLocation.id = :locationId " +
            "AND ca.timestamp BETWEEN :startTime AND :endTime " +
            "AND ca.foodCounter IS NULL")
    Long getTotalVisitorsByInCount(
            @Param("locationId") Long locationId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    @Query("SELECT AVG(c.queueLength) FROM CafeteriaAnalytics c WHERE c.foodCounter.id = :foodCounterId AND c.timestamp >= :startTime")
    Double getAverageQueueLength(
            @Param("foodCounterId") Long foodCounterId,
            @Param("startTime") LocalDateTime startTime
    );

    // ==================== PEAK HOUR ANALYSIS ====================

    @Query("""
        SELECT
          EXTRACT(HOUR FROM c.timestamp) as hour,
          AVG(c.currentOccupancy) as avgOccupancy,
          MAX(c.currentOccupancy) as maxOccupancy,
          SUM(c.inCount) as totalInflow
        FROM CafeteriaAnalytics c
        WHERE c.cafeteriaLocation.id = :cafeteriaLocationId
        AND c.timestamp >= :startTime
        GROUP BY EXTRACT(HOUR FROM c.timestamp)
        ORDER BY avgOccupancy DESC
    """)
    List<Object[]> getPeakHours(
            @Param("cafeteriaLocationId") Long cafeteriaLocationId,
            @Param("startTime") LocalDateTime startTime
    );

    // ==================== CONGESTION ANALYSIS ====================

    @Query("SELECT COUNT(c) FROM CafeteriaAnalytics c WHERE c.cafeteriaLocation.id = :cafeteriaLocationId AND c.congestionLevel = :level AND c.timestamp >= :startTime")
    Long countByCongestionLevel(
            @Param("cafeteriaLocationId") Long cafeteriaLocationId,
            @Param("level") String level,
            @Param("startTime") LocalDateTime startTime
    );

    @Query("""
        SELECT c FROM CafeteriaAnalytics c 
        WHERE c.cafeteriaLocation.id = :cafeteriaLocationId 
        AND c.congestionLevel = 'HIGH' 
        AND c.timestamp >= :startTime 
        ORDER BY c.timestamp DESC
    """)
    List<CafeteriaAnalytics> findHighCongestionPeriods(
            @Param("cafeteriaLocationId") Long cafeteriaLocationId,
            @Param("startTime") LocalDateTime startTime
    );

    // ==================== COUNTER COMPARISON ====================

    /**
     * Original method - kept for backward compatibility
     */
    @Query("""
        SELECT
          c.foodCounter.counterName,
          AVG(c.queueLength) as avgQueue,
          AVG(c.avgDwellTime) as avgDwell,
          AVG(c.estimatedWaitTime) as avgWait,
          SUM(c.inCount) as totalServed
        FROM CafeteriaAnalytics c
        WHERE c.cafeteriaLocation.id = :cafeteriaLocationId
        AND c.timestamp >= :startTime
        GROUP BY c.foodCounter.counterName
        ORDER BY totalServed DESC
    """)
    List<Object[]> getCounterPerformanceComparison(
            @Param("cafeteriaLocationId") Long cafeteriaLocationId,
            @Param("startTime") LocalDateTime startTime
    );

    /**
     * ✅ NEW: Counter performance with MAX wait time
     * Replaces estimation (avgWait * 1.5) with real MAX value
     */
    // In CafeteriaAnalyticsRepository.java

    /**
     * ✅ FIXED: Counter performance with counter ID included
     */
    @Query("""
    SELECT
      c.foodCounter.id,
      c.foodCounter.counterName,
      AVG(c.queueLength) as avgQueue,
      AVG(c.avgDwellTime) as avgDwell,
      AVG(c.estimatedWaitTime) as avgWait,
      MAX(c.estimatedWaitTime) as maxWait,
      SUM(c.inCount) as totalServed
    FROM CafeteriaAnalytics c
    WHERE c.cafeteriaLocation.id = :cafeteriaLocationId
    AND c.foodCounter IS NOT NULL
    AND c.timestamp >= :startTime
    GROUP BY c.foodCounter.id, c.foodCounter.counterName
    ORDER BY totalServed DESC
""")
    List<Object[]> getCounterPerformanceComparisonWithMax(
            @Param("cafeteriaLocationId") Long cafeteriaLocationId,
            @Param("startTime") LocalDateTime startTime
    );

    // ==================== TENANT-BASED QUERIES ====================

    @Query("""
        SELECT c FROM CafeteriaAnalytics c
        WHERE c.cafeteriaLocation.tenant.tenantCode = :tenantCode
        AND c.cafeteriaLocation.cafeteriaCode = :cafeteriaCode
        AND c.timestamp >= :startTime
        ORDER BY c.timestamp DESC
    """)
    List<CafeteriaAnalytics> findByTenantAndCafeteria(
            @Param("tenantCode") String tenantCode,
            @Param("cafeteriaCode") String cafeteriaCode,
            @Param("startTime") LocalDateTime startTime
    );

    // Add this method to your CafeteriaAnalyticsRepository interface:

    /**
     * ✅ NEW QUERY: Find analytics by specific counter ID and time range
     */
    @Query("SELECT ca FROM CafeteriaAnalytics ca " +
            "WHERE ca.foodCounter.id = :counterId " +
            "AND ca.timestamp BETWEEN :startTime AND :endTime " +
            "ORDER BY ca.timestamp DESC")
    List<CafeteriaAnalytics> findByFoodCounterIdAndTimestampBetween(
            @Param("counterId") Long counterId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

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




}