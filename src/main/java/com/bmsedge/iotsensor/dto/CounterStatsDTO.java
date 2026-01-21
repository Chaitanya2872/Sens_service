
package com.bmsedge.iotsensor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// In CounterStatsDTO.java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CounterStatsDTO {
    private Integer totalVisitors;      // ✅ Now represents sum of inCount
    private Double avgWaitTime;
    private Integer minWaitTime;
    private Integer maxWaitTime;
    private String mostCommonWaitTime;  // ✅ Now represents peak queue length
    private Integer peakQueueLength;    // ✅ NEW: Optional - for clarity
}
