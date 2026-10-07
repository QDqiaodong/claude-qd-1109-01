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
import com.bee.apiary.dto.HandlingBoardItem;
import com.bee.apiary.dto.InspectionBoardItem;
import com.bee.apiary.dto.InspectionHandlingRequest;
import com.bee.apiary.dto.InspectionRecheckRequest;
import com.bee.apiary.entity.Inspection;
import com.bee.apiary.service.InspectionService;

@RestController
@RequestMapping("/api/inspections")
public class InspectionController {

    @Autowired
    private InspectionService service;

    @GetMapping
    public ApiResponse<List<InspectionBoardItem>> list(
            @RequestParam(required = false) Long hiveId,
            @RequestParam(required = false) String beeHealth,
            @RequestParam(required = false) String handlingStatus,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDate) {
        return ApiResponse.success(service.list(hiveId, beeHealth, handlingStatus, month, dueDate));
    }

    @GetMapping("/handlings")
    public ApiResponse<List<HandlingBoardItem>> listHandlings(
            @RequestParam(required = false) Long hiveId,
            @RequestParam(required = false) String beeHealth,
            @RequestParam(required = false) String handlingStatus,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDate) {
        return ApiResponse.success(service.listHandlings(hiveId, beeHealth, handlingStatus, month, dueDate));
    }

    @GetMapping("/{id}")
    public ApiResponse<InspectionBoardItem> get(@PathVariable Long id) {
        return ApiResponse.success(service.get(id));
    }

    @PostMapping
    public ApiResponse<InspectionBoardItem> create(@RequestBody Inspection input) {
        return ApiResponse.success(service.create(input));
    }

    @PutMapping("/{id}")
    public ApiResponse<InspectionBoardItem> update(@PathVariable Long id, @RequestBody Inspection input) {
        return ApiResponse.success(service.update(id, input));
    }

    /** 待处置 → 待复检：必须登记措施、负责人、截止日期和现场说明。 */
    @PostMapping("/handlings/{handlingId}/complete")
    public ApiResponse<HandlingBoardItem> completeHandling(
            @PathVariable Long handlingId,
            @RequestBody InspectionHandlingRequest input) {
        return ApiResponse.success(service.completeHandling(handlingId, input));
    }

    /** 待复检 → 已解除；不合格则保留异常并重新生成待处置项、顺延期限。 */
    @PostMapping("/handlings/{handlingId}/recheck")
    public ApiResponse<HandlingBoardItem> submitRecheck(
            @PathVariable Long handlingId,
            @RequestBody InspectionRecheckRequest input) {
        return ApiResponse.success(service.submitRecheck(handlingId, input));
    }
}
