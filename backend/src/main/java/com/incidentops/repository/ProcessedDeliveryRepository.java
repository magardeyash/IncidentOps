package com.incidentops.repository;

import com.incidentops.entity.ProcessedDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedDeliveryRepository extends JpaRepository<ProcessedDelivery, String> {
}
