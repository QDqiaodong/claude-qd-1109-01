package com.bee.apiary.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class InspectionBoardItem {

    public Long id;
    public Long hiveId;
    public String hiveCode;
    public String hiveLocation;
    public LocalDate inspectDate;
    public BigDecimal honeyKg;
    public String beeHealth;
    public String note;
    public String status;
    public Long rootInspectionId;
    public Long activeHandlingId;
    public LocalDateTime createdAt;

    public HandlingBoardItem activeHandling;
    public boolean openRisk;
    public boolean overdue;
    public boolean consecutiveAbnormal;
    public boolean hasFailedRecheck;
    public String riskState;
}
