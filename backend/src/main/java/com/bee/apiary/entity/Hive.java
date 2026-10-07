package com.bee.apiary.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "hive")
public class Hive {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "code", nullable = false, length = 32)
    public String code;

    @Column(name = "location", length = 64)
    public String location;

    @Column(name = "status", length = 16)
    public String status;

    /** 软删除标记：删除后保留关联巡检与处置历史，列表不再展示 */
    @Column(name = "deleted", nullable = false)
    public Boolean deleted = false;

    @Column(name = "created_at")
    public LocalDateTime createdAt = LocalDateTime.now();
}
