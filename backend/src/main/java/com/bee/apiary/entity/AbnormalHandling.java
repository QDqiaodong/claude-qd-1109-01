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
 * 异常处置项：异常（一般/病态）巡检生成的待办，记录处置措施、负责人、
 * 截止日期与现场说明。处置完成后进入待复检；复检不合格时本条置为
 * 「复检不合格」并保留历史，另起一轮新的待处置（active=0）。
 */
@Entity
@Table(name = "abnormal_handling")
public class AbnormalHandling {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "inspection_id", nullable = false)
    public Long inspectionId;

    @Column(name = "hive_id", nullable = false)
    public Long hiveId;

    @Column(name = "round_no", nullable = false)
    public Integer roundNo = 1;

    /** 上一轮处置 id：复检不合格顺延链路 */
    @Column(name = "source_handling_id")
    public Long sourceHandlingId;

    @Column(name = "measure", length = 255)
    public String measure;

    @Column(name = "handler", length = 32)
    public String handler;

    @Column(name = "due_date")
    public LocalDate dueDate;

    @Column(name = "site_note", length = 500)
    public String siteNote;

    /** 待处置 / 待复检 / 复检不合格 / 已解除 */
    @Column(name = "status", nullable = false, length = 16)
    public String status;

    @Column(name = "registered_at")
    public LocalDateTime registeredAt;

    @Column(name = "completed_at")
    public LocalDateTime completedAt;

    /** 是否当前生效处置：同一巡检同时仅一条，由唯一索引 uk_abnormal_active_inspection 保证 */
    @Column(name = "active", nullable = false)
    public Boolean active = true;

    @Column(name = "created_at")
    public LocalDateTime createdAt = LocalDateTime.now();
}
