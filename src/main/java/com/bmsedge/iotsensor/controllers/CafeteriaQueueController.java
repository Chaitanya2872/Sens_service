package com.bmsedge.iotsensor.controllers;

import com.bmsedge.iotsensor.dto.CafeteriaQueueDTO;
import com.bmsedge.iotsensor.service.CafeteriaQueueService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cafeteria")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class CafeteriaQueueController {

    private final CafeteriaQueueService cafeteriaQueueService;

    @Value("${cafeteria.nodejs.url:http://172.105.62.238:3000}")
    private String nodejsUrl;

    /**
     * GET endpoint to fetch data from Node.js and save to database
     * Endpoint: GET /api/cafeteria/node/cafe
     */
    @GetMapping("/node/cafe")
    public ResponseEntity<Map<String, Object>> getCafeteriaQueueData() {
        // Create RestTemplate instance locally (no dependency injection needed)
        RestTemplate restTemplate = new RestTemplate();
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            log.info("Fetching cafeteria data from Node.js: {}", nodejsUrl);

            // Fetch data from Node.js
            String endpoint = nodejsUrl + "/current-queue";
            String response = restTemplate.getForObject(endpoint, String.class);

            if (response == null || response.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "message", "Empty response from Node.js server",
                        "nodejsUrl", endpoint
                ));
            }

            log.info("Received data from Node.js: {}", response);

            // Parse JSON response
            @SuppressWarnings("unchecked")
            Map<String, Object> queueData = objectMapper.readValue(response, Map.class);

            // Create DTO
            CafeteriaQueueDTO dto = CafeteriaQueueDTO.builder()
                    .twoGoodQ((Integer) queueData.get("TwoGoodQ"))
                    .uttarDakshinQ((Integer) queueData.get("UttarDakshinQ"))
                    .tandoorQ((Integer) queueData.get("TandoorQ"))
                    .twoGoodT((String) queueData.get("TwoGoodT"))
                    .uttarDakshinT((String) queueData.get("UttarDakshinT"))
                    .tandoorT((String) queueData.get("TandoorT"))
                    .build();

            // Save to database
            List<CafeteriaQueueDTO> saved = cafeteriaQueueService.saveCafeteriaQueueData(dto);

            log.info("✅ Successfully saved {} records to database", saved.size());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Data fetched from Node.js and saved successfully",
                    "nodejsUrl", endpoint,
                    "recordsSaved", saved.size(),
                    "data", saved,
                    "rawData", queueData
            ));

        } catch (Exception e) {
            log.error("❌ Error fetching/saving cafeteria data: {}", e.getMessage(), e);

            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Error: " + e.getMessage(),
                    "nodejsUrl", nodejsUrl + "/current-queue",
                    "error", e.getClass().getSimpleName()
            ));
        }
    }

    /**
     * POST endpoint to receive queue data directly
     * Endpoint: POST /api/cafeteria/queue
     */
    @PostMapping("/queue")
    public ResponseEntity<Map<String, Object>> saveQueueData(@RequestBody CafeteriaQueueDTO dto) {
        try {
            List<CafeteriaQueueDTO> saved = cafeteriaQueueService.saveCafeteriaQueueData(dto);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Cafeteria queue data saved successfully",
                    "recordsSaved", saved.size(),
                    "timestamp", java.time.LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("Error saving queue data: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Error saving cafeteria queue data: " + e.getMessage()
            ));
        }
    }

    /**
     * GET latest queue status for all counters
     * Endpoint: GET /api/cafeteria/queue/latest
     */
    @GetMapping("/queue/latest")
    public ResponseEntity<Map<String, Object>> getLatestQueueStatus() {
        Map<String, Object> status = cafeteriaQueueService.getLatestQueueStatus();
        return ResponseEntity.ok(status);
    }

    /**
     * GET status for specific counter
     * Endpoint: GET /api/cafeteria/counter/{counterName}
     */
    @GetMapping("/counter/{counterName}")
    public ResponseEntity<Map<String, Object>> getCounterStatus(@PathVariable String counterName) {
        Map<String, Object> status = cafeteriaQueueService.getCounterStatus(counterName);
        return ResponseEntity.ok(status);
    }


    /**
     * GET all counters status summary
     * Endpoint: GET /api/cafeteria/status
     */
    @GetMapping("/status")
    public ResponseEntity<List<Map<String, Object>>> getAllCountersStatus() {
        List<Map<String, Object>> status = cafeteriaQueueService.getAllCountersStatus();
        return ResponseEntity.ok(status);
    }

    /**
     * GET historical queue data
     * Endpoint: GET /api/cafeteria/queue/history?hours=24
     */
    @GetMapping("/queue/history")
    public ResponseEntity<Map<String, Object>> getHistoricalData(
            @RequestParam(defaultValue = "24") Integer hours) {
        Map<String, Object> history = cafeteriaQueueService.getHistoricalQueueData(hours);
        return ResponseEntity.ok(history);
    }

    /**
     * GET recent data for specific counter
     * Endpoint: GET /api/cafeteria/counter/{counterName}/recent?hours=24
     */
    @GetMapping("/counter/{counterName}/recent")
    public ResponseEntity<List<CafeteriaQueueDTO>> getRecentByCounter(
            @PathVariable String counterName,
            @RequestParam(defaultValue = "24") Integer hours) {
        List<CafeteriaQueueDTO> data = cafeteriaQueueService.getRecentByCounter(counterName, hours);
        return ResponseEntity.ok(data);
    }

    /**
     * GET statistics for specific counter
     * Endpoint: GET /api/cafeteria/counter/{counterName}/stats?hours=24
     */
    @GetMapping("/counter/{counterName}/stats")
    public ResponseEntity<Map<String, Object>> getCounterStatistics(
            @PathVariable String counterName,
            @RequestParam(defaultValue = "24") Integer hours) {
        Map<String, Object> stats = cafeteriaQueueService.getCounterStatistics(counterName, hours);
        return ResponseEntity.ok(stats);
    }

    /**
     * GET active alerts (WARNING or CRITICAL status)
     * Endpoint: GET /api/cafeteria/alerts
     */
    @GetMapping("/alerts")
    public ResponseEntity<List<CafeteriaQueueDTO>> getActiveAlerts() {
        List<CafeteriaQueueDTO> alerts = cafeteriaQueueService.getActiveAlerts();
        return ResponseEntity.ok(alerts);
    }


    @GetMapping("/analytics/realtime")
    public ResponseEntity<?> realTime(@RequestParam(defaultValue = "6") int hours) {
        return ResponseEntity.ok(cafeteriaQueueService.getRealTimeAnalytics(hours));
    }

    @GetMapping("/analytics/weekly")
    public ResponseEntity<?> weekly() {
        return ResponseEntity.ok(cafeteriaQueueService.getWeeklyTraffic());
    }

    @GetMapping("/analytics/distribution")
    public ResponseEntity<?> distribution() {
        return ResponseEntity.ok(cafeteriaQueueService.getCurrentDistribution());
    }

    @GetMapping("/analytics/peak-hours")
    public ResponseEntity<?> peakHours(
            @RequestParam(defaultValue = "6") int hours) {
        return ResponseEntity.ok(
                cafeteriaQueueService.getPeakHourPerformance(hours)
        );
    }

    // ==================== NEW DATE-WISE AND HOURLY APIs ====================

    /**
     * GET data for a specific date (all records for that day)
     * Endpoint: GET /api/cafeteria/data/by-date?date=2024-12-24
     *
     * Example: /api/cafeteria/data/by-date?date=2024-12-24
     * Returns all queue records for December 24, 2024
     */
    @GetMapping("/data/by-date")
    public ResponseEntity<Map<String, Object>> getDataByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Map<String, Object> data = cafeteriaQueueService.getDataByDate(date);
        return ResponseEntity.ok(data);
    }

    /**
     * GET data for a specific date and counter
     * Endpoint: GET /api/cafeteria/data/by-date/{counterName}?date=2024-12-24
     *
     * Example: /api/cafeteria/data/by-date/TwoGood?date=2024-12-24
     * Returns all TwoGood counter records for December 24, 2024
     */
    @GetMapping("/data/by-date/{counterName}")
    public ResponseEntity<Map<String, Object>> getDataByDateAndCounter(
            @PathVariable String counterName,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Map<String, Object> data = cafeteriaQueueService.getDataByDateAndCounter(date, counterName);
        return ResponseEntity.ok(data);
    }

    /**
     * GET all records within date-time range (optionally filtered by counter)
     * Endpoint: GET /api/cafeteria/data/all-records?from=2024-12-24T00:00:00&to=2024-12-24T23:59:59&counterName=TwoGood
     *
     * counterName is optional - if not provided, returns data for all counters
     * Default time range is last 1 hour if parameters not provided
     */
    @GetMapping("/data/all-records")
    public ResponseEntity<Map<String, Object>> getAllRecords(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String counterName) {

        // Set default time range to last 1 hour if not provided
        if (from == null) {
            from = LocalDateTime.now().minusHours(1);
        }
        if (to == null) {
            to = LocalDateTime.now();
        }

        Map<String, Object> data = cafeteriaQueueService.getAllRecords(from, to, counterName);
        return ResponseEntity.ok(data);
    }


    @GetMapping("/data/hourly")
    public ResponseEntity<Map<String, Object>> getHourlyData(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Map<String, Object> data = cafeteriaQueueService.getHourlyAggregatedData(date);
        return ResponseEntity.ok(data);
    }


    @GetMapping("/data/hourly/{counterName}")
    public ResponseEntity<Map<String, Object>> getHourlyDataByCounter(
            @PathVariable String counterName,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Map<String, Object> data = cafeteriaQueueService.getHourlyAggregatedDataByCounter(date, counterName);
        return ResponseEntity.ok(data);
    }


    @GetMapping("/data/date-range")
    public ResponseEntity<Map<String, Object>> getDataByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Map<String, Object> data = cafeteriaQueueService.getDataByDateRange(startDate, endDate);
        return ResponseEntity.ok(data);
    }

    /**
     * GET daily summary statistics for a date range
     * Endpoint: GET /api/cafeteria/data/daily-summary?startDate=2024-12-20&endDate=2024-12-24
     *
     * Example: /api/cafeteria/data/daily-summary?startDate=2024-12-20&endDate=2024-12-24
     * Returns daily aggregated statistics for each day in the range
     */
    @GetMapping("/data/daily-summary")
    public ResponseEntity<List<Map<String, Object>>> getDailySummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<Map<String, Object>> data = cafeteriaQueueService.getDailySummary(startDate, endDate);
        return ResponseEntity.ok(data);
    }

    /**
     * GET data by timestamp (specific time)
     * Endpoint: GET /api/cafeteria/data/by-timestamp?timestamp=2024-12-24T14:30:00
     *
     * Example: /api/cafeteria/data/by-timestamp?timestamp=2024-12-24T14:30:00
     * Returns records around the specified timestamp (within 5 minutes)
     */
    @GetMapping("/data/by-timestamp")
    public ResponseEntity<Map<String, Object>> getDataByTimestamp(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime timestamp,
            @RequestParam(defaultValue = "5") Integer minutesRange) {
        Map<String, Object> data = cafeteriaQueueService.getDataByTimestamp(timestamp, minutesRange);
        return ResponseEntity.ok(data);
    }

    /**
     * GET data for specific hour of a day
     * Endpoint: GET /api/cafeteria/data/by-hour?date=2024-12-24&hour=14
     *
     * Example: /api/cafeteria/data/by-hour?date=2024-12-24&hour=14
     * Returns all records between 2:00 PM - 2:59 PM on December 24, 2024
     */
    @GetMapping("/data/by-hour")
    public ResponseEntity<Map<String, Object>> getDataByHour(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam Integer hour) {
        Map<String, Object> data = cafeteriaQueueService.getDataByHour(date, hour);
        return ResponseEntity.ok(data);
    }

    // ==================== END NEW APIs ====================


    /**
     * Test connection to Node.js
     * Endpoint: GET /api/cafeteria/test-connection
     */
    @GetMapping("/test-connection")
    public ResponseEntity<Map<String, Object>> testNodeJsConnection() {
        RestTemplate restTemplate = new RestTemplate();

        try {
            String endpoint = nodejsUrl + "/health";
            String response = restTemplate.getForObject(endpoint, String.class);

            return ResponseEntity.ok(Map.of(
                    "status", "connected",
                    "nodejsUrl", nodejsUrl,
                    "response", response
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "status", "disconnected",
                    "nodejsUrl", nodejsUrl,
                    "error", e.getMessage()
            ));
        }
    }
}