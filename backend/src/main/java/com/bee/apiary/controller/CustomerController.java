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
import com.bee.apiary.entity.Customer;
import com.bee.apiary.service.CustomerService;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    @Autowired
    private CustomerService service;

    @GetMapping
    public ApiResponse<List<Customer>> list(@RequestParam(required = false) String type) {
        return ApiResponse.success(service.list(type));
    }

    @PostMapping
    public ApiResponse<Customer> create(@RequestBody Customer input) {
        return ApiResponse.success(service.create(input));
    }

    @PutMapping("/{id}")
    public ApiResponse<Customer> update(@PathVariable Long id, @RequestBody Customer input) {
        return ApiResponse.success(service.update(id, input));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }
}
