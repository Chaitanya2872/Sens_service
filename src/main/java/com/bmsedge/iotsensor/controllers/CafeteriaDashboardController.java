package com.bmsedge.iotsensor.controllers;

import com.bmsedge.iotsensor.dto.DashboardDataDTO;
import com.bmsedge.iotsensor.dto.EnhancedCounterCongestionDTO;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.bmsedge.iotsensor.dto.CounterDwellTimeResponseDTO;

import java.io.Serializable;
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
     *
     * Endpoint: GET /api/cafeteria/dashboard/{tenantCode}/{cafeteriaCode}
     *
     * Query Parameters:
     * - timeFilter: daily | weekly | monthly (default: daily)
     * - timeRange: number of hours for daily view (default: 24)
     *
     * Example: GET /api/cafeteria/dashboard/intel-rmz-ecoworld/srr-4a?timeFilter=daily&timeRange=24
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
     * ✅ Get dwell time data for a specific counter
     *
     * Endpoint: GET /api/cafeteria/dashboard/{tenantCode}/{cafeteriaCode}/dwell-time/{counterName}
     *
     * Example: GET /api/cafeteria/dashboard/intel-rmz-ecoworld/srr-4a/dwell-time/Mini%20Meals?timeFilter=daily
     */
    @GetMapping("/{tenantCode}/{cafeteriaCode}/dwell-time/{counterName}")
    public ResponseEntity<CounterDwellTimeResponseDTO> getDwellTimeByCounter(
            @PathVariable String tenantCode,
            @PathVariable String cafeteriaCode,
            @PathVariable String counterName,
            @RequestParam(defaultValue = "daily") String timeFilter,
            @RequestParam(required = false) Integer timeRange
    ) {
        log.info("Fetching dwell time for tenant: {}, cafeteria: {}, counter: {}, filter: {}",
                tenantCode, cafeteriaCode, counterName, timeFilter);

        try {
            CounterDwellTimeResponseDTO response = dashboardService.getDwellTimeByCounter(
                    tenantCode, cafeteriaCode, counterName, timeFilter, timeRange
            );
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
     *
     * Endpoint: GET /api/cafeteria/dashboard/{tenantCode}/{cafeteriaCode}/counters/list
     *
     * Example: GET /api/cafeteria/dashboard/intel-rmz-ecoworld/srr-4a/counters/list
     */
    @GetMapping("/{tenantCode}/{cafeteriaCode}/counters/list")
    public ResponseEntity<Map<String, Object>> getAvailableCounters(
            @PathVariable String tenantCode,
            @PathVariable String cafeteriaCode
    ) {
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
     * 🔍 DEBUG ENDPOINT: Inspect raw data for a specific counter
     *
     * Endpoint: GET /api/cafeteria/dashboard/{tenantCode}/{cafeteriaCode}/debug/counter/{counterName}
     *
     * Example: GET /api/cafeteria/dashboard/intel-rmz-ecoworld/srr-4a/debug/counter/Mini%20Meals?limit=10
     */
    @GetMapping("/{tenantCode}/{cafeteriaCode}/debug/counter/{counterName}")
    public ResponseEntity<Map<String, Object>> debugCounterData(
            @PathVariable String tenantCode,
            @PathVariable String cafeteriaCode,
            @PathVariable String counterName,
            @RequestParam(defaultValue = "10") Integer limit
    ) {
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

            // Determine primary field
            String primaryField = "None";
            if (withManualWait > withAvgDwell && withManualWait > withEstWait) {
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
                    "avgDwellTimePercentage", totalRecords > 0 ? String.format("%.1f%%", (withAvgDwell * 100.0 / totalRecords)) : "0%",
                    "estimatedWaitTimePercentage", totalRecords > 0 ? String.format("%.1f%%", (withEstWait * 100.0 / totalRecords)) : "0%",
                    "manualWaitTimePercentage", totalRecords > 0 ? String.format("%.1f%%", (withManualWait * 100.0 / totalRecords)) : "0%",
                    "primaryField", primaryField
            );

            String recommendation;
            if (withAvgDwell == 0 && withEstWait == 0 && withManualWait == 0) {
                recommendation = "⚠️ NO DWELL/WAIT TIME DATA - Check MQTT publisher for this counter";
            } else if (withManualWait > 0) {
                recommendation = "✅ Using manualWaitTime (common for Mini Meals, Two Good)";
            } else if (withAvgDwell > 0) {
                recommendation = "✅ Using avgDwellTime";
            } else {
                recommendation = "✅ Using estimatedWaitTime";
            }

            return ResponseEntity.ok(Map.of(
                    "counterName", counterName,
                    "counterId", counter.getId(),
                    "timeRange", Map.of("start", startOfDay.toString(), "end", now.toString()),
                    "dataQuality", dataQuality,
                    "sampleRecords", sampleRecords,
                    "recommendation", recommendation
            ));

        } catch (Exception e) {
            log.error("Error debugging counter data: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", e.getMessage(),
                    "counterName", counterName
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
                    "message", totalCount > 0 ? "Database has " + totalCount + " records" : "Database is empty - MQTT not saving data"
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "error", e.getMessage(),
                    "type", e.getClass().getSimpleName()
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
     *
     * Endpoint: GET /api/cafeteria/dashboard/{tenantCode}/{cafeteriaCode}/congestion/enhanced
     *
     * Returns max, avg, min occupancy per counter per time bucket
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

            // ✅ SIMPLE FIX: Get ALL records from last 24 hours
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

    // Add to CafeteriaDashboardController.java

    /**
     * 🔍 DEBUG: Check congestion trend raw data
     */
    @GetMapping("/{tenantCode}/{cafeteriaCode}/debug/congestion")
    public ResponseEntity<Map<String, Object>> debugCongestionTrend(
            @PathVariable String tenantCode,
            @PathVariable String cafeteriaCode,
            @RequestParam(defaultValue = "daily") String timeFilter
    ) {
        try {
            CafeteriaLocation location = locationRepository.findByTenantCodeAndCafeteriaCode(tenantCode, cafeteriaCode)
                    .orElseThrow(() -> new RuntimeException("Cafeteria not found"));

            LocalDateTime startTime = LocalDateTime.of(LocalDate.now(), LocalTime.of(7, 0));
            LocalDateTime endTime = LocalDateTime.now();

            // Get raw analytics
            List<CafeteriaAnalytics> analytics = analyticsRepository.findByCafeteriaLocationAndTimeRange(
                    location.getId(), startTime, endTime);

            // Analyze data structure
            long totalRecords = analytics.size();
            long withCounters = analytics.stream().filter(a -> a.getFoodCounter() != null).count();
            long cafeteriaLevel = analytics.stream().filter(a -> a.getFoodCounter() == null).count();
            long withOccupancy = analytics.stream()
                    .filter(a -> a.getCurrentOccupancy() != null && a.getCurrentOccupancy() > 0)
                    .count();

            // Get unique counter names
            List<String> counterNames = analytics.stream()
                    .filter(a -> a.getFoodCounter() != null)
                    .map(a -> a.getFoodCounter().getCounterName())
                    .distinct()
                    .collect(Collectors.toList());

            // Sample data
            List<Map<String, Object>> sampleRecords = analytics.stream()
                    .limit(5)
                    .map(a -> {
                        Map<String, Object> record = new HashMap<>();
                        record.put("id", a.getId());
                        record.put("timestamp", a.getTimestamp().toString());
                        record.put("counterName", a.getFoodCounter() != null ?
                                a.getFoodCounter().getCounterName() : "CAFETERIA_LEVEL");
                        record.put("currentOccupancy", a.getCurrentOccupancy());
                        record.put("queueLength", a.getQueueLength());
                        return record;
                    })
                    .collect(Collectors.toList());

            // Time distribution
            Map<String, Long> timeDistribution = analytics.stream()
                    .collect(Collectors.groupingBy(
                            a -> a.getTimestamp().getHour() + ":00",
                            Collectors.counting()
                    ));

            return ResponseEntity.ok(Map.of(
                    "totalRecords", totalRecords,
                    "counterLevelRecords", withCounters,
                    "cafeteriaLevelRecords", cafeteriaLevel,
                    "recordsWithOccupancy", withOccupancy,
                    "uniqueCounters", counterNames,
                    "counterCount", counterNames.size(),
                    "timeRange", Map.of("start", startTime.toString(), "end", endTime.toString()),
                    "sampleRecords", sampleRecords,
                    "timeDistribution", timeDistribution,
                    "diagnosis", diagnoseCongestionIssue(totalRecords, withCounters, withOccupancy)
            ));

        } catch (Exception e) {
            log.error("Debug congestion error: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", e.getMessage(),
                    "stackTrace", Arrays.toString(e.getStackTrace())
            ));
        }
    }

    private String diagnoseCongestionIssue(long total, long withCounters, long withOccupancy) {
        if (total == 0) {
            return "❌ NO DATA - No analytics records found. Check MQTT connection.";
        }
        if (withOccupancy == 0) {
            return "❌ NO OCCUPANCY DATA - Records exist but currentOccupancy is null/zero.";
        }
        if (withCounters == 0) {
            return "⚠️ CAFETERIA-LEVEL ONLY - No counter-specific data. Will show 'Cafeteria Overall'.";
        }
        return "✅ DATA AVAILABLE - " + withCounters + " counter records, " + withOccupancy + " with occupancy.";
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
                "timestamp", java.time.LocalDateTime.now().toString()
        ));
    }
}