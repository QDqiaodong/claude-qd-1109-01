package com.bee.apiary.dto;

import java.time.LocalDate;

/**
 * 处置复检登记：一条处置完成后仅允许一次。
 * 合格解除提醒；不合格保留原异常、另起待处置并顺延风险期限。
 */
public class RecheckRequest {

    /** 合格 / 不合格 */
    public String result;
    public String inspector;
    public LocalDate recheckDate;
    public String note;
}
