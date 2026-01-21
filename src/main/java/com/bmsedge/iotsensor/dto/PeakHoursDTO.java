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
public class PeakHoursDTO {
    private String currentStatus;
    private String nextPeak;
    private List<PeakSlot> peakSlots;
    private String highestPeak;
    private Integer averagePeakOccupancy;
}
