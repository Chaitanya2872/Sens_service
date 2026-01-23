package com.bmsedge.iotsensor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response wrapper for Counter Master Data API
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CounterMasterDataResponseDTO {

    // Metadata
    private String cafeteriaName;
    private String cafeteriaCode;
    private LocalDateTime reportGeneratedAt;
    private String timeRange;

    // Summary Statistics
    private SummaryStats summary;

    // Master Data List
    private List<CounterMasterDataDTO> data;

    // Pagination (if needed)
    private Integer totalRecords;
    private Integer recordsReturned;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryStats {
        private Integer totalCounters;
        private Integer activeCounters;
        private Integer averageOccupancy;
        private Double averageWaitTime;
        private String mostCongestedCounter;
        private String leastCongestedCounter;
    }
}