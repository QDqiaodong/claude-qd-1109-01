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

    @Column(name = "code", nullable = false, length = 32, unique = true)
    public String code;

    @Column(name = "location", length = 64)
    public String location;

    @Column(name = "status", length = 16)
    public String status;

    @Column(name = "created_at")
    public LocalDateTime createdAt = LocalDateTime.now();
}
