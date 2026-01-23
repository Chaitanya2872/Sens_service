package com.bmsedge.iotsensor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for Average Queue Comparison Chart
 * Shows average queue length for each counter
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CounterQueueComparisonDTO {

    private String counterName;
    private Double averageQueueLength;
    private Integer maxQueueLength;
    private Integer minQueueLength;
    private Integer dataPoints;           // Number of records used for calculation
    private String status;                // LIGHT, MODERATE, HEAVY

    /**
     * Response wrapper for queue comparison endpoint
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private List<CounterQueueComparisonDTO> counters;
        private String timeRange;
        private LocalDateTime reportGeneratedAt;
        private Integer totalCounters;
        private Summary summary;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Summary {
            private String busiestCounter;
            private String leastBusyCounter;
            private Double overallAverage;
        }
    }
}