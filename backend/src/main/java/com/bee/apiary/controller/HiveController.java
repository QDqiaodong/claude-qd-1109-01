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
import com.bee.apiary.entity.Hive;
import com.bee.apiary.service.HiveService;

@RestController
@RequestMapping("/api/hives")
public class HiveController {

    @Autowired
    private HiveService service;

    @GetMapping
    public ApiResponse<List<Hive>> list(@RequestParam(required = false) String status) {
        return ApiResponse.success(service.list(status));
    }

    @PostMapping
    public ApiResponse<Hive> create(@RequestBody Hive input) {
        return ApiResponse.success(service.create(input));
    }

    @PutMapping("/{id}")
    public ApiResponse<Hive> update(@PathVariable Long id, @RequestBody Hive input) {
        return ApiResponse.success(service.update(id, input));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }
}
