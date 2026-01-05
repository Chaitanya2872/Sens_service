package com.bmsedge.iotsensor.model;

import lombok.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "cafeteria_queue")
public class CafeteriaQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String counterName;          // TwoGood, UttarDakshin, Tandoor

    @Column(nullable = false)
    private Integer queueCount;          // Number of people in queue

    @Column(length = 100)
    private String waitTimeText;         // "Ready to Serve", "5-10 mins", etc.

    private Double waitTimeMinutes;      // Calculated wait time in minutes

    @Column(length = 50)
    private String serviceStatus;        // READY_TO_SERVE, SHORT_WAIT, MEDIUM_WAIT, LONG_WAIT

    @Column(length = 50)
    private String status;               // NORMAL, WARNING, CRITICAL

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();

        if (status == null) {
            status = "NORMAL";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}