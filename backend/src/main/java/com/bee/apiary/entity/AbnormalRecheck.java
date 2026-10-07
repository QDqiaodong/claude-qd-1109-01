package com.bee.apiary.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 异常处置复检：一条处置完成后最多一次（handling_id 唯一）。
 * 合格解除提醒；不合格保留原异常，由处置表重新生成待处置项并顺延风险期限。
 */
@Entity
@Table(name = "abnormal_recheck")
public class AbnormalRecheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "handling_id", nullable = false)
    public Long handlingId;

    @Column(name = "inspection_id", nullable = false)
    public Long inspectionId;

    @Column(name = "hive_id", nullable = false)
    public Long hiveId;

    /** 合格 / 不合格 */
    @Column(name = "result", nullable = false, length = 16)
    public String result;

    @Column(name = "inspector", nullable = false, length = 32)
    public String inspector;

    @Column(name = "recheck_date", nullable = false)
    public LocalDate recheckDate;

    @Column(name = "note", length = 500)
    public String note;

    @Column(name = "created_at")
    public LocalDateTime createdAt = LocalDateTime.now();
}
