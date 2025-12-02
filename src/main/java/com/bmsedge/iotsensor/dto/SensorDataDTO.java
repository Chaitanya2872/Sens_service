package com.bmsedge.iotsensor.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SensorDataDTO {
    private Long id;
    private String deviceId;
    private String type;
    private Double value;
    private String unit;
    private String status;
    private String quality;
    private Double threshold;
    private LocalDateTime timestamp;

    // Location info
    private Long locationId;
    private String locationName;
    private Integer floor;
    private String zone;
    private String building;
}