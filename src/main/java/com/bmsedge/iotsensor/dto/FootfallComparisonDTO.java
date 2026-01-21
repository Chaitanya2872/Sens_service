package com.bmsedge.iotsensor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FootfallComparisonDTO {
    private String timestamp;
    private Integer cafeteriaFootfall;
    private Integer countersFootfall;
    private Double ratio;
    private String insight;
}