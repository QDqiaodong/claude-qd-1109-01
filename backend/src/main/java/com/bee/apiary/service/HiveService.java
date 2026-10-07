package com.bee.apiary.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bee.apiary.common.BizException;
import com.bee.apiary.common.Dict;
import com.bee.apiary.entity.Hive;
import com.bee.apiary.repository.HiveRepository;

@Service
public class HiveService {

    @Autowired
    private HiveRepository repo;

    public List<Hive> list(String status) {
        if (status != null && !status.isEmpty()) {
            if (!Dict.isValidHiveStatus(status)) {
                throw new BizException("蜂箱状态非法：" + status);
            }
            return repo.findByStatus(status);
        }
        return repo.findAll();
    }

    public Hive get(Long id) {
        return repo.findById(id).orElseThrow(() -> new BizException("蜂箱不存在：" + id));
    }

    public Hive create(Hive input) {
        if (input.code == null || input.code.isBlank()) {
            throw new BizException("箱号不能为空");
        }
        if (repo.findByCode(input.code).isPresent()) {
            throw new BizException("箱号已存在：" + input.code);
        }
        if (input.status != null && !Dict.isValidHiveStatus(input.status)) {
            throw new BizException("蜂箱状态非法：" + input.status);
        }
        Hive saved = new Hive();
        saved.code = input.code;
        saved.location = input.location;
        saved.status = (input.status == null) ? "待检" : input.status;
        return repo.save(saved);
    }

    public Hive update(Long id, Hive input) {
        Hive existing = get(id);
        if (input.code != null) {
            if (input.code.isBlank()) {
                throw new BizException("箱号不能为空");
            }
            Hive other = repo.findByCode(input.code).orElse(null);
            if (other != null && !other.id.equals(id)) {
                throw new BizException("箱号已存在：" + input.code);
            }
            existing.code = input.code;
        }
        if (input.location != null) {
            existing.location = input.location;
        }
        if (input.status != null) {
            if (!Dict.isValidHiveStatus(input.status)) {
                throw new BizException("蜂箱状态非法：" + input.status);
            }
            existing.status = input.status;
        }
        return repo.save(existing);
    }

    public void delete(Long id) {
        if (!repo.existsById(id)) {
            throw new BizException("蜂箱不存在：" + id);
        }
        repo.deleteById(id);
    }
}
