package com.bmsedge.iotsensor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// ==================== REQUEST DTOs ====================

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class MqttPayloadDTO {
    private Integer occupancy;
    private Double avg_dwell;
    private Double max_dwell;
    private Integer incount;
    private Double estimate_wait_time;
    private Double waiting_time_min;
    private String deviceId;
    private LocalDateTime timestamp;
}