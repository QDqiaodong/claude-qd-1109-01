package com.bee.apiary.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bee.apiary.entity.Hive;

public interface HiveRepository extends JpaRepository<Hive, Long> {

    /** 箱号唯一仅在未删除蜂箱间生效（active_code 唯一索引） */
    Optional<Hive> findByCodeAndDeletedFalse(String code);

    List<Hive> findByStatusAndDeletedFalse(String status);

    List<Hive> findByDeletedFalse();
}
