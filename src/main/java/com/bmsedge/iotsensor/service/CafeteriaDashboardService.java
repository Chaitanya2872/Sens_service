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

@Service
@RequiredArgsConstructor
@Slf4j
public class CafeteriaDashboardService {

    private final CafeteriaAnalyticsRepository analyticsRepository;
    private final CafeteriaLocationRepository locationRepository;
    private final FoodCounterRepository counterRepository;
    private final TenantRepository tenantRepository;

    @Transactional(readOnly = true)
    public DashboardDataDTO getDashboardData(String tenantCode, String cafeteriaCode, String timeFilter, Integer timeRange) {
        log.info("Fetching dashboard data for tenant: {}, cafeteria: {}, filter: {}, range: {}",
                tenantCode, cafeteriaCode, timeFilter, timeRange);

        try {
            CafeteriaLocation location = locationRepository.findByTenantCodeAndCafeteriaCode(tenantCode, cafeteriaCode)
                    .orElseThrow(() -> new RuntimeException("Cafeteria not found"));

            LocalDateTime startTime = calculateStartTime(timeFilter, timeRange);
            LocalDateTime endTime = LocalDateTime.now();

            DashboardDataDTO dashboard = DashboardDataDTO.builder()
                    .occupancyStatus(getOccupancyStatus(location.getId()))
                    .flowData(getFlowData(location.getId(), startTime, endTime, timeFilter))
                    .counterStatus(getCounterStatus(location.getId()))
                    .dwellTimeData(getDwellTimeData(location.getId(), startTime, endTime))
                    .footfallComparison(getFootfallComparison(location.getId(), startTime, endTime, timeFilter))
                    .occupancyTrend(getOccupancyTrend(location.getId(), startTime, endTime, timeFilter))
                    .counterCongestionTrend(getCounterCongestionTrend(location.getId(), startTime, endTime, timeFilter))
                    .counterEfficiency(getCounterEfficiency(location.getId(), startTime, endTime))
                    .todaysVisitors(getTodaysVisitors(location.getId()))
                    .avgDwellTime(getAvgDwellTime(location.getId()))
                    .peakHours(getPeakHours(location.getId()))
                    .lastUpdated(LocalDateTime.now())
                    .build();

            return dashboard;
        } catch (Exception e) {
            log.error("Error fetching dashboard data: {}", e.getMessage(), e);
            throw new RuntimeException("Error fetching dashboard data: " + e.getMessage(), e);
        }
    }

    private OccupancyStatusDTO getOccupancyStatus(Long cafeteriaLocationId) {
        try {
            // ✅ Get latest data for ALL counters
            List<CafeteriaAnalytics> latestAnalytics = analyticsRepository.findLatestForAllCounters(cafeteriaLocationId);

            if (latestAnalytics.isEmpty()) {
                log.warn("No occupancy data found for cafeteria location: {}", cafeteriaLocationId);
                return buildEmptyOccupancyStatus();
            }

            // ✅ SUM occupancy from all counters
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

    private List<FlowDataDTO> getFlowData(Long cafeteriaLocationId, LocalDateTime startTime, LocalDateTime endTime, String timeFilter) {
        try {
            LocalDateTime now = LocalDateTime.now();

            if (timeFilter.equals("daily")) {
                LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.of(7, 0));
                startTime = todayStart;
                endTime = now;
                log.info("Daily view: filtering data from {} to {}", startTime, endTime);
            }

            List<CafeteriaAnalytics> analytics = analyticsRepository.findByCafeteriaLocationAndTimeRange(
                    cafeteriaLocationId, startTime, endTime);

            analytics = analytics.stream()
                    .filter(a -> !a.getTimestamp().isAfter(now))
                    .collect(Collectors.toList());

            log.info("Found {} analytics records for flow data", analytics.size());

            return aggregateFlowData(analytics, timeFilter);
        } catch (Exception e) {
            log.error("Error in getFlowData: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * ✅ UPDATED: Get counter status - uses COALESCE for dwell time
     */
    private List<CounterStatusDTO> getCounterStatus(Long cafeteriaLocationId) {
        try {
            List<CafeteriaAnalytics> latestAnalytics = analyticsRepository.findLatestForAllCounters(cafeteriaLocationId);

            return latestAnalytics.stream()
                    .filter(analytics -> analytics.getFoodCounter() != null)
                    .map(analytics -> {
                        FoodCounter counter = analytics.getFoodCounter();
                        String counterName = counter.getCounterName();

                        // ✅ Get counter-specific average dwell time (uses COALESCE)
                        Double avgDwell = getCounterSpecificAvgDwellTime(counter.getId());

                        log.debug("Counter: {} - AvgDwell: {}min, Queue: {}, Wait: {}min",
                                counterName,
                                avgDwell != null ? String.format("%.1f", avgDwell) : "N/A",
                                analytics.getQueueLength(),
                                analytics.getEstimatedWaitTime());

                        return CounterStatusDTO.builder()
                                .counterName(counterName)
                                .queueLength(analytics.getQueueLength() != null ? analytics.getQueueLength() : 0)
                                .waitTime(analytics.getEstimatedWaitTime() != null ? analytics.getEstimatedWaitTime() : 0.0)
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
     * ✅ UPDATED: Get counter-specific average dwell time with COALESCE
     * Priority: avgDwellTime → estimatedWaitTime → manualWaitTime
     */
    private Double getCounterSpecificAvgDwellTime(Long counterId) {
        try {
            LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.of(7, 0));
            LocalDateTime now = LocalDateTime.now();

            List<CafeteriaAnalytics> counterAnalytics = analyticsRepository
                    .findByFoodCounterIdAndTimestampBetween(counterId, startOfDay, now);

            if (counterAnalytics.isEmpty()) {
                log.debug("No analytics data found for counter ID: {}", counterId);
                return 0.0;
            }

            // ✅ Calculate average using COALESCE logic
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

            log.debug("✅ Counter ID {} - avg={}min (avgD:{}, estW:{}, manW:{} from {} records)",
                    counterId, String.format("%.2f", avgDwell),
                    usedAvgDwell, usedEstWait, usedManualWait, validTimes.size());

            return avgDwell;

        } catch (Exception e) {
            log.error("Error calculating counter-specific avg dwell time: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * ✅ UPDATED: Get dwell time distribution with COALESCE
     */
    private List<DwellTimeDataDTO> getDwellTimeData(Long cafeteriaLocationId, LocalDateTime startTime, LocalDateTime endTime) {
        try {
            List<CafeteriaAnalytics> analytics = analyticsRepository.findByCafeteriaLocationAndTimeRange(
                    cafeteriaLocationId, startTime, endTime);

            // Group by individual minute values using COALESCE
            Map<Integer, Long> dwellTimeByMinute = new TreeMap<>();
            int usedAvgDwell = 0, usedEstWait = 0, usedManualWait = 0, skipped = 0;

            for (CafeteriaAnalytics a : analytics) {
                Double dwell = null;

                // COALESCE: avgDwellTime → estimatedWaitTime → manualWaitTime
                if (a.getAvgDwellTime() != null && a.getAvgDwellTime() > 0) {
                    dwell = a.getAvgDwellTime();
                    usedAvgDwell++;
                } else if (a.getEstimatedWaitTime() != null && a.getEstimatedWaitTime() > 0) {
                    dwell = a.getEstimatedWaitTime();
                    usedEstWait++;
                } else if (a.getManualWaitTime() != null && a.getManualWaitTime() > 0) {
                    dwell = a.getManualWaitTime();
                    usedManualWait++;
                }

                if (dwell == null || dwell <= 0) {
                    skipped++;
                    continue;
                }

                int minutes = (int) Math.round(dwell);
                dwellTimeByMinute.put(minutes, dwellTimeByMinute.getOrDefault(minutes, 0L) + 1);
            }

            log.info("📊 Dwell data sources - avgDwell: {}, estWait: {}, manualWait: {}, skipped: {}",
                    usedAvgDwell, usedEstWait, usedManualWait, skipped);

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

    /**
     * ✅ UPDATED: Get dwell time data for a specific counter with proper inCount calculation
     */
    @Transactional(readOnly = true)
    public CounterDwellTimeResponseDTO getDwellTimeByCounter(
            String tenantCode,
            String cafeteriaCode,
            String counterName,
            String timeFilter,
            Integer timeRange
    ) {
        log.info("Fetching counter-specific dwell time for counter: {}", counterName);

        try {
            CafeteriaLocation location = locationRepository.findByTenantCodeAndCafeteriaCode(tenantCode, cafeteriaCode)
                    .orElseThrow(() -> new RuntimeException("Cafeteria not found: " + tenantCode + "/" + cafeteriaCode));

            FoodCounter counter = counterRepository.findByCounterNameAndCafeteriaLocation(counterName, location)
                    .orElseThrow(() -> new RuntimeException("Counter not found: " + counterName));

            log.info("Found counter: {} with ID: {}", counterName, counter.getId());

            LocalDateTime startTime = calculateStartTime(timeFilter, timeRange);
            LocalDateTime endTime = LocalDateTime.now();

            // Get analytics data for this specific counter
            List<CafeteriaAnalytics> analytics = analyticsRepository
                    .findByFoodCounterIdAndTimestampBetween(counter.getId(), startTime, endTime);

            log.info("✅ Found {} analytics records for counter: {}", analytics.size(), counterName);

            // Log data quality
            long withAvgDwell = analytics.stream()
                    .filter(a -> a.getAvgDwellTime() != null && a.getAvgDwellTime() > 0)
                    .count();
            long withEstWait = analytics.stream()
                    .filter(a -> a.getEstimatedWaitTime() != null && a.getEstimatedWaitTime() > 0)
                    .count();
            long withManualWait = analytics.stream()
                    .filter(a -> a.getManualWaitTime() != null && a.getManualWaitTime() > 0)
                    .count();

            log.info("📊 Data quality for '{}' - avgDwell: {}, estWait: {}, manualWait: {} out of {}",
                    counterName, withAvgDwell, withEstWait, withManualWait, analytics.size());

            // Calculate distribution and stats
            List<DwellTimeDataDTO> dwellTimeData = calculateDwellTimeDistributionWithFallback(analytics);

            // ✅ UPDATED: Pass all required parameters (same as counter efficiency)
            CounterStatsDTO stats = calculateCounterStatsWithFallback(
                    analytics,
                    counterName,
                    counter.getId(),           // ✅ NEW: counterId
                    location.getId(),          // ✅ NEW: cafeteriaLocationId
                    startTime,                 // ✅ NEW: startTime
                    endTime                    // ✅ NEW: endTime
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
     * ✅ Calculate dwell time distribution with COALESCE
     */
    private List<DwellTimeDataDTO> calculateDwellTimeDistributionWithFallback(List<CafeteriaAnalytics> analytics) {
        Map<Integer, Long> dwellTimeByMinute = new TreeMap<>();
        int usedAvgDwell = 0, usedEstWait = 0, usedManualWait = 0, skipped = 0;

        for (CafeteriaAnalytics a : analytics) {
            Double dwell = null;

            if (a.getAvgDwellTime() != null && a.getAvgDwellTime() > 0) {
                dwell = a.getAvgDwellTime();
                usedAvgDwell++;
            } else if (a.getEstimatedWaitTime() != null && a.getEstimatedWaitTime() > 0) {
                dwell = a.getEstimatedWaitTime();
                usedEstWait++;
            } else if (a.getManualWaitTime() != null && a.getManualWaitTime() > 0) {
                dwell = a.getManualWaitTime();
                usedManualWait++;
            }

            if (dwell == null || dwell <= 0) {
                skipped++;
                continue;
            }

            int minutes = (int) Math.round(dwell);
            dwellTimeByMinute.put(minutes, dwellTimeByMinute.getOrDefault(minutes, 0L) + 1);
        }

        log.info("📊 Distribution sources - avgDwell: {}, estWait: {}, manualWait: {}, skipped: {}",
                usedAvgDwell, usedEstWait, usedManualWait, skipped);

        if (dwellTimeByMinute.isEmpty()) {
            log.warn("⚠️ No valid time data found");
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
     * ✅ Calculate counter statistics with COALESCE
     */
    /**
     * ✅ UPDATED: Calculate counter statistics with inCount and peak queue
     */
    /**
            * ✅ UPDATED: Calculate counter statistics using timeline query (same as counter efficiency)
 */
    /**
     * ✅ UPDATED: Calculate counter statistics using timeline query (same as counter efficiency)
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

        // ✅ SAME AS COUNTER EFFICIENCY: Get timeline data
        List<Object[]> inCountTimeline = analyticsRepository.getCounterInCountTimeline(
                cafeteriaLocationId,
                counterId,
                startTime,
                endTime
        );

        // ✅ SAME AS COUNTER EFFICIENCY: Calculate total served using delta method
        Integer totalVisitors = calculateTotalServedFromInCount(inCountTimeline);

        log.info("✅ Counter '{}' - Total entries (inCount deltas): {}", counterName, totalVisitors);

        // ✅ FIXED: Use currentOccupancy (actual people at counter) for peak queue
        Integer peakQueue = analytics.stream()
                .filter(a -> a.getCurrentOccupancy() != null)
                .mapToInt(CafeteriaAnalytics::getCurrentOccupancy)
                .max()
                .orElse(0);

        log.info("✅ Counter '{}' - Peak occupancy (currentOccupancy): {}", counterName, peakQueue);

        // Existing time calculations (wait time logic)
        List<Double> validTimes = new ArrayList<>();
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

        if (validTimes.isEmpty()) {
            log.warn("⚠️ No valid time data for counter '{}'", counterName);
            return CounterStatsDTO.builder()
                    .totalVisitors(totalVisitors)
                    .avgWaitTime(0.0)
                    .minWaitTime(0)
                    .maxWaitTime(0)
                    .mostCommonWaitTime("Peak occupancy: " + peakQueue + " people")  // ✅ Changed label
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

        String mostCommonDisplay = "Peak occupancy: " + peakQueue + " people";  // ✅ Changed label

        log.info("✅ Counter '{}' stats: entries={}, avg={}min, min={}min, max={}min, peakOccupancy={}",
                counterName, totalVisitors, String.format("%.2f", avgWaitTime), minWaitTime, maxWaitTime, peakQueue);

        return CounterStatsDTO.builder()
                .totalVisitors(totalVisitors)
                .avgWaitTime(Math.round(avgWaitTime * 100.0) / 100.0)
                .minWaitTime(minWaitTime)
                .maxWaitTime(maxWaitTime)
                .mostCommonWaitTime(mostCommonDisplay)
                .peakQueueLength(peakQueue)  // ✅ Now contains peak currentOccupancy
                .build();
    }

    private CounterStatsDTO buildEmptyCounterStats() {
        return CounterStatsDTO.builder()
                .totalVisitors(0)
                .avgWaitTime(0.0)
                .minWaitTime(0)
                .maxWaitTime(0)
                .mostCommonWaitTime("Peak occupancy: 0 people")  // ✅ Changed label
                .peakQueueLength(0)
                .build();
    }

    private List<FootfallComparisonDTO> getFootfallComparison(Long cafeteriaLocationId, LocalDateTime startTime, LocalDateTime endTime, String timeFilter) {
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

    private List<OccupancyTrendDTO> getOccupancyTrend(Long cafeteriaLocationId, LocalDateTime startTime, LocalDateTime endTime, String timeFilter) {
        try {
            LocalDateTime now = LocalDateTime.now();

            if (timeFilter.equals("daily")) {
                LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.of(7, 0));
                startTime = todayStart;
                endTime = now;
            }

            List<CafeteriaAnalytics> analytics = analyticsRepository.findByCafeteriaLocationAndTimeRange(
                    cafeteriaLocationId, startTime, endTime);

            analytics = analytics.stream()
                    .filter(a -> !a.getTimestamp().isAfter(now))
                    .collect(Collectors.toList());

            return aggregateOccupancyTrend(analytics, timeFilter);
        } catch (Exception e) {
            log.error("Error in getOccupancyTrend: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    private List<CounterCongestionTrendDTO> getCounterCongestionTrend(
            Long cafeteriaLocationId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String timeFilter) {
        try {
            LocalDateTime now = LocalDateTime.now();

            if (timeFilter.equals("daily")) {
                LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.of(7, 0));
                startTime = todayStart;
                endTime = now;
            }

            List<CafeteriaAnalytics> analytics = analyticsRepository.findByCafeteriaLocationAndTimeRange(
                    cafeteriaLocationId, startTime, endTime);

            // ✅ Log raw data count
            log.info("📊 Fetched {} analytics records for congestion trend (time: {} to {})",
                    analytics.size(), startTime, endTime);

            // ✅ Filter future data
            analytics = analytics.stream()
                    .filter(a -> !a.getTimestamp().isAfter(now))
                    .collect(Collectors.toList());

            log.info("📊 After filtering future data: {} records remain", analytics.size());

            // ✅ Check data quality
            long withOccupancy = analytics.stream()
                    .filter(a -> a.getCurrentOccupancy() != null && a.getCurrentOccupancy() > 0)
                    .count();

            log.info("📊 Records with occupancy data: {}/{}", withOccupancy, analytics.size());

            if (analytics.isEmpty()) {
                log.warn("⚠️ No analytics data available for congestion trend");
                return new ArrayList<>();
            }

            return aggregateCounterCongestion(analytics, timeFilter);
        } catch (Exception e) {
            log.error("❌ Error in getCounterCongestionTrend: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }




    /**
     * ✅ UPDATED: Aggregate counter congestion using CURRENT_OCCUPANCY (MAX per hour)
     * Shows peak occupancy at each counter per time bucket
     */
    /**
     * ✅ FIXED: Aggregate counter congestion with better fallback logic
     */
    private List<CounterCongestionTrendDTO> aggregateCounterCongestion(
            List<CafeteriaAnalytics> analytics,
            String timeFilter) {

        if (analytics.isEmpty()) {
            log.warn("⚠️ No analytics data available for congestion trend");
            return new ArrayList<>();
        }

        // Group analytics by time bucket
        Map<String, List<CafeteriaAnalytics>> grouped = analytics.stream()
                .collect(Collectors.groupingBy(a -> formatTimestamp(a.getTimestamp(), timeFilter)));

        log.info("📊 Processing {} time buckets for congestion trend", grouped.size());

        List<CounterCongestionTrendDTO> result = grouped.entrySet().stream()
                .map(entry -> {
                    Map<String, Integer> counterQueues = new HashMap<>();

                    // Separate counter-specific and cafeteria-level data
                    List<CafeteriaAnalytics> counterData = entry.getValue().stream()
                            .filter(a -> a.getFoodCounter() != null)
                            .collect(Collectors.toList());

                    List<CafeteriaAnalytics> cafeteriaData = entry.getValue().stream()
                            .filter(a -> a.getFoodCounter() == null)
                            .collect(Collectors.toList());

                    // PRIORITY 1: Use counter-specific data if available
                    if (!counterData.isEmpty()) {
                        counterQueues = counterData.stream()
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

                        log.debug("✅ Time: {} - Found counter data for {} counters",
                                entry.getKey(), counterQueues.size());
                    }

                    // PRIORITY 2: Fallback to cafeteria-level data
                    if (counterQueues.isEmpty() && !cafeteriaData.isEmpty()) {
                        Optional<Integer> maxOccupancy = cafeteriaData.stream()
                                .map(a -> a.getCurrentOccupancy() != null ? a.getCurrentOccupancy() : 0)
                                .filter(o -> o > 0)
                                .max(Integer::compare);

                        if (maxOccupancy.isPresent()) {
                            counterQueues.put("Cafeteria Overall", maxOccupancy.get());
                            log.debug("⚠️ Time: {} - Using cafeteria-level data: {} occupancy",
                                    entry.getKey(), maxOccupancy.get());
                        }
                    }

                    // PRIORITY 3: If still no data, log warning but include empty entry
                    if (counterQueues.isEmpty()) {
                        log.warn("⚠️ Time: {} - No occupancy data available (counter or cafeteria level)",
                                entry.getKey());
                        // Return empty map for this time bucket instead of filtering it out
                    }

                    return CounterCongestionTrendDTO.builder()
                            .timestamp(entry.getKey())
                            .counterQueues(counterQueues)
                            .build();
                })
                .sorted(Comparator.comparing(CounterCongestionTrendDTO::getTimestamp))
                .collect(Collectors.toList());

        log.info("✅ Generated {} congestion trend records", result.size());

        // Log sample of what we're returning
        if (!result.isEmpty()) {
            CounterCongestionTrendDTO sample = result.get(0);
            log.info("📊 Sample record - Time: {}, Counters: {}",
                    sample.getTimestamp(), sample.getCounterQueues().keySet());
        }

        return result;
    }

    public List<EnhancedCounterCongestionDTO> getEnhancedCounterCongestionTrend(
            Long cafeteriaLocationId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String timeFilter) {
        try {
            LocalDateTime now = LocalDateTime.now();

            if (timeFilter.equals("daily")) {
                LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.of(7, 0));
                startTime = todayStart;
                endTime = now;
            }

            List<CafeteriaAnalytics> analytics = analyticsRepository.findByCafeteriaLocationAndTimeRange(
                    cafeteriaLocationId, startTime, endTime);

            analytics = analytics.stream()
                    .filter(a -> !a.getTimestamp().isAfter(now))
                    .collect(Collectors.toList());

            return aggregateEnhancedCounterCongestion(analytics, timeFilter);
        } catch (Exception e) {
            log.error("Error in getEnhancedCounterCongestionTrend: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * ✅ FIXED: Return minute-level granular data with full statistics (max, avg, min, count)
     * Returns individual data points for each minute, not aggregated by hour
     */
    /**
     * ✅ FIXED: Enhanced congestion with better data handling
     */
    /**
     * ✅ FIXED: Enhanced congestion with ALL counters included
     */
    /**
     * ✅ FIXED: Return minute-level granular data with full statistics (max, avg, min, count)
     * Returns individual data points for each minute, not aggregated by hour
     * Only returns counter-specific data - NO cafeteria-level fallback
     */
    private List<EnhancedCounterCongestionDTO> aggregateEnhancedCounterCongestion(
            List<CafeteriaAnalytics> analytics,
            String timeFilter) {

        // ✅ STRICT FILTER: Only counter-specific data with occupancy
        List<CafeteriaAnalytics> counterData = analytics.stream()
                .filter(a -> a.getFoodCounter() != null)  // Must have a food counter
                .filter(a -> a.getCurrentOccupancy() != null)  // Must have occupancy data
                .filter(a -> a.getCurrentOccupancy() > 0)  // Must be non-zero
                .collect(Collectors.toList());

        log.info("📊 Filtered {} counter-specific records (from {} total)",
                counterData.size(), analytics.size());

        if (counterData.isEmpty()) {
            log.warn("⚠️ No counter-specific data found! Returning empty list.");
            return new ArrayList<>();
        }

        // Group by minute-level timestamp and counter
        Map<String, Map<String, List<Integer>>> timeBuckets = new HashMap<>();

        for (CafeteriaAnalytics analytic : counterData) {
            // Use minute-level formatting for granular data
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

                        // Determine status based on max occupancy
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

                // ✅ Only add if we have counter stats (no "Cafeteria Overall" fallback)
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

    /**
     * ✅ NEW: Format timestamp with minutes for granular data
     */
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

    private List<EnhancedCounterCongestionDTO> aggregateEnhancedCounterCongestionWithOccupancy(
            List<CafeteriaAnalytics> analytics,
            String timeFilter) {

        // Group by time bucket
        Map<String, List<CafeteriaAnalytics>> grouped = analytics.stream()
                .collect(Collectors.groupingBy(a -> formatTimestamp(a.getTimestamp(), timeFilter)));

        return grouped.entrySet().stream()
                .map(entry -> {
                    Map<String, QueueStats> counterStats = new HashMap<>();

                    // Get only counter-specific data
                    List<CafeteriaAnalytics> counterData = entry.getValue().stream()
                            .filter(a -> a.getFoodCounter() != null)
                            .collect(Collectors.toList());

                    if (!counterData.isEmpty()) {
                        // Group by counter name
                        Map<String, List<CafeteriaAnalytics>> byCounter = counterData.stream()
                                .collect(Collectors.groupingBy(
                                        a -> a.getFoodCounter().getCounterName()
                                ));

                        // Calculate stats for each counter using CURRENT_OCCUPANCY
                        byCounter.forEach((counterName, records) -> {
                            List<Integer> occupancies = records.stream()
                                    .map(a -> a.getCurrentOccupancy() != null ? a.getCurrentOccupancy() : 0)
                                    .filter(o -> o > 0)
                                    .collect(Collectors.toList());

                            if (!occupancies.isEmpty()) {
                                Integer maxOccupancy = occupancies.stream().max(Integer::compare).orElse(0);
                                Double avgOccupancy = occupancies.stream().mapToInt(Integer::intValue).average().orElse(0.0);
                                Integer minOccupancy = occupancies.stream().min(Integer::compare).orElse(0);

                                // Determine congestion status based on max occupancy
                                String status = "LIGHT";
                                if (maxOccupancy >= 50) {
                                    status = "HEAVY";
                                } else if (maxOccupancy >= 30) {
                                    status = "MODERATE";
                                }

                                counterStats.put(counterName, QueueStats.builder()
                                        .maxQueue(maxOccupancy)      // Actually max occupancy
                                        .avgQueue(Math.round(avgOccupancy * 100.0) / 100.0)
                                        .minQueue(minOccupancy)
                                        .dataPoints(occupancies.size())
                                        .status(status)
                                        .build());
                            }
                        });

                        log.debug("📊 Time: {} - Occupancy stats for {} counters",
                                entry.getKey(), counterStats.size());
                    }

                    return EnhancedCounterCongestionDTO.builder()
                            .timestamp(entry.getKey())
                            .counterStats(counterStats)
                            .build();
                })
                .filter(dto -> !dto.getCounterStats().isEmpty())
                .sorted(Comparator.comparing(EnhancedCounterCongestionDTO::getTimestamp))
                .collect(Collectors.toList());
    }

    // In CafeteriaDashboardService.java

    private List<CounterEfficiencyDTO> getCounterEfficiency(
            Long cafeteriaLocationId,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        try {
            log.info("📊 Fetching counter efficiency for cafeteria: {}, startTime: {}",
                    cafeteriaLocationId, startTime);

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
                            // ✅ CORRECT FIELD MAPPING (with counter ID)
                            Long counterId = ((Number) row[0]).longValue();        // row[0] = counter ID
                            String counterName = (String) row[1];                  // row[1] = counter name
                            Double avgQueue = row[2] != null ? ((Number) row[2]).doubleValue() : 0.0;     // row[2] = avg queue
                            Double avgDwell = row[3] != null ? ((Number) row[3]).doubleValue() : 0.0;     // row[3] = avg dwell
                            Double avgWait = row[4] != null ? ((Number) row[4]).doubleValue() : 0.0;      // row[4] = avg wait
                            Double maxWait = row[5] != null ? ((Number) row[5]).doubleValue() : 0.0;      // row[5] = MAX wait ✅
                            Long totalServedFromQuery = row[6] != null ? ((Number) row[6]).longValue() : 0L; // row[6] = sum inCount

                            log.debug("📊 Processing counter: {} (ID: {})", counterName, counterId);

                            // Get timeline data for this counter
                            List<Object[]> inCountTimeline =
                                    analyticsRepository.getCounterInCountTimeline(
                                            cafeteriaLocationId,
                                            counterId,
                                            startTime,
                                            endTime
                                    );

                            log.debug("📊 Timeline has {} records for counter: {}",
                                    inCountTimeline.size(), counterName);

                            // Calculate total served from timeline
                            int totalServed = calculateTotalServedFromInCount(inCountTimeline);

                            // Fallback to query sum if timeline calculation returns 0
                            if (totalServed == 0 && totalServedFromQuery != null && totalServedFromQuery > 0) {
                                totalServed = totalServedFromQuery.intValue();
                                log.debug("📊 Using query sum for counter '{}': {}", counterName, totalServed);
                            }

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
                                    .peakWaitTime(maxWait)  // ✅ MAX wait time from query
                                    .efficiency(efficiency)
                                    .build();
                        } catch (Exception e) {
                            log.error("❌ Error processing counter efficiency row: {}", e.getMessage(), e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)  // Remove any null entries from errors
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("❌ Error in getCounterEfficiency: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    //just for testing

    private int calculateTotalServedFromInCount(List<Object[]> timeline) {
        int totalServed = 0;
        Integer previous = null;

        for (Object[] row : timeline) {
            if (row == null || row[1] == null) continue;

            Integer current = ((Number) row[1]).intValue();

            if (previous != null) {
                int delta = current - previous;

                // ✅ Count only positive deltas
                if (delta > 0) {
                    totalServed += delta;
                }
            }

            previous = current;
        }

        return totalServed;
    }




    private TodaysVisitorsDTO getTodaysVisitors(Long cafeteriaLocationId) {
        try {
            LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.of(7, 0));
            LocalDateTime now = LocalDateTime.now();

            // ✅ Get all counters for this cafeteria
            List<FoodCounter> counters = counterRepository.findByCafeteriaLocationId(cafeteriaLocationId);

            if (counters.isEmpty()) {
                log.warn("No counters found for cafeteria location: {}", cafeteriaLocationId);
                return buildEmptyTodaysVisitors();
            }

            // ✅ Calculate total visitors by summing deltas from ALL counters
            int totalVisitorsToday = 0;
            for (FoodCounter counter : counters) {
                List<Object[]> timeline = analyticsRepository.getCounterInCountTimeline(
                        cafeteriaLocationId,
                        counter.getId(),
                        startOfDay,
                        now
                );
                int counterVisitors = calculateTotalServedFromInCount(timeline);
                totalVisitorsToday += counterVisitors;

                log.debug("Counter '{}': {} entries", counter.getCounterName(), counterVisitors);
            }

            log.info("✅ Total visitors today (all counters): {}", totalVisitorsToday);

            // ✅ Calculate yesterday's visitors the same way
            LocalDateTime yesterdayStart = startOfDay.minusDays(1);
            LocalDateTime yesterdayEnd = now.minusDays(1);

            int totalVisitorsYesterday = 0;
            for (FoodCounter counter : counters) {
                List<Object[]> timeline = analyticsRepository.getCounterInCountTimeline(
                        cafeteriaLocationId,
                        counter.getId(),
                        yesterdayStart,
                        yesterdayEnd
                );
                totalVisitorsYesterday += calculateTotalServedFromInCount(timeline);
            }

            log.info("✅ Total visitors yesterday (all counters): {}", totalVisitorsYesterday);

            // Calculate percentage change
            Double percentageChange = 0.0;
            String trend = "up";
            if (totalVisitorsYesterday > 0) {
                percentageChange = ((totalVisitorsToday - totalVisitorsYesterday) * 100.0) / totalVisitorsYesterday;
                trend = percentageChange >= 0 ? "up" : "down";
            }

            // ✅ Calculate last hour visitors
            LocalDateTime lastHourStart = now.minusHours(1);

            int totalVisitorsLastHour = 0;
            for (FoodCounter counter : counters) {
                List<Object[]> timeline = analyticsRepository.getCounterInCountTimeline(
                        cafeteriaLocationId,
                        counter.getId(),
                        lastHourStart,
                        now
                );
                totalVisitorsLastHour += calculateTotalServedFromInCount(timeline);
            }

            log.info("✅ Total visitors last hour (all counters): {}", totalVisitorsLastHour);

            return TodaysVisitorsDTO.builder()
                    .total(totalVisitorsToday)
                    .sinceTime("7:00 AM")
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

    /**
     * ✅ UPDATED: Get average dwell time with COALESCE
     */
    private AvgDwellTimeDTO getAvgDwellTime(Long cafeteriaLocationId) {
        try {
            List<FoodCounter> counters = counterRepository.findByCafeteriaLocationId(cafeteriaLocationId);

            if (counters.isEmpty()) {
                log.warn("No counters found for cafeteria location: {}", cafeteriaLocationId);
                return buildEmptyAvgDwellTime();
            }

            List<Double> counterAverages = new ArrayList<>();

            for (FoodCounter counter : counters) {
                Double counterAvg = getCounterSpecificAvgDwellTime(counter.getId());
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

    private PeakHoursDTO getPeakHours(Long cafeteriaLocationId) {
        try {
            LocalDateTime startOfWeek = LocalDateTime.now().minusDays(7);
            List<Object[]> peakData = analyticsRepository.getPeakHours(cafeteriaLocationId, startOfWeek);

            List<PeakSlot> peakSlots = peakData.stream()
                    .limit(3)
                    .map(row -> {
                        Integer hour = ((Number) row[0]).intValue();
                        Double avgOccupancy = ((Number) row[1]).doubleValue();

                        String type = "Regular";
                        if (hour >= 12 && hour <= 14) type = "Lunch Rush";
                        else if (hour >= 19 && hour <= 21) type = "Dinner Peak";
                        else if (hour >= 8 && hour <= 9) type = "Breakfast";

                        return PeakSlot.builder()
                                .time(String.format("%02d:00 - %02d:00", hour, hour + 2))
                                .type(type)
                                .occupancy(avgOccupancy.intValue())
                                .build();
                    })
                    .collect(Collectors.toList());

            int currentHour = LocalDateTime.now().getHour();
            String currentStatus = (currentHour >= 8 && currentHour < 9) ||
                    (currentHour >= 12 && currentHour < 14) ||
                    (currentHour >= 19 && currentHour < 21) ? "Peak Hours" : "Off-Peak";

            String nextPeak = currentHour < 8 ? "8:00 AM" :
                    currentHour < 12 ? "12:00 PM" :
                            currentHour < 19 ? "7:00 PM" : "Tomorrow 8:00 AM";

            return PeakHoursDTO.builder()
                    .currentStatus(currentStatus)
                    .nextPeak(nextPeak)
                    .peakSlots(peakSlots)
                    .highestPeak(peakSlots.isEmpty() ? "12:00 PM" : peakSlots.get(0).getTime())
                    .averagePeakOccupancy(peakSlots.isEmpty() ? 0 :
                            peakSlots.stream().mapToInt(PeakSlot::getOccupancy).sum() / peakSlots.size())
                    .build();
        } catch (Exception e) {
            log.error("Error in getPeakHours: {}", e.getMessage(), e);
            return PeakHoursDTO.builder()
                    .currentStatus("Off-Peak")
                    .nextPeak("12:00 PM")
                    .peakSlots(new ArrayList<>())
                    .highestPeak("12:00 PM")
                    .averagePeakOccupancy(0)
                    .build();
        }
    }

    private LocalDateTime calculateStartTime(String timeFilter, Integer timeRange) {
        LocalDateTime now = LocalDateTime.now();

        switch (timeFilter) {
            case "daily":
                return now.minusHours(timeRange != null ? timeRange : 24);
            case "weekly":
                return now.minusDays(7);
            case "monthly":
                return now.minusMonths(1);
            default:
                return now.minusHours(24);
        }
    }

    private List<FlowDataDTO> aggregateFlowData(List<CafeteriaAnalytics> analytics, String timeFilter) {
        Map<String, List<CafeteriaAnalytics>> grouped = analytics.stream()
                .collect(Collectors.groupingBy(a -> formatTimestamp(a.getTimestamp(), timeFilter)));

        return grouped.entrySet().stream()
                .map(entry -> {
                    int inflow = entry.getValue().stream()
                            .mapToInt(a -> a.getInCount() != null ? a.getInCount() : 0)
                            .sum();

                    int outflow = 0;
                    try {
                        outflow = entry.getValue().stream()
                                .mapToInt(a -> {
                                    try {
                                        Object outCount = a.getClass().getMethod("getOutCount").invoke(a);
                                        return outCount != null ? ((Integer) outCount) : 0;
                                    } catch (Exception e) {
                                        return 0;
                                    }
                                })
                                .sum();
                    } catch (Exception e) {
                        log.debug("⚠️ outCount not available");
                    }

                    if (outflow == 0) {
                        outflow = (int) (inflow * 0.90);
                    }

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

    private List<OccupancyTrendDTO> aggregateOccupancyTrend(List<CafeteriaAnalytics> analytics, String timeFilter) {
        Map<String, List<CafeteriaAnalytics>> grouped = analytics.stream()
                .collect(Collectors.groupingBy(a -> formatTimestamp(a.getTimestamp(), timeFilter)));

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

    private List<CounterCongestionTrendDTO> aggregateCounterCongestionWithOccupancy(
            List<CafeteriaAnalytics> analytics,
            String timeFilter) {

        Map<String, List<CafeteriaAnalytics>> grouped = analytics.stream()
                .collect(Collectors.groupingBy(a -> formatTimestamp(a.getTimestamp(), timeFilter)));

        return grouped.entrySet().stream()
                .map(entry -> {
                    Map<String, Integer> counterQueues = new HashMap<>();

                    // Get only counter-specific data
                    List<CafeteriaAnalytics> counterData = entry.getValue().stream()
                            .filter(a -> a.getFoodCounter() != null)
                            .collect(Collectors.toList());

                    if (!counterData.isEmpty()) {
                        // ✅ Use CURRENT_OCCUPANCY (not queue_length)
                        counterQueues = counterData.stream()
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

                        log.debug("✅ Time: {} - Max occupancy: {}", entry.getKey(), counterQueues);
                    }

                    // Fallback to cafeteria-level data if no counter data
                    if (counterQueues.isEmpty()) {
                        List<CafeteriaAnalytics> cafeteriaData = entry.getValue().stream()
                                .filter(a -> a.getFoodCounter() == null)
                                .collect(Collectors.toList());

                        if (!cafeteriaData.isEmpty()) {
                            Optional<Integer> maxOccupancy = cafeteriaData.stream()
                                    .map(a -> a.getCurrentOccupancy() != null ? a.getCurrentOccupancy() : 0)
                                    .max(Integer::compare);

                            if (maxOccupancy.isPresent() && maxOccupancy.get() > 0) {
                                counterQueues.put("Cafeteria Level", maxOccupancy.get());
                            }
                        }
                    }

                    return CounterCongestionTrendDTO.builder()
                            .timestamp(entry.getKey())
                            .counterQueues(counterQueues)  // Now contains occupancy, not queue_length
                            .build();
                })
                .filter(dto -> !dto.getCounterQueues().isEmpty())
                .sorted(Comparator.comparing(CounterCongestionTrendDTO::getTimestamp))
                .collect(Collectors.toList());
    }

    private String formatTimestamp(LocalDateTime timestamp, String timeFilter) {
        switch (timeFilter) {
            case "daily":
                return String.format("%02d:00", timestamp.getHour());
            case "weekly":
                String[] days = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
                return days[timestamp.getDayOfWeek().getValue() % 7];
            case "monthly":
                String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
                return months[timestamp.getMonthValue() - 1];
            default:
                return timestamp.toString();
        }
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