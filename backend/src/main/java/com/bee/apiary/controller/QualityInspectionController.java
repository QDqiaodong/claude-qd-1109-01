package com.bee.apiary.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bee.apiary.common.ApiResponse;
import com.bee.apiary.dto.QualityBatchDto;
import com.bee.apiary.dto.QualityInspectionRequest;
import com.bee.apiary.dto.QualityRectificationRequest;
import com.bee.apiary.service.QualityInspectionService;

@RestController
@RequestMapping("/api/quality-inspections")
public class QualityInspectionController {

    @Autowired
    private QualityInspectionService service;

    @GetMapping
    public ApiResponse<List<QualityBatchDto>> board(@RequestParam(required = false) String batchStatus,
                                                    @RequestParam(required = false) String conclusion) {
        return ApiResponse.success(service.board(batchStatus, conclusion));
    }

    @GetMapping("/batches/{batchId}")
    public ApiResponse<QualityBatchDto> detail(@PathVariable Long batchId) {
        return ApiResponse.success(service.detail(batchId));
    }

    @PostMapping("/batches/{batchId}/initial")
    public ApiResponse<QualityBatchDto> submitInitial(@PathVariable Long batchId,
                                                      @RequestBody QualityInspectionRequest input) {
        return ApiResponse.success(service.submitInitial(batchId, input));
    }

    @PostMapping("/batches/{batchId}/recheck")
    public ApiResponse<QualityBatchDto> submitRecheck(@PathVariable Long batchId,
                                                      @RequestBody QualityInspectionRequest input) {
        return ApiResponse.success(service.submitRecheck(batchId, input));
    }

    @PostMapping("/batches/{batchId}/rectification")
    public ApiResponse<QualityBatchDto> addRectification(@PathVariable Long batchId,
                                                         @RequestBody QualityRectificationRequest input) {
        return ApiResponse.success(service.addRectification(batchId, input));
    }
}
