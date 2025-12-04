package com.bmsedge.iotsensor.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class SensorDataDTO {
    // Original fields for standard sensors
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

    // === NEW: Odor sensor fields from ThingsBoard ===
    @JsonProperty("device")
    private String device;

    @JsonProperty("odorbattery")
    private Double odorbattery;

    @JsonProperty("odortemperature")
    private Double odortemperature;

    @JsonProperty("odorhumidity")
    private Double odorhumidity;

    @JsonProperty("odornh3")
    private Double odornh3;

    @JsonProperty("odorh2s_high_precision")
    private Double odorh2s_high_precision;
}