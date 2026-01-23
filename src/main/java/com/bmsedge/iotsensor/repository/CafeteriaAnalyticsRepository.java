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
    /**
     * ✅ UPDATED: Counter performance with counter ID and MAX wait time
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

    // Add this method to CafeteriaAnalyticsRepository.java

    /**
     * ✅ Get master data for all counters with latest analytics
     * Returns comprehensive data for reporting/export
     */
    // Add these methods to CafeteriaAnalyticsRepository.java

    /**
     * ✅ Get master data for all counters with latest analytics
     * Returns comprehensive data for reporting/export
     */
    @Query("""
    SELECT 
        fc.id,
        fc.counterName,
        fc.counterCode,
        fc.counterType,
        fc.deviceId,
        cl.cafeteriaName,
        cl.cafeteriaCode,
        cl.floor,
        cl.zone,
        ca.timestamp,
        ca.currentOccupancy,
        ca.capacity,
        ca.occupancyPercentage,
        ca.avgDwellTime,
        ca.estimatedWaitTime,
        ca.manualWaitTime,
        ca.queueLength,
        ca.congestionLevel,
        ca.serviceStatus,
        ca.inCount,
        ca.maxDwellTime,
        fc.active
    FROM CafeteriaAnalytics ca
    JOIN ca.foodCounter fc
    JOIN ca.cafeteriaLocation cl
    WHERE cl.id = :cafeteriaLocationId
    AND ca.timestamp >= :startTime
    AND ca.timestamp <= :endTime
    ORDER BY ca.timestamp DESC, fc.counterName ASC
""")
    List<Object[]> getMasterDataForCounters(
            @Param("cafeteriaLocationId") Long cafeteriaLocationId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * ✅ Get latest master data for all counters (one record per counter)
     */
    @Query("""
    SELECT 
        fc.id,
        fc.counterName,
        fc.counterCode,
        fc.counterType,
        fc.deviceId,
        cl.cafeteriaName,
        cl.cafeteriaCode,
        cl.floor,
        cl.zone,
        ca.timestamp,
        ca.currentOccupancy,
        ca.capacity,
        ca.occupancyPercentage,
        ca.avgDwellTime,
        ca.estimatedWaitTime,
        ca.manualWaitTime,
        ca.queueLength,
        ca.congestionLevel,
        ca.serviceStatus,
        ca.inCount,
        ca.maxDwellTime,
        fc.active
    FROM CafeteriaAnalytics ca
    JOIN ca.foodCounter fc
    JOIN ca.cafeteriaLocation cl
    WHERE cl.id = :cafeteriaLocationId
    AND ca.id IN (
        SELECT MAX(ca2.id)
        FROM CafeteriaAnalytics ca2
        WHERE ca2.foodCounter.id = fc.id
        AND ca2.timestamp >= :startTime
    )
    ORDER BY fc.counterName ASC
""")
    List<Object[]> getLatestMasterDataForAllCounters(
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


    // Add these methods to CafeteriaAnalyticsRepository.java

// ==================== QUEUE ANALYSIS METHODS ====================

    /**
     * ✅ Get average queue length per counter for comparison chart
     */
    @Query("""
    SELECT
      c.foodCounter.id,
      c.foodCounter.counterName,
      AVG(c.queueLength) as avgQueue,
      MAX(c.queueLength) as maxQueue,
      MIN(c.queueLength) as minQueue,
      COUNT(c) as dataPoints
    FROM CafeteriaAnalytics c
    WHERE c.cafeteriaLocation.id = :cafeteriaLocationId
    AND c.foodCounter IS NOT NULL
    AND c.queueLength IS NOT NULL
    AND c.timestamp >= :startTime
    AND c.timestamp <= :endTime
    GROUP BY c.foodCounter.id, c.foodCounter.counterName
    ORDER BY avgQueue DESC
""")
    List<Object[]> getAverageQueueComparison(
            @Param("cafeteriaLocationId") Long cafeteriaLocationId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * ✅ Get congestion rate per counter (percentage of time in each congestion level)
     */
    @Query("""
    SELECT
      c.foodCounter.id,
      c.foodCounter.counterName,
      c.congestionLevel,
      COUNT(c) as recordCount
    FROM CafeteriaAnalytics c
    WHERE c.cafeteriaLocation.id = :cafeteriaLocationId
    AND c.foodCounter IS NOT NULL
    AND c.congestionLevel IS NOT NULL
    AND c.timestamp >= :startTime
    AND c.timestamp <= :endTime
    GROUP BY c.foodCounter.id, c.foodCounter.counterName, c.congestionLevel
    ORDER BY c.foodCounter.counterName, c.congestionLevel
""")
    List<Object[]> getCongestionRateByCounter(
            @Param("cafeteriaLocationId") Long cafeteriaLocationId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     /**
     * ✅ FIXED: PostgreSQL-compatible queue length time series
     */
    @Query("""
    SELECT
      TO_CHAR(c.timestamp, 'YYYY-MM-DD HH24:MI') as timeBucket,
      c.foodCounter.id,
      c.foodCounter.counterName,
      AVG(c.queueLength) as avgQueue,
      MAX(c.queueLength) as maxQueue,
      COUNT(c) as dataPoints
    FROM CafeteriaAnalytics c
    WHERE c.cafeteriaLocation.id = :cafeteriaLocationId
    AND c.foodCounter IS NOT NULL
    AND c.queueLength IS NOT NULL
    AND c.timestamp >= :startTime
    AND c.timestamp <= :endTime
    GROUP BY TO_CHAR(c.timestamp, 'YYYY-MM-DD HH24:MI'), c.foodCounter.id, c.foodCounter.counterName
    ORDER BY timeBucket, c.foodCounter.counterName
""")
    List<Object[]> getQueueLengthTimeSeries(
            @Param("cafeteriaLocationId") Long cafeteriaLocationId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * ✅ Alternative method for databases that don't support DATE_FORMAT
     * Groups by extracting hour and minute separately
     */
    @Query("""
    SELECT
      EXTRACT(HOUR FROM c.timestamp) as hour,
      (EXTRACT(MINUTE FROM c.timestamp) / 5) * 5 as minuteBucket,
      c.foodCounter.id,
      c.foodCounter.counterName,
      AVG(c.queueLength) as avgQueue,
      MAX(c.queueLength) as maxQueue,
      COUNT(c) as dataPoints
    FROM CafeteriaAnalytics c
    WHERE c.cafeteriaLocation.id = :cafeteriaLocationId
    AND c.foodCounter IS NOT NULL
    AND c.queueLength IS NOT NULL
    AND c.timestamp >= :startTime
    AND c.timestamp <= :endTime
    GROUP BY hour, minuteBucket, c.foodCounter.id, c.foodCounter.counterName
    ORDER BY hour, minuteBucket, c.foodCounter.counterName
""")
    List<Object[]> getQueueLengthTimeSeriesAlternative(
            @Param("cafeteriaLocationId") Long cafeteriaLocationId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
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

    /**
     * ✅ FIXED: PostgreSQL-compatible in_count time series
     */
    @Query("""
    SELECT
      TO_CHAR(c.timestamp, 'YYYY-MM-DD HH24:MI') as timeBucket,
      c.foodCounter.id,
      c.foodCounter.counterName,
      AVG(c.inCount) as avgInCount,
      MAX(c.inCount) as maxInCount,
      SUM(c.inCount) as totalInCount,
      COUNT(c) as dataPoints
    FROM CafeteriaAnalytics c
    WHERE c.cafeteriaLocation.id = :cafeteriaLocationId
    AND c.foodCounter IS NOT NULL
    AND c.inCount IS NOT NULL
    AND c.timestamp >= :startTime
    AND c.timestamp <= :endTime
    GROUP BY TO_CHAR(c.timestamp, 'YYYY-MM-DD HH24:MI'), c.foodCounter.id, c.foodCounter.counterName
    ORDER BY timeBucket, c.foodCounter.counterName
""")
    List<Object[]> getInCountTimeSeries(
            @Param("cafeteriaLocationId") Long cafeteriaLocationId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * ✅ Alternative in_count time series (for databases without DATE_FORMAT)
     */
    @Query("""
    SELECT
      EXTRACT(HOUR FROM c.timestamp) as hour,
      (EXTRACT(MINUTE FROM c.timestamp) / :intervalMinutes) * :intervalMinutes as minuteBucket,
      c.foodCounter.id,
      c.foodCounter.counterName,
      AVG(c.inCount) as avgInCount,
      MAX(c.inCount) as maxInCount,
      SUM(c.inCount) as totalInCount,
      COUNT(c) as dataPoints
    FROM CafeteriaAnalytics c
    WHERE c.cafeteriaLocation.id = :cafeteriaLocationId
    AND c.foodCounter IS NOT NULL
    AND c.inCount IS NOT NULL
    AND c.timestamp >= :startTime
    AND c.timestamp <= :endTime
    GROUP BY hour, minuteBucket, c.foodCounter.id, c.foodCounter.counterName
    ORDER BY hour, minuteBucket, c.foodCounter.counterName
""")
    List<Object[]> getInCountTimeSeriesAlternative(
            @Param("cafeteriaLocationId") Long cafeteriaLocationId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("intervalMinutes") Integer intervalMinutes
    );

    /**
     * ✅ Get average in_count per counter (for comparison chart)
     */
    @Query("""
    SELECT
      c.foodCounter.id,
      c.foodCounter.counterName,
      AVG(c.inCount) as avgInCount,
      MAX(c.inCount) as maxInCount,
      MIN(c.inCount) as minInCount,
      SUM(c.inCount) as totalInCount,
      COUNT(c) as dataPoints
    FROM CafeteriaAnalytics c
    WHERE c.cafeteriaLocation.id = :cafeteriaLocationId
    AND c.foodCounter IS NOT NULL
    AND c.inCount IS NOT NULL
    AND c.timestamp >= :startTime
    AND c.timestamp <= :endTime
    GROUP BY c.foodCounter.id, c.foodCounter.counterName
    ORDER BY avgInCount DESC
""")
    List<Object[]> getAverageInCountComparison(
            @Param("cafeteriaLocationId") Long cafeteriaLocationId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * ✅ FIXED: PostgreSQL-compatible occupancy time series
     */
    @Query("""
    SELECT
      TO_CHAR(c.timestamp, 'YYYY-MM-DD HH24:MI') as timeBucket,
      c.foodCounter.id,
      c.foodCounter.counterName,
      AVG(c.currentOccupancy) as avgOccupancy,
      MAX(c.currentOccupancy) as maxOccupancy,
      COUNT(c) as dataPoints
    FROM CafeteriaAnalytics c
    WHERE c.cafeteriaLocation.id = :cafeteriaLocationId
    AND c.foodCounter IS NOT NULL
    AND c.currentOccupancy IS NOT NULL
    AND c.timestamp >= :startTime
    AND c.timestamp <= :endTime
    GROUP BY TO_CHAR(c.timestamp, 'YYYY-MM-DD HH24:MI'), c.foodCounter.id, c.foodCounter.counterName
    ORDER BY timeBucket, c.foodCounter.counterName
""")
    List<Object[]> getCurrentOccupancyTimeSeries(
            @Param("cafeteriaLocationId") Long cafeteriaLocationId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );



}