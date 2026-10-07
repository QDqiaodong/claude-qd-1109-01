package com.bee.apiary.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bee.apiary.entity.Inspection;

public interface InspectionRepository extends JpaRepository<Inspection, Long> {

    Optional<Inspection> findByHiveIdAndInspectDate(Long hiveId, LocalDate inspectDate);

    List<Inspection> findByHiveId(Long hiveId);

    List<Inspection> findByStatus(String status);

    List<Inspection> findByInspectDateBetween(LocalDate start, LocalDate end);
}
