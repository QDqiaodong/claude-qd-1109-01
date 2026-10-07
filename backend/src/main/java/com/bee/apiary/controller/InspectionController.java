package com.bee.apiary.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bee.apiary.common.ApiResponse;
import com.bee.apiary.entity.Inspection;
import com.bee.apiary.service.InspectionService;

@RestController
@RequestMapping("/api/inspections")
public class InspectionController {

    @Autowired
    private InspectionService service;

    @GetMapping
    public ApiResponse<List<Inspection>> list(
            @RequestParam(required = false) Long hiveId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String month) {
        return ApiResponse.success(service.list(hiveId, status, month));
    }

    @PostMapping
    public ApiResponse<Inspection> create(@RequestBody Inspection input) {
        return ApiResponse.success(service.create(input));
    }

    @PutMapping("/{id}")
    public ApiResponse<Inspection> update(@PathVariable Long id, @RequestBody Inspection input) {
        return ApiResponse.success(service.update(id, input));
    }

    /** 状态机：已记录 → 已复检 */
    @PostMapping("/{id}/recheck")
    public ApiResponse<Inspection> recheck(@PathVariable Long id) {
        return ApiResponse.success(service.recheck(id));
    }
}
