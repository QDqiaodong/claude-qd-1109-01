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
    /** 已记录 → 已复检，逐级推进 */
    public static final List<String> INSPECTION_FLOW = List.of("已记录", "已复检");

    public static final List<String> INSPECTION_STATUS = List.of("已记录", "已复检");

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

    /** 巡检状态只能逐级推进：已记录 → 已复检 */
    public static boolean canTransferInspection(String from, String to) {
        int i = INSPECTION_FLOW.indexOf(from);
        return i >= 0 && i + 1 < INSPECTION_FLOW.size() && INSPECTION_FLOW.get(i + 1).equals(to);
    }

    public static boolean isValidInspectionStatus(String status) {
        return status != null && INSPECTION_STATUS.contains(status);
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
