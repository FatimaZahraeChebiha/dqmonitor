package com.dataquality.backend.repository;

import com.dataquality.backend.model.AnomalyCorrection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnomalyCorrectionRepository extends JpaRepository<AnomalyCorrection, Long> {
    List<AnomalyCorrection> findByDatasetIdOrderByCorrectedAtDesc(Long datasetId);
}
