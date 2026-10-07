package com.bee.apiary.service;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bee.apiary.common.BizException;
import com.bee.apiary.common.Dict;
import com.bee.apiary.entity.HoneyBatch;
import com.bee.apiary.repository.HiveRepository;
import com.bee.apiary.repository.HoneyBatchRepository;
import com.bee.apiary.repository.QualityHandlingRepository;
import com.bee.apiary.repository.QualityInspectionRepository;

@Service
public class HoneyBatchService {

    @Autowired
    private HoneyBatchRepository repo;

    @Autowired
    private HiveRepository hiveRepository;

    @Autowired
    private QualityInspectionRepository qualityInspectionRepository;

    @Autowired
    private QualityHandlingRepository qualityHandlingRepository;

    public java.util.List<HoneyBatch> list(String status) {
        if (status != null && !status.isEmpty()) {
            if (!Dict.isValidHoneyBatchStatus(status)) {
                throw new BizException("批次状态非法：" + status);
            }
            return repo.findAll().stream().filter(b -> status.equals(b.status)).toList();
        }
        return repo.findAll();
    }

    public HoneyBatch get(Long id) {
        return repo.findById(id).orElseThrow(() -> new BizException("采蜜批次不存在：" + id));
    }

    public HoneyBatch create(HoneyBatch input) {
        if (input.hiveId == null) {
            throw new BizException("请选择蜂箱");
        }
        if (!hiveRepository.existsById(input.hiveId)) {
            throw new BizException("蜂箱不存在：" + input.hiveId);
        }
        if (input.batchNo == null || input.batchNo.isBlank()) {
            throw new BizException("批次号不能为空");
        }
        if (repo.findByBatchNo(input.batchNo).isPresent()) {
            throw new BizException("批次号已存在：" + input.batchNo);
        }
        if (input.weightKg == null || input.weightKg.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("重量必须大于 0");
        }
        HoneyBatch saved = new HoneyBatch();
        saved.hiveId = input.hiveId;
        saved.batchNo = input.batchNo;
        saved.weightKg = input.weightKg;
        saved.status = "待质检";
        saved.qualityStatus = "待质检";
        return repo.save(saved);
    }

    public HoneyBatch update(Long id, HoneyBatch input) {
        HoneyBatch existing = get(id);
        if ("已作废".equals(existing.status)) {
            throw new BizException("已作废批次不可修改");
        }
        if (!"待质检".equals(existing.status)) {
            throw new BizException("批次已进入质检流转，基础信息已锁定；请在质检页面查看状态");
        }
        if (input.version != null && existing.version != null && !existing.version.equals(input.version)) {
            throw new BizException("页面数据已过期，请刷新后再保存，避免覆盖最新质检状态");
        }
        if (input.status != null && !input.status.equals(existing.status)) {
            throw new BizException("质检和入库状态必须通过质检放行/入库动作流转，不能直接修改");
        }
        if (input.hiveId != null && !input.hiveId.equals(existing.hiveId)) {
            if (!hiveRepository.existsById(input.hiveId)) {
                throw new BizException("蜂箱不存在：" + input.hiveId);
            }
            existing.hiveId = input.hiveId;
        }
        if (input.batchNo != null && !input.batchNo.equals(existing.batchNo)) {
            if (input.batchNo.isBlank()) {
                throw new BizException("批次号不能为空");
            }
            HoneyBatch other = repo.findByBatchNo(input.batchNo).orElse(null);
            if (other != null && !other.id.equals(id)) {
                throw new BizException("批次号已存在：" + input.batchNo);
            }
            existing.batchNo = input.batchNo;
        }
        if (input.weightKg != null) {
            if (input.weightKg.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("重量必须大于 0");
            }
            existing.weightKg = input.weightKg;
        }
        // 状态流转只能走质检放行、复检和 stock-in 动作，这里不允许直接改 status
        return repo.save(existing);
    }

    /** 状态机：只有质检放行/复检合格的批次才能入库；重复点击第二次会被拒绝。 */
    @Transactional
    public HoneyBatch stockIn(Long id) {
        HoneyBatch existing = repo.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("采蜜批次不存在：" + id));
        if ("已作废".equals(existing.status)) {
            throw new BizException("已作废批次不可入库");
        }
        if ("已入库".equals(existing.status)) {
            throw new BizException("批次已入库，请勿重复操作");
        }
        if (!Dict.isReleasedHoneyBatchStatus(existing.status)) {
            String reason = switch (existing.status == null ? "" : existing.status) {
                case "待质检" -> "批次尚未质检合格，只有含水率≤20%且感官合格并放行后才能入库";
                case "隔离待整改" -> "批次隔离待整改，完成整改并复检合格前不能入库";
                case "隔离拒收" -> "复检不合格且已明确拒绝入库，禁止入库";
                default -> "质检未放行，不能入库";
            };
            throw new BizException(reason);
        }
        existing.status = "已入库";
        existing.qualityStatus = "已入库";
        return repo.save(existing);
    }

    public void delete(Long id) {
        if (!repo.existsById(id)) {
            throw new BizException("采蜜批次不存在：" + id);
        }
        if (qualityInspectionRepository.existsByBatchId(id) || qualityHandlingRepository.existsByBatchId(id)) {
            throw new BizException("批次已有可追溯质检或处理记录，不能删除；如不再使用请作废处理");
        }
        repo.deleteById(id);
    }
}
