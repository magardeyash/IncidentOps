package com.incidentops.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "processed_deliveries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedDelivery {

    @Id
    @Column(name = "delivery_id", nullable = false)
    private String deliveryId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
