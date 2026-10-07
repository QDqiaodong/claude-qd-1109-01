package com.bee.apiary.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "quality_handling")
public class QualityHandling {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "batch_id", nullable = false)
    public Long batchId;

    @Column(name = "inspection_id")
    public Long inspectionId;

    @Column(name = "handling_type", nullable = false, length = 32)
    public String handlingType;

    @Column(name = "opinion", nullable = false, length = 500)
    public String opinion;

    @Column(name = "handler", nullable = false, length = 32)
    public String handler;

    @Column(name = "handled_at", nullable = false)
    public LocalDateTime handledAt;

    @Column(name = "created_at")
    public LocalDateTime createdAt = LocalDateTime.now();
}
