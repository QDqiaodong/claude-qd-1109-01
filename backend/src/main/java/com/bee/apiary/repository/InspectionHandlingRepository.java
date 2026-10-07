package com.bee.apiary.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bee.apiary.entity.InspectionHandling;

public interface InspectionHandlingRepository extends JpaRepository<InspectionHandling, Long> {

    List<InspectionHandling> findByHiveIdOrderByCreatedAtDescIdDesc(Long hiveId);

    List<InspectionHandling> findByRootInspectionIdOrderByRoundNoAscIdAsc(Long rootInspectionId);

    Optional<InspectionHandling> findFirstByHiveIdAndStatusInOrderByCreatedAtDescIdDesc(
            Long hiveId, List<String> statuses);

    boolean existsByHiveIdAndStatusIn(Long hiveId, List<String> statuses);

    boolean existsByRootInspectionId(Long rootInspectionId);

    boolean existsBySourceInspectionId(Long sourceInspectionId);
}
