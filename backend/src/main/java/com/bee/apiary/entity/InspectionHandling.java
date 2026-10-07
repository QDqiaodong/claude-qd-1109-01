package com.bee.apiary.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "inspection_handling")
public class InspectionHandling {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "hive_id", nullable = false)
    public Long hiveId;

    /** 首条异常巡检，复检不合格重新生成处置项时保持不变 */
    @Column(name = "root_inspection_id", nullable = false)
    public Long rootInspectionId;

    /** 本轮处置对应的巡检：初轮为病态/连续异常巡检，顺延轮次为复检记录 */
    @Column(name = "source_inspection_id", nullable = false)
    public Long sourceInspectionId;

    /** 上一轮待复检处置；首轮为空，用于保留完整责任链 */
    @Column(name = "previous_handling_id")
    public Long previousHandlingId;

    @Column(name = "round_no", nullable = false)
    public Integer roundNo = 1;

    @Column(name = "action_type", length = 32)
    public String actionType;

    @Column(name = "site_note", length = 500)
    public String siteNote;

    @Column(name = "owner_name", length = 32)
    public String ownerName;

    @Column(name = "deadline")
    public LocalDate deadline;

    @Column(name = "completed_at")
    public LocalDateTime completedAt;

    /** 待处置 / 待复检 / 已解除 / 复检不合格 */
    @Column(name = "status", nullable = false, length = 16)
    public String status;

    @Column(name = "recheck_result", length = 16)
    public String recheckResult;

    @Column(name = "recheck_note", length = 500)
    public String recheckNote;

    @Column(name = "recheck_inspector", length = 32)
    public String recheckInspector;

    @Column(name = "recheck_at")
    public LocalDateTime recheckAt;

    @Column(name = "created_at")
    public LocalDateTime createdAt = LocalDateTime.now();
}
