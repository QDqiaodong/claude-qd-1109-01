package com.bee.apiary.dto;

public class InspectionRecheckRequest {

    /** 复检后的蜂群健康：合格要求健康/一般；不合格通常为病态 */
    public String beeHealth;
    public String note;
    public String inspector;
}
