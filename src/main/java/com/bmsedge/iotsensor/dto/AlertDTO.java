package com.bmsedge.iotsensor.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertDTO {
    private Long id;
    private String title;
    private String message;
    private String severity;
    private String type;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime acknowledgedAt;
    private LocalDateTime resolvedAt;

    // Location info
    private Long locationId;
    private String locationName;
    private Integer floor;
    private String zone;

    // Sensor info
    private Long sensorDataId;
    private String deviceId;
    private Double value;
    private String unit;



}