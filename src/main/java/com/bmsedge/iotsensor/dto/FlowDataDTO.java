package com.bmsedge.iotsensor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;



@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowDataDTO {
    private String timestamp;
    private Integer inflow;
    private Integer outflow;
    private Integer netFlow;
}
