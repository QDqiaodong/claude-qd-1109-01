package com.bee.apiary.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "inspection")
public class Inspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "hive_id", nullable = false)
    public Long hiveId;

    @Column(name = "inspect_date", nullable = false)
    public LocalDate inspectDate;

    @Column(name = "honey_kg", nullable = false, precision = 8, scale = 2)
    public BigDecimal honeyKg;

    @Column(name = "bee_health", length = 16)
    public String beeHealth;

    @Column(name = "note", length = 255)
    public String note;

    @Column(name = "status", length = 16)
    public String status;

    @Column(name = "created_at")
    public LocalDateTime createdAt = LocalDateTime.now();
}
