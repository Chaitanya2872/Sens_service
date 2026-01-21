package com.bmsedge.iotsensor.controllers;

import com.bmsedge.iotsensor.service.EmailReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/cafeteria/reports")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class EmailReportController {

    private final EmailReportService emailReportService;

    /**
     * Send daily report manually
     * POST /api/cafeteria/reports/daily/{tenantCode}/{cafeteriaCode}
     */
    @PostMapping("/daily/{tenantCode}/{cafeteriaCode}")
    public ResponseEntity<?> sendDailyReport(
            @PathVariable String tenantCode,
            @PathVariable String cafeteriaCode) {

        try {
            log.info("📧 Manual daily report request for {}/{}", tenantCode, cafeteriaCode);

            emailReportService.sendDailyReportForLocation(tenantCode, cafeteriaCode);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Daily report sent successfully",
                    "cafeteria", cafeteriaCode
            ));
        } catch (Exception e) {
            log.error("❌ Error sending daily report: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Send weekly report manually
     * POST /api/cafeteria/reports/weekly/{tenantCode}/{cafeteriaCode}
     */
    @PostMapping("/weekly/{tenantCode}/{cafeteriaCode}")
    public ResponseEntity<?> sendWeeklyReport(
            @PathVariable String tenantCode,
            @PathVariable String cafeteriaCode) {

        try {
            log.info("📧 Manual weekly report request for {}/{}", tenantCode, cafeteriaCode);

            emailReportService.sendWeeklyReportForLocation(tenantCode, cafeteriaCode);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Weekly report sent successfully",
                    "cafeteria", cafeteriaCode
            ));
        } catch (Exception e) {
            log.error("❌ Error sending weekly report: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Send custom report
     * POST /api/cafeteria/reports/custom
     */
    @PostMapping("/custom")
    public ResponseEntity<?> sendCustomReport(@RequestBody CustomReportRequest request) {
        try {
            log.info("📧 Custom report request for {}/{}", request.getTenantCode(), request.getCafeteriaCode());

            String[] recipients = request.getRecipients() != null ?
                    request.getRecipients() : new String[]{};

            String subject = request.getSubject() != null ?
                    request.getSubject() :
                    "Cafeteria Analytics Report - " + request.getCafeteriaCode();

            emailReportService.sendCustomReport(
                    request.getTenantCode(),
                    request.getCafeteriaCode(),
                    request.getTimeFilter(),
                    request.getTimeRange(),
                    recipients,
                    subject
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Custom report sent successfully"
            ));
        } catch (Exception e) {
            log.error("❌ Error sending custom report: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Trigger all daily reports manually
     * POST /api/cafeteria/reports/trigger/daily
     */
    @PostMapping("/trigger/daily")
    public ResponseEntity<?> triggerAllDailyReports() {
        try {
            log.info("📧 Triggering all daily reports");

            emailReportService.sendDailyReport();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Daily reports triggered for all locations"
            ));
        } catch (Exception e) {
            log.error("❌ Error triggering daily reports: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Trigger all weekly reports manually
     * POST /api/cafeteria/reports/trigger/weekly
     */
    @PostMapping("/trigger/weekly")
    public ResponseEntity<?> triggerAllWeeklyReports() {
        try {
            log.info("📧 Triggering all weekly reports");

            emailReportService.sendWeeklyReport();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Weekly reports triggered for all locations"
            ));
        } catch (Exception e) {
            log.error("❌ Error triggering weekly reports: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * DTO for custom report request
     */
    public static class CustomReportRequest {
        private String tenantCode;
        private String cafeteriaCode;
        private String timeFilter = "daily";
        private int timeRange = 24;
        private String[] recipients;
        private String subject;

        // Getters and Setters
        public String getTenantCode() { return tenantCode; }
        public void setTenantCode(String tenantCode) { this.tenantCode = tenantCode; }

        public String getCafeteriaCode() { return cafeteriaCode; }
        public void setCafeteriaCode(String cafeteriaCode) { this.cafeteriaCode = cafeteriaCode; }

        public String getTimeFilter() { return timeFilter; }
        public void setTimeFilter(String timeFilter) { this.timeFilter = timeFilter; }

        public int getTimeRange() { return timeRange; }
        public void setTimeRange(int timeRange) { this.timeRange = timeRange; }

        public String[] getRecipients() { return recipients; }
        public void setRecipients(String[] recipients) { this.recipients = recipients; }

        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
    }
}