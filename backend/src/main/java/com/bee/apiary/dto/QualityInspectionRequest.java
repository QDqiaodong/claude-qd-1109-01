package com.bee.apiary.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class QualityInspectionRequest {

    public String sampleNo;
    public BigDecimal moisturePercent;
    public String sensoryResult;
    public String inspector;
    public LocalDateTime inspectedAt;
    public String note;
}
