package com.bmsedge.iotsensor.service;

import com.bmsedge.iotsensor.dto.CafeteriaQueueDTO;
import com.bmsedge.iotsensor.model.CafeteriaQueue;
import com.bmsedge.iotsensor.model.DeviceLocationMapping;
import com.bmsedge.iotsensor.model.Location;
import com.bmsedge.iotsensor.repository.CafeteriaQueueRepository;
import com.bmsedge.iotsensor.repository.DeviceLocationMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CafeteriaQueueService {

    private final CafeteriaQueueRepository queueRepository;
    private final DeviceLocationMappingRepository deviceLocationMappingRepository;

    // Save cafeteria queue data (from Node.js or sensors)
    @Transactional
    public List<CafeteriaQueueDTO> saveCafeteriaQueueData(CafeteriaQueueDTO dto) {
        log.info("Saving cafeteria queue data - TwoGood:{}, UttarDakshin:{}, Tandoor:{}",
                dto.getTwoGoodQ(), dto.getUttarDakshinQ(), dto.getTandoorQ());

        LocalDateTime timestamp = LocalDateTime.now();
        List<CafeteriaQueue> savedRecords = new ArrayList<>();

        // Process TwoGood counter
        if (dto.getTwoGoodQ() != null && dto.getTwoGoodT() != null) {
            CafeteriaQueue twoGood = saveCounter("TwoGood", dto.getTwoGoodQ(),
                    dto.getTwoGoodT(), timestamp);
            savedRecords.add(twoGood);
        }

        // Process UttarDakshin counter
        if (dto.getUttarDakshinQ() != null && dto.getUttarDakshinT() != null) {
            CafeteriaQueue uttarDakshin = saveCounter("UttarDakshin", dto.getUttarDakshinQ(),
                    dto.getUttarDakshinT(), timestamp);
            savedRecords.add(uttarDakshin);
        }

        // Process Tandoor counter
        if (dto.getTandoorQ() != null && dto.getTandoorT() != null) {
            CafeteriaQueue tandoor = saveCounter("Tandoor", dto.getTandoorQ(),
                    dto.getTandoorT(), timestamp);
            savedRecords.add(tandoor);
        }

        log.info("Saved {} cafeteria queue records", savedRecords.size());

        return savedRecords.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // Save individual counter
    private CafeteriaQueue saveCounter(String counterName, Integer queueCount,
                                       String waitTimeText, LocalDateTime timestamp) {
        log.info("Saving counter: {} - Queue: {}, Wait: {}", counterName, queueCount, waitTimeText);

        // Get location for this counter
        String deviceId = "CAFETERIA_" + counterName.toUpperCase();
        Location location = getLocationForDevice(deviceId);

        // Parse wait time to minutes
        Double waitTimeMinutes = parseWaitTimeToMinutes(waitTimeText);

        // Determine service status
        String serviceStatus = determineServiceStatus(queueCount, waitTimeMinutes);

        // Determine alert status
        String status = determineQueueStatus(queueCount, waitTimeMinutes);

        CafeteriaQueue queue = CafeteriaQueue.builder()
                .counterName(counterName)
                .queueCount(queueCount)
                .waitTimeText(waitTimeText)
                .waitTimeMinutes(waitTimeMinutes)
                .serviceStatus(serviceStatus)
                .status(status)
                .location(location)
                .timestamp(timestamp)
                .build();

        return queueRepository.save(queue);
    }

    // Parse wait time text to minutes
    private Double parseWaitTimeToMinutes(String waitTimeText) {
        if (waitTimeText == null || waitTimeText.trim().isEmpty()) {
            return 0.0;
        }

        String normalized = waitTimeText.toLowerCase().trim();

        // "Ready to Serve" or similar
        if (normalized.contains("ready") || normalized.contains("no wait")) {
            return 0.0;
        }

        try {
            // Pattern: "5-10 mins"
            if (normalized.matches(".*\\d+\\s*-\\s*\\d+.*")) {
                String[] parts = normalized.split("-");
                double min = Double.parseDouble(parts[0].replaceAll("[^0-9.]", "").trim());
                double max = Double.parseDouble(parts[1].replaceAll("[^0-9.]", "").trim());
                return (min + max) / 2.0;
            }

            // Pattern: "15 Mins", "10 minutes"
            if (normalized.matches(".*\\d+.*")) {
                String numberStr = normalized.replaceAll("[^0-9.]", "").trim();
                if (!numberStr.isEmpty()) {
                    return Double.parseDouble(numberStr);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse wait time: {}", waitTimeText);
        }

        return 0.0;
    }

    // Determine service status
    private String determineServiceStatus(Integer queueCount, Double waitTimeMinutes) {
        if (queueCount == 0 && waitTimeMinutes == 0) {
            return "READY_TO_SERVE";
        } else if (waitTimeMinutes <= 5) {
            return "SHORT_WAIT";
        } else if (waitTimeMinutes <= 15) {
            return "MEDIUM_WAIT";
        } else {
            return "LONG_WAIT";
        }
    }

    // Determine queue status for alerts
    private String determineQueueStatus(Integer queueCount, Double waitTimeMinutes) {
        if (queueCount > 20 || waitTimeMinutes > 20) {
            return "CRITICAL";
        } else if (queueCount > 10 || waitTimeMinutes > 10) {
            return "WARNING";
        }
        return "NORMAL";
    }

    // Get location for device
    private Location getLocationForDevice(String deviceId) {
        return deviceLocationMappingRepository.findByDeviceId(deviceId)
                .map(DeviceLocationMapping::getLocation)
                .orElse(null);
    }

    // === GET METHODS ===

    // Get latest queue status for all counters
    public Map<String, Object> getLatestQueueStatus() {
        List<CafeteriaQueue> latest = queueRepository.findLatestForAllCounters();

        Map<String, CafeteriaQueueDTO> counters = new HashMap<>();
        for (CafeteriaQueue queue : latest) {
            counters.put(queue.getCounterName(), convertToDTO(queue));
        }

        return Map.of(
                "timestamp", LocalDateTime.now(),
                "counters", counters
        );
    }

    // Get latest status for specific counter
    public Map<String, Object> getCounterStatus(String counterName) {
        Optional<CafeteriaQueue> latest = queueRepository.findLatestByCounterName(counterName);

        Map<String, Object> result = new HashMap<>();
        result.put("counterName", counterName);
        result.put("timestamp", LocalDateTime.now());

        if (latest.isPresent()) {
            CafeteriaQueue queue = latest.get();
            result.put("queueCount", queue.getQueueCount());
            result.put("waitTimeText", queue.getWaitTimeText());
            result.put("waitTimeMinutes", queue.getWaitTimeMinutes());
            result.put("serviceStatus", queue.getServiceStatus());
            result.put("status", queue.getStatus());
            result.put("lastUpdate", queue.getTimestamp());

            if (queue.getLocation() != null) {
                result.put("locationName", queue.getLocation().getName());
                result.put("floor", queue.getLocation().getFloor());
            }
        } else {
            result.put("status", "NO_DATA");
        }

        return result;
    }

    // Get all counters status
    public List<Map<String, Object>> getAllCountersStatus() {
        List<String> counters = Arrays.asList("TwoGood", "UttarDakshin", "Tandoor");
        return counters.stream()
                .map(this::getCounterStatus)
                .collect(Collectors.toList());
    }

    // Get historical queue data
    public Map<String, Object> getHistoricalQueueData(Integer hours) {
        LocalDateTime startTime = LocalDateTime.now().minusHours(hours);
        List<CafeteriaQueue> allData = queueRepository.findRecent(startTime);

        Map<String, List<CafeteriaQueueDTO>> counterData = new HashMap<>();
        for (CafeteriaQueue queue : allData) {
            String counter = queue.getCounterName();
            counterData.computeIfAbsent(counter, k -> new ArrayList<>())
                    .add(convertToDTO(queue));
        }

        return Map.of(
                "hours", hours,
                "timestamp", LocalDateTime.now(),
                "counters", counterData
        );
    }

    // Get recent data for specific counter
    public List<CafeteriaQueueDTO> getRecentByCounter(String counterName, Integer hours) {
        LocalDateTime startTime = LocalDateTime.now().minusHours(hours);
        return queueRepository.findRecentByCounter(counterName, startTime).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // Get statistics for counter
    public Map<String, Object> getCounterStatistics(String counterName, Integer hours) {
        LocalDateTime startTime = LocalDateTime.now().minusHours(hours);

        Double avgQueue = queueRepository.getAverageQueueCount(counterName, startTime);
        Double avgWait = queueRepository.getAverageWaitTime(counterName, startTime);

        List<CafeteriaQueue> recentData = queueRepository.findRecentByCounter(counterName, startTime);

        int maxQueue = recentData.stream()
                .mapToInt(CafeteriaQueue::getQueueCount)
                .max()
                .orElse(0);

        double maxWait = recentData.stream()
                .mapToDouble(CafeteriaQueue::getWaitTimeMinutes)
                .max()
                .orElse(0.0);

        long criticalCount = recentData.stream()
                .filter(q -> "CRITICAL".equals(q.getStatus()))
                .count();

        long warningCount = recentData.stream()
                .filter(q -> "WARNING".equals(q.getStatus()))
                .count();

        return Map.of(
                "counterName", counterName,
                "hours", hours,
                "averageQueue", avgQueue != null ? avgQueue : 0.0,
                "averageWaitMinutes", avgWait != null ? avgWait : 0.0,
                "maxQueue", maxQueue,
                "maxWaitMinutes", maxWait,
                "criticalCount", criticalCount,
                "warningCount", warningCount,
                "totalReadings", recentData.size()
        );
    }

    // Get active alerts
    public List<CafeteriaQueueDTO> getActiveAlerts() {
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);

        List<CafeteriaQueue> criticalQueues = queueRepository.findByStatusRecent("CRITICAL", oneHourAgo);
        List<CafeteriaQueue> warningQueues = queueRepository.findByStatusRecent("WARNING", oneHourAgo);

        List<CafeteriaQueue> allAlerts = new ArrayList<>();
        allAlerts.addAll(criticalQueues);
        allAlerts.addAll(warningQueues);

        return allAlerts.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    //Analytics
    public Map<String, Object> getRealTimeAnalytics(int hours) {
        LocalDateTime start = LocalDateTime.now().minusHours(hours);
        List<Object[]> rows = queueRepository.getHourlyAnalytics(start);

        Map<String, List<Map<String, Object>>> counters = new HashMap<>();

        for (Object[] r : rows) {
            String counter = (String) r[1];
            counters.computeIfAbsent(counter, k -> new ArrayList<>())
                    .add(Map.of(
                            "time", r[0],
                            "avgQueue", r[2],
                            "avgWait", r[3]
                    ));
        }

        return Map.of(
                "hours", hours,
                "counters", counters
        );
    }

    public List<Map<String, Object>> getWeeklyTraffic() {
        LocalDateTime start = LocalDateTime.now().minusDays(7);
        return queueRepository.getDailyTraffic(start).stream()
                .map(r -> Map.of(
                        "date", r[0],
                        "totalQueue", r[1],
                        "avgWait", r[2]
                ))
                .toList();
    }

    public List<Map<String, Object>> getCurrentDistribution() {
        return queueRepository.findLatestForAllCounters().stream()
                .map(q -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("counter", q.getCounterName());
                    map.put("queue", q.getQueueCount());
                    return map;
                })
                .toList();
    }

    public List<Map<String, Object>> getPeakHourPerformance(int hours) {
        LocalDateTime start = LocalDateTime.now().minusHours(hours);

        return queueRepository.getPeakHourStats(start).stream()
                .map(r -> Map.of(
                        "hour", r[0],
                        "avgQueue", r[1],
                        "maxQueue", r[2],
                        "avgWait", r[3]
                ))
                .toList();
    }

    // ==================== NEW DATE-WISE AND HOURLY METHODS ====================

    /**
     * Get all data for a specific date
     */
    public Map<String, Object> getDataByDate(LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

        List<CafeteriaQueue> records = queueRepository.findByDate(startOfDay, endOfDay);

        // Group by counter
        Map<String, List<CafeteriaQueueDTO>> counterData = new HashMap<>();
        for (CafeteriaQueue queue : records) {
            String counter = queue.getCounterName();
            counterData.computeIfAbsent(counter, k -> new ArrayList<>())
                    .add(convertToDTO(queue));
        }

        // Calculate statistics
        Map<String, Object> statistics = calculateDailyStatistics(records);

        return Map.of(
                "date", date,
                "totalRecords", records.size(),
                "counters", counterData,
                "statistics", statistics
        );
    }

    /**
     * Get data for a specific date and counter
     */
    public Map<String, Object> getDataByDateAndCounter(LocalDate date, String counterName) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

        List<CafeteriaQueue> records = queueRepository.findByDateAndCounter(counterName, startOfDay, endOfDay);

        List<CafeteriaQueueDTO> data = records.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        // Calculate statistics
        Map<String, Object> statistics = calculateCounterStatistics(records);

        return Map.of(
                "date", date,
                "counterName", counterName,
                "totalRecords", records.size(),
                "data", data,
                "statistics", statistics
        );
    }

    /**
     * Get hourly aggregated data for a specific date
     */
    public Map<String, Object> getHourlyAggregatedData(LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

        List<Object[]> rows = queueRepository.getHourlyAggregatedDataForDate(startOfDay, endOfDay);

        Map<String, List<Map<String, Object>>> counterData = new HashMap<>();

        for (Object[] row : rows) {
            Integer hour = ((Number) row[0]).intValue();
            String counterName = (String) row[1];
            Double avgQueue = (Double) row[2];
            Integer maxQueue = (Integer) row[3];
            Integer minQueue = (Integer) row[4];
            Double avgWait = (Double) row[5];
            Double maxWait = (Double) row[6];
            Long recordCount = (Long) row[7];

            Map<String, Object> hourData = new HashMap<>();
            hourData.put("hour", hour);
            hourData.put("timeRange", String.format("%02d:00 - %02d:59", hour, hour));
            hourData.put("avgQueue", avgQueue);
            hourData.put("maxQueue", maxQueue);
            hourData.put("minQueue", minQueue);
            hourData.put("avgWaitMinutes", avgWait);
            hourData.put("maxWaitMinutes", maxWait);
            hourData.put("recordCount", recordCount);

            counterData.computeIfAbsent(counterName, k -> new ArrayList<>()).add(hourData);
        }

        return Map.of(
                "date", date,
                "counters", counterData
        );
    }

    /**
     * Get hourly aggregated data for specific date and counter
     */
    public Map<String, Object> getHourlyAggregatedDataByCounter(LocalDate date, String counterName) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

        List<Object[]> rows = queueRepository.getHourlyAggregatedDataForDateAndCounter(counterName, startOfDay, endOfDay);

        List<Map<String, Object>> hourlyData = new ArrayList<>();

        for (Object[] row : rows) {
            Integer hour = ((Number) row[0]).intValue();
            Double avgQueue = (Double) row[1];
            Integer maxQueue = (Integer) row[2];
            Integer minQueue = (Integer) row[3];
            Double avgWait = (Double) row[4];
            Double maxWait = (Double) row[5];
            Long recordCount = (Long) row[6];

            Map<String, Object> hourData = new HashMap<>();
            hourData.put("hour", hour);
            hourData.put("timeRange", String.format("%02d:00 - %02d:59", hour, hour));
            hourData.put("avgQueue", avgQueue);
            hourData.put("maxQueue", maxQueue);
            hourData.put("minQueue", minQueue);
            hourData.put("avgWaitMinutes", avgWait);
            hourData.put("maxWaitMinutes", maxWait);
            hourData.put("recordCount", recordCount);

            hourlyData.add(hourData);
        }

        return Map.of(
                "date", date,
                "counterName", counterName,
                "hourlyData", hourlyData
        );
    }

    /**
     * Get data for a date range
     */
    public Map<String, Object> getDataByDateRange(LocalDate startDate, LocalDate endDate) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();

        List<CafeteriaQueue> records = queueRepository.findByDateRange(startDateTime, endDateTime);

        // Group by date and counter
        Map<LocalDate, Map<String, List<CafeteriaQueueDTO>>> dateCounterData = new HashMap<>();

        for (CafeteriaQueue queue : records) {
            LocalDate recordDate = queue.getTimestamp().toLocalDate();
            String counter = queue.getCounterName();

            dateCounterData.computeIfAbsent(recordDate, k -> new HashMap<>())
                    .computeIfAbsent(counter, k -> new ArrayList<>())
                    .add(convertToDTO(queue));
        }

        return Map.of(
                "startDate", startDate,
                "endDate", endDate,
                "totalRecords", records.size(),
                "dateRange", dateCounterData
        );
    }

    /**
     * Get daily summary for date range
     */
    public List<Map<String, Object>> getDailySummary(LocalDate startDate, LocalDate endDate) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();

        List<Object[]> rows = queueRepository.getDailySummaryForDateRange(startDateTime, endDateTime);

        List<Map<String, Object>> dailySummary = new ArrayList<>();

        for (Object[] row : rows) {
            LocalDate date = ((java.sql.Date) row[0]).toLocalDate();
            String counterName = (String) row[1];
            Double avgQueue = (Double) row[2];
            Integer maxQueue = (Integer) row[3];
            Integer minQueue = (Integer) row[4];
            Double avgWait = (Double) row[5];
            Double maxWait = (Double) row[6];
            Long recordCount = (Long) row[7];
            Long criticalCount = (Long) row[8];
            Long warningCount = (Long) row[9];

            Map<String, Object> summary = new HashMap<>();
            summary.put("date", date);
            summary.put("counterName", counterName);
            summary.put("avgQueue", avgQueue);
            summary.put("maxQueue", maxQueue);
            summary.put("minQueue", minQueue);
            summary.put("avgWaitMinutes", avgWait);
            summary.put("maxWaitMinutes", maxWait);
            summary.put("recordCount", recordCount);
            summary.put("criticalCount", criticalCount);
            summary.put("warningCount", warningCount);

            dailySummary.add(summary);
        }

        return dailySummary;
    }

    /**
     * Get data by specific timestamp (within a time range)
     */
    public Map<String, Object> getDataByTimestamp(LocalDateTime timestamp, Integer minutesRange) {
        LocalDateTime startTime = timestamp.minusMinutes(minutesRange);
        LocalDateTime endTime = timestamp.plusMinutes(minutesRange);

        List<CafeteriaQueue> records = queueRepository.findByTimestampRange(startTime, endTime);

        Map<String, List<CafeteriaQueueDTO>> counterData = new HashMap<>();
        for (CafeteriaQueue queue : records) {
            String counter = queue.getCounterName();
            counterData.computeIfAbsent(counter, k -> new ArrayList<>())
                    .add(convertToDTO(queue));
        }

        return Map.of(
                "targetTimestamp", timestamp,
                "minutesRange", minutesRange,
                "startTime", startTime,
                "endTime", endTime,
                "totalRecords", records.size(),
                "counters", counterData
        );
    }

    /**
     * Get data for a specific hour of a day
     */
    public Map<String, Object> getDataByHour(LocalDate date, Integer hour) {
        if (hour < 0 || hour > 23) {
            throw new IllegalArgumentException("Hour must be between 0 and 23");
        }

        LocalDateTime startOfHour = date.atTime(hour, 0);
        LocalDateTime endOfHour = startOfHour.plusHours(1);

        List<CafeteriaQueue> records = queueRepository.findByHour(startOfHour, endOfHour);

        Map<String, List<CafeteriaQueueDTO>> counterData = new HashMap<>();
        for (CafeteriaQueue queue : records) {
            String counter = queue.getCounterName();
            counterData.computeIfAbsent(counter, k -> new ArrayList<>())
                    .add(convertToDTO(queue));
        }

        // Get statistics for this hour
        List<Object[]> statRows = queueRepository.getStatisticsForHour(startOfHour, endOfHour);
        Map<String, Map<String, Object>> counterStats = new HashMap<>();

        for (Object[] row : statRows) {
            String counterName = (String) row[0];
            Double avgQueue = (Double) row[1];
            Integer maxQueue = (Integer) row[2];
            Integer minQueue = (Integer) row[3];
            Double avgWait = (Double) row[4];
            Double maxWait = (Double) row[5];
            Long recordCount = (Long) row[6];

            Map<String, Object> stats = new HashMap<>();
            stats.put("avgQueue", avgQueue);
            stats.put("maxQueue", maxQueue);
            stats.put("minQueue", minQueue);
            stats.put("avgWaitMinutes", avgWait);
            stats.put("maxWaitMinutes", maxWait);
            stats.put("recordCount", recordCount);

            counterStats.put(counterName, stats);
        }

        return Map.of(
                "date", date,
                "hour", hour,
                "timeRange", String.format("%02d:00 - %02d:59", hour, hour),
                "totalRecords", records.size(),
                "counters", counterData,
                "statistics", counterStats
        );
    }

    // ==================== HELPER METHODS ====================

    private Map<String, Object> calculateDailyStatistics(List<CafeteriaQueue> records) {
        if (records.isEmpty()) {
            return Map.of("message", "No data available");
        }

        double avgQueue = records.stream()
                .mapToInt(CafeteriaQueue::getQueueCount)
                .average()
                .orElse(0.0);

        int maxQueue = records.stream()
                .mapToInt(CafeteriaQueue::getQueueCount)
                .max()
                .orElse(0);

        int minQueue = records.stream()
                .mapToInt(CafeteriaQueue::getQueueCount)
                .min()
                .orElse(0);

        double avgWait = records.stream()
                .mapToDouble(CafeteriaQueue::getWaitTimeMinutes)
                .average()
                .orElse(0.0);

        double maxWait = records.stream()
                .mapToDouble(CafeteriaQueue::getWaitTimeMinutes)
                .max()
                .orElse(0.0);

        long criticalCount = records.stream()
                .filter(q -> "CRITICAL".equals(q.getStatus()))
                .count();

        long warningCount = records.stream()
                .filter(q -> "WARNING".equals(q.getStatus()))
                .count();

        return Map.of(
                "avgQueue", avgQueue,
                "maxQueue", maxQueue,
                "minQueue", minQueue,
                "avgWaitMinutes", avgWait,
                "maxWaitMinutes", maxWait,
                "criticalCount", criticalCount,
                "warningCount", warningCount
        );
    }

    private Map<String, Object> calculateCounterStatistics(List<CafeteriaQueue> records) {
        if (records.isEmpty()) {
            return Map.of("message", "No data available");
        }

        double avgQueue = records.stream()
                .mapToInt(CafeteriaQueue::getQueueCount)
                .average()
                .orElse(0.0);

        int maxQueue = records.stream()
                .mapToInt(CafeteriaQueue::getQueueCount)
                .max()
                .orElse(0);

        int minQueue = records.stream()
                .mapToInt(CafeteriaQueue::getQueueCount)
                .min()
                .orElse(0);

        double avgWait = records.stream()
                .mapToDouble(CafeteriaQueue::getWaitTimeMinutes)
                .average()
                .orElse(0.0);

        double maxWait = records.stream()
                .mapToDouble(CafeteriaQueue::getWaitTimeMinutes)
                .max()
                .orElse(0.0);

        long criticalCount = records.stream()
                .filter(q -> "CRITICAL".equals(q.getStatus()))
                .count();

        long warningCount = records.stream()
                .filter(q -> "WARNING".equals(q.getStatus()))
                .count();

        return Map.of(
                "avgQueue", avgQueue,
                "maxQueue", maxQueue,
                "minQueue", minQueue,
                "avgWaitMinutes", avgWait,
                "maxWaitMinutes", maxWait,
                "criticalCount", criticalCount,
                "warningCount", warningCount
        );
    }

    /**
     * Get all records within date-time range, optionally filtered by counter
     */
    public Map<String, Object> getAllRecords(LocalDateTime from, LocalDateTime to, String counterName) {
        List<CafeteriaQueue> records;

        // Fetch records based on whether counterName is provided
        if (counterName != null && !counterName.trim().isEmpty()) {
            records = queueRepository.findByCounterAndTimeRange(counterName, from, to);
        } else {
            records = queueRepository.findByTimestampRange(from, to);
        }

        // Group by counter
        Map<String, List<CafeteriaQueueDTO>> counterData = new HashMap<>();
        for (CafeteriaQueue queue : records) {
            String counter = queue.getCounterName();
            counterData.computeIfAbsent(counter, k -> new ArrayList<>())
                    .add(convertToDTO(queue));
        }

        // Calculate statistics
        Map<String, Object> statistics = calculateRangeStatistics(records, counterName);

        Map<String, Object> result = new HashMap<>();
        result.put("from", from);
        result.put("to", to);
        result.put("totalRecords", records.size());

        if (counterName != null && !counterName.trim().isEmpty()) {
            result.put("counterName", counterName);
            result.put("data", counterData.get(counterName));
        } else {
            result.put("counters", counterData);
        }

        result.put("statistics", statistics);

        return result;
    }

    /**
     * Calculate statistics for a time range
     */
    private Map<String, Object> calculateRangeStatistics(List<CafeteriaQueue> records, String counterName) {
        if (records.isEmpty()) {
            return Map.of("message", "No data available for the specified range");
        }

        Map<String, Object> stats = new HashMap<>();

        if (counterName != null && !counterName.trim().isEmpty()) {
            // Single counter statistics
            stats.putAll(calculateCounterStatistics(records));
        } else {
            // Statistics grouped by counter
            Map<String, Map<String, Object>> counterStats = new HashMap<>();

            // Group records by counter
            Map<String, List<CafeteriaQueue>> groupedRecords = records.stream()
                    .collect(Collectors.groupingBy(CafeteriaQueue::getCounterName));

            // Calculate stats for each counter
            for (Map.Entry<String, List<CafeteriaQueue>> entry : groupedRecords.entrySet()) {
                counterStats.put(entry.getKey(), calculateCounterStatistics(entry.getValue()));
            }

            stats.put("byCounter", counterStats);

            // Overall statistics
            stats.put("overall", calculateCounterStatistics(records));
        }

        return stats;
    }

    // ==================== END NEW METHODS ====================

    // Convert entity to DTO
    private CafeteriaQueueDTO convertToDTO(CafeteriaQueue queue) {
        CafeteriaQueueDTO dto = CafeteriaQueueDTO.builder()
                .id(queue.getId())
                .counterName(queue.getCounterName())
                .queueCount(queue.getQueueCount())
                .waitTimeText(queue.getWaitTimeText())
                .waitTimeMinutes(queue.getWaitTimeMinutes())
                .serviceStatus(queue.getServiceStatus())
                .status(queue.getStatus())
                .timestamp(queue.getTimestamp())
                .createdAt(queue.getCreatedAt())
                .build();

        if (queue.getLocation() != null) {
            dto.setLocationId(queue.getLocation().getId());
            dto.setLocationName(queue.getLocation().getName());
            dto.setFloor(queue.getLocation().getFloor());
            dto.setZone(queue.getLocation().getZone());
        }

        return dto;
    }
}