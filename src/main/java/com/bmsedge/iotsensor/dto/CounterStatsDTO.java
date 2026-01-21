
package com.bmsedge.iotsensor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CounterStatsDTO {
    private Integer totalVisitors;
    private Double avgWaitTime;
    private Integer minWaitTime;
    private Integer maxWaitTime;
    private String mostCommonWaitTime;
}
