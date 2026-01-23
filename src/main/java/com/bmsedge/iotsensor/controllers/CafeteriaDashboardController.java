package com.bmsedge.iotsensor.controllers;

import com.bmsedge.iotsensor.dto.*;
import com.bmsedge.iotsensor.model.CafeteriaAnalytics;
import com.bmsedge.iotsensor.model.CafeteriaLocation;
import com.bmsedge.iotsensor.model.FoodCounter;
import com.bmsedge.iotsensor.repository.CafeteriaAnalyticsRepository;
import com.bmsedge.iotsensor.repository.CafeteriaLocationRepository;
import com.bmsedge.iotsensor.repository.FoodCounterRepository;
import com.bmsedge.iotsensor.service.CafeteriaDashboardService;
import com.bmsedge.iotsensor.service.MqttCafeteriaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.Serializable;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/cafeteria/dashboard")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class CafeteriaDashboardController {

    private final CafeteriaDashboardService dashboardService;
    private final MqttCafeteriaService mqttService;
    private final CafeteriaAnalyticsRepository analyticsRepository;
    private final CafeteriaLocationRepository locationRepository;
    private final FoodCounterRepository counterRepository;

    /**
     * GET comprehensive dashboard data for a specific tenant and cafeteria
     */
    @GetMapping("/{tenantCode}/{cafeteriaCode}")
    public ResponseEntity<DashboardDataDTO> getDashboardData(
            @PathVariable String tenantCode,
            @PathVariable String cafeteriaCode,
            @RequestParam(defaultValue = "daily") String timeFilter,
            @RequestParam(required = false) Integer timeRange) {

        log.info("Dashboard request - Tenant: {}, Cafeteria: {}, Filter: {}, Range: {}",
                tenantCode, cafeteriaCode, timeFilter, timeRange);

        if (timeRange == null) {
            timeRange = switch (timeFilter) {
                case "weekly" -> 168;
                case "monthly" -> 720;
                default -> 24;
            };
        }

        DashboardDataDTO dashboard = dashboardService.getDashboardData(
                tenantCode, cafeteriaCode, timeFilter, timeRange);

        return ResponseEntity.ok(dashboard);
    }

    /**
     * ✅ GET Counter Master Data List (JSON or CSV)
     */
    @GetMapping("/{tenantCode}/{cafeteriaCode}/counters/master-data")
    public ResponseEntity<?> getCounterMasterData(
            @PathVariable String tenantCode,
            @PathVariable String cafeteriaCode,
            @RequestParam(defaultValue = "daily") String timeFilter,
            @RequestParam(required = false) Integer timeRange,
            @RequestParam(required = false) Boolean latestOnly,
            @RequestParam(defaultValue = "json") String format) {

        // Default to false if not provided
        if (latestOnly == null) {
            latestOnly = false;
        }

        log.info("📋 Master Data Request - Tenant: {}, Cafeteria: {}, Filter: {}, Range: {}, Latest: {}, Format: {}",
                tenantCode, cafeteriaCode, timeFilter, timeRange, latestOnly, format);

        try {
            CounterMasterDataResponseDTO response = dashboardService.getCounterMasterData(
                    tenantCode, cafeteriaCode, timeFilter, timeRange, latestOnly);

            log.info("✅ Service returned {} records (latestOnly: {})",
                    response.getTotalRecords(), latestOnly);

            // Handle CSV format
            if ("csv".equalsIgnoreCase(format)) {
                log.info("📄 Generating CSV export with {} records", response.getTotalRecords());
                return exportMasterDataAsCsv(response);
            }

            // Default JSON response
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Error fetching master data: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", e.getMessage(),
                    "timestamp", LocalDateTime.now()
            ));
        }
    }

    // ==================== QUEUE ANALYSIS ENDPOINTS (FIXED WITH TIME RANGE SUPPORT) ====================

    /**
     * ✅ FIXED: GET Queue Length Trends (Line Chart) - NOW SUPPORTS TIME RANGE
     * Shows time-series data of in_count (inflow) for all counters
     * Default: 5-minute intervals, current day
     */
    @GetMapping("/{tenantCode}/{cafeteriaCode}/queue-trends")
    public ResponseEntity<QueueLengthTrendDTO.Response> getQueueLengthTrends(
            @PathVariable String tenantCode,
            @PathVariable String cafeteriaCode,
            @RequestParam(defaultValue = "5") Integer intervalMinutes,
            @RequestParam(defaultValue = "daily") String timeFilter,
            @RequestParam(required = false) Integer timeRange) {

        log.info("📊 Queue Trends Request (IN_COUNT) - Tenant: {}, Cafeteria: {}, Interval: {} min, Filter: {}, Range: {}",
                tenantCode, cafeteriaCode, intervalMinutes, timeFilter, timeRange);

        try {
            QueueLengthTrendDTO.Response response =
                    dashboardService.getQueueLengthTrends(tenantCode, cafeteriaCode, intervalMinutes, timeFilter, timeRange);

            log.info("✅ Returned in_count trends: {} time points for {} counters",
                    response.getSummary().getTotalDataPoints(), response.getCounters().size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Error fetching in_count trends: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(
                    QueueLengthTrendDTO.Response.builder()
                            .trends(new ArrayList<>())
                            .reportGeneratedAt(LocalDateTime.now())
                            .interval(intervalMinutes + "-minute")
                            .counters(new ArrayList<>())
                            .build()
            );
        }
    }

    /**
     * ✅ FIXED: GET Average Queue Comparison (Bar Chart) - NOW SUPPORTS TIME RANGE
     * Shows average in_count (inflow) for each counter
     */
    @GetMapping("/{tenantCode}/{cafeteriaCode}/queue-comparison")
    public ResponseEntity<CounterQueueComparisonDTO.Response> getAverageQueueComparison(
            @PathVariable String tenantCode,
            @PathVariable String cafeteriaCode,
            @RequestParam(defaultValue = "daily") String timeFilter,
            @RequestParam(required = false) Integer timeRange) {

        log.info("📊 Queue Comparison Request (IN_COUNT) - Tenant: {}, Cafeteria: {}, Filter: {}, Range: {}",
                tenantCode, cafeteriaCode, timeFilter, timeRange);

        try {
            CounterQueueComparisonDTO.Response response =
                    dashboardService.getAverageQueueComparison(tenantCode, cafeteriaCode, timeFilter, timeRange);

            log.info("✅ Returned in_count comparison for {} counters", response.getTotalCounters());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Error fetching in_count comparison: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(
                    CounterQueueComparisonDTO.Response.builder()
                            .counters(new ArrayList<>())
                            .reportGeneratedAt(LocalDateTime.now())
                            .totalCounters(0)
                            .build()
            );
        }
    }

    /**
     * ✅ FIXED: GET Congestion Rate Comparison (Bar Chart) - NOW SUPPORTS TIME RANGE
     * Shows percentage of time each counter spent in HIGH congestion
     */
    @GetMapping("/{tenantCode}/{cafeteriaCode}/congestion-rate")
    public ResponseEntity<CounterCongestionRateDTO.Response> getCongestionRateComparison(
            @PathVariable String tenantCode,
            @PathVariable String cafeteriaCode,
            @RequestParam(defaultValue = "daily") String timeFilter,
            @RequestParam(required = false) Integer timeRange) {

        log.info("📊 Congestion Rate Request - Tenant: {}, Cafeteria: {}, Filter: {}, Range: {}",
                tenantCode, cafeteriaCode, timeFilter, timeRange);

        try {
            CounterCongestionRateDTO.Response response =
                    dashboardService.getCongestionRateComparison(tenantCode, cafeteriaCode, timeFilter, timeRange);

            log.info("✅ Returned congestion rate for {} counters", response.getTotalCounters());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Error fetching congestion rate: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(
                    CounterCongestionRateDTO.Response.builder()
                            .counters(new ArrayList<>())
                            .reportGeneratedAt(LocalDateTime.now())
                            .totalCounters(0)
                            .build()
            );
        }
    }

    /**
     * ✅ FIXED: GET Queue KPIs Summary - NOW SUPPORTS TIME RANGE
     * Provides calculated KPIs from queue data
     */
    @GetMapping("/{tenantCode}/{cafeteriaCode}/queue-kpis")
    public ResponseEntity<QueueKPIResponseDTO> getQueueKPIs(
            @PathVariable String tenantCode,
            @PathVariable String cafeteriaCode,
            @RequestParam(defaultValue = "daily") String timeFilter,
            @RequestParam(required = false) Integer timeRange) {

        log.info("📊 Queue KPIs Request - Tenant: {}, Cafeteria: {}, Filter: {}, Range: {}",
                tenantCode, cafeteriaCode, timeFilter, timeRange);

        try {
            QueueKPIResponseDTO response = dashboardService.getQueueKPIs(tenantCode, cafeteriaCode, timeFilter, timeRange);

            log.info("✅ Returned Queue KPIs");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Error fetching Queue KPIs: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(
                    QueueKPIResponseDTO.builder()
                            .overallAvgQueue(0.0)
                            .peakQueueLength(0.0)
                            .mostCongestedCounter("N/A")
                            .congestionRate(0.0)
                            .peakHourAvgQueue(0.0)
                            .peakHourRange("N/A")
                            .reportGeneratedAt(LocalDateTime.now())
                            .build()
            );
        }
    }

    /**
     * 🔍 DEBUG ENDPOINT: Inspect queue data quality
     */
    @GetMapping("/{tenantCode}/{cafeteriaCode}/debug/queue-data")
    public ResponseEntity<Map<String, Object>> debugQueueData(
            @PathVariable String tenantCode,
            @PathVariable String cafeteriaCode) {

        log.info("🔍 Debugging queue data for: {}/{}", tenantCode, cafeteriaCode);

        try {
            CafeteriaLocation location = locationRepository.findByTenantCodeAndCafeteriaCode(tenantCode, cafeteriaCode)
                    .orElseThrow(() -> new RuntimeException("Cafeteria not found"));

            LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.of(7, 0));
            LocalDateTime now = LocalDateTime.now();

            List<CafeteriaAnalytics> analytics = analyticsRepository
                    .findByCafeteriaLocationAndTimeRange(location.getId(), startOfDay, now);

            // Filter counter-level data
            List<CafeteriaAnalytics> counterData = analytics.stream()
                    .filter(a -> a.getFoodCounter() != null)
                    .collect(Collectors.toList());

            long withQueue = counterData.stream()
                    .filter(a -> a.getQueueLength() != null && a.getQueueLength() > 0)
                    .count();

            long withInCount = counterData.stream()
                    .filter(a -> a.getInCount() != null && a.getInCount() > 0)
                    .count();

            long withCongestion = counterData.stream()
                    .filter(a -> a.getCongestionLevel() != null)
                    .count();

            Map<String, Long> queueByCounter = counterData.stream()
                    .filter(a -> a.getQueueLength() != null)
                    .collect(Collectors.groupingBy(
                            a -> a.getFoodCounter().getCounterName(),
                            Collectors.counting()
                    ));

            Map<String, Long> inCountByCounter = counterData.stream()
                    .filter(a -> a.getInCount() != null)
                    .collect(Collectors.groupingBy(
                            a -> a.getFoodCounter().getCounterName(),
                            Collectors.counting()
                    ));

            Map<String, Long> congestionDistribution = counterData.stream()
                    .filter(a -> a.getCongestionLevel() != null)
                    .collect(Collectors.groupingBy(
                            CafeteriaAnalytics::getCongestionLevel,
                            Collectors.counting()
                    ));

            String diagnosis;
            if (counterData.isEmpty()) {
                diagnosis = "❌ NO COUNTER DATA";
            } else if (withInCount == 0) {
                diagnosis = "❌ NO IN_COUNT DATA";
            } else if (withCongestion == 0) {
                diagnosis = "⚠️ NO CONGESTION LEVEL DATA";
            } else {
                diagnosis = "✅ DATA AVAILABLE";
            }

            return ResponseEntity.ok(Map.of(
                    "totalRecords", analytics.size(),
                    "counterRecords", counterData.size(),
                    "recordsWithQueue", withQueue,
                    "recordsWithInCount", withInCount
            ));

        } catch (Exception e) {
            log.error("Debug queue data error: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * ✅ DEDICATED CSV EXPORT ENDPOINT
     */
    @GetMapping("/{tenantCode}/{cafeteriaCode}/counters/master-data/export")
    public ResponseEntity<String> exportCounterMasterDataCsv(
            @PathVariable String tenantCode,
            @PathVariable String cafeteriaCode,
            @RequestParam(defaultValue = "daily") String timeFilter,
            @RequestParam(required = false) Integer timeRange) {

        log.info("📄 CSV Export Request - Tenant: {}, Cafeteria: {}, Filter: {}, Range: {}",
                tenantCode, cafeteriaCode, timeFilter, timeRange);

        try {
            // Force latestOnly = false for export
            CounterMasterDataResponseDTO response = dashboardService.getCounterMasterData(
                    tenantCode, cafeteriaCode, timeFilter, timeRange, false);

            log.info("✅ Exporting {} records to CSV", response.getTotalRecords());

            return exportMasterDataAsCsv(response);

        } catch (Exception e) {
            log.error("❌ Error exporting CSV: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("Error generating CSV: " + e.getMessage());
        }
    }

    /**
     * ✅ Export master data as CSV
     */
    private ResponseEntity<String> exportMasterDataAsCsv(CounterMasterDataResponseDTO response) {
        try {
            log.info("📄 Building CSV with {} records", response.getData().size());

            StringBuilder csv = new StringBuilder();

            // CSV Header
            csv.append("Counter Name,Counter Code,Device ID,Location,")
                    .append("Timestamp,Current Occupancy,Capacity,Occupancy %,")
                    .append("Avg Dwell Time (min),Estimated Wait Time (min),Manual Wait Time (min),")
                    .append("Wait Time Display,Queue Length,In Count,")
                    .append("Congestion Level,Service Status,Status Display,")
                    .append("Max Dwell Time (min),Active\n");

            // CSV Data Rows
            int rowCount = 0;
            for (CounterMasterDataDTO data : response.getData()) {
                csv.append(escapeCsv(data.getCounterName())).append(",")
                        .append(escapeCsv(data.getCounterCode())).append(",")
                        .append(escapeCsv(data.getDeviceId())).append(",")
                        .append(escapeCsv(data.getLocation())).append(",")
                        .append(data.getTimestamp() != null ? data.getTimestamp() : "").append(",")
                        .append(data.getCurrentOccupancy() != null ? data.getCurrentOccupancy() : "").append(",")
                        .append(data.getCapacity() != null ? data.getCapacity() : "").append(",")
                        .append(data.getOccupancyPercentage() != null ?
                                String.format("%.1f", data.getOccupancyPercentage()) : "").append(",")
                        .append(data.getAvgDwellTime() != null ?
                                String.format("%.2f", data.getAvgDwellTime()) : "").append(",")
                        .append(data.getEstimatedWaitTime() != null ?
                                String.format("%.2f", data.getEstimatedWaitTime()) : "").append(",")
                        .append(data.getManualWaitTime() != null ?
                                String.format("%.2f", data.getManualWaitTime()) : "").append(",")
                        .append(escapeCsv(data.getWaitTimeDisplay())).append(",")
                        .append(data.getQueueLength() != null ? data.getQueueLength() : "").append(",")
                        .append(data.getInCount() != null ? data.getInCount() : "").append(",")
                        .append(escapeCsv(data.getCongestionLevel())).append(",")
                        .append(escapeCsv(data.getServiceStatus())).append(",")
                        .append(escapeCsv(data.getStatusDisplay())).append(",")
                        .append(data.getMaxDwellTime() != null ?
                                String.format("%.2f", data.getMaxDwellTime()) : "").append(",")
                        .append(data.getIsActive() != null && data.getIsActive() ? "Yes" : "No").append("\n");
                rowCount++;
            }

            log.info("✅ CSV generated successfully with {} rows (+ header)", rowCount);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("text/csv"));
            headers.setContentDisposition(ContentDisposition.builder("attachment")
                    .filename("counter_master_data_" + response.getCafeteriaCode() + "_" +
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv")
                    .build());

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(csv.toString());

        } catch (Exception e) {
            log.error("❌ Error generating CSV: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("Error generating CSV: " + e.getMessage());
        }
    }

    /**
     * ✅ Escape CSV values
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * ✅ GET simplified counter list (lightweight version)
     */
    @GetMapping("/{tenantCode}/{cafeteriaCode}/counters/list-simple")
    public ResponseEntity<Map<String, Object>> getSimpleCounterList(
            @PathVariable String tenantCode,
            @PathVariable String cafeteriaCode) {

        log.info("📋 Simple Counter List Request - Tenant: {}, Cafeteria: {}",
                tenantCode, cafeteriaCode);

        try {
            // For simple list, always get latest only
            CounterMasterDataResponseDTO response = dashboardService.getCounterMasterData(
                    tenantCode, cafeteriaCode, "daily", 1, true);

            // Extract simplified data
            List<Map<String, ? extends Serializable>> simpleList = response.getData().stream()
                    .map(data -> Map.of(
                            "counterName", data.getCounterName(),
                            "timestamp", data.getTimestamp(),
                            "occupancy", data.getCurrentOccupancy() != null ? data.getCurrentOccupancy() : 0,
                            "inCount", data.getInCount() != null ? data.getInCount() : 0,
                            "waitTime", data.getWaitTimeDisplay(),
                            "status", data.getStatusDisplay()
                    ))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "counters", simpleList,
                    "totalCounters", simpleList.size(),
                    "timestamp", LocalDateTime.now()
            ));

        } catch (Exception e) {
            log.error("❌ Error fetching simple counter list: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", e.getMessage(),
                    "counters", new ArrayList<>()
            ));
        }
    }

    /**
     * ✅ Get dwell time data for a specific counter
     */
    @GetMapping("/{tenantCode}/{cafeteriaCode}/dwell-time/{counterName}")
    public ResponseEntity<CounterDwellTimeResponseDTO> getDwellTimeByCounter(
            @PathVariable String tenantCode,
            @PathVariable String cafeteriaCode,
            @PathVariable String counterName,
            @RequestParam(defaultValue = "daily") String timeFilter,
            @RequestParam(required = false) Integer timeRange) {

        log.info("Fetching dwell time for tenant: {}, cafeteria: {}, counter: {}, filter: {}",
                tenantCode, cafeteriaCode, counterName, timeFilter);

        try {
            CounterDwellTimeResponseDTO response = dashboardService.getDwellTimeByCounter(
                    tenantCode, cafeteriaCode, counterName, timeFilter, timeRange);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching counter dwell time: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(
                    CounterDwellTimeResponseDTO.builder()
                            .counterName(counterName)
                            .dwellTimeData(new ArrayList<>())
                            .timestamp(LocalDateTime.now())
                            .build()
            );
        }
    }

    /**
     * ✅ Get list of all available counters
     */
    @GetMapping("/{tenantCode}/{cafeteriaCode}/counters/list")
    public ResponseEntity<Map<String, Object>> getAvailableCounters(
            @PathVariable String tenantCode,
            @PathVariable String cafeteriaCode) {

        log.info("Fetching available counters for tenant: {}, cafeteria: {}", tenantCode, cafeteriaCode);

        try {
            List<String> counters = dashboardService.getAvailableCounters(tenantCode, cafeteriaCode);
            return ResponseEntity.ok(Map.of(
                    "counters", counters,
                    "count", counters.size(),
                    "timestamp", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("Error fetching available counters: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "counters", new ArrayList<>(),
                    "count", 0,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * GET occupancy status only (lightweight endpoint)
     */
    @GetMapping("/{tenantCode}/{cafeteriaCode}/occupancy")
    public ResponseEntity<Map<String, Object>> getOccupancyStatus(
            @PathVariable String tenantCode,
            @PathVariable String cafeteriaCode) {

        DashboardDataDTO dashboard = dashboardService.getDashboardData(
                tenantCode, cafeteriaCode, "daily", 1);

        return ResponseEntity.ok(Map.of(
                "occupancyStatus", dashboard.getOccupancyStatus(),
                "timestamp", dashboard.getLastUpdated()
        ));
    }

    /**
     * GET counter status only
     */
    @GetMapping("/{tenantCode}/{cafeteriaCode}/counters")
    public ResponseEntity<Map<String, Object>> getCounterStatus(
            @PathVariable String tenantCode,
            @PathVariable String cafeteriaCode) {

        DashboardDataDTO dashboard = dashboardService.getDashboardData(
                tenantCode, cafeteriaCode, "daily", 1);

        return ResponseEntity.ok(Map.of(
                "counters", dashboard.getCounterStatus(),
                "timestamp", dashboard.getLastUpdated()
        ));
    }

    /**
     * GET today's KPIs only
     */
    @GetMapping("/{tenantCode}/{cafeteriaCode}/kpis")
    public ResponseEntity<Map<String, Object>> getKPIs(
            @PathVariable String tenantCode,
            @PathVariable String cafeteriaCode) {

        DashboardDataDTO dashboard = dashboardService.getDashboardData(
                tenantCode, cafeteriaCode, "daily", 24);

        return ResponseEntity.ok(Map.of(
                "occupancyStatus", dashboard.getOccupancyStatus(),
                "todaysVisitors", dashboard.getTodaysVisitors(),
                "avgDwellTime", dashboard.getAvgDwellTime(),
                "peakHours", dashboard.getPeakHours(),
                "timestamp", dashboard.getLastUpdated()
        ));
    }

    /**
     * GET analytics charts data
     */
    @GetMapping("/{tenantCode}/{cafeteriaCode}/analytics")
    public ResponseEntity<Map<String, Object>> getAnalytics(
            @PathVariable String tenantCode,
            @PathVariable String cafeteriaCode,
            @RequestParam(defaultValue = "daily") String timeFilter,
            @RequestParam(required = false) Integer timeRange) {

        DashboardDataDTO dashboard = dashboardService.getDashboardData(
                tenantCode, cafeteriaCode, timeFilter, timeRange);

        return ResponseEntity.ok(Map.of(
                "flowData", dashboard.getFlowData(),
                "dwellTimeData", dashboard.getDwellTimeData(),
                "occupancyTrend", dashboard.getOccupancyTrend(),
                "counterCongestionTrend", dashboard.getCounterCongestionTrend(),
                "footfallComparison", dashboard.getFootfallComparison(),
                "counterEfficiency", dashboard.getCounterEfficiency(),
                "timestamp", dashboard.getLastUpdated()
        ));
    }

    /**
     * ✅ GET enhanced counter congestion with full statistics
     */
    @GetMapping("/{tenantCode}/{cafeteriaCode}/congestion/enhanced")
    public ResponseEntity<Map<String, Object>> getEnhancedCongestion(
            @PathVariable String tenantCode,
            @PathVariable String cafeteriaCode,
            @RequestParam(defaultValue = "daily") String timeFilter,
            @RequestParam(required = false) Integer timeRange) {

        try {
            CafeteriaLocation location = locationRepository.findByTenantCodeAndCafeteriaCode(tenantCode, cafeteriaCode)
                    .orElseThrow(() -> new RuntimeException("Cafeteria not found"));

            LocalDateTime endTime = LocalDateTime.now();
            LocalDateTime startTime = endTime.minusHours(24);

            log.info("📊 Fetching ALL data from last 24 hours: {} to {}", startTime, endTime);

            List<EnhancedCounterCongestionDTO> congestionData =
                    dashboardService.getEnhancedCounterCongestionTrend(
                            location.getId(), startTime, endTime, timeFilter);

            return ResponseEntity.ok(Map.of(
                    "timeRange", Map.of("start", startTime.toString(), "end", endTime.toString()),
                    "timeFilter", timeFilter,
                    "totalTimeBuckets", congestionData.size(),
                    "congestionTrend", congestionData,
                    "timestamp", LocalDateTime.now()
            ));

        } catch (Exception e) {
            log.error("Error: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", e.getMessage(),
                    "congestionTrend", new ArrayList<>()
            ));
        }
    }

    /**
     * 🔍 DEBUG ENDPOINT: Inspect raw data for a specific counter
     */
    @GetMapping("/{tenantCode}/{cafeteriaCode}/debug/counter/{counterName}")
    public ResponseEntity<Map<String, Object>> debugCounterData(
            @PathVariable String tenantCode,
            @PathVariable String cafeteriaCode,
            @PathVariable String counterName,
            @RequestParam(defaultValue = "10") Integer limit) {

        log.info("🔍 Debugging counter data for: {}", counterName);

        try {
            CafeteriaLocation location = locationRepository.findByTenantCodeAndCafeteriaCode(tenantCode, cafeteriaCode)
                    .orElseThrow(() -> new RuntimeException("Cafeteria not found"));

            FoodCounter counter = counterRepository.findByCounterNameAndCafeteriaLocation(counterName, location)
                    .orElseThrow(() -> new RuntimeException("Counter not found: " + counterName));

            LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.of(7, 0));
            LocalDateTime now = LocalDateTime.now();

            List<CafeteriaAnalytics> analytics = analyticsRepository
                    .findByFoodCounterIdAndTimestampBetween(counter.getId(), startOfDay, now);

            // Get sample records
            List<Map<String, ? extends Serializable>> sampleRecords = analytics.stream()
                    .limit(limit)
                    .map(a -> Map.of(
                            "id", a.getId(),
                            "timestamp", a.getTimestamp().toString(),
                            "inCount", a.getInCount() != null ? a.getInCount() : "NULL",
                            "avgDwellTime", a.getAvgDwellTime() != null ? a.getAvgDwellTime() : "NULL",
                            "estimatedWaitTime", a.getEstimatedWaitTime() != null ? a.getEstimatedWaitTime() : "NULL",
                            "manualWaitTime", a.getManualWaitTime() != null ? a.getManualWaitTime() : "NULL",
                            "queueLength", a.getQueueLength() != null ? a.getQueueLength() : "NULL",
                            "currentOccupancy", a.getCurrentOccupancy() != null ? a.getCurrentOccupancy() : "NULL"
                    ))
                    .collect(Collectors.toList());

            // Data quality stats
            long totalRecords = analytics.size();
            long withAvgDwell = analytics.stream().filter(a -> a.getAvgDwellTime() != null && a.getAvgDwellTime() > 0).count();
            long withEstWait = analytics.stream().filter(a -> a.getEstimatedWaitTime() != null && a.getEstimatedWaitTime() > 0).count();
            long withManualWait = analytics.stream().filter(a -> a.getManualWaitTime() != null && a.getManualWaitTime() > 0).count();
            long withInCount = analytics.stream().filter(a -> a.getInCount() != null && a.getInCount() > 0).count();

            String primaryField = "None";
            if (withInCount > 0) {
                primaryField = "inCount (✅ AVAILABLE)";
            } else if (withManualWait > withAvgDwell && withManualWait > withEstWait) {
                primaryField = "manualWaitTime";
            } else if (withAvgDwell > 0) {
                primaryField = "avgDwellTime";
            } else if (withEstWait > 0) {
                primaryField = "estimatedWaitTime";
            }

            Map<String, Object> dataQuality = Map.of(
                    "totalRecords", totalRecords,
                    "recordsWithAvgDwellTime", withAvgDwell,
                    "recordsWithEstimatedWaitTime", withEstWait,
                    "recordsWithManualWaitTime", withManualWait,
                    "recordsWithInCount", withInCount,
                    "primaryField", primaryField
            );

            String recommendation;
            if (withInCount > 0) {
                recommendation = "✅ Using inCount for queue trends - PERFECT!";
            } else if (withAvgDwell == 0 && withEstWait == 0 && withManualWait == 0) {
                recommendation = "⚠️ NO TIME DATA - Check MQTT publisher";
            } else if (withManualWait > 0) {
                recommendation = "✅ Using manualWaitTime for dwell time";
            } else if (withAvgDwell > 0) {
                recommendation = "✅ Using avgDwellTime for dwell time";
            } else {
                recommendation = "✅ Using estimatedWaitTime for dwell time";
            }

            return ResponseEntity.ok(Map.of(
                    "counterName", counterName,
                    "counterId", counter.getId(),
                    "timeRange", Map.of("start", startOfDay.toString(), "end", now.toString()),
                    "dataQuality", dataQuality,
                    "sampleRecords", sampleRecords,
                    "recommendation", recommendation,
                    "note", "Queue trends now use in_count field"
            ));

        } catch (Exception e) {
            log.error("Error debugging counter data: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", e.getMessage(),
                    "counterName", counterName
            ));
        }
    }

    /**
     * 🔍 DEBUG: Check congestion trend raw data
     */
    @GetMapping("/{tenantCode}/{cafeteriaCode}/debug/congestion")
    public ResponseEntity<Map<String, Object>> debugCongestionTrend(
            @PathVariable String tenantCode,
            @PathVariable String cafeteriaCode,
            @RequestParam(defaultValue = "daily") String timeFilter) {

        try {
            CafeteriaLocation location = locationRepository.findByTenantCodeAndCafeteriaCode(tenantCode, cafeteriaCode)
                    .orElseThrow(() -> new RuntimeException("Cafeteria not found"));

            LocalDateTime startTime = LocalDateTime.of(LocalDate.now(), LocalTime.of(7, 0));
            LocalDateTime endTime = LocalDateTime.now();

            List<CafeteriaAnalytics> analytics = analyticsRepository.findByCafeteriaLocationAndTimeRange(
                    location.getId(), startTime, endTime);

            long totalRecords = analytics.size();
            long withCounters = analytics.stream().filter(a -> a.getFoodCounter() != null).count();
            long cafeteriaLevel = analytics.stream().filter(a -> a.getFoodCounter() == null).count();
            long withOccupancy = analytics.stream()
                    .filter(a -> a.getCurrentOccupancy() != null && a.getCurrentOccupancy() > 0)
                    .count();

            List<String> counterNames = analytics.stream()
                    .filter(a -> a.getFoodCounter() != null)
                    .map(a -> a.getFoodCounter().getCounterName())
                    .distinct()
                    .collect(Collectors.toList());

            Map<String, Long> timeDistribution = analytics.stream()
                    .collect(Collectors.groupingBy(
                            a -> a.getTimestamp().getHour() + ":00",
                            Collectors.counting()
                    ));

            String diagnosis;
            if (totalRecords == 0) {
                diagnosis = "❌ NO DATA - Check MQTT connection";
            } else if (withOccupancy == 0) {
                diagnosis = "❌ NO OCCUPANCY DATA";
            } else if (withCounters == 0) {
                diagnosis = "⚠️ CAFETERIA-LEVEL ONLY";
            } else {
                diagnosis = "✅ DATA AVAILABLE - " + withCounters + " counter records";
            }

            return ResponseEntity.ok(Map.of(
                    "totalRecords", totalRecords,
                    "counterLevelRecords", withCounters,
                    "cafeteriaLevelRecords", cafeteriaLevel,
                    "recordsWithOccupancy", withOccupancy,
                    "uniqueCounters", counterNames,
                    "counterCount", counterNames.size(),
                    "timeDistribution", timeDistribution,
                    "diagnosis", diagnosis
            ));

        } catch (Exception e) {
            log.error("Debug congestion error: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/debug/raw-data")
    public ResponseEntity<?> getDebugRawData() {
        try {
            long totalCount = analyticsRepository.count();
            return ResponseEntity.ok(Map.of(
                    "totalRecords", totalCount,
                    "status", totalCount > 0 ? "HAS_DATA" : "NO_DATA",
                    "message", totalCount > 0 ? "Database has " + totalCount + " records" : "Database is empty"
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * POST test data (for testing MQTT integration)
     */
    @PostMapping("/test/{deviceId}")
    public ResponseEntity<Map<String, Object>> publishTestData(@PathVariable String deviceId) {
        try {
            mqttService.publishTestMessage(deviceId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Test message published for device: " + deviceId
            ));
        } catch (Exception e) {
            log.error("Error publishing test message", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * GET MQTT connection status
     */
    @GetMapping("/mqtt/status")
    public ResponseEntity<Map<String, Object>> getMqttStatus() {
        boolean connected = mqttService.isConnected();
        return ResponseEntity.ok(Map.of(
                "connected", connected,
                "status", connected ? "CONNECTED" : "DISCONNECTED"
        ));
    }

    /**
     * GET health check
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "Cafeteria Dashboard API",
                "timestamp", LocalDateTime.now().toString(),
                "note", "Queue charts now use in_count (inflow) field"
        ));
    }
}