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
public class CafeteriaQueueDTO {

    private Long id;
    private String counterName;
    private Integer queueCount;
    private String waitTimeText;
    private Double waitTimeMinutes;
    private String serviceStatus;
    private String status;

    // Location info
    private Long locationId;
    private String locationName;
    private Integer floor;
    private String zone;

    private LocalDateTime timestamp;
    private LocalDateTime createdAt;

    // === Fields from Node.js server (for incoming data) ===
    @JsonProperty("TwoGoodQ")
    private Integer twoGoodQ;

    @JsonProperty("UttarDakshinQ")
    private Integer uttarDakshinQ;

    @JsonProperty("TandoorQ")
    private Integer tandoorQ;

    @JsonProperty("TwoGoodT")
    private String twoGoodT;

    @JsonProperty("UttarDakshinT")
    private String uttarDakshinT;

    @JsonProperty("TandoorT")
    private String tandoorT;
}