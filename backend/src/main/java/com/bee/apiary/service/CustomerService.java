package com.bee.apiary.service;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bee.apiary.common.BizException;
import com.bee.apiary.common.Dict;
import com.bee.apiary.entity.Customer;
import com.bee.apiary.repository.CustomerRepository;

@Service
public class CustomerService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    @Autowired
    private CustomerRepository repo;

    public List<Customer> list(String type) {
        if (type != null && !type.isEmpty()) {
            if (!Dict.isValidCustomerType(type)) {
                throw new BizException("客户类型非法：" + type);
            }
            return repo.findAll().stream().filter(c -> type.equals(c.type)).toList();
        }
        return repo.findAll();
    }

    public Customer get(Long id) {
        return repo.findById(id).orElseThrow(() -> new BizException("客户不存在：" + id));
    }

    public Customer create(Customer input) {
        if (input.name == null || input.name.isBlank()) {
            throw new BizException("客户名不能为空");
        }
        if (input.phone == null || !PHONE_PATTERN.matcher(input.phone).matches()) {
            throw new BizException("手机号格式不正确（应为 1[3-9] 开头的 11 位号码）");
        }
        if (repo.findByPhone(input.phone).isPresent()) {
            throw new BizException("手机号已存在：" + input.phone);
        }
        if (input.type != null && !Dict.isValidCustomerType(input.type)) {
            throw new BizException("客户类型非法：" + input.type);
        }
        Customer saved = new Customer();
        saved.name = input.name;
        saved.phone = input.phone;
        saved.type = (input.type == null) ? "个人" : input.type;
        return repo.save(saved);
    }

    public Customer update(Long id, Customer input) {
        Customer existing = get(id);
        if (input.name != null) {
            if (input.name.isBlank()) {
                throw new BizException("客户名不能为空");
            }
            existing.name = input.name;
        }
        if (input.phone != null && !input.phone.equals(existing.phone)) {
            if (!PHONE_PATTERN.matcher(input.phone).matches()) {
                throw new BizException("手机号格式不正确（应为 1[3-9] 开头的 11 位号码）");
            }
            Customer other = repo.findByPhone(input.phone).orElse(null);
            if (other != null && !other.id.equals(id)) {
                throw new BizException("手机号已存在：" + input.phone);
            }
            existing.phone = input.phone;
        }
        if (input.type != null) {
            if (!Dict.isValidCustomerType(input.type)) {
                throw new BizException("客户类型非法：" + input.type);
            }
            existing.type = input.type;
        }
        return repo.save(existing);
    }

    public void delete(Long id) {
        if (!repo.existsById(id)) {
            throw new BizException("客户不存在：" + id);
        }
        repo.deleteById(id);
    }
}
