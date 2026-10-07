package com.bee.apiary.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "quality_inspection")
public class QualityInspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "batch_id", nullable = false)
    public Long batchId;

    @Column(name = "sample_no", nullable = false, length = 64, unique = true)
    public String sampleNo;

    @Column(name = "round_type", nullable = false, length = 16)
    public String roundType;

    @Column(name = "moisture_percent", nullable = false, precision = 6, scale = 2)
    public BigDecimal moisturePercent;

    @Column(name = "sensory_result", nullable = false, length = 16)
    public String sensoryResult;

    @Column(name = "result", nullable = false, length = 16)
    public String result;

    @Column(name = "inspector", nullable = false, length = 32)
    public String inspector;

    @Column(name = "inspected_at", nullable = false)
    public LocalDateTime inspectedAt;

    @Column(name = "note", length = 500)
    public String note;

    @Column(name = "active", nullable = false)
    public Boolean active = Boolean.TRUE;

    @Column(name = "created_at")
    public LocalDateTime createdAt = LocalDateTime.now();
}
