package com.bee.apiary.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bee.apiary.entity.QualityInspection;

public interface QualityInspectionRepository extends JpaRepository<QualityInspection, Long> {

    Optional<QualityInspection> findBySampleNo(String sampleNo);

    Optional<QualityInspection> findByBatchIdAndActiveTrue(Long batchId);

    List<QualityInspection> findByBatchIdOrderByInspectedAtDescIdDesc(Long batchId);

    List<QualityInspection> findAllByOrderByInspectedAtDescIdDesc();

    boolean existsByBatchId(Long batchId);

    boolean existsByBatchIdAndRoundType(Long batchId, String roundType);
}
