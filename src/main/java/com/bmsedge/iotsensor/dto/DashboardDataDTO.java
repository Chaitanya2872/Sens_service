package com.bmsedge.iotsensor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDataDTO {
    private OccupancyStatusDTO occupancyStatus;
    private List<FlowDataDTO> flowData;
    private List<CounterStatusDTO> counterStatus;
    private List<DwellTimeDataDTO> dwellTimeData;
    private List<FootfallComparisonDTO> footfallComparison;
    private List<OccupancyTrendDTO> occupancyTrend;
    private List<CounterCongestionTrendDTO> counterCongestionTrend;
    private List<CounterEfficiencyDTO> counterEfficiency;
    private TodaysVisitorsDTO todaysVisitors;
    private AvgDwellTimeDTO avgDwellTime;
    private PeakHoursDTO peakHours;
    private LocalDateTime lastUpdated;
}
