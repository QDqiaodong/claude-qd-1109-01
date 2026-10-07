package com.bee.apiary.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.bee.apiary.entity.AbnormalHandling;
import com.bee.apiary.entity.AbnormalRecheck;

/**
 * 巡检看板条目：巡检主体 + 当前生效处置链派生信息 + 全部处置/复检历史。
 * 月历与列表共用同一数据源，处置任务只存于后端、视图切换不重复生成。
 */
public class InspectionDto {

    public Long id;
    public Long hiveId;
    public String hiveCode;
    public Boolean hiveDeleted;
    public LocalDate inspectDate;
    public BigDecimal honeyKg;
    public String beeHealth;
    public String note;
    /** 已记录 / 待处置 / 待复检 / 已解除 */
    public String status;
    public LocalDateTime createdAt;

    /** 当前生效处置（待处置 / 待复检），无则为 null */
    public AbnormalHandling activeHandling;
    /** 是否逾期：生效处置为「待处置」且截止日期早于今天 */
    public Boolean overdue;
    /** 截止日期（取自当前生效处置，筛选与提醒用） */
    public LocalDate dueDate;
    /** 连续异常：处置已进入第 2 轮及以上（曾复检不合格顺延） */
    public Boolean continuousAbnormal;
    /** 处置轮次（无处置链为 0） */
    public Integer handlingRound;

    public List<AbnormalHandling> handlings;
    public List<AbnormalRecheck> rechecks;
}
