package com.bee.apiary.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "customer")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "name", nullable = false, length = 64)
    public String name;

    @Column(name = "phone", length = 24, unique = true)
    public String phone;

    @Column(name = "type", length = 16)
    public String type;

    @Column(name = "created_at")
    public LocalDateTime createdAt = LocalDateTime.now();
}
