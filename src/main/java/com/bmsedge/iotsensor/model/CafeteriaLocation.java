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
@Table(name = "cafeteria_location")
public class CafeteriaLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false, length = 100)
    private String cafeteriaName;        // e.g., "SRR 4A"

    @Column(unique = true, nullable = false, length = 100)
    private String cafeteriaCode;        // e.g., "srr-4a"

    @Column(nullable = false)
    private Integer floor;               // Floor number

    @Column(length = 50)
    private String zone;                 // Zone identifier

    private String description;
    private Integer capacity;            // Maximum capacity

    @OneToMany(mappedBy = "cafeteriaLocation", cascade = CascadeType.ALL)
    private List<FoodCounter> foodCounters;

    @OneToMany(mappedBy = "cafeteriaLocation", cascade = CascadeType.ALL)
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