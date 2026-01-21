package com.bmsedge.iotsensor.model;

import lombok.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "food_counter")
public class FoodCounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cafeteria_location_id", nullable = false)
    private CafeteriaLocation cafeteriaLocation;

    @Column(nullable = false, length = 100)
    private String counterName;          // e.g., "Bisi Oota/ Mini meals Counter"

    @Column(unique = true, nullable = false, length = 100)
    private String counterCode;          // e.g., "bisi-oota"

    @Column(unique = true, nullable = false, length = 100)
    private String deviceId;             // MQTT device identifier

    @Column(length = 50)
    private String counterType;          // e.g., "MAIN_COUNTER", "SNACK_COUNTER"

    private String description;

    @OneToMany(mappedBy = "foodCounter", cascade = CascadeType.ALL)
    private List<CafeteriaAnalytics> analytics;

    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (active == null) {
            active = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}