package com.bmsedge.iotsensor.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationDTO {
    private Long id;
    private String name;
    private String type;
    private Integer floor;
    private String zone;
    private String building;
    private String description;
    private Double latitude;
    private Double longitude;
    private Boolean active;
}