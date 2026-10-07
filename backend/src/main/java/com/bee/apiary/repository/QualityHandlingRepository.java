package com.bee.apiary.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bee.apiary.entity.QualityHandling;

public interface QualityHandlingRepository extends JpaRepository<QualityHandling, Long> {

    List<QualityHandling> findByBatchIdOrderByHandledAtDescIdDesc(Long batchId);

    List<QualityHandling> findAllByOrderByHandledAtDescIdDesc();

    boolean existsByBatchId(Long batchId);
}
