package com.bee.apiary.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bee.apiary.entity.AbnormalHandling;

public interface AbnormalHandlingRepository extends JpaRepository<AbnormalHandling, Long> {

    /** 巡检当前生效处置（同时至多一条，唯一索引兜底） */
    Optional<AbnormalHandling> findByInspectionIdAndActiveTrue(Long inspectionId);

    List<AbnormalHandling> findByInspectionIdOrderByRoundNoDescIdDesc(Long inspectionId);

    List<AbnormalHandling> findByActiveTrue();

    List<AbnormalHandling> findByHiveIdAndActiveTrue(Long hiveId);
}
