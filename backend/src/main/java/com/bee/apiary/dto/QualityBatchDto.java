package com.bee.apiary.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.bee.apiary.entity.QualityHandling;
import com.bee.apiary.entity.QualityInspection;

public class QualityBatchDto {

    public Long id;
    public Long hiveId;
    public String batchNo;
    public BigDecimal weightKg;
    public String status;
    public String qualityStatus;
    public Long version;
    public LocalDateTime createdAt;

    public QualityInspection activeInspection;
    public List<QualityInspection> inspections;
    public List<QualityHandling> handlings;
    public Integer inspectionCount;
    public Boolean recheckSubmitted;

    public ActionState inspectAction;
    public ActionState rectifyAction;
    public ActionState recheckAction;
    public ActionState stockInAction;
}
