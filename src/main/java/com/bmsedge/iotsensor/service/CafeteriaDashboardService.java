package com.bmsedge.iotsensor.service;

import com.bmsedge.iotsensor.dto.*;
import com.bmsedge.iotsensor.model.*;
import com.bmsedge.iotsensor.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class CafeteriaDashboardService {

    private final CafeteriaAnalyticsRepository analyticsRepository;
    private final CafeteriaLocationRepository locationRepository;
    private final FoodCounterRepository counterRepository;
    private final TenantRepository tenantRepository;

    // ==================== COUNTER-SPECIFIC CONFIGURATION ====================

    /**
     * ✅ Counter names that use manual_wait_time ONLY
     */
    private static final Set<String> MANUAL_WAIT_TIME_COUNTERS = Set.of(
            "Mini Meals",
            "Two Good",
            "mini meals",  // case variations
            "two good",
            "MINI MEALS",
            "TWO GOOD"
    );

    /**
     * ✅ Check if counter uses manual wait time only
     */
    private boolean usesManualWaitTimeOnly(String counterName) {
        if (counterName == null) return false;
        return MANUAL_WAIT_TIME_COUNTERS.stream()
                .anyMatch(name -> name.equalsIgnoreCase(counterName.trim()));
    }

    // ==================== TIME RANGE CALCULATION (FIXED) ====================

    /**
     * ✅ FIXED: Calculate time range based on filter and range parameters
     * This method now properly honors the timeFilter and timeRange parameters
     * instead of always returning current day only.
     *
     * @param timeFilter Filter type: "daily", "weekly", "monthly"
     * @param timeRange Optional explicit hour range (takes precedence over timeFilter)
     * @return Array of [startTime, endTime]
     */
    private LocalDateTime[] calculateTimeRange(String timeFilter, Integer timeRange) {
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime;

        if (timeRange != null && timeRange > 0) {
            // Explicit hour range takes precedence
            startTime = endTime.minusHours(timeRange);
            log.info("📅 Using explicit time range: {} hours ago to now", timeRange);
        } else {
            // Use filter to determine range
            switch (timeFilter != null ? timeFilter.toLowerCase() : "daily") {
                case "daily":
                    // Today from 7:00 AM
                    startTime = LocalDateTime.of(LocalDate.now(), LocalTime.of(7, 0));
                    log.info("📅 Using daily filter: 7:00 AM today to now");
                    break;
                case "weekly":
                    // Last 7 days
                    startTime = endTime.minusDays(7);
                    log.info("📅 Using weekly filter: last 7 days to now");
                    break;
                case "monthly":
                    // Last 30 days
                    startTime = endTime.minusDays(30);
                    log.info("📅 Using monthly filter: last 30 days to now");
                    break;
                default:
                    // Default to current day
                    startTime = LocalDateTime.of(LocalDate.now(), LocalTime.of(7, 0));
                    log.warn("⚠️ Unknown filter '{}', defaulting to daily", timeFilter);
            }
        }

        return new LocalDateTime[]{startTime, endTime};
    }

    /**
     * @deprecated Use calculateTimeRange() instead.
     * This method is kept for backward compatibility but should not be used.
     */
    @Deprecated
    private LocalDateTime getCurrentDayStart() {
        return LocalDateTime.of(LocalDate.now(), LocalTime.of(7, 0));
    }

    /**
     * @deprecated Use calculateTimeRange() instead.
     * This method is kept for backward compatibility but should not be used.
     */
    @Deprecated
    private LocalDateTime getCurrentDayEnd() {
        return LocalDateTime.now();
    }

    // ==================== MAIN DASHBOARD METHOD (FIXED) ====================

    @Transactional(readOnly = true)
    public DashboardDataDTO getDashboardData(String tenantCode, String cafeteriaCode, String timeFilter, Integer timeRange) {
        log.info("📅 Fetching dashboard data for tenant: {}, cafeteria: {}, filter: {}, range: {} hrs",
                tenantCode, cafeteriaCode, timeFilter, timeRange);

        try {
            CafeteriaLocation location = locationRepository.findByTenantCodeAndCafeteriaCode(tenantCode, cafeteriaCode)
                    .orElseThrow(() -> new RuntimeException("Cafeteria not found"));

            // ✅ FIXED: Properly calculate time range from parameters
            LocalDateTime[] calculatedRange = calculateTimeRange(timeFilter, timeRange);
            LocalDateTime startTime = calculatedRange[0];
            LocalDateTime endTime = calculatedRange[1];

            log.info("📅 Time range: {} to {} (Filter: {}, Range: {} hrs)",
                    startTime, endTime, timeFilter, timeRange);

            DashboardDataDTO dashboard = DashboardDataDTO.builder()
                    .occupancyStatus(getOccupancyStatus(location.getId()))
                    .flowData(getFlowData(location.getId(), startTime, endTime))
                    .counterStatus(getCounterStatus(location.getId()))
                    .dwellTimeData(getDwellTimeData(location.getId(), startTime, endTime))
                    .footfallComparison(getFootfallComparison(location.getId(), startTime, endTime))
                    .occupancyTrend(getOccupancyTrend(location.getId(), startTime, endTime))
                    .counterCongestionTrend(getCounterCongestionTrend(location.getId(), startTime, endTime))
                    .counterEfficiency(getCounterEfficiency(location.getId(), startTime, endTime))
                    .todaysVisitors(getTodaysVisitors(location.getId(), startTime, endTime))
                    .avgDwellTime(getAvgDwellTime(location.getId(), startTime, endTime))
                    .peakHours(getPeakHours(location.getId(), startTime, endTime))
                    .lastUpdated(LocalDateTime.now())
                    .build();

            return dashboard;
        } catch (Exception e) {
            log.error("Error fetching dashboard data: {}", e.getMessage(), e);
            throw new RuntimeException("Error fetching dashboard data: " + e.getMessage(), e);
        }
    }

    // ==================== OCCUPANCY STATUS ====================

    private OccupancyStatusDTO getOccupancyStatus(Long cafeteriaLocationId) {
        try {
            // ✅ Get latest data for ALL counters (counter-level only)
            List<CafeteriaAnalytics> latestAnalytics = analyticsRepository.findLatestForAllCounters(cafeteriaLocationId);

            if (latestAnalytics.isEmpty()) {
                log.warn("No occupancy data found for cafeteria location: {}", cafeteriaLocationId);
                return buildEmptyOccupancyStatus();
            }

            // ✅ SUM occupancy from all counters (counter-level data only)
            int totalOccupancy = latestAnalytics.stream()
                    .filter(a -> a.getFoodCounter() != null)  // Only counter-level data
                    .mapToInt(a -> a.getCurrentOccupancy() != null ? a.getCurrentOccupancy() : 0)
                    .sum();

            log.info("✅ Total occupancy across all counters: {}", totalOccupancy);

            // Get capacity from location or use default
            CafeteriaLocation location = locationRepository.findById(cafeteriaLocationId).orElse(null);
            Integer capacity = (location != null && location.getCapacity() != null)
                    ? location.getCapacity()
                    : 728;

            // Calculate percentage
            Double percentage = capacity > 0 ? (totalOccupancy * 100.0) / capacity : 0.0;

            // Determine congestion level based on percentage
            String congestionLevel;
            if (percentage < 40) {
                congestionLevel = "LOW";
            } else if (percentage < 75) {
                congestionLevel = "MEDIUM";
            } else {
                congestionLevel = "HIGH";
            }

            log.info("📊 Occupancy Status: {}/{} ({}%) - {}",
                    totalOccupancy, capacity, String.format("%.1f", percentage), congestionLevel);

            return OccupancyStatusDTO.builder()
                    .currentOccupancy(totalOccupancy)
                    .capacity(capacity)
                    .occupancyPercentage(percentage)
                    .congestionLevel(congestionLevel)
                    .timestamp(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Error in getOccupancyStatus: {}", e.getMessage(), e);
            return buildEmptyOccupancyStatus();
        }
    }

    private OccupancyStatusDTO buildEmptyOccupancyStatus() {
        return OccupancyStatusDTO.builder()
                .currentOccupancy(0)
                .capacity(728)
                .occupancyPercentage(0.0)
                .congestionLevel("LOW")
                .timestamp(LocalDateTime.now())
                .build();
    }

    // ==================== FLOW DATA ====================

    /**
     * ✅ Uses provided time range, counter-level filtering
     */
    private List<FlowDataDTO> getFlowData(Long cafeteriaLocationId, LocalDateTime startTime, LocalDateTime endTime) {
        try {
            log.info("📊 Fetching flow data from {} to {}", startTime, endTime);

            List<CafeteriaAnalytics> analytics = analyticsRepository.findByCafeteriaLocationAndTimeRange(
                    cafeteriaLocationId, startTime, endTime);

            // ✅ Filter counter-level data only
            analytics = analytics.stream()
                    .filter(a -> a.getFoodCounter() != null)
                    .filter(a -> !a.getTimestamp().isAfter(endTime))
                    .collect(Collectors.toList());

            log.info("Found {} counter-level analytics records for flow data", analytics.size());

            return aggregateFlowData(analytics);
        } catch (Exception e) {
            log.error("Error in getFlowData: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    private List<FlowDataDTO> aggregateFlowData(List<CafeteriaAnalytics> analytics) {
        Map<String, List<CafeteriaAnalytics>> grouped = analytics.stream()
                .collect(Collectors.groupingBy(a -> formatTimestamp(a.getTimestamp())));

        return grouped.entrySet().stream()
                .map(entry -> {
                    int inflow = entry.getValue().stream()
                            .mapToInt(a -> a.getInCount() != null ? a.getInCount() : 0)
                            .sum();

                    // Estimate outflow as 90% of inflow
                    int outflow = (int) (inflow * 0.90);

                    return FlowDataDTO.builder()
                            .timestamp(entry.getKey())
                            .inflow(inflow)
                            .outflow(outflow)
                            .netFlow(inflow - outflow)
                            .build();
                })
                .sorted(Comparator.comparing(FlowDataDTO::getTimestamp))
                .collect(Collectors.toList());
    }

    // ==================== COUNTER STATUS ====================

    /**
     * ✅ Get counter status with proper counter-specific dwell time calculation
     */
    private List<CounterStatusDTO> getCounterStatus(Long cafeteriaLocationId) {
        try {
            List<CafeteriaAnalytics> latestAnalytics = analyticsRepository.findLatestForAllCounters(cafeteriaLocationId);

            return latestAnalytics.stream()
                    .filter(analytics -> analytics.getFoodCounter() != null)  // Counter-level only
                    .map(analytics -> {
                        FoodCounter counter = analytics.getFoodCounter();
                        String counterName = counter.getCounterName();

                        // ✅ Get counter-specific average dwell time (considers counter type)
                        // Note: This uses current day for averaging - might need time range parameter
                        Double avgDwell = getCounterSpecificAvgDwellTime(counter.getId(), counterName);

                        log.debug("Counter: {} - AvgDwell: {}min, Queue: {}, Wait: {}min",
                                counterName,
                                avgDwell != null ? String.format("%.1f", avgDwell) : "N/A",
                                analytics.getQueueLength(),
                                analytics.getEstimatedWaitTime());

                        return CounterStatusDTO.builder()
                                .counterName(counterName)
                                .queueLength(analytics.getQueueLength() != null ? analytics.getQueueLength() : 0)
                                .waitTime(avgDwell != null ? avgDwell : 0.0)  // Use calculated avg dwell
                                .congestionLevel(analytics.getCongestionLevel() != null ? analytics.getCongestionLevel() : "LOW")
                                .serviceStatus(analytics.getServiceStatus() != null ? analytics.getServiceStatus() : "UNKNOWN")
                                .lastUpdated(analytics.getTimestamp())
                                .build();
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error in getCounterStatus: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * ✅ Counter-specific dwell time with different logic per counter type
     * NOTE: Currently uses current day - could be enhanced to accept time range
     *
     * Mini Meals & Two Good: Use manual_wait_time ONLY
     * Other counters: Use COALESCE (avgDwellTime → estimatedWaitTime → manualWaitTime)
     */
    private Double getCounterSpecificAvgDwellTime(Long counterId, String counterName) {
        try {
            // TODO: Consider accepting startTime/endTime parameters for custom ranges
            LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.of(7, 0));
            LocalDateTime now = LocalDateTime.now();

            List<CafeteriaAnalytics> counterAnalytics = analyticsRepository
                    .findByFoodCounterIdAndTimestampBetween(counterId, startOfDay, now);

            if (counterAnalytics.isEmpty()) {
                log.debug("No analytics data found for counter ID: {}", counterId);
                return 0.0;
            }

            // ✅ COUNTER-SPECIFIC LOGIC
            if (usesManualWaitTimeOnly(counterName)) {
                // For Mini Meals & Two Good: Use ONLY manual_wait_time
                log.info("🔧 Counter '{}' uses manual_wait_time ONLY", counterName);

                List<Double> manualTimes = counterAnalytics.stream()
                        .map(CafeteriaAnalytics::getManualWaitTime)
                        .filter(Objects::nonNull)
                        .filter(t -> t > 0)
                        .collect(Collectors.toList());

                if (manualTimes.isEmpty()) {
                    log.warn("⚠️ No manual_wait_time data for counter '{}'", counterName);
                    return 0.0;
                }

                Double avgManual = manualTimes.stream()
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0.0);

                log.info("✅ Counter '{}' - avg manual_wait_time: {}min from {} records",
                        counterName, String.format("%.2f", avgManual), manualTimes.size());

                return avgManual;

            } else {
                // For other counters: Use COALESCE logic
                log.info("🔧 Counter '{}' uses COALESCE logic (avgDwell → estWait → manual)", counterName);

                List<Double> validTimes = new ArrayList<>();
                int usedAvgDwell = 0, usedEstWait = 0, usedManualWait = 0;

                for (CafeteriaAnalytics a : counterAnalytics) {
                    Double time = null;

                    // COALESCE: avgDwellTime → estimatedWaitTime → manualWaitTime
                    if (a.getAvgDwellTime() != null && a.getAvgDwellTime() > 0) {
                        time = a.getAvgDwellTime();
                        usedAvgDwell++;
                    } else if (a.getEstimatedWaitTime() != null && a.getEstimatedWaitTime() > 0) {
                        time = a.getEstimatedWaitTime();
                        usedEstWait++;
                    } else if (a.getManualWaitTime() != null && a.getManualWaitTime() > 0) {
                        time = a.getManualWaitTime();
                        usedManualWait++;
                    }

                    if (time != null && time > 0) {
                        validTimes.add(time);
                    }
                }

                if (validTimes.isEmpty()) {
                    return 0.0;
                }

                Double avgDwell = validTimes.stream()
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0.0);

                log.info("✅ Counter '{}' - avg={}min (avgD:{}, estW:{}, manW:{} from {} records)",
                        counterName, String.format("%.2f", avgDwell),
                        usedAvgDwell, usedEstWait, usedManualWait, validTimes.size());

                return avgDwell;
            }

        } catch (Exception e) {
            log.error("Error calculating counter-specific avg dwell time: {}", e.getMessage());
            return 0.0;
        }
    }

    // ==================== DWELL TIME DISTRIBUTION ====================

    /**
     * ✅ Uses provided time range, COUNTER-LEVEL ONLY
     * Uses counter-specific logic for each counter type
     */
    private List<DwellTimeDataDTO> getDwellTimeData(Long cafeteriaLocationId, LocalDateTime startTime, LocalDateTime endTime) {
        try {
            log.info("📊 Fetching dwell time data from {} to {}", startTime, endTime);

            List<CafeteriaAnalytics> analytics = analyticsRepository.findByCafeteriaLocationAndTimeRange(
                    cafeteriaLocationId, startTime, endTime);

            // ✅ CRITICAL: Filter counter-level data only (food_counter_id NOT NULL)
            analytics = analytics.stream()
                    .filter(a -> a.getFoodCounter() != null)
                    .collect(Collectors.toList());

            log.info("📊 Found {} counter-level records for dwell time analysis", analytics.size());

            // Group by individual minute values using counter-specific logic
            Map<Integer, Long> dwellTimeByMinute = new TreeMap<>();
            int usedManual = 0, usedAvgDwell = 0, usedEstWait = 0, usedManualFallback = 0, skipped = 0;

            for (CafeteriaAnalytics a : analytics) {
                String counterName = a.getFoodCounter() != null ? a.getFoodCounter().getCounterName() : null;
                Double dwell = null;

                if (usesManualWaitTimeOnly(counterName)) {
                    // Mini Meals & Two Good: Use ONLY manual_wait_time
                    if (a.getManualWaitTime() != null && a.getManualWaitTime() > 0) {
                        dwell = a.getManualWaitTime();
                        usedManual++;
                    }
                } else {
                    // Other counters: COALESCE logic
                    if (a.getAvgDwellTime() != null && a.getAvgDwellTime() > 0) {
                        dwell = a.getAvgDwellTime();
                        usedAvgDwell++;
                    } else if (a.getEstimatedWaitTime() != null && a.getEstimatedWaitTime() > 0) {
                        dwell = a.getEstimatedWaitTime();
                        usedEstWait++;
                    } else if (a.getManualWaitTime() != null && a.getManualWaitTime() > 0) {
                        dwell = a.getManualWaitTime();
                        usedManualFallback++;
                    }
                }

                if (dwell == null || dwell <= 0) {
                    skipped++;
                    continue;
                }

                int minutes = (int) Math.round(dwell);
                dwellTimeByMinute.put(minutes, dwellTimeByMinute.getOrDefault(minutes, 0L) + 1);
            }

            log.info("📊 Dwell data sources - manual(Mini/Two): {}, avgDwell: {}, estWait: {}, manualFallback: {}, skipped: {}",
                    usedManual, usedAvgDwell, usedEstWait, usedManualFallback, skipped);

            if (dwellTimeByMinute.isEmpty()) {
                log.warn("No dwell time data available for cafeteria location: {}", cafeteriaLocationId);
                return new ArrayList<>();
            }

            long total = dwellTimeByMinute.values().stream().mapToLong(Long::longValue).sum();

            log.info("Found {} unique dwell time values, total {} records",
                    dwellTimeByMinute.size(), total);

            return dwellTimeByMinute.entrySet().stream()
                    .map(entry -> {
                        int minutes = entry.getKey();
                        long count = entry.getValue();
                        double percentage = total > 0 ? (count * 100.0) / total : 0.0;

                        String timeRange = minutes + " min";

                        return DwellTimeDataDTO.builder()
                                .timeRange(timeRange)
                                .count(Long.valueOf(count).intValue())
                                .percentage(percentage)
                                .build();
                    })
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error in getDwellTimeData: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    // ==================== COUNTER-SPECIFIC DWELL TIME ====================

    /**
     * ✅ FIXED: Get dwell time data for a specific counter with proper time range handling
     */
    @Transactional(readOnly = true)
    public CounterDwellTimeResponseDTO getDwellTimeByCounter(
            String tenantCode,
            String cafeteriaCode,
            String counterName,
            String timeFilter,
            Integer timeRange
    ) {
        log.info("Fetching counter-specific dwell time for counter: {}, filter: {}, range: {}",
                counterName, timeFilter, timeRange);

        try {
            CafeteriaLocation location = locationRepository.findByTenantCodeAndCafeteriaCode(tenantCode, cafeteriaCode)
                    .orElseThrow(() -> new RuntimeException("Cafeteria not found: " + tenantCode + "/" + cafeteriaCode));

            FoodCounter counter = counterRepository.findByCounterNameAndCafeteriaLocation(counterName, location)
                    .orElseThrow(() -> new RuntimeException("Counter not found: " + counterName));

            log.info("Found counter: {} with ID: {}", counterName, counter.getId());

            // ✅ FIXED: Use proper time range calculation
            LocalDateTime[] calculatedRange = calculateTimeRange(timeFilter, timeRange);
            LocalDateTime startTime = calculatedRange[0];
            LocalDateTime endTime = calculatedRange[1];

            log.info("📅 Using time range: {} to {}", startTime, endTime);

            // Get analytics data for this specific counter
            List<CafeteriaAnalytics> analytics = analyticsRepository
                    .findByFoodCounterIdAndTimestampBetween(counter.getId(), startTime, endTime);

            log.info("✅ Found {} analytics records for counter: {}", analytics.size(), counterName);

            // Calculate distribution and stats using counter-specific logic
            List<DwellTimeDataDTO> dwellTimeData = calculateDwellTimeDistributionForCounter(analytics, counterName);

            CounterStatsDTO stats = calculateCounterStatsWithFallback(
                    analytics,
                    counterName,
                    counter.getId(),
                    location.getId(),
                    startTime,
                    endTime
            );

            return CounterDwellTimeResponseDTO.builder()
                    .counterName(counterName)
                    .dwellTimeData(dwellTimeData)
                    .stats(stats)
                    .timestamp(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Error fetching counter dwell time: {}", e.getMessage(), e);

            return CounterDwellTimeResponseDTO.builder()
                    .counterName(counterName)
                    .dwellTimeData(new ArrayList<>())
                    .stats(CounterStatsDTO.builder()
                            .totalVisitors(0)
                            .avgWaitTime(0.0)
                            .minWaitTime(0)
                            .maxWaitTime(0)
                            .mostCommonWaitTime("No data")
                            .peakQueueLength(0)
                            .build())
                    .timestamp(LocalDateTime.now())
                    .build();
        }
    }

    @Transactional(readOnly = true)
    public List<String> getAvailableCounters(String tenantCode, String cafeteriaCode) {
        try {
            CafeteriaLocation location = locationRepository.findByTenantCodeAndCafeteriaCode(tenantCode, cafeteriaCode)
                    .orElseThrow(() -> new RuntimeException("Cafeteria not found: " + tenantCode + "/" + cafeteriaCode));

            List<FoodCounter> counters = counterRepository.findByCafeteriaLocation(location);

            List<String> counterNames = counters.stream()
                    .map(FoodCounter::getCounterName)
                    .sorted()
                    .collect(Collectors.toList());

            log.info("Found {} counters for cafeteria: {}", counterNames.size(), cafeteriaCode);

            return counterNames;

        } catch (Exception e) {
            log.error("Error fetching available counters: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * ✅ Calculate dwell time distribution for specific counter with counter-specific logic
     */
    private List<DwellTimeDataDTO> calculateDwellTimeDistributionForCounter(
            List<CafeteriaAnalytics> analytics,
            String counterName) {

        Map<Integer, Long> dwellTimeByMinute = new TreeMap<>();
        int usedManual = 0, usedAvgDwell = 0, usedEstWait = 0, usedManualFallback = 0, skipped = 0;

        for (CafeteriaAnalytics a : analytics) {
            Double dwell = null;

            if (usesManualWaitTimeOnly(counterName)) {
                // Mini Meals & Two Good: Use ONLY manual_wait_time
                if (a.getManualWaitTime() != null && a.getManualWaitTime() > 0) {
                    dwell = a.getManualWaitTime();
                    usedManual++;
                }
            } else {
                // Other counters: COALESCE logic
                if (a.getAvgDwellTime() != null && a.getAvgDwellTime() > 0) {
                    dwell = a.getAvgDwellTime();
                    usedAvgDwell++;
                } else if (a.getEstimatedWaitTime() != null && a.getEstimatedWaitTime() > 0) {
                    dwell = a.getEstimatedWaitTime();
                    usedEstWait++;
                } else if (a.getManualWaitTime() != null && a.getManualWaitTime() > 0) {
                    dwell = a.getManualWaitTime();
                    usedManualFallback++;
                }
            }

            if (dwell == null || dwell <= 0) {
                skipped++;
                continue;
            }

            int minutes = (int) Math.round(dwell);
            dwellTimeByMinute.put(minutes, dwellTimeByMinute.getOrDefault(minutes, 0L) + 1);
        }

        log.info("📊 Distribution for '{}' - manual: {}, avgD: {}, estW: {}, manualFB: {}, skipped: {}",
                counterName, usedManual, usedAvgDwell, usedEstWait, usedManualFallback, skipped);

        if (dwellTimeByMinute.isEmpty()) {
            log.warn("⚠️ No valid time data found for counter '{}'", counterName);
            return new ArrayList<>();
        }

        long total = dwellTimeByMinute.values().stream().mapToLong(Long::longValue).sum();

        return dwellTimeByMinute.entrySet().stream()
                .map(entry -> {
                    int minutes = entry.getKey();
                    long count = entry.getValue();
                    double percentage = total > 0 ? (count * 100.0) / total : 0.0;

                    return DwellTimeDataDTO.builder()
                            .timeRange(minutes + " min")
                            .count(Long.valueOf(count).intValue())
                            .percentage(percentage)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * ✅ Calculate counter statistics using OCCUPANCY-BASED deltas
     */
    private CounterStatsDTO calculateCounterStatsWithFallback(
            List<CafeteriaAnalytics> analytics,
            String counterName,
            Long counterId,
            Long cafeteriaLocationId,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        if (analytics.isEmpty()) {
            log.warn("No analytics data for counter: {}", counterName);
            return buildEmptyCounterStats();
        }

        // ✅ OCCUPANCY-BASED DELTA: Calculate total entries from current_occupancy
        Integer totalVisitors = calculateTotalEntriesFromOccupancy(analytics);

        log.info("✅ Counter '{}' - Total entries (occupancy deltas): {}", counterName, totalVisitors);

        // ✅ Use currentOccupancy for peak queue
        Integer peakQueue = analytics.stream()
                .filter(a -> a.getCurrentOccupancy() != null)
                .mapToInt(CafeteriaAnalytics::getCurrentOccupancy)
                .max()
                .orElse(0);

        log.info("✅ Counter '{}' - Peak occupancy: {}", counterName, peakQueue);

        // ✅ Calculate wait time statistics using counter-specific logic
        List<Double> validTimes = new ArrayList<>();

        if (usesManualWaitTimeOnly(counterName)) {
            // Mini Meals & Two Good: Use ONLY manual_wait_time
            validTimes = analytics.stream()
                    .map(CafeteriaAnalytics::getManualWaitTime)
                    .filter(Objects::nonNull)
                    .filter(t -> t > 0)
                    .collect(Collectors.toList());

            log.info("📊 Stats for '{}' - Using {} manual_wait_time records", counterName, validTimes.size());
        } else {
            // Other counters: COALESCE logic
            int usedAvgDwell = 0, usedEstWait = 0, usedManualWait = 0;

            for (CafeteriaAnalytics a : analytics) {
                Double time = null;

                if (a.getAvgDwellTime() != null && a.getAvgDwellTime() > 0) {
                    time = a.getAvgDwellTime();
                    usedAvgDwell++;
                } else if (a.getEstimatedWaitTime() != null && a.getEstimatedWaitTime() > 0) {
                    time = a.getEstimatedWaitTime();
                    usedEstWait++;
                } else if (a.getManualWaitTime() != null && a.getManualWaitTime() > 0) {
                    time = a.getManualWaitTime();
                    usedManualWait++;
                }

                if (time != null && time > 0) {
                    validTimes.add(time);
                }
            }

            log.info("📊 Stats for '{}' - avgD:{}, estW:{}, manW:{} from {} records",
                    counterName, usedAvgDwell, usedEstWait, usedManualWait, validTimes.size());
        }

        if (validTimes.isEmpty()) {
            log.warn("⚠️ No valid time data for counter '{}'", counterName);
            return CounterStatsDTO.builder()
                    .totalVisitors(totalVisitors)
                    .avgWaitTime(0.0)
                    .minWaitTime(0)
                    .maxWaitTime(0)
                    .mostCommonWaitTime("Peak occupancy: " + peakQueue + " people")
                    .peakQueueLength(peakQueue)
                    .build();
        }

        Double avgWaitTime = validTimes.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        Integer minWaitTime = validTimes.stream()
                .mapToInt(d -> (int) Math.round(d))
                .min()
                .orElse(0);

        Integer maxWaitTime = validTimes.stream()
                .mapToInt(d -> (int) Math.round(d))
                .max()
                .orElse(0);

        String mostCommonDisplay = "Peak occupancy: " + peakQueue + " people";

        log.info("✅ Counter '{}' stats: entries={}, avg={}min, min={}min, max={}min, peakOcc={}",
                counterName, totalVisitors, String.format("%.2f", avgWaitTime), minWaitTime, maxWaitTime, peakQueue);

        return CounterStatsDTO.builder()
                .totalVisitors(totalVisitors)
                .avgWaitTime(Math.round(avgWaitTime * 100.0) / 100.0)
                .minWaitTime(minWaitTime)
                .maxWaitTime(maxWaitTime)
                .mostCommonWaitTime(mostCommonDisplay)
                .peakQueueLength(peakQueue)
                .build();
    }

    private CounterStatsDTO buildEmptyCounterStats() {
        return CounterStatsDTO.builder()
                .totalVisitors(0)
                .avgWaitTime(0.0)
                .minWaitTime(0)
                .maxWaitTime(0)
                .mostCommonWaitTime("Peak occupancy: 0 people")
                .peakQueueLength(0)
                .build();
    }

    // ==================== FOOTFALL COMPARISON ====================

    /**
     * ✅ Uses provided time range
     */
    private List<FootfallComparisonDTO> getFootfallComparison(Long cafeteriaLocationId, LocalDateTime startTime, LocalDateTime endTime) {
        try {
            List<Object[]> cafeteriaFlow = analyticsRepository.getCafeteriaHourlyFlow(cafeteriaLocationId, startTime, endTime);

            Map<Integer, Integer> counterFootfallByHour = new HashMap<>();
            try {
                List<Object[]> counterFlow = analyticsRepository.getCounterHourlyFlow(cafeteriaLocationId, startTime, endTime);
                counterFootfallByHour = counterFlow.stream()
                        .collect(Collectors.toMap(
                                row -> ((Number) row[0]).intValue(),
                                row -> ((Number) row[1]).intValue(),
                                (existing, replacement) -> existing
                        ));
                log.info("✅ Using real counter footfall data");
            } catch (Exception e) {
                log.warn("⚠️ getCounterHourlyFlow not available: {}", e.getMessage());
            }

            final Map<Integer, Integer> finalCounterFootfall = counterFootfallByHour;

            return cafeteriaFlow.stream()
                    .map(row -> {
                        Integer hour = ((Number) row[0]).intValue();
                        Integer cafeteriaFootfall = ((Number) row[1]).intValue();

                        Integer countersFootfall = finalCounterFootfall.getOrDefault(hour, (int) (cafeteriaFootfall * 0.75));

                        Double ratio = countersFootfall > 0 ? cafeteriaFootfall.doubleValue() / countersFootfall : 1.0;

                        String insight = "Normal flow";
                        if (countersFootfall == 0) {
                            insight = "No counter data available";
                        } else if (ratio > 1.5) {
                            insight = "Counter hopping detected";
                        } else if (ratio < 0.8) {
                            insight = "Potential congestion at counters";
                        }

                        return FootfallComparisonDTO.builder()
                                .timestamp(String.format("%02d:00", hour))
                                .cafeteriaFootfall(cafeteriaFootfall)
                                .countersFootfall(countersFootfall)
                                .ratio(ratio)
                                .insight(insight)
                                .build();
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error in getFootfallComparison: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    // ==================== OCCUPANCY TREND ====================

    /**
     * ✅ Uses provided time range, COUNTER-LEVEL ONLY
     */
    private List<OccupancyTrendDTO> getOccupancyTrend(Long cafeteriaLocationId, LocalDateTime startTime, LocalDateTime endTime) {
        try {
            log.info("📊 Fetching occupancy trend from {} to {}", startTime, endTime);

            List<CafeteriaAnalytics> analytics = analyticsRepository.findByCafeteriaLocationAndTimeRange(
                    cafeteriaLocationId, startTime, endTime);

            // ✅ Filter counter-level data only
            analytics = analytics.stream()
                    .filter(a -> a.getFoodCounter() != null)
                    .filter(a -> !a.getTimestamp().isAfter(endTime))
                    .collect(Collectors.toList());

            log.info("📊 Found {} counter-level records for occupancy trend", analytics.size());

            return aggregateOccupancyTrend(analytics);
        } catch (Exception e) {
            log.error("Error in getOccupancyTrend: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    private List<OccupancyTrendDTO> aggregateOccupancyTrend(List<CafeteriaAnalytics> analytics) {
        Map<String, List<CafeteriaAnalytics>> grouped = analytics.stream()
                .collect(Collectors.groupingBy(a -> formatTimestamp(a.getTimestamp())));

        return grouped.entrySet().stream()
                .map(entry -> {
                    int avgOccupancy = (int) entry.getValue().stream()
                            .mapToInt(a -> a.getCurrentOccupancy() != null ? a.getCurrentOccupancy() : 0)
                            .average()
                            .orElse(0);

                    return OccupancyTrendDTO.builder()
                            .timestamp(entry.getKey())
                            .occupancy(avgOccupancy)
                            .hour(extractHour(entry.getKey()))
                            .build();
                })
                .sorted(Comparator.comparing(OccupancyTrendDTO::getTimestamp))
                .collect(Collectors.toList());
    }

    // ==================== COUNTER CONGESTION TREND ====================

    /**
     * ✅ Uses provided time range, COUNTER-LEVEL ONLY
     */
    private List<CounterCongestionTrendDTO> getCounterCongestionTrend(
            Long cafeteriaLocationId,
            LocalDateTime startTime,
            LocalDateTime endTime) {
        try {
            log.info("📊 Fetching congestion trend from {} to {}", startTime, endTime);

            List<CafeteriaAnalytics> analytics = analyticsRepository.findByCafeteriaLocationAndTimeRange(
                    cafeteriaLocationId, startTime, endTime);

            // ✅ Filter counter-level data only
            analytics = analytics.stream()
                    .filter(a -> a.getFoodCounter() != null)
                    .filter(a -> !a.getTimestamp().isAfter(endTime))
                    .collect(Collectors.toList());

            log.info("📊 Found {} counter-level records for congestion trend", analytics.size());

            // ✅ Check data quality
            long withOccupancy = analytics.stream()
                    .filter(a -> a.getCurrentOccupancy() != null && a.getCurrentOccupancy() > 0)
                    .count();

            log.info("📊 Records with occupancy data: {}/{}", withOccupancy, analytics.size());

            if (analytics.isEmpty()) {
                log.warn("⚠️ No counter-level analytics data available for congestion trend");
                return new ArrayList<>();
            }

            return aggregateCounterCongestion(analytics);
        } catch (Exception e) {
            log.error("❌ Error in getCounterCongestionTrend: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * ✅ Aggregate counter congestion using CURRENT_OCCUPANCY
     */
    private List<CounterCongestionTrendDTO> aggregateCounterCongestion(List<CafeteriaAnalytics> analytics) {

        if (analytics.isEmpty()) {
            log.warn("⚠️ No analytics data available for congestion trend");
            return new ArrayList<>();
        }

        // Group analytics by time bucket
        Map<String, List<CafeteriaAnalytics>> grouped = analytics.stream()
                .collect(Collectors.groupingBy(a -> formatTimestamp(a.getTimestamp())));

        log.info("📊 Processing {} time buckets for congestion trend", grouped.size());

        List<CounterCongestionTrendDTO> result = grouped.entrySet().stream()
                .map(entry -> {
                    // ✅ Get max occupancy per counter for this time bucket
                    Map<String, Integer> counterQueues = entry.getValue().stream()
                            .collect(Collectors.groupingBy(
                                    a -> a.getFoodCounter().getCounterName(),
                                    Collectors.mapping(
                                            a -> a.getCurrentOccupancy() != null ? a.getCurrentOccupancy() : 0,
                                            Collectors.maxBy(Integer::compare)
                                    )
                            ))
                            .entrySet().stream()
                            .filter(e -> e.getValue().isPresent())
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    e -> e.getValue().get()
                            ));

                    if (!counterQueues.isEmpty()) {
                        log.debug("✅ Time: {} - Found counter data for {} counters",
                                entry.getKey(), counterQueues.size());
                    } else {
                        log.warn("⚠️ Time: {} - No occupancy data available",
                                entry.getKey());
                    }

                    return CounterCongestionTrendDTO.builder()
                            .timestamp(entry.getKey())
                            .counterQueues(counterQueues)
                            .build();
                })
                .sorted(Comparator.comparing(CounterCongestionTrendDTO::getTimestamp))
                .collect(Collectors.toList());

        log.info("✅ Generated {} congestion trend records", result.size());

        return result;
    }

    // ==================== COUNTER MASTER DATA ====================

    /**
     * ✅ FIXED: Get master data for all counters with proper time range handling
     */
    @Transactional(readOnly = true)
    public CounterMasterDataResponseDTO getCounterMasterData(
            String tenantCode,
            String cafeteriaCode,
            String timeFilter,
            Integer timeRange,
            Boolean latestOnly) {

        log.info("📋 Fetching counter master data for {}/{} - Latest: {}, Filter: {}, Range: {}",
                tenantCode, cafeteriaCode, latestOnly, timeFilter, timeRange);

        try {
            CafeteriaLocation location = locationRepository.findByTenantCodeAndCafeteriaCode(tenantCode, cafeteriaCode)
                    .orElseThrow(() -> new RuntimeException("Cafeteria not found: " + tenantCode + "/" + cafeteriaCode));

            // ✅ FIXED: Use proper time range calculation
            LocalDateTime[] calculatedRange = calculateTimeRange(timeFilter, timeRange);
            LocalDateTime startTime = calculatedRange[0];
            LocalDateTime endTime = calculatedRange[1];

            log.info("📅 Time range: {} to {}", startTime, endTime);

            // Choose query based on latestOnly flag
            List<Object[]> results;
            if (latestOnly != null && latestOnly) {
                results = analyticsRepository.getLatestMasterDataForAllCounters(location.getId(), startTime);
                log.info("✅ Fetched latest data for {} counters", results.size());
            } else {
                results = analyticsRepository.getMasterDataForCounters(location.getId(), startTime, endTime);
                log.info("✅ Fetched {} historical records", results.size());
            }

            if (results.isEmpty()) {
                log.warn("⚠️ No master data found for cafeteria: {}", cafeteriaCode);
                return buildEmptyMasterDataResponse(location, startTime, endTime);
            }

            // Convert to DTOs
            List<CounterMasterDataDTO> masterDataList = results.stream()
                    .map(this::mapToCounterMasterDataDTO)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            log.info("📊 Successfully mapped {} master data records", masterDataList.size());

            // Calculate summary statistics
            CounterMasterDataResponseDTO.SummaryStats summary = calculateMasterDataSummary(masterDataList);

            return CounterMasterDataResponseDTO.builder()
                    .cafeteriaName(location.getCafeteriaName())
                    .cafeteriaCode(location.getCafeteriaCode())
                    .reportGeneratedAt(LocalDateTime.now())
                    .timeRange(formatTimeRange(startTime, endTime))
                    .summary(summary)
                    .data(masterDataList)
                    .totalRecords(masterDataList.size())
                    .recordsReturned(masterDataList.size())
                    .build();

        } catch (Exception e) {
            log.error("❌ Error fetching counter master data: {}", e.getMessage(), e);
            throw new RuntimeException("Error fetching counter master data: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ Map database result to DTO
     */
    private CounterMasterDataDTO mapToCounterMasterDataDTO(Object[] row) {
        try {
            Long counterId = row[0] != null ? ((Number) row[0]).longValue() : null;
            String counterName = (String) row[1];
            String counterCode = (String) row[2];
            String counterType = (String) row[3];
            String deviceId = (String) row[4];
            String cafeteriaName = (String) row[5];
            String cafeteriaCode = (String) row[6];
            Integer floor = row[7] != null ? ((Number) row[7]).intValue() : null;
            String zone = (String) row[8];
            LocalDateTime timestamp = (LocalDateTime) row[9];
            Integer currentOccupancy = row[10] != null ? ((Number) row[10]).intValue() : null;
            Integer capacity = row[11] != null ? ((Number) row[11]).intValue() : null;
            Double occupancyPercentage = row[12] != null ? ((Number) row[12]).doubleValue() : null;
            Double avgDwellTime = row[13] != null ? ((Number) row[13]).doubleValue() : null;
            Double estimatedWaitTime = row[14] != null ? ((Number) row[14]).doubleValue() : null;
            Double manualWaitTime = row[15] != null ? ((Number) row[15]).doubleValue() : null;
            Integer queueLength = row[16] != null ? ((Number) row[16]).intValue() : null;
            String congestionLevel = (String) row[17];
            String serviceStatus = (String) row[18];
            Integer inCount = row[19] != null ? ((Number) row[19]).intValue() : null;
            Double maxDwellTime = row[20] != null ? ((Number) row[20]).doubleValue() : null;
            Boolean isActive = row[21] != null ? (Boolean) row[21] : true;

            String locationDisplay = buildLocationDisplay(cafeteriaName, floor, zone);

            // ✅ Calculate effective wait time using counter-specific logic
            Double effectiveWaitTime = null;
            String waitTimeSource = "N/A";

            if (usesManualWaitTimeOnly(counterName)) {
                // Mini Meals & Two Good: Use ONLY manual_wait_time
                if (manualWaitTime != null && manualWaitTime > 0) {
                    effectiveWaitTime = manualWaitTime;
                    waitTimeSource = "manual (Mini/Two)";
                }
            } else {
                // Other counters: COALESCE logic
                if (avgDwellTime != null && avgDwellTime > 0) {
                    effectiveWaitTime = avgDwellTime;
                    waitTimeSource = "avgDwell";
                } else if (estimatedWaitTime != null && estimatedWaitTime > 0) {
                    effectiveWaitTime = estimatedWaitTime;
                    waitTimeSource = "estimated";
                } else if (manualWaitTime != null && manualWaitTime > 0) {
                    effectiveWaitTime = manualWaitTime;
                    waitTimeSource = "manual";
                }
            }

            String waitTimeDisplay = effectiveWaitTime != null
                    ? String.format("%.1f min", effectiveWaitTime)
                    : "N/A";

            log.debug("Counter: {} - Wait Time: {} (source: {})",
                    counterName, waitTimeDisplay, waitTimeSource);

            String statusDisplay = formatStatusDisplay(serviceStatus, congestionLevel);

            return CounterMasterDataDTO.builder()
                    .counterId(counterId)
                    .counterName(counterName)
                    .counterCode(counterCode)
                    .counterType(counterType)
                    .deviceId(deviceId)
                    .cafeteriaName(cafeteriaName)
                    .cafeteriaCode(cafeteriaCode)
                    .location(locationDisplay)
                    .timestamp(timestamp)
                    .currentOccupancy(currentOccupancy)
                    .capacity(capacity)
                    .occupancyPercentage(occupancyPercentage)
                    .avgDwellTime(avgDwellTime)
                    .estimatedWaitTime(estimatedWaitTime)
                    .manualWaitTime(manualWaitTime)
                    .queueLength(queueLength)
                    .congestionLevel(congestionLevel)
                    .serviceStatus(serviceStatus)
                    .inCount(inCount)
                    .maxDwellTime(maxDwellTime)
                    .waitTimeDisplay(waitTimeDisplay)
                    .statusDisplay(statusDisplay)
                    .isActive(isActive)
                    .build();

        } catch (Exception e) {
            log.error("❌ Error mapping counter master data row: {}", e.getMessage(), e);
            return null;
        }
    }

    private String buildLocationDisplay(String cafeteriaName, Integer floor, String zone) {
        StringBuilder location = new StringBuilder();

        if (cafeteriaName != null) {
            location.append(cafeteriaName);
        }

        if (floor != null) {
            if (location.length() > 0) {
                location.append(" - ");
            }
            location.append("Floor ").append(floor);
        }

        if (zone != null && !zone.trim().isEmpty()) {
            if (location.length() > 0) {
                location.append(", ");
            }
            location.append("Zone ").append(zone);
        }

        return location.length() > 0 ? location.toString() : "N/A";
    }

    private CounterMasterDataResponseDTO.SummaryStats calculateMasterDataSummary(List<CounterMasterDataDTO> data) {

        if (data.isEmpty()) {
            log.warn("⚠️ No data available for summary calculation");
            return CounterMasterDataResponseDTO.SummaryStats.builder()
                    .totalCounters(0)
                    .activeCounters(0)
                    .averageOccupancy(0)
                    .averageWaitTime(0.0)
                    .mostCongestedCounter("N/A")
                    .leastCongestedCounter("N/A")
                    .build();
        }

        long activeCount = data.stream()
                .filter(d -> d.getIsActive() != null && d.getIsActive())
                .count();

        log.info("📊 Summary: {} total counters, {} active", data.size(), activeCount);

        int avgOccupancy = (int) data.stream()
                .filter(d -> d.getCurrentOccupancy() != null)
                .mapToInt(CounterMasterDataDTO::getCurrentOccupancy)
                .average()
                .orElse(0.0);

        // Calculate average wait time using counter-specific logic
        double avgWaitTime = data.stream()
                .map(d -> {
                    // Apply counter-specific logic
                    if (usesManualWaitTimeOnly(d.getCounterName())) {
                        // Mini Meals & Two Good: Use manual wait time only
                        if (d.getManualWaitTime() != null && d.getManualWaitTime() > 0) {
                            return d.getManualWaitTime();
                        }
                    } else {
                        // Other counters: COALESCE
                        if (d.getAvgDwellTime() != null && d.getAvgDwellTime() > 0) {
                            return d.getAvgDwellTime();
                        } else if (d.getEstimatedWaitTime() != null && d.getEstimatedWaitTime() > 0) {
                            return d.getEstimatedWaitTime();
                        } else if (d.getManualWaitTime() != null && d.getManualWaitTime() > 0) {
                            return d.getManualWaitTime();
                        }
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        log.info("📊 Average occupancy: {}, Average wait time: {:.2f} min",
                avgOccupancy, avgWaitTime);

        String mostCongested = data.stream()
                .filter(d -> d.getCurrentOccupancy() != null)
                .max(Comparator.comparing(CounterMasterDataDTO::getCurrentOccupancy))
                .map(d -> d.getCounterName() + " (" + d.getCurrentOccupancy() + ")")
                .orElse("N/A");

        String leastCongested = data.stream()
                .filter(d -> d.getCurrentOccupancy() != null)
                .filter(d -> d.getCurrentOccupancy() > 0)
                .min(Comparator.comparing(CounterMasterDataDTO::getCurrentOccupancy))
                .map(d -> d.getCounterName() + " (" + d.getCurrentOccupancy() + ")")
                .orElse("N/A");

        log.info("📊 Most congested: {}, Least congested: {}", mostCongested, leastCongested);

        return CounterMasterDataResponseDTO.SummaryStats.builder()
                .totalCounters(data.size())
                .activeCounters((int) activeCount)
                .averageOccupancy(avgOccupancy)
                .averageWaitTime(Math.round(avgWaitTime * 100.0) / 100.0)
                .mostCongestedCounter(mostCongested)
                .leastCongestedCounter(leastCongested)
                .build();
    }

    private String formatStatusDisplay(String serviceStatus, String congestionLevel) {
        if (serviceStatus == null && congestionLevel == null) {
            return "Unknown";
        }

        String status = serviceStatus != null ? serviceStatus : "UNKNOWN";
        String congestion = congestionLevel != null ? congestionLevel : "LOW";

        String statusText = switch (status) {
            case "READY_TO_SERVE" -> "Ready to Serve";
            case "SHORT_WAIT" -> "Short Wait";
            case "MEDIUM_WAIT" -> "Medium Wait";
            case "LONG_WAIT" -> "Long Wait";
            default -> "Unknown";
        };

        String congestionIndicator = switch (congestion) {
            case "LOW" -> "🟢";
            case "MEDIUM" -> "🟡";
            case "HIGH" -> "🔴";
            default -> "⚪";
        };

        return congestionIndicator + " " + statusText + " (" + congestion + ")";
    }

    private String formatTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");
        return startTime.format(formatter) + " - " + endTime.format(formatter);
    }

    private CounterMasterDataResponseDTO buildEmptyMasterDataResponse(
            CafeteriaLocation location,
            LocalDateTime startTime,
            LocalDateTime endTime) {

        log.warn("⚠️ Building empty master data response");

        return CounterMasterDataResponseDTO.builder()
                .cafeteriaName(location.getCafeteriaName())
                .cafeteriaCode(location.getCafeteriaCode())
                .reportGeneratedAt(LocalDateTime.now())
                .timeRange(formatTimeRange(startTime, endTime))
                .summary(CounterMasterDataResponseDTO.SummaryStats.builder()
                        .totalCounters(0)
                        .activeCounters(0)
                        .averageOccupancy(0)
                        .averageWaitTime(0.0)
                        .mostCongestedCounter("N/A")
                        .leastCongestedCounter("N/A")
                        .build())
                .data(new ArrayList<>())
                .totalRecords(0)
                .recordsReturned(0)
                .build();
    }

    // ==================== ENHANCED COUNTER CONGESTION ====================

    /**
     * ✅ Enhanced counter congestion with proper time range handling
     */
    public List<EnhancedCounterCongestionDTO> getEnhancedCounterCongestionTrend(
            Long cafeteriaLocationId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String timeFilter) {
        try {
            log.info("📊 Fetching enhanced congestion from {} to {}", startTime, endTime);

            List<CafeteriaAnalytics> analytics = analyticsRepository.findByCafeteriaLocationAndTimeRange(
                    cafeteriaLocationId, startTime, endTime);

            // ✅ STRICT FILTER: Only counter-specific data with occupancy
            List<CafeteriaAnalytics> counterData = analytics.stream()
                    .filter(a -> a.getFoodCounter() != null)
                    .filter(a -> a.getCurrentOccupancy() != null)
                    .filter(a -> a.getCurrentOccupancy() > 0)
                    .collect(Collectors.toList());

            log.info("📊 Filtered {} counter-specific records (from {} total)",
                    counterData.size(), analytics.size());

            if (counterData.isEmpty()) {
                log.warn("⚠️ No counter-specific data found! Returning empty list.");
                return new ArrayList<>();
            }

            return aggregateEnhancedCounterCongestion(counterData, timeFilter);
        } catch (Exception e) {
            log.error("Error in getEnhancedCounterCongestionTrend: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    private List<EnhancedCounterCongestionDTO> aggregateEnhancedCounterCongestion(
            List<CafeteriaAnalytics> analytics,
            String timeFilter) {

        // Group by minute-level timestamp and counter
        Map<String, Map<String, List<Integer>>> timeBuckets = new HashMap<>();

        for (CafeteriaAnalytics analytic : analytics) {
            String timeKey = formatTimestampWithMinutes(analytic.getTimestamp(), timeFilter);
            String counterName = analytic.getFoodCounter().getCounterName();
            Integer occupancy = analytic.getCurrentOccupancy();

            timeBuckets.computeIfAbsent(timeKey, k -> new HashMap<>())
                    .computeIfAbsent(counterName, k -> new ArrayList<>())
                    .add(occupancy);
        }

        log.info("📊 Grouped into {} time buckets", timeBuckets.size());

        // Build enhanced DTOs for each minute
        List<EnhancedCounterCongestionDTO> result = new ArrayList<>();

        for (Map.Entry<String, Map<String, List<Integer>>> entry : timeBuckets.entrySet()) {
            String timeKey = entry.getKey();
            Map<String, List<Integer>> countersInBucket = entry.getValue();

            if (countersInBucket != null && !countersInBucket.isEmpty()) {
                Map<String, QueueStats> statsMap = new HashMap<>();

                for (Map.Entry<String, List<Integer>> counterEntry : countersInBucket.entrySet()) {
                    String counterName = counterEntry.getKey();
                    List<Integer> occupancies = counterEntry.getValue();

                    if (!occupancies.isEmpty()) {
                        int max = occupancies.stream().max(Integer::compare).orElse(0);
                        int min = occupancies.stream().min(Integer::compare).orElse(0);
                        double avg = occupancies.stream().mapToInt(Integer::intValue).average().orElse(0.0);

                        String status;
                        if (max >= 12) {
                            status = "HEAVY";
                        } else if (max >= 8) {
                            status = "MODERATE";
                        } else {
                            status = "LIGHT";
                        }

                        statsMap.put(counterName, QueueStats.builder()
                                .maxQueue(max)
                                .minQueue(min)
                                .avgQueue(Math.round(avg * 100.0) / 100.0)
                                .dataPoints(occupancies.size())
                                .status(status)
                                .build());
                    }
                }

                if (!statsMap.isEmpty()) {
                    result.add(EnhancedCounterCongestionDTO.builder()
                            .timestamp(timeKey)
                            .counterStats(statsMap)
                            .build());
                }
            }
        }

        log.info("✅ Generated {} enhanced congestion time buckets with counter-specific data", result.size());
        return result.stream()
                .sorted(Comparator.comparing(EnhancedCounterCongestionDTO::getTimestamp))
                .collect(Collectors.toList());
    }

    private String formatTimestampWithMinutes(LocalDateTime timestamp, String timeFilter) {
        switch (timeFilter) {
            case "daily":
                return String.format("%02d:%02d", timestamp.getHour(), timestamp.getMinute());
            case "weekly":
                String[] days = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
                return days[timestamp.getDayOfWeek().getValue() % 7] + " " +
                        String.format("%02d:%02d", timestamp.getHour(), timestamp.getMinute());
            case "monthly":
                return String.format("%02d/%02d %02d:%02d",
                        timestamp.getMonthValue(),
                        timestamp.getDayOfMonth(),
                        timestamp.getHour(),
                        timestamp.getMinute());
            default:
                return timestamp.toString();
        }
    }

    // ==================== COUNTER EFFICIENCY ====================

    /**
     * ✅ FIXED: Counter efficiency with proper time range and OCCUPANCY-based deltas
     */
    private List<CounterEfficiencyDTO> getCounterEfficiency(
            Long cafeteriaLocationId,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        try {
            log.info("📊 Fetching counter efficiency from {} to {}", startTime, endTime);

            List<Object[]> performance =
                    analyticsRepository.getCounterPerformanceComparisonWithMax(
                            cafeteriaLocationId, startTime
                    );

            log.info("📊 Query returned {} counter performance records", performance.size());

            if (performance.isEmpty()) {
                log.warn("⚠️ No counter performance data found for cafeteria: {}", cafeteriaLocationId);
                return new ArrayList<>();
            }

            return performance.stream()
                    .map(row -> {
                        try {
                            Long counterId = ((Number) row[0]).longValue();
                            String counterName = (String) row[1];
                            Double avgQueue = row[2] != null ? ((Number) row[2]).doubleValue() : 0.0;
                            Double avgDwell = row[3] != null ? ((Number) row[3]).doubleValue() : 0.0;
                            Double avgWait = row[4] != null ? ((Number) row[4]).doubleValue() : 0.0;
                            Double maxWait = row[5] != null ? ((Number) row[5]).doubleValue() : 0.0;

                            log.debug("📊 Processing counter: {} (ID: {})", counterName, counterId);

                            // ✅ OCCUPANCY-BASED: Get analytics for occupancy delta calculation
                            List<CafeteriaAnalytics> counterAnalytics = analyticsRepository
                                    .findByFoodCounterIdAndTimestampBetween(counterId, startTime, endTime);

                            // Calculate total served from occupancy deltas
                            int totalServed = calculateTotalEntriesFromOccupancy(counterAnalytics);

                            log.debug("📊 Counter '{}': {} entries from occupancy deltas",
                                    counterName, totalServed);

                            // Calculate efficiency
                            Integer efficiency =
                                    avgWait > 0
                                            ? Math.min(100, (int) (100 / avgWait * 5))
                                            : 100;

                            log.info("✅ Counter: {} - Served: {}, AvgWait: {}min, PeakWait: {}min, Efficiency: {}%",
                                    counterName, totalServed,
                                    String.format("%.2f", avgWait),
                                    String.format("%.2f", maxWait),
                                    efficiency);

                            return CounterEfficiencyDTO.builder()
                                    .counterName(counterName)
                                    .avgServiceTime(avgDwell)
                                    .totalServed(totalServed)
                                    .avgWaitTime(avgWait)
                                    .peakWaitTime(maxWait)
                                    .efficiency(efficiency)
                                    .build();
                        } catch (Exception e) {
                            log.error("❌ Error processing counter efficiency row: {}", e.getMessage(), e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("❌ Error in getCounterEfficiency: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * ✅ OCCUPANCY-BASED DELTA: Calculate total entries from current_occupancy
     *
     * Formula: New Entries = Current Occupancy - Previous Occupancy
     * Only count POSITIVE deltas (people entering)
     */
    private int calculateTotalEntriesFromOccupancy(List<CafeteriaAnalytics> analytics) {
        if (analytics.isEmpty()) {
            return 0;
        }

        // Sort by timestamp to ensure chronological order
        List<CafeteriaAnalytics> sorted = analytics.stream()
                .sorted(Comparator.comparing(CafeteriaAnalytics::getTimestamp))
                .collect(Collectors.toList());

        int totalEntries = 0;
        Integer previousOccupancy = null;

        for (CafeteriaAnalytics a : sorted) {
            Integer currentOccupancy = a.getCurrentOccupancy();

            if (currentOccupancy == null) {
                continue;
            }

            if (previousOccupancy != null) {
                int delta = currentOccupancy - previousOccupancy;

                // ✅ Count only positive deltas (new entries)
                if (delta > 0) {
                    totalEntries += delta;
                    log.trace("⬆️ Occupancy delta: {} → {} = +{}", previousOccupancy, currentOccupancy, delta);
                } else if (delta < 0) {
                    log.trace("⬇️ Occupancy delta: {} → {} = {} (ignored)", previousOccupancy, currentOccupancy, delta);
                }
            }

            previousOccupancy = currentOccupancy;
        }

        log.info("✅ Total entries from occupancy deltas: {} (from {} records)", totalEntries, sorted.size());
        return totalEntries;
    }

    // ==================== TODAY'S VISITORS ====================

    /**
     * ✅ FIXED: Today's visitors with proper time range and OCCUPANCY-based deltas
     */
    private TodaysVisitorsDTO getTodaysVisitors(Long cafeteriaLocationId, LocalDateTime startTime, LocalDateTime endTime) {
        try {
            log.info("📊 Calculating visitors from {} to {}", startTime, endTime);

            // ✅ Get all counters for this cafeteria
            List<FoodCounter> counters = counterRepository.findByCafeteriaLocationId(cafeteriaLocationId);

            if (counters.isEmpty()) {
                log.warn("No counters found for cafeteria location: {}", cafeteriaLocationId);
                return buildEmptyTodaysVisitors();
            }

            // ✅ OCCUPANCY-BASED: Calculate total visitors by summing deltas from ALL counters
            int totalVisitorsToday = 0;
            for (FoodCounter counter : counters) {
                List<CafeteriaAnalytics> analytics = analyticsRepository
                        .findByFoodCounterIdAndTimestampBetween(counter.getId(), startTime, endTime);

                int counterEntries = calculateTotalEntriesFromOccupancy(analytics);
                totalVisitorsToday += counterEntries;

                log.debug("Counter '{}': {} entries", counter.getCounterName(), counterEntries);
            }

            log.info("✅ Total visitors for period (all counters): {}", totalVisitorsToday);

            // ✅ Calculate comparison period (previous day of same length)
            long hoursDiff = java.time.Duration.between(startTime, endTime).toHours();
            LocalDateTime comparisonStart = startTime.minusHours(hoursDiff);
            LocalDateTime comparisonEnd = startTime;

            int totalVisitorsComparison = 0;
            for (FoodCounter counter : counters) {
                List<CafeteriaAnalytics> analytics = analyticsRepository
                        .findByFoodCounterIdAndTimestampBetween(counter.getId(), comparisonStart, comparisonEnd);

                totalVisitorsComparison += calculateTotalEntriesFromOccupancy(analytics);
            }

            log.info("✅ Total visitors for comparison period (all counters): {}", totalVisitorsComparison);

            // Calculate percentage change
            Double percentageChange = 0.0;
            String trend = "up";
            if (totalVisitorsComparison > 0) {
                percentageChange = ((totalVisitorsToday - totalVisitorsComparison) * 100.0) / totalVisitorsComparison;
                trend = percentageChange >= 0 ? "up" : "down";
            }

            // ✅ Calculate last hour visitors
            LocalDateTime lastHourStart = endTime.minusHours(1);

            int totalVisitorsLastHour = 0;
            for (FoodCounter counter : counters) {
                List<CafeteriaAnalytics> analytics = analyticsRepository
                        .findByFoodCounterIdAndTimestampBetween(counter.getId(), lastHourStart, endTime);

                totalVisitorsLastHour += calculateTotalEntriesFromOccupancy(analytics);
            }

            log.info("✅ Total visitors last hour (all counters): {}", totalVisitorsLastHour);

            // Format since time
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("h:mm a");
            String sinceTime = startTime.format(formatter);

            return TodaysVisitorsDTO.builder()
                    .total(totalVisitorsToday)
                    .sinceTime(sinceTime)
                    .lastHour(totalVisitorsLastHour)
                    .percentageChange(Math.round(percentageChange * 100.0) / 100.0)
                    .trend(trend)
                    .build();

        } catch (Exception e) {
            log.error("Error in getTodaysVisitors: {}", e.getMessage(), e);
            return buildEmptyTodaysVisitors();
        }
    }

    private TodaysVisitorsDTO buildEmptyTodaysVisitors() {
        return TodaysVisitorsDTO.builder()
                .total(0)
                .sinceTime("7:00 AM")
                .lastHour(0)
                .percentageChange(0.0)
                .trend("up")
                .build();
    }

    // ==================== AVERAGE DWELL TIME ====================

    /**
     * ✅ FIXED: Get average dwell time with proper time range, COUNTER-LEVEL ONLY with counter-specific logic
     */
    private AvgDwellTimeDTO getAvgDwellTime(Long cafeteriaLocationId, LocalDateTime startTime, LocalDateTime endTime) {
        try {
            List<FoodCounter> counters = counterRepository.findByCafeteriaLocationId(cafeteriaLocationId);

            if (counters.isEmpty()) {
                log.warn("No counters found for cafeteria location: {}", cafeteriaLocationId);
                return buildEmptyAvgDwellTime();
            }

            List<Double> counterAverages = new ArrayList<>();

            for (FoodCounter counter : counters) {
                // Get analytics for this counter within time range
                List<CafeteriaAnalytics> analytics = analyticsRepository
                        .findByFoodCounterIdAndTimestampBetween(counter.getId(), startTime, endTime);

                if (analytics.isEmpty()) {
                    continue;
                }

                // Calculate average using counter-specific logic
                Double counterAvg = calculateCounterAverage(analytics, counter.getCounterName());
                if (counterAvg != null && counterAvg > 0) {
                    counterAverages.add(counterAvg);
                }
            }

            if (counterAverages.isEmpty()) {
                log.warn("No valid dwell time data across all counters");
                return buildEmptyAvgDwellTime();
            }

            Double avgDwell = counterAverages.stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);

            int minutes = avgDwell.intValue();
            int seconds = (int) ((avgDwell - minutes) * 60);

            log.info("✅ Overall avg dwell time: {}min {}sec (across {} counters)",
                    minutes, seconds, counterAverages.size());

            return AvgDwellTimeDTO.builder()
                    .minutes(minutes)
                    .seconds(seconds)
                    .totalSeconds((int) (avgDwell * 60))
                    .formatted(String.format("%dm %ds", minutes, seconds))
                    .percentageChange(0.0)
                    .trend("up")
                    .note(String.format("Across %d counters", counterAverages.size()))
                    .build();
        } catch (Exception e) {
            log.error("Error in getAvgDwellTime: {}", e.getMessage(), e);
            return buildEmptyAvgDwellTime();
        }
    }

    /**
     * Helper method to calculate counter-specific average
     */
    private Double calculateCounterAverage(List<CafeteriaAnalytics> analytics, String counterName) {
        List<Double> validTimes = new ArrayList<>();

        if (usesManualWaitTimeOnly(counterName)) {
            // Mini Meals & Two Good: Use ONLY manual_wait_time
            validTimes = analytics.stream()
                    .map(CafeteriaAnalytics::getManualWaitTime)
                    .filter(Objects::nonNull)
                    .filter(t -> t > 0)
                    .collect(Collectors.toList());
        } else {
            // Other counters: COALESCE logic
            for (CafeteriaAnalytics a : analytics) {
                Double time = null;

                if (a.getAvgDwellTime() != null && a.getAvgDwellTime() > 0) {
                    time = a.getAvgDwellTime();
                } else if (a.getEstimatedWaitTime() != null && a.getEstimatedWaitTime() > 0) {
                    time = a.getEstimatedWaitTime();
                } else if (a.getManualWaitTime() != null && a.getManualWaitTime() > 0) {
                    time = a.getManualWaitTime();
                }

                if (time != null && time > 0) {
                    validTimes.add(time);
                }
            }
        }

        if (validTimes.isEmpty()) {
            return null;
        }

        return validTimes.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    private AvgDwellTimeDTO buildEmptyAvgDwellTime() {
        return AvgDwellTimeDTO.builder()
                .minutes(0)
                .seconds(0)
                .totalSeconds(0)
                .formatted("0m 0s")
                .percentageChange(0.0)
                .trend("up")
                .note("No data available")
                .build();
    }

    // ==================== PEAK HOURS ====================

    /**
     * ✅ FIXED: Peak hours with proper time range - Per-counter analysis showing when each counter has highest occupancy
     */
    private PeakHoursDTO getPeakHours(Long cafeteriaLocationId, LocalDateTime startTime, LocalDateTime endTime) {
        try {
            // Get all counters
            List<FoodCounter> counters = counterRepository.findByCafeteriaLocationId(cafeteriaLocationId);

            if (counters.isEmpty()) {
                log.warn("No counters found for cafeteria location: {}", cafeteriaLocationId);
                return buildEmptyPeakHours();
            }

            // ✅ Find peak hour for each counter (when current_occupancy is highest)
            List<PeakSlot> peakSlots = new ArrayList<>();

            for (FoodCounter counter : counters) {
                List<CafeteriaAnalytics> analytics = analyticsRepository
                        .findByFoodCounterIdAndTimestampBetween(counter.getId(), startTime, endTime);

                if (analytics.isEmpty()) {
                    continue;
                }

                // Find the time with highest occupancy for this counter
                Optional<CafeteriaAnalytics> peakRecord = analytics.stream()
                        .filter(a -> a.getCurrentOccupancy() != null)
                        .max(Comparator.comparing(CafeteriaAnalytics::getCurrentOccupancy));

                if (peakRecord.isPresent()) {
                    CafeteriaAnalytics peak = peakRecord.get();
                    int peakHour = peak.getTimestamp().getHour();
                    int peakOccupancy = peak.getCurrentOccupancy();

                    String type = determinePeakType(peakHour);

                    peakSlots.add(PeakSlot.builder()
                            .time(String.format("%s - %02d:00", counter.getCounterName(), peakHour))
                            .type(type)
                            .occupancy(peakOccupancy)
                            .build());

                    log.info("✅ Counter '{}' peak: {}:00 with {} occupancy",
                            counter.getCounterName(), peakHour, peakOccupancy);
                }
            }

            // Sort by occupancy and take top 3
            peakSlots = peakSlots.stream()
                    .sorted(Comparator.comparing(PeakSlot::getOccupancy).reversed())
                    .limit(3)
                    .collect(Collectors.toList());

            int currentHour = LocalDateTime.now().getHour();
            String currentStatus = determinePeakType(currentHour);

            String nextPeak = currentHour < 8 ? "8:00 AM" :
                    currentHour < 12 ? "12:00 PM" :
                            currentHour < 19 ? "7:00 PM" : "Tomorrow 8:00 AM";

            return PeakHoursDTO.builder()
                    .currentStatus(currentStatus)
                    .nextPeak(nextPeak)
                    .peakSlots(peakSlots)
                    .highestPeak(peakSlots.isEmpty() ? "N/A" : peakSlots.get(0).getTime())
                    .averagePeakOccupancy(peakSlots.isEmpty() ? 0 :
                            peakSlots.stream().mapToInt(PeakSlot::getOccupancy).sum() / peakSlots.size())
                    .build();
        } catch (Exception e) {
            log.error("Error in getPeakHours: {}", e.getMessage(), e);
            return buildEmptyPeakHours();
        }
    }

    // ==================== QUEUE ANALYSIS SERVICE METHODS ====================

    /**
     * ✅ FIXED: Get in_count trends (inflow) for line chart - NOW SUPPORTS TIME RANGE
     */
    @Transactional(readOnly = true)
    public QueueLengthTrendDTO.Response getQueueLengthTrends(
            String tenantCode,
            String cafeteriaCode,
            Integer intervalMinutes,
            String timeFilter,
            Integer timeRange
    ) {
        log.info("📊 Fetching IN_COUNT trends for {}/{} with {} minute intervals, filter: {}, range: {}",
                tenantCode, cafeteriaCode, intervalMinutes, timeFilter, timeRange);

        try {
            CafeteriaLocation location = locationRepository.findByTenantCodeAndCafeteriaCode(tenantCode, cafeteriaCode)
                    .orElseThrow(() -> new RuntimeException("Cafeteria not found"));

            // ✅ FIXED: Use proper time range calculation
            LocalDateTime[] calculatedRange = calculateTimeRange(timeFilter, timeRange);
            LocalDateTime startTime = calculatedRange[0];
            LocalDateTime endTime = calculatedRange[1];

            log.info("📅 Time range: {} to {}", startTime, endTime);

            // ✅ Use IN_COUNT instead of queueLength
            List<Object[]> results;
            try {
                results = analyticsRepository.getInCountTimeSeries(
                        location.getId(), startTime, endTime);
            } catch (Exception e) {
                log.warn("⚠️ Primary query failed, using alternative: {}", e.getMessage());
                results = analyticsRepository.getInCountTimeSeriesAlternative(
                        location.getId(), startTime, endTime, intervalMinutes);
            }

            if (results.isEmpty()) {
                log.warn("⚠️ No in_count trend data found for cafeteria: {}", cafeteriaCode);
                return buildEmptyQueueTrends(startTime, endTime, intervalMinutes);
            }

            // Group by time bucket
            Map<String, Map<String, Double>> timeSeriesMap = new TreeMap<>();
            Set<String> allCounters = new HashSet<>();

            for (Object[] row : results) {
                String timeBucket = formatTimeBucket(row);
                String counterName = (String) row[2];
                Double avgInCount = row[3] != null ? ((Number) row[3]).doubleValue() : 0.0;

                timeSeriesMap
                        .computeIfAbsent(timeBucket, k -> new HashMap<>())
                        .put(counterName, Math.round(avgInCount * 100.0) / 100.0);

                allCounters.add(counterName);
            }

            // Build trend DTOs
            List<QueueLengthTrendDTO> trends = timeSeriesMap.entrySet().stream()
                    .map(entry -> QueueLengthTrendDTO.builder()
                            .timestamp(entry.getKey())
                            .counterQueues(entry.getValue())
                            .build())
                    .collect(Collectors.toList());

            // Find peak
            String peakTime = "";
            String peakCounter = "";
            Double peakValue = 0.0;

            for (Map.Entry<String, Map<String, Double>> entry : timeSeriesMap.entrySet()) {
                for (Map.Entry<String, Double> counterEntry : entry.getValue().entrySet()) {
                    if (counterEntry.getValue() > peakValue) {
                        peakValue = counterEntry.getValue();
                        peakCounter = counterEntry.getKey();
                        peakTime = entry.getKey();
                    }
                }
            }

            QueueLengthTrendDTO.Response.Summary summary = QueueLengthTrendDTO.Response.Summary.builder()
                    .peakTime(peakTime)
                    .peakCounter(peakCounter)
                    .peakQueueLength(peakValue)
                    .totalDataPoints(trends.size())
                    .build();

            log.info("✅ In_count trends: {} time points, {} counters, peak: {} at {} ({})",
                    trends.size(), allCounters.size(), peakValue, peakTime, peakCounter);

            return QueueLengthTrendDTO.Response.builder()
                    .trends(trends)
                    .timeRange(formatTimeRange(startTime, endTime))
                    .interval(intervalMinutes + "-minute")
                    .reportGeneratedAt(LocalDateTime.now())
                    .counters(new ArrayList<>(allCounters))
                    .summary(summary)
                    .build();

        } catch (Exception e) {
            log.error("❌ Error fetching in_count trends: {}", e.getMessage(), e);
            throw new RuntimeException("Error fetching in_count trends: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ FIXED: Get average in_count comparison - NOW SUPPORTS TIME RANGE
     */
    @Transactional(readOnly = true)
    public CounterQueueComparisonDTO.Response getAverageQueueComparison(
            String tenantCode,
            String cafeteriaCode,
            String timeFilter,
            Integer timeRange
    ) {
        log.info("📊 Fetching average IN_COUNT comparison for {}/{}, filter: {}, range: {}",
                tenantCode, cafeteriaCode, timeFilter, timeRange);

        try {
            CafeteriaLocation location = locationRepository.findByTenantCodeAndCafeteriaCode(tenantCode, cafeteriaCode)
                    .orElseThrow(() -> new RuntimeException("Cafeteria not found: " + tenantCode + "/" + cafeteriaCode));

            // ✅ FIXED: Use proper time range calculation instead of hardcoded current day
            LocalDateTime[] calculatedRange = calculateTimeRange(timeFilter, timeRange);
            LocalDateTime startTime = calculatedRange[0];
            LocalDateTime endTime = calculatedRange[1];

            log.info("📅 Time range: {} to {} (Filter: {}, Range: {} hrs)",
                    startTime, endTime, timeFilter, timeRange);

            // ✅ Use IN_COUNT instead of queueLength
            List<Object[]> results = analyticsRepository.getAverageInCountComparison(
                    location.getId(), startTime, endTime);

            if (results.isEmpty()) {
                log.warn("⚠️ No in_count data found for cafeteria: {}", cafeteriaCode);
                return buildEmptyQueueComparison(startTime, endTime);
            }

            List<CounterQueueComparisonDTO> counters = results.stream()
                    .map(row -> {
                        String counterName = (String) row[1];
                        Double avgInCount = row[2] != null ? ((Number) row[2]).doubleValue() : 0.0;
                        Integer maxInCount = row[3] != null ? ((Number) row[3]).intValue() : 0;
                        Integer minInCount = row[4] != null ? ((Number) row[4]).intValue() : 0;
                        Integer dataPoints = row[6] != null ? ((Number) row[6]).intValue() : 0;

                        String status = determineQueueStatus(avgInCount);

                        return CounterQueueComparisonDTO.builder()
                                .counterName(counterName)
                                .averageQueueLength(Math.round(avgInCount * 100.0) / 100.0)
                                .maxQueueLength(maxInCount)
                                .minQueueLength(minInCount)
                                .dataPoints(dataPoints)
                                .status(status)
                                .build();
                    })
                    .collect(Collectors.toList());

            // Calculate summary
            CounterQueueComparisonDTO busiest = counters.stream()
                    .max(Comparator.comparing(CounterQueueComparisonDTO::getAverageQueueLength))
                    .orElse(null);

            CounterQueueComparisonDTO leastBusy = counters.stream()
                    .min(Comparator.comparing(CounterQueueComparisonDTO::getAverageQueueLength))
                    .orElse(null);

            Double overallAvg = counters.stream()
                    .mapToDouble(CounterQueueComparisonDTO::getAverageQueueLength)
                    .average()
                    .orElse(0.0);

            CounterQueueComparisonDTO.Response.Summary summary = CounterQueueComparisonDTO.Response.Summary.builder()
                    .busiestCounter(busiest != null ? busiest.getCounterName() : "N/A")
                    .leastBusyCounter(leastBusy != null ? leastBusy.getCounterName() : "N/A")
                    .overallAverage(Math.round(overallAvg * 100.0) / 100.0)
                    .build();

            log.info("✅ In_count comparison: {} counters, busiest: {}, avg: {}",
                    counters.size(), summary.getBusiestCounter(), summary.getOverallAverage());

            return CounterQueueComparisonDTO.Response.builder()
                    .counters(counters)
                    .timeRange(formatTimeRange(startTime, endTime))
                    .reportGeneratedAt(LocalDateTime.now())
                    .totalCounters(counters.size())
                    .summary(summary)
                    .build();

        } catch (Exception e) {
            log.error("❌ Error fetching in_count comparison: {}", e.getMessage(), e);
            throw new RuntimeException("Error fetching in_count comparison: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ FIXED: Get congestion rate comparison data - NOW SUPPORTS TIME RANGE
     */
    @Transactional(readOnly = true)
    public CounterCongestionRateDTO.Response getCongestionRateComparison(
            String tenantCode,
            String cafeteriaCode,
            String timeFilter,
            Integer timeRange
    ) {
        log.info("📊 Fetching congestion rate comparison for {}/{}, filter: {}, range: {}",
                tenantCode, cafeteriaCode, timeFilter, timeRange);

        try {
            CafeteriaLocation location = locationRepository.findByTenantCodeAndCafeteriaCode(tenantCode, cafeteriaCode)
                    .orElseThrow(() -> new RuntimeException("Cafeteria not found: " + tenantCode + "/" + cafeteriaCode));

            // ✅ FIXED: Use proper time range calculation instead of hardcoded current day
            LocalDateTime[] calculatedRange = calculateTimeRange(timeFilter, timeRange);
            LocalDateTime startTime = calculatedRange[0];
            LocalDateTime endTime = calculatedRange[1];

            log.info("📅 Time range: {} to {} (Filter: {}, Range: {} hrs)",
                    startTime, endTime, timeFilter, timeRange);

            // Get all counters for this cafeteria
            List<FoodCounter> counters = counterRepository.findByCafeteriaLocationId(location.getId());

            if (counters.isEmpty()) {
                log.warn("⚠️ No counters found for cafeteria: {}", cafeteriaCode);
                return buildEmptyCongestionRate(startTime, endTime);
            }

            List<CounterCongestionRateDTO> counterRates = new ArrayList<>();

            // Calculate congestion rate for each counter
            for (FoodCounter counter : counters) {
                List<CafeteriaAnalytics> analytics = analyticsRepository
                        .findByFoodCounterIdAndTimestampBetween(counter.getId(), startTime, endTime);

                if (analytics.isEmpty()) {
                    log.debug("No analytics data for counter: {}", counter.getCounterName());
                    continue;
                }

                // ✅ Filter operational data only (current_occupancy > 1)
                List<CafeteriaAnalytics> operationalData = analytics.stream()
                        .filter(a -> a.getCurrentOccupancy() != null)
                        .filter(a -> a.getCurrentOccupancy() > 1.0)
                        .collect(Collectors.toList());

                if (operationalData.isEmpty()) {
                    log.debug("No operational data for counter: {}", counter.getCounterName());
                    continue;
                }

                // ✅ Categorize by congestion level based on current_occupancy
                int lowCount = 0;
                int mediumCount = 0;
                int highCount = 0;

                for (CafeteriaAnalytics a : operationalData) {
                    double occupancy = a.getCurrentOccupancy();

                    if (occupancy <= 50) {
                        lowCount++;
                    } else if (occupancy <= 100) {
                        mediumCount++;
                    } else {
                        highCount++;
                    }
                }

                int totalRecords = operationalData.size();

                // Calculate percentages
                double lowPct = (lowCount * 100.0) / totalRecords;
                double mediumPct = (mediumCount * 100.0) / totalRecords;
                double highPct = (highCount * 100.0) / totalRecords;

                // ✅ Calculate congestion rate: (HIGH% × 1.0) + (MEDIUM% × 0.5)
                double congestionRate = (highPct * 1.0) + (mediumPct * 0.5);

                log.info("📊 Counter '{}': Low={}%, Med={}%, High={}%, Rate={}% (from {} operational records)",
                        counter.getCounterName(),
                        Math.round(lowPct * 10) / 10.0,
                        Math.round(mediumPct * 10) / 10.0,
                        Math.round(highPct * 10) / 10.0,
                        Math.round(congestionRate * 10) / 10.0,
                        totalRecords);

                counterRates.add(CounterCongestionRateDTO.builder()
                        .counterName(counter.getCounterName())
                        .congestionRate(Math.round(congestionRate * 100.0) / 100.0)
                        .totalRecords(totalRecords)
                        .highCongestionRecords(highCount)
                        .mediumCongestionRecords(mediumCount)
                        .lowCongestionRecords(lowCount)
                        .highPercentage(Math.round(highPct * 100.0) / 100.0)
                        .mediumPercentage(Math.round(mediumPct * 100.0) / 100.0)
                        .lowPercentage(Math.round(lowPct * 100.0) / 100.0)
                        .build());
            }

            // Sort by congestion rate (descending)
            counterRates.sort(Comparator.comparing(CounterCongestionRateDTO::getCongestionRate).reversed());

            if (counterRates.isEmpty()) {
                log.warn("⚠️ No congestion data calculated for cafeteria: {}", cafeteriaCode);
                return buildEmptyCongestionRate(startTime, endTime);
            }

            // Calculate summary
            CounterCongestionRateDTO mostCongested = counterRates.get(0);
            CounterCongestionRateDTO leastCongested = counterRates.get(counterRates.size() - 1);

            // Weighted average (by operational records)
            double totalWeightedRate = 0.0;
            int totalOperationalRecords = 0;

            for (CounterCongestionRateDTO rate : counterRates) {
                totalWeightedRate += rate.getCongestionRate() * rate.getTotalRecords();
                totalOperationalRecords += rate.getTotalRecords();
            }

            Double overallRate = totalOperationalRecords > 0
                    ? totalWeightedRate / totalOperationalRecords
                    : 0.0;

            String recommendation = generateCongestionRecommendationV2(overallRate, mostCongested);

            CounterCongestionRateDTO.Response.Summary summary = CounterCongestionRateDTO.Response.Summary.builder()
                    .mostCongestedCounter(mostCongested.getCounterName())
                    .leastCongestedCounter(leastCongested.getCounterName())
                    .overallCongestionRate(Math.round(overallRate * 100.0) / 100.0)
                    .recommendation(recommendation)
                    .build();

            log.info("✅ Congestion rate summary: {} counters, most={} ({}%), least={} ({}%), overall={}%",
                    counterRates.size(),
                    summary.getMostCongestedCounter(),
                    mostCongested.getCongestionRate(),
                    summary.getLeastCongestedCounter(),
                    leastCongested.getCongestionRate(),
                    summary.getOverallCongestionRate());

            return CounterCongestionRateDTO.Response.builder()
                    .counters(counterRates)
                    .timeRange(formatTimeRange(startTime, endTime))
                    .reportGeneratedAt(LocalDateTime.now())
                    .totalCounters(counterRates.size())
                    .summary(summary)
                    .build();

        } catch (Exception e) {
            log.error("❌ Error fetching congestion rate: {}", e.getMessage(), e);
            throw new RuntimeException("Error fetching congestion rate: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ Generate intelligent recommendations based on congestion data
     */
    private String generateCongestionRecommendationV2(Double overallRate, CounterCongestionRateDTO mostCongested) {
        String counterName = mostCongested.getCounterName();
        double rate = mostCongested.getCongestionRate();

        if (rate > 60) {
            return String.format("🔴 Critical: '%s' experiences severe congestion (%.1f%% of operational time in HIGH/MEDIUM load). " +
                            "Immediate action needed: add staff, open express lanes, or extend service hours.",
                    counterName, rate);
        } else if (rate > 40) {
            return String.format("🟡 Warning: '%s' has high congestion (%.1f%% of operational time under pressure). " +
                            "Consider redistributing staff during peak periods or optimizing service workflow.",
                    counterName, rate);
        } else if (rate > 25) {
            return String.format("🟠 Moderate: '%s' shows elevated congestion (%.1f%%). " +
                            "Current staffing adequate but monitor trends, especially during lunch/dinner rushes.",
                    counterName, rate);
        } else if (rate > 10) {
            return String.format("🟢 Good: All counters operating efficiently. '%s' shows minor congestion (%.1f%%) during peak hours. " +
                            "Continue current operations.",
                    counterName, rate);
        } else {
            return "🟢 Optimal: All counters operating with minimal congestion. Service levels are excellent across all stations.";
        }
    }

    /**
     * ✅ FIXED: Get Queue KPIs calculated from actual data - NOW SUPPORTS TIME RANGE
     */
    @Transactional(readOnly = true)
    public QueueKPIResponseDTO getQueueKPIs(
            String tenantCode,
            String cafeteriaCode,
            String timeFilter,
            Integer timeRange
    ) {
        log.info("📊 Calculating Queue KPIs for {}/{}, filter: {}, range: {}",
                tenantCode, cafeteriaCode, timeFilter, timeRange);

        try {
            LocalDateTime[] calculatedRange = calculateTimeRange(timeFilter, timeRange);
            LocalDateTime startTime = calculatedRange[0];
            LocalDateTime endTime = calculatedRange[1];

            CounterQueueComparisonDTO.Response queueComparison = getAverageQueueComparison(tenantCode, cafeteriaCode, timeFilter, timeRange);
            CounterCongestionRateDTO.Response congestionRate = getCongestionRateComparison(tenantCode, cafeteriaCode, timeFilter, timeRange);
            QueueLengthTrendDTO.Response queueTrends = getQueueLengthTrends(tenantCode, cafeteriaCode, 5, timeFilter, timeRange);

            Double overallAvgQueue = queueComparison.getSummary().getOverallAverage();

            Double peakQueueLength = queueTrends.getSummary() != null
                    ? queueTrends.getSummary().getPeakQueueLength()
                    : 0.0;

            // ✅ FIXED: Use the busiest counter from queue comparison (highest avg in_count)
            String mostCongestedCounter = queueComparison.getSummary().getBusiestCounter();

            // ✅ FIXED: Get congestion rate for the actual busiest counter
            Double congestionRateValue = congestionRate.getCounters().stream()
                    .filter(c -> c.getCounterName().equals(mostCongestedCounter))
                    .findFirst()
                    .map(CounterCongestionRateDTO::getCongestionRate)
                    .orElse(0.0);

            String peakTime = queueTrends.getSummary() != null
                    ? queueTrends.getSummary().getPeakTime()
                    : "N/A";

            Double peakHourAvgQueue = calculatePeakHourAverage(queueTrends);
            String peakHourRange = calculatePeakHourRange(peakTime);

            QueueKPIResponseDTO response = QueueKPIResponseDTO.builder()
                    .overallAvgQueue(overallAvgQueue)
                    .peakQueueLength(peakQueueLength)
                    .mostCongestedCounter(mostCongestedCounter)  // Now matches busiest from trends
                    .congestionRate(congestionRateValue)  // Now shows rate for that specific counter
                    .peakHourAvgQueue(peakHourAvgQueue)
                    .peakHourRange(peakHourRange)
                    .timeRange(formatTimeRange(startTime, endTime))
                    .reportGeneratedAt(LocalDateTime.now())
                    .build();

            log.info("✅ Queue KPIs calculated: avgQueue={}, peak={}, mostCongested={} (avgQueue={}), rate={}%",
                    overallAvgQueue, peakQueueLength, mostCongestedCounter,
                    queueComparison.getCounters().stream()
                            .filter(c -> c.getCounterName().equals(mostCongestedCounter))
                            .findFirst()
                            .map(CounterQueueComparisonDTO::getAverageQueueLength)
                            .orElse(0.0),
                    congestionRateValue);

            return response;

        } catch (Exception e) {
            log.error("❌ Error calculating Queue KPIs: {}", e.getMessage(), e);
            throw new RuntimeException("Error calculating Queue KPIs: " + e.getMessage(), e);
        }
    }

    private Double calculatePeakHourAverage(QueueLengthTrendDTO.Response queueTrends) {
        if (queueTrends.getTrends() == null || queueTrends.getTrends().isEmpty()) {
            return 0.0;
        }

        // Find index of peak
        int peakIndex = -1;
        Double peakValue = 0.0;

        for (int i = 0; i < queueTrends.getTrends().size(); i++) {
            QueueLengthTrendDTO trend = queueTrends.getTrends().get(i);
            Double maxInTrend = trend.getCounterQueues().values().stream()
                    .max(Double::compare)
                    .orElse(0.0);

            if (maxInTrend > peakValue) {
                peakValue = maxInTrend;
                peakIndex = i;
            }
        }

        if (peakIndex == -1) return 0.0;

        // Get trends around peak (±4 intervals = ±20 minutes for 5-min intervals)
        int windowSize = 4;
        int startIndex = Math.max(0, peakIndex - windowSize);
        int endIndex = Math.min(queueTrends.getTrends().size() - 1, peakIndex + windowSize);

        List<Double> windowValues = new ArrayList<>();
        for (int i = startIndex; i <= endIndex; i++) {
            QueueLengthTrendDTO trend = queueTrends.getTrends().get(i);
            Double avgInTrend = trend.getCounterQueues().values().stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);
            windowValues.add(avgInTrend);
        }

        return windowValues.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    private String calculatePeakHourRange(String peakTime) {
        if (peakTime == null || peakTime.equals("N/A")) {
            return "N/A";
        }

        try {
            // Parse time (format: "HH:mm")
            String[] parts = peakTime.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);

            // Calculate range (±20 minutes)
            LocalTime startTime = LocalTime.of(hour, minute).minusMinutes(20);
            LocalTime endTime = LocalTime.of(hour, minute).plusMinutes(20);

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("hh:mm a");
            return startTime.format(formatter) + " - " + endTime.format(formatter);
        } catch (Exception e) {
            return "N/A";
        }
    }

    // ==================== HELPER METHODS ====================

    /**
     * Determine queue status based on average queue length
     */
    private String determineQueueStatus(Double avgQueue) {
        if (avgQueue < 5) {
            return "LIGHT";
        } else if (avgQueue < 10) {
            return "MODERATE";
        } else {
            return "HEAVY";
        }
    }

    /**
     * Generate congestion recommendation
     */
    private String generateCongestionRecommendation(Double overallRate, CounterCongestionRateDTO mostCongested) {
        if (overallRate < 20) {
            return "✅ All counters operating efficiently";
        } else if (overallRate < 40) {
            return "⚠️ Monitor " + (mostCongested != null ? mostCongested.getCounterName() : "busy counters");
        } else {
            return "🔴 Consider adding staff to " + (mostCongested != null ? mostCongested.getCounterName() : "congested counters");
        }
    }

    /**
     * Format time bucket from query result
     */
    private String formatTimeBucket(Object[] row) {
        // If using DATE_FORMAT query (row[0] is formatted string)
        if (row[0] instanceof String) {
            try {
                LocalDateTime dt = LocalDateTime.parse((String) row[0],
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                return String.format("%02d:%02d", dt.getHour(), dt.getMinute());
            } catch (Exception e) {
                return (String) row[0];
            }
        }

        // If using EXTRACT query (row[0] = hour, row[1] = minute bucket)
        Integer hour = ((Number) row[0]).intValue();
        Integer minute = row.length > 1 ? ((Number) row[1]).intValue() : 0;
        return String.format("%02d:%02d", hour, minute);
    }

    /**
     * Build empty queue comparison response
     */
    private CounterQueueComparisonDTO.Response buildEmptyQueueComparison(
            LocalDateTime startTime, LocalDateTime endTime) {
        return CounterQueueComparisonDTO.Response.builder()
                .counters(new ArrayList<>())
                .timeRange(formatTimeRange(startTime, endTime))
                .reportGeneratedAt(LocalDateTime.now())
                .totalCounters(0)
                .summary(CounterQueueComparisonDTO.Response.Summary.builder()
                        .busiestCounter("N/A")
                        .leastBusyCounter("N/A")
                        .overallAverage(0.0)
                        .build())
                .build();
    }

    /**
     * Build empty congestion rate response
     */
    private CounterCongestionRateDTO.Response buildEmptyCongestionRate(
            LocalDateTime startTime, LocalDateTime endTime) {
        return CounterCongestionRateDTO.Response.builder()
                .counters(new ArrayList<>())
                .timeRange(formatTimeRange(startTime, endTime))
                .reportGeneratedAt(LocalDateTime.now())
                .totalCounters(0)
                .summary(CounterCongestionRateDTO.Response.Summary.builder()
                        .mostCongestedCounter("N/A")
                        .leastCongestedCounter("N/A")
                        .overallCongestionRate(0.0)
                        .recommendation("No data available")
                        .build())
                .build();
    }

    /**
     * Build empty queue trends response
     */
    private QueueLengthTrendDTO.Response buildEmptyQueueTrends(
            LocalDateTime startTime, LocalDateTime endTime, Integer intervalMinutes) {
        return QueueLengthTrendDTO.Response.builder()
                .trends(new ArrayList<>())
                .timeRange(formatTimeRange(startTime, endTime))
                .interval(intervalMinutes + "-minute")
                .reportGeneratedAt(LocalDateTime.now())
                .counters(new ArrayList<>())
                .summary(QueueLengthTrendDTO.Response.Summary.builder()
                        .peakTime("N/A")
                        .peakCounter("N/A")
                        .peakQueueLength(0.0)
                        .totalDataPoints(0)
                        .build())
                .build();
    }

    private String determinePeakType(int hour) {
        if (hour >= 8 && hour <= 9) return "Breakfast Peak";
        if (hour >= 12 && hour <= 14) return "Lunch Rush";
        if (hour >= 19 && hour <= 21) return "Dinner Peak";
        return "Off-Peak";
    }

    private PeakHoursDTO buildEmptyPeakHours() {
        return PeakHoursDTO.builder()
                .currentStatus("Off-Peak")
                .nextPeak("12:00 PM")
                .peakSlots(new ArrayList<>())
                .highestPeak("N/A")
                .averagePeakOccupancy(0)
                .build();
    }

    // ==================== UTILITY METHODS ====================

    private String formatTimestamp(LocalDateTime timestamp) {
        return String.format("%02d:00", timestamp.getHour());
    }

    private int extractHour(String timestamp) {
        try {
            if (timestamp.contains(":")) {
                return Integer.parseInt(timestamp.split(":")[0]);
            }
        } catch (Exception e) {
            // Ignore
        }
        return 0;
    }
}