package com.bmsedge.iotsensor.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceLocationMappingDTO {
    private Long id;
    private String deviceId;
    private Long locationId;
    private String locationName;
    private Integer floor;
    private String zone;
    private String deviceType;
    private String description;
    private Boolean active;
}