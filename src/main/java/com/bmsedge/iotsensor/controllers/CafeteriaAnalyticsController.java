package com.bmsedge.iotsensor.controllers;

import com.bmsedge.iotsensor.service.CafeteriaAnalyticsService;
import com.bmsedge.iotsensor.service.CafeteriaQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cafeteria")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class CafeteriaAnalyticsController {

    private final CafeteriaAnalyticsService analyticsService;
    private final CafeteriaQueueService queueService;

    /**
     * GET smart insights for a specific date
     * Endpoint: GET /api/cafeteria/insights/smart?date=2024-12-30
     *
     * Returns:
     * - Peak hour analysis
     * - Footfall status (HIGH/NORMAL/LOW)
     * - Throughput metrics
     * - Occupancy analysis
     * - Per-counter breakdown
     */
    @GetMapping("/insights/smart")
    public ResponseEntity<Map<String, Object>> getSmartInsights(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        if (date == null) {
            date = LocalDate.now();
        }

        log.info("Fetching smart insights for date: {}", date);
        Map<String, Object> insights = analyticsService.getSmartInsights(date);
        return ResponseEntity.ok(insights);
    }

    /**
     * GET wait time trend analysis
     * Endpoint: GET /api/cafeteria/analytics/wait-time-trend?startDate=2024-12-23&endDate=2024-12-30
     *
     * Returns:
     * - Hourly wait time trends
     * - Daily wait time trends
     * - Meal session breakdown
     */
    @GetMapping("/analytics/wait-time-trend")
    public ResponseEntity<Map<String, Object>> getWaitTimeTrend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        if (endDate == null) {
            endDate = LocalDate.now();
        }
        if (startDate == null) {
            startDate = endDate.minusDays(7);
        }

        log.info("Fetching wait time trend from {} to {}", startDate, endDate);
        Map<String, Object> trend = analyticsService.getWaitTimeTrend(startDate, endDate);
        return ResponseEntity.ok(trend);
    }

    /**
     * GET wait time by meal session for a specific date
     * Endpoint: GET /api/cafeteria/analytics/wait-time-by-session?date=2024-12-30
     *
     * Returns wait time stats grouped by breakfast, lunch, dinner
     */
    @GetMapping("/analytics/wait-time-by-session")
    public ResponseEntity<Map<String, Object>> getWaitTimeBySession(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        if (date == null) {
            date = LocalDate.now();
        }

        log.info("Fetching wait time by session for date: {}", date);
        Map<String, Object> sessionData = analyticsService.getWaitTimeBySession(date);
        return ResponseEntity.ok(sessionData);
    }

    /**
     * GET traffic pattern analysis
     * Endpoint: GET /api/cafeteria/analytics/traffic-pattern?days=30
     *
     * Returns:
     * - Weekday vs Weekend comparison
     * - Daily peak detection
     * - Overall traffic stats
     */
    @GetMapping("/analytics/traffic-pattern")
    public ResponseEntity<Map<String, Object>> getTrafficPattern(
            @RequestParam(defaultValue = "30") int days) {

        log.info("Fetching traffic pattern for last {} days", days);
        Map<String, Object> pattern = analyticsService.getTrafficPattern(days);
        return ResponseEntity.ok(pattern);
    }

    /**
     * GET weekly summary for all counters
     * Endpoint: GET /api/cafeteria/analytics/weekly-summary?weeks=4
     *
     * Returns weekly aggregated statistics for each counter
     */
    @GetMapping("/analytics/weekly-summary")
    public ResponseEntity<Map<String, Object>> getWeeklySummary(
            @RequestParam(defaultValue = "4") int weeks) {

        log.info("Fetching weekly summary for last {} weeks", weeks);
        Map<String, Object> summary = analyticsService.getWeeklySummary(weeks);
        return ResponseEntity.ok(summary);
    }

    /**
     * GET peak day analysis for last N days
     * Endpoint: GET /api/cafeteria/analytics/peak-days?days=7
     *
     * Returns the busiest days with their statistics
     */
    @GetMapping("/analytics/peak-days")
    public ResponseEntity<Map<String, Object>> getPeakDays(
            @RequestParam(defaultValue = "7") int days) {

        log.info("Fetching peak days for last {} days", days);
        Map<String, Object> pattern = analyticsService.getTrafficPattern(days);

        // Extract just the peak days information
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> peakDays = (List<Map<String, Object>>) pattern.get("peakDays");

        Map<String, Object> result = Map.of(
                "days", days,
                "peakDays", peakDays != null ? peakDays : List.of()
        );

        return ResponseEntity.ok(result);
    }

    /**
     * GET comprehensive dashboard data
     * Endpoint: GET /api/cafeteria/analytics/dashboard?date=2024-12-30
     *
     * Returns all analytics for dashboard in one call
     */
    @GetMapping("/analytics/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboardData(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        if (date == null) {
            date = LocalDate.now();
        }

        log.info("Fetching comprehensive dashboard data for date: {}", date);

        // Fetch all required data
        Map<String, Object> smartInsights = analyticsService.getSmartInsights(date);
        Map<String, Object> waitTimeBySession = analyticsService.getWaitTimeBySession(date);
        Map<String, Object> weeklyPattern = analyticsService.getTrafficPattern(7);
        Map<String, Object> latestStatus = queueService.getLatestQueueStatus();

        // Combine into single response
        Map<String, Object> dashboard = Map.of(
                "smartInsights", smartInsights,
                "waitTimeBySession", waitTimeBySession,
                "weeklyPattern", weeklyPattern,
                "latestStatus", latestStatus,
                "timestamp", java.time.LocalDateTime.now().toString()
        );

        return ResponseEntity.ok(dashboard);
    }

    /**
     * GET hourly breakdown for current day
     * Endpoint: GET /api/cafeteria/analytics/today-hourly
     *
     * Returns hourly statistics for today
     */
    @GetMapping("/analytics/today-hourly")
    public ResponseEntity<Map<String, Object>> getTodayHourly() {
        LocalDate today = LocalDate.now();

        log.info("Fetching hourly breakdown for today: {}", today);
        Map<String, Object> hourlyData = queueService.getHourlyAggregatedData(today);

        return ResponseEntity.ok(hourlyData);
    }

    /**
     * GET meal session performance comparison
     * Endpoint: GET /api/cafeteria/analytics/session-comparison?days=7
     *
     * Compares breakfast, lunch, dinner performance over period
     */
    @GetMapping("/analytics/session-comparison")
    public ResponseEntity<Map<String, Object>> getSessionComparison(
            @RequestParam(defaultValue = "7") int days) {

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days);

        log.info("Fetching session comparison from {} to {}", startDate, endDate);
        Map<String, Object> trend = analyticsService.getWaitTimeTrend(startDate, endDate);

        // Extract session analysis
        @SuppressWarnings("unchecked")
        Map<String, Object> sessions = (Map<String, Object>) trend.get("sessions");

        Map<String, Object> result = Map.of(
                "period", startDate + " to " + endDate,
                "days", days,
                "sessions", sessions != null ? sessions : Map.of()
        );

        return ResponseEntity.ok(result);
    }
}