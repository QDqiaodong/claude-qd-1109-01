package com.bee.apiary.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bee.apiary.entity.AbnormalRecheck;

public interface AbnormalRecheckRepository extends JpaRepository<AbnormalRecheck, Long> {

    /** 一条处置最多一次复检 */
    Optional<AbnormalRecheck> findByHandlingId(Long handlingId);

    List<AbnormalRecheck> findByInspectionIdOrderByRecheckDateDescIdDesc(Long inspectionId);
}
