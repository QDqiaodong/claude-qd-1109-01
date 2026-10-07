package com.bee.apiary.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class HandlingBoardItem {

    public Long id;
    public Long hiveId;
    public String hiveCode;
    public String hiveLocation;
    public Long rootInspectionId;
    public Long sourceInspectionId;
    public LocalDate sourceInspectDate;
    public String sourceBeeHealth;
    public Long previousHandlingId;
    public Integer roundNo;
    public String actionType;
    public String siteNote;
    public String ownerName;
    public LocalDate deadline;
    public LocalDateTime completedAt;
    public String status;
    public String recheckResult;
    public String recheckNote;
    public String recheckInspector;
    public LocalDateTime recheckAt;
    public LocalDateTime createdAt;

    public boolean overdue;
    public String riskState;
}
