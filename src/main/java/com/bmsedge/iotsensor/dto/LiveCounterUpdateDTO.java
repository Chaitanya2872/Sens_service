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
public class LiveCounterUpdateDTO {
    private String cafeteriaCode;
    private List<CounterStatusDTO> counters;
    private OccupancyStatusDTO occupancyStatus;
    private LocalDateTime timestamp;
    private String updateType; // "counter_update", "occupancy_update", "full_update"
}