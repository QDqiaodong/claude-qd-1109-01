package com.bee.apiary.common;

import java.util.List;

/**
 * 养蜂场业务词典与规则参数表：蜂箱状态、蜂群健康、巡检流转、
 * 采蜜批次状态流转、客户类型。
 *
 * <p>状态集合与流程集中在此，Service 引用做校验与状态机推进。</p>
 */
public final class Dict {

    private Dict() {
    }

    // ---------------- 蜂箱 ----------------
    /** 正常 / 待检 / 患病，可自由切换 */
    public static final List<String> HIVE_STATUS = List.of("正常", "待检", "患病");

    // ---------------- 蜂群健康 ----------------
    /** 健康 / 一般 / 病态，仅作取值校验 */
    public static final List<String> BEE_HEALTH = List.of("健康", "一般", "病态");

    // ---------------- 巡检 ----------------
    /** 病态异常先进入待处置，复检解除后进入已解除 */
    public static final List<String> INSPECTION_STATUS = List.of(
            "已记录", "待处置", "待复检", "风险关联", "已解除");

    /** 处置流转：待处置 → 待复检 → 复检合格/复检不合格(终态) */
    public static final List<String> HANDLING_STATUS = List.of(
            "待处置", "待复检", "已解除", "复检不合格");

    /** 列表筛选中的业务状态 */
    public static final List<String> HANDLING_VIEW_STATUS = List.of(
            "待处置", "待复检", "已解除", "复检不合格", "逾期");

    /** 异常处置措施 */
    public static final List<String> INSPECTION_ACTION_TYPES = List.of(
            "隔离治疗", "换脾消毒", "补饲强群", "更换蜂王", "转场观察", "其他");

    /** 默认处置期限：发现异常后 7 天 */
    public static final java.time.Period DEFAULT_HANDLING_DEADLINE = java.time.Period.ofDays(7);

    /** 复检不合格后风险期限顺延天数 */
    public static final java.time.Period RECHECK_EXTEND_PERIOD = java.time.Period.ofDays(7);

    // ---------------- 采蜜批次 ----------------
    /** 待质检 → 质检放行/复检合格 → 已入库；不合格进入隔离，已作废为终态 */
    public static final List<String> HONEY_BATCH_STATUS = List.of(
            "待质检", "质检放行", "隔离待整改", "复检合格", "隔离拒收", "已入库", "已作废");

    /** 质检放行后允许入库的批次状态 */
    public static final List<String> HONEY_BATCH_RELEASED_STATUS = List.of("质检放行", "复检合格");

    /** 初检/复检 */
    public static final List<String> QUALITY_ROUND = List.of("初检", "复检");

    /** 感官结论与质检结论 */
    public static final List<String> QUALITY_RESULT = List.of("合格", "不合格");

    /** 质检处理意见类型 */
    public static final List<String> QUALITY_HANDLING_TYPE = List.of("隔离处理", "整改", "复检放行", "拒绝入库");

    /** 含水率放行上限（%） */
    public static final java.math.BigDecimal HONEY_MOISTURE_LIMIT = new java.math.BigDecimal("20.00");

    // ---------------- 客户 ----------------
    /** 商超 / 个人 */
    public static final List<String> CUSTOMER_TYPE = List.of("商超", "个人");

    // ---------------- 取值校验方法 ----------------

    public static boolean isValidHiveStatus(String status) {
        return status != null && HIVE_STATUS.contains(status);
    }

    public static boolean isValidBeeHealth(String health) {
        return health != null && BEE_HEALTH.contains(health);
    }

    public static boolean isValidInspectionStatus(String status) {
        return status != null && INSPECTION_STATUS.contains(status);
    }

    public static boolean isValidHandlingStatus(String status) {
        return status != null && HANDLING_STATUS.contains(status);
    }

    public static boolean isValidHandlingViewStatus(String status) {
        return status != null && HANDLING_VIEW_STATUS.contains(status);
    }

    public static boolean isValidInspectionActionType(String type) {
        return type != null && INSPECTION_ACTION_TYPES.contains(type);
    }

    public static boolean isValidHoneyBatchStatus(String status) {
        return status != null && HONEY_BATCH_STATUS.contains(status);
    }

    public static boolean isReleasedHoneyBatchStatus(String status) {
        return status != null && HONEY_BATCH_RELEASED_STATUS.contains(status);
    }

    public static boolean isValidQualityRound(String round) {
        return round != null && QUALITY_ROUND.contains(round);
    }

    public static boolean isValidQualityResult(String result) {
        return result != null && QUALITY_RESULT.contains(result);
    }

    public static boolean isValidQualityHandlingType(String type) {
        return type != null && QUALITY_HANDLING_TYPE.contains(type);
    }

    public static boolean isValidCustomerType(String type) {
        return type != null && CUSTOMER_TYPE.contains(type);
    }
}
