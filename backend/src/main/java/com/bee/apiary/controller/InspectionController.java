package com.bee.apiary.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bee.apiary.common.ApiResponse;
import com.bee.apiary.dto.HandlingRequest;
import com.bee.apiary.dto.InspectionDto;
import com.bee.apiary.dto.RecheckRequest;
import com.bee.apiary.entity.Inspection;
import com.bee.apiary.service.InspectionService;

@RestController
@RequestMapping("/api/inspections")
public class InspectionController {

    @Autowired
    private InspectionService service;

    /**
     * 看板查询：蜂箱 / 巡检状态 / 月份 / 蜂群健康 / 处置状态（逾期·待处置·待复检·已解除·复检不合格）
     * / 截止日期区间。月历与列表共用同一数据源。
     */
    @GetMapping
    public ApiResponse<List<InspectionDto>> list(
            @RequestParam(required = false) Long hiveId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String beeHealth,
            @RequestParam(required = false) String handlingStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo) {
        return ApiResponse.success(service.list(hiveId, status, month, beeHealth, handlingStatus, dueFrom, dueTo));
    }

    @GetMapping("/{id}")
    public ApiResponse<InspectionDto> detail(@PathVariable Long id) {
        return ApiResponse.success(service.detail(id));
    }

    @PostMapping
    public ApiResponse<InspectionDto> create(@RequestBody Inspection input) {
        return ApiResponse.success(service.create(input));
    }

    @PutMapping("/{id}")
    public ApiResponse<InspectionDto> update(@PathVariable Long id, @RequestBody Inspection input) {
        return ApiResponse.success(service.update(id, input));
    }

    /** 登记/完成异常处置：处置措施、负责人、截止日期齐全后才能完成进入待复检 */
    @PostMapping("/handlings/{handlingId}/register")
    public ApiResponse<InspectionDto> registerHandling(@PathVariable Long handlingId,
                                                       @RequestBody HandlingRequest input) {
        return ApiResponse.success(service.registerHandling(handlingId, input));
    }

    /** 处置复检：每条处置仅一次；合格解除，不合格保留原异常、重新待处置并顺延期限 */
    @PostMapping("/handlings/{handlingId}/recheck")
    public ApiResponse<InspectionDto> recheckHandling(@PathVariable Long handlingId,
                                                      @RequestBody RecheckRequest input) {
        return ApiResponse.success(service.recheck(handlingId, input));
    }
}
