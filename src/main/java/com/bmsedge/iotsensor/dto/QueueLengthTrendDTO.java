package com.bmsedge.iotsensor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO for Queue Length Trends Chart
 * Shows time-series data of queue lengths for all counters
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueLengthTrendDTO {

    private String timestamp;              // HH:mm format
    private Map<String, Double> counterQueues;  // counterName -> average queue length

    /**
     * Single counter trend point
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CounterTrendPoint {
        private String counterName;
        private Double queueLength;
        private String status;             // LIGHT, MODERATE, HEAVY
    }

    /**
     * Response wrapper for queue trends endpoint
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private List<QueueLengthTrendDTO> trends;
        private String timeRange;
        private String interval;           // e.g., "5-minute"
        private LocalDateTime reportGeneratedAt;
        private List<String> counters;     // List of counter names
        private Summary summary;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Summary {
            private String peakTime;
            private String peakCounter;
            private Double peakQueueLength;
            private Integer totalDataPoints;
        }
    }
}