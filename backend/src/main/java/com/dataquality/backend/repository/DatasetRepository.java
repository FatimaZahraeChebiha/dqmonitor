package com.dataquality.backend.repository;

import com.dataquality.backend.model.Dataset;
import com.dataquality.backend.model.DatasetStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DatasetRepository extends JpaRepository<Dataset, Long> {
    Page<Dataset> findAllByOrderByUploadedAtDesc(Pageable pageable);

    List<Dataset> findByStatusOrderByUploadedAtAsc(DatasetStatus status);
}
