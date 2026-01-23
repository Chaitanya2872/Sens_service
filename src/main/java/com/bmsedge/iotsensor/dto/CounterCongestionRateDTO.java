package com.bmsedge.iotsensor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for Congestion Rate Comparison Chart
 * Shows percentage of time each counter spent in different congestion levels
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CounterCongestionRateDTO {

    private String counterName;
    private Double congestionRate;        // Percentage (0-100)
    private Integer totalRecords;
    private Integer highCongestionRecords;
    private Integer mediumCongestionRecords;
    private Integer lowCongestionRecords;
    private Double highPercentage;
    private Double mediumPercentage;
    private Double lowPercentage;

    /**
     * Response wrapper for congestion rate comparison endpoint
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private List<CounterCongestionRateDTO> counters;
        private String timeRange;
        private LocalDateTime reportGeneratedAt;
        private Integer totalCounters;
        private Summary summary;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Summary {
            private String mostCongestedCounter;
            private String leastCongestedCounter;
            private Double overallCongestionRate;
            private String recommendation;
        }
    }
}