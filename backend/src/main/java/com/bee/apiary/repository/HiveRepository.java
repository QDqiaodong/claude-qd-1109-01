package com.bee.apiary.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bee.apiary.entity.Hive;

public interface HiveRepository extends JpaRepository<Hive, Long> {

    Optional<Hive> findByCode(String code);

    List<Hive> findByStatus(String status);
}
