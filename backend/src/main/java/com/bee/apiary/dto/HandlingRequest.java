package com.bee.apiary.dto;

import java.time.LocalDate;

/**
 * 异常处置登记：为待处置项补录处置措施、负责人、截止日期、现场说明。
 * 完成处置（待处置 → 待复检）时措施、负责人、截止日期三项必填。
 */
public class HandlingRequest {

    public String measure;
    public String handler;
    public LocalDate dueDate;
    public String siteNote;
    /** true=保存登记但仍待处置；false/缺省=信息齐全后完成处置进入待复检 */
    public Boolean keepPending;
}
