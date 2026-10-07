package com.bee.apiary.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bee.apiary.common.BizException;
import com.bee.apiary.common.Dict;
import com.bee.apiary.entity.Hive;
import com.bee.apiary.entity.Inspection;
import com.bee.apiary.repository.HiveRepository;
import com.bee.apiary.repository.InspectionRepository;

@Service
public class InspectionService {

    @Autowired
    private InspectionRepository repo;

    @Autowired
    private HiveRepository hiveRepository;

    /** 列表：支持按蜂箱、状态、月份(inspectDate 落在当月)过滤 */
    public List<Inspection> list(Long hiveId, String status, String month) {
        List<Inspection> all;
        if (hiveId != null) {
            if (!hiveRepository.existsById(hiveId)) {
                throw new BizException("蜂箱不存在：" + hiveId);
            }
            all = repo.findByHiveId(hiveId);
        } else {
            all = repo.findAll();
        }
        if (status != null && !status.isEmpty()) {
            if (!Dict.isValidInspectionStatus(status)) {
                throw new BizException("巡检状态非法：" + status);
            }
            final String s = status;
            all = all.stream().filter(i -> s.equals(i.status)).toList();
        }
        if (month != null && !month.isEmpty()) {
            LocalDate[] range = parseMonth(month);
            final LocalDate start = range[0];
            final LocalDate end = range[1];
            all = all.stream().filter(i -> !i.inspectDate.isBefore(start) && !i.inspectDate.isAfter(end)).toList();
        }
        return all;
    }

    public Inspection get(Long id) {
        return repo.findById(id).orElseThrow(() -> new BizException("巡检记录不存在：" + id));
    }

    public Inspection create(Inspection input) {
        if (input.hiveId == null) {
            throw new BizException("请选择蜂箱");
        }
        Hive hive = hiveRepository.findById(input.hiveId)
                .orElseThrow(() -> new BizException("蜂箱不存在：" + input.hiveId));
        if (input.inspectDate == null) {
            throw new BizException("巡检日期不能为空");
        }
        if (input.honeyKg == null) {
            throw new BizException("产蜜重量不能为空");
        }
        if (input.honeyKg.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("产蜜重量不能为负数");
        }
        if (input.beeHealth == null || !Dict.isValidBeeHealth(input.beeHealth)) {
            throw new BizException("蜂群健康取值非法：" + input.beeHealth);
        }
        if ("病态".equals(input.beeHealth) && (input.note == null || input.note.isBlank())) {
            throw new BizException("蜂群健康为病态时必须填写备注");
        }
        if (repo.findByHiveIdAndInspectDate(input.hiveId, input.inspectDate).isPresent()) {
            throw new BizException("该蜂箱 " + input.inspectDate + " 已存在巡检记录，不可重复");
        }
        Inspection saved = new Inspection();
        saved.hiveId = hive.id;
        saved.inspectDate = input.inspectDate;
        saved.honeyKg = input.honeyKg;
        saved.beeHealth = input.beeHealth;
        saved.note = input.note;
        saved.status = "已记录";
        return repo.save(saved);
    }

    public Inspection update(Long id, Inspection input) {
        Inspection existing = get(id);
        if (input.hiveId != null && !input.hiveId.equals(existing.hiveId)) {
            if (!hiveRepository.existsById(input.hiveId)) {
                throw new BizException("蜂箱不存在：" + input.hiveId);
            }
            existing.hiveId = input.hiveId;
        }
        if (input.inspectDate != null && !input.inspectDate.equals(existing.inspectDate)) {
            if (repo.findByHiveIdAndInspectDate(existing.hiveId, input.inspectDate).isPresent()) {
                throw new BizException("该蜂箱 " + input.inspectDate + " 已存在巡检记录，不可重复");
            }
            existing.inspectDate = input.inspectDate;
        }
        if (input.honeyKg != null) {
            if (input.honeyKg.compareTo(BigDecimal.ZERO) < 0) {
                throw new BizException("产蜜重量不能为负数");
            }
            existing.honeyKg = input.honeyKg;
        }
        if (input.beeHealth != null) {
            if (!Dict.isValidBeeHealth(input.beeHealth)) {
                throw new BizException("蜂群健康取值非法：" + input.beeHealth);
            }
            if ("病态".equals(input.beeHealth)
                    && (existing.note == null || existing.note.isBlank())
                    && (input.note == null || input.note.isBlank())) {
                throw new BizException("蜂群健康为病态时必须填写备注");
            }
            existing.beeHealth = input.beeHealth;
        }
        if (input.note != null) {
            existing.note = input.note;
        }
        if (input.status != null && !input.status.equals(existing.status)) {
            if (!Dict.canTransferInspection(existing.status, input.status)) {
                throw new BizException("巡检状态只能由「已记录」推进到「已复检」");
            }
            existing.status = input.status;
        }
        return repo.save(existing);
    }

    /** 状态机：已记录 → 已复检 */
    public Inspection recheck(Long id) {
        Inspection existing = get(id);
        if (!Dict.canTransferInspection(existing.status, "已复检")) {
            throw new BizException("只有「已记录」的巡检才能复检");
        }
        existing.status = "已复检";
        return repo.save(existing);
    }

    private LocalDate[] parseMonth(String month) {
        // 期望格式 yyyy-MM
        int year = Integer.parseInt(month.substring(0, 4));
        int m = Integer.parseInt(month.substring(5, 7));
        LocalDate start = LocalDate.of(year, m, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        return new LocalDate[] { start, end };
    }
}
