package com.bee.apiary.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import jakarta.persistence.QueryHint;

import com.bee.apiary.entity.HoneyBatch;

public interface HoneyBatchRepository extends JpaRepository<HoneyBatch, Long> {

    Optional<HoneyBatch> findByBatchNo(String batchNo);

    /** 状态推进时加行锁；同一批次的重复提交和并发提交在数据库层串行化。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from HoneyBatch b where b.id = :id")
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    Optional<HoneyBatch> findByIdForUpdate(Long id);
}
