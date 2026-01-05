package com.bmsedge.iotsensor.repository;

import com.bmsedge.iotsensor.model.CafeteriaQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CafeteriaQueueRepository extends JpaRepository<CafeteriaQueue, Long> {

    List<CafeteriaQueue> findByCounterName(String counterName);

    @Query("SELECT c FROM CafeteriaQueue c WHERE c.counterName = :counterName ORDER BY c.timestamp DESC LIMIT 1")
    Optional<CafeteriaQueue> findLatestByCounterName(@Param("counterName") String counterName);

    @Query("SELECT c FROM CafeteriaQueue c WHERE c.timestamp >= :startTime ORDER BY c.timestamp DESC")
    List<CafeteriaQueue> findRecent(@Param("startTime") LocalDateTime startTime);

    @Query("SELECT c FROM CafeteriaQueue c WHERE c.counterName = :counterName AND c.timestamp >= :startTime ORDER BY c.timestamp DESC")
    List<CafeteriaQueue> findRecentByCounter(@Param("counterName") String counterName,
                                             @Param("startTime") LocalDateTime startTime);

    @Query("SELECT c FROM CafeteriaQueue c WHERE c.location.id = :locationId AND c.timestamp >= :startTime ORDER BY c.timestamp DESC")
    List<CafeteriaQueue> findRecentByLocation(@Param("locationId") Long locationId,
                                              @Param("startTime") LocalDateTime startTime);

    @Query("SELECT c FROM CafeteriaQueue c WHERE c.status = :status AND c.timestamp >= :startTime ORDER BY c.timestamp DESC")
    List<CafeteriaQueue> findByStatusRecent(@Param("status") String status,
                                            @Param("startTime") LocalDateTime startTime);

    @Query("SELECT AVG(c.queueCount) FROM CafeteriaQueue c WHERE c.counterName = :counterName AND c.timestamp >= :startTime")
    Double getAverageQueueCount(@Param("counterName") String counterName,
                                @Param("startTime") LocalDateTime startTime);

    @Query("SELECT AVG(c.waitTimeMinutes) FROM CafeteriaQueue c WHERE c.counterName = :counterName AND c.timestamp >= :startTime")
    Double getAverageWaitTime(@Param("counterName") String counterName,
                              @Param("startTime") LocalDateTime startTime);

    @Query("SELECT DISTINCT c.counterName FROM CafeteriaQueue c ORDER BY c.counterName")
    List<String> findAllCounterNames();

    @Query("""
SELECT
  DATE_TRUNC('hour', c.timestamp) as bucket,
  c.counterName,
  AVG(c.queueCount),
  AVG(c.waitTimeMinutes)
FROM CafeteriaQueue c
WHERE c.timestamp >= :start
GROUP BY bucket, c.counterName
ORDER BY bucket
""")
    List<Object[]> getHourlyAnalytics(
            @Param("start") LocalDateTime start
    );


    @Query("""
SELECT
  DATE(c.timestamp),
  SUM(c.queueCount),
  AVG(c.waitTimeMinutes)
FROM CafeteriaQueue c
WHERE c.timestamp >= :start
GROUP BY DATE(c.timestamp)
ORDER BY DATE(c.timestamp)
""")
    List<Object[]> getDailyTraffic(@Param("start") LocalDateTime start);


    @Query("""
SELECT
  EXTRACT(HOUR FROM c.timestamp),
  AVG(c.queueCount),
  MAX(c.queueCount),
  AVG(c.waitTimeMinutes)
FROM CafeteriaQueue c
WHERE c.timestamp >= :start
GROUP BY EXTRACT(HOUR FROM c.timestamp)
ORDER BY EXTRACT(HOUR FROM c.timestamp)
""")
    List<Object[]> getPeakHourStats(@Param("start") LocalDateTime start);


    @Query("""
SELECT c
FROM CafeteriaQueue c
JOIN (
    SELECT c2.counterName AS counterName, MAX(c2.timestamp) AS maxTime
    FROM CafeteriaQueue c2
    GROUP BY c2.counterName
) latest
ON c.counterName = latest.counterName AND c.timestamp = latest.maxTime
""")
    List<CafeteriaQueue> findLatestForAllCounters();


    @Query("SELECT COUNT(c) FROM CafeteriaQueue c WHERE c.status = :status AND c.timestamp >= :startTime")
    Long countByStatusRecent(@Param("status") String status,
                             @Param("startTime") LocalDateTime startTime);

    // ==================== NEW DATE-WISE QUERIES ====================

    /**
     * Find all records for a specific date (all day)
     */
    @Query("SELECT c FROM CafeteriaQueue c WHERE c.timestamp >= :startOfDay AND c.timestamp < :endOfDay ORDER BY c.timestamp ASC")
    List<CafeteriaQueue> findByDate(@Param("startOfDay") LocalDateTime startOfDay,
                                    @Param("endOfDay") LocalDateTime endOfDay);

    /**
     * Find all records for a specific date and counter
     */
    @Query("SELECT c FROM CafeteriaQueue c WHERE c.counterName = :counterName AND c.timestamp >= :startOfDay AND c.timestamp < :endOfDay ORDER BY c.timestamp ASC")
    List<CafeteriaQueue> findByDateAndCounter(@Param("counterName") String counterName,
                                              @Param("startOfDay") LocalDateTime startOfDay,
                                              @Param("endOfDay") LocalDateTime endOfDay);

    /**
     * Find records within a date range
     */
    @Query("SELECT c FROM CafeteriaQueue c WHERE c.timestamp >= :startDateTime AND c.timestamp <= :endDateTime ORDER BY c.timestamp ASC")
    List<CafeteriaQueue> findByDateRange(@Param("startDateTime") LocalDateTime startDateTime,
                                         @Param("endDateTime") LocalDateTime endDateTime);

    /**
     * Get hourly aggregated data for a specific date
     */
    @Query("""
SELECT
  EXTRACT(HOUR FROM c.timestamp) as hour,
  c.counterName,
  AVG(c.queueCount) as avgQueue,
  MAX(c.queueCount) as maxQueue,
  MIN(c.queueCount) as minQueue,
  AVG(c.waitTimeMinutes) as avgWait,
  MAX(c.waitTimeMinutes) as maxWait,
  COUNT(c) as recordCount
FROM CafeteriaQueue c
WHERE c.timestamp >= :startOfDay AND c.timestamp < :endOfDay
GROUP BY EXTRACT(HOUR FROM c.timestamp), c.counterName
ORDER BY hour, c.counterName
""")
    List<Object[]> getHourlyAggregatedDataForDate(@Param("startOfDay") LocalDateTime startOfDay,
                                                  @Param("endOfDay") LocalDateTime endOfDay);

    /**
     * Get hourly aggregated data for a specific date and counter
     */
    @Query("""
SELECT
  EXTRACT(HOUR FROM c.timestamp) as hour,
  AVG(c.queueCount) as avgQueue,
  MAX(c.queueCount) as maxQueue,
  MIN(c.queueCount) as minQueue,
  AVG(c.waitTimeMinutes) as avgWait,
  MAX(c.waitTimeMinutes) as maxWait,
  COUNT(c) as recordCount
FROM CafeteriaQueue c
WHERE c.counterName = :counterName AND c.timestamp >= :startOfDay AND c.timestamp < :endOfDay
GROUP BY EXTRACT(HOUR FROM c.timestamp)
ORDER BY hour
""")
    List<Object[]> getHourlyAggregatedDataForDateAndCounter(@Param("counterName") String counterName,
                                                            @Param("startOfDay") LocalDateTime startOfDay,
                                                            @Param("endOfDay") LocalDateTime endOfDay);

    /**
     * Get daily summary statistics for date range
     */
    @Query("""
SELECT
  DATE(c.timestamp) as date,
  c.counterName,
  AVG(c.queueCount) as avgQueue,
  MAX(c.queueCount) as maxQueue,
  MIN(c.queueCount) as minQueue,
  AVG(c.waitTimeMinutes) as avgWait,
  MAX(c.waitTimeMinutes) as maxWait,
  COUNT(c) as recordCount,
  SUM(CASE WHEN c.status = 'CRITICAL' THEN 1 ELSE 0 END) as criticalCount,
  SUM(CASE WHEN c.status = 'WARNING' THEN 1 ELSE 0 END) as warningCount
FROM CafeteriaQueue c
WHERE c.timestamp >= :startDateTime AND c.timestamp <= :endDateTime
GROUP BY DATE(c.timestamp), c.counterName
ORDER BY date, c.counterName
""")
    List<Object[]> getDailySummaryForDateRange(@Param("startDateTime") LocalDateTime startDateTime,
                                               @Param("endDateTime") LocalDateTime endDateTime);

    /**
     * Find records around a specific timestamp (within a time range)
     */
    @Query("SELECT c FROM CafeteriaQueue c WHERE c.timestamp >= :startTime AND c.timestamp <= :endTime ORDER BY c.timestamp ASC")
    List<CafeteriaQueue> findByTimestampRange(@Param("startTime") LocalDateTime startTime,
                                              @Param("endTime") LocalDateTime endTime);

    /**
     * Find records for a specific hour of a day
     */
    @Query("SELECT c FROM CafeteriaQueue c WHERE c.timestamp >= :startOfHour AND c.timestamp < :endOfHour ORDER BY c.timestamp ASC")
    List<CafeteriaQueue> findByHour(@Param("startOfHour") LocalDateTime startOfHour,
                                    @Param("endOfHour") LocalDateTime endOfHour);

    /**
     * Find records by counter and time range
     */
    @Query("SELECT c FROM CafeteriaQueue c WHERE c.counterName = :counterName AND c.timestamp >= :from AND c.timestamp <= :to ORDER BY c.timestamp ASC")
    List<CafeteriaQueue> findByCounterAndTimeRange(@Param("counterName") String counterName,
                                                   @Param("from") LocalDateTime from,
                                                   @Param("to") LocalDateTime to);

    /**
     * Get statistics for specific hour
     */
    @Query("""
SELECT
  c.counterName,
  AVG(c.queueCount) as avgQueue,
  MAX(c.queueCount) as maxQueue,
  MIN(c.queueCount) as minQueue,
  AVG(c.waitTimeMinutes) as avgWait,
  MAX(c.waitTimeMinutes) as maxWait,
  COUNT(c) as recordCount
FROM CafeteriaQueue c
WHERE c.timestamp >= :startOfHour AND c.timestamp < :endOfHour
GROUP BY c.counterName
ORDER BY c.counterName
""")
    List<Object[]> getStatisticsForHour(@Param("startOfHour") LocalDateTime startOfHour,
                                        @Param("endOfHour") LocalDateTime endOfHour);

    // ==================== END NEW QUERIES ====================
}