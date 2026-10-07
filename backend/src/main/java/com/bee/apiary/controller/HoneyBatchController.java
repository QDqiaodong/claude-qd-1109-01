package com.bee.apiary.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bee.apiary.common.ApiResponse;
import com.bee.apiary.entity.HoneyBatch;
import com.bee.apiary.service.HoneyBatchService;

@RestController
@RequestMapping("/api/honey-batches")
public class HoneyBatchController {

    @Autowired
    private HoneyBatchService service;

    @GetMapping
    public ApiResponse<List<HoneyBatch>> list(@RequestParam(required = false) String status) {
        return ApiResponse.success(service.list(status));
    }

    @PostMapping
    public ApiResponse<HoneyBatch> create(@RequestBody HoneyBatch input) {
        return ApiResponse.success(service.create(input));
    }

    @PutMapping("/{id}")
    public ApiResponse<HoneyBatch> update(@PathVariable Long id, @RequestBody HoneyBatch input) {
        return ApiResponse.success(service.update(id, input));
    }

    /** 状态机：待质检 → 已入库 */
    @PostMapping("/{id}/stock-in")
    public ApiResponse<HoneyBatch> stockIn(@PathVariable Long id) {
        return ApiResponse.success(service.stockIn(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }
}
