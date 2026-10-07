package com.bee.apiary.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bee.apiary.common.BizException;
import com.bee.apiary.common.Dict;
import com.bee.apiary.entity.Hive;
import com.bee.apiary.repository.HiveRepository;

@Service
public class HiveService {

    @Autowired
    private HiveRepository repo;

    /** 台账列表不展示已删除蜂箱（其巡检与处置历史在巡检看板仍可追溯） */
    public List<Hive> list(String status) {
        if (status != null && !status.isEmpty()) {
            if (!Dict.isValidHiveStatus(status)) {
                throw new BizException("蜂箱状态非法：" + status);
            }
            return repo.findByStatusAndDeletedFalse(status);
        }
        return repo.findByDeletedFalse();
    }

    public Hive get(Long id) {
        return repo.findById(id).orElseThrow(() -> new BizException("蜂箱不存在：" + id));
    }

    public Hive create(Hive input) {
        if (input.code == null || input.code.isBlank()) {
            throw new BizException("箱号不能为空");
        }
        String code = input.code.trim();
        if (repo.findByCodeAndDeletedFalse(code).isPresent()) {
            throw new BizException("箱号已存在：" + code);
        }
        if (input.status != null && !Dict.isValidHiveStatus(input.status)) {
            throw new BizException("蜂箱状态非法：" + input.status);
        }
        Hive saved = new Hive();
        saved.code = code;
        saved.location = input.location;
        saved.status = (input.status == null) ? "待检" : input.status;
        saved.deleted = false;
        return repo.save(saved);
    }

    public Hive update(Long id, Hive input) {
        Hive existing = get(id);
        if (Boolean.TRUE.equals(existing.deleted)) {
            throw new BizException("蜂箱已删除，不能编辑；如需恢复请重新建档（历史巡检与处置仍保留）");
        }
        if (input.code != null) {
            if (input.code.isBlank()) {
                throw new BizException("箱号不能为空");
            }
            String code = input.code.trim();
            Hive other = repo.findByCodeAndDeletedFalse(code).orElse(null);
            if (other != null && !other.id.equals(id)) {
                throw new BizException("箱号已存在：" + code);
            }
            existing.code = code;
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

    /**
     * 软删除：蜂箱下架后其巡检、异常处置与复检记录全部保留可追溯；
     * 箱号唯一索引仅作用于未删除蜂箱，删除后箱号可重新建档使用。
     */
    @Transactional
    public void delete(Long id) {
        Hive existing = get(id);
        if (!Boolean.TRUE.equals(existing.deleted)) {
            existing.deleted = true;
            repo.save(existing);
        }
    }
}
