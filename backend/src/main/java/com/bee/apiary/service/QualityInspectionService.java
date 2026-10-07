package com.bee.apiary.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bee.apiary.common.BizException;
import com.bee.apiary.common.Dict;
import com.bee.apiary.dto.ActionState;
import com.bee.apiary.dto.QualityBatchDto;
import com.bee.apiary.dto.QualityInspectionRequest;
import com.bee.apiary.dto.QualityRectificationRequest;
import com.bee.apiary.entity.HoneyBatch;
import com.bee.apiary.entity.QualityHandling;
import com.bee.apiary.entity.QualityInspection;
import com.bee.apiary.repository.HoneyBatchRepository;
import com.bee.apiary.repository.QualityHandlingRepository;
import com.bee.apiary.repository.QualityInspectionRepository;

@Service
public class QualityInspectionService {

    @Autowired
    private HoneyBatchRepository batchRepository;

    @Autowired
    private QualityInspectionRepository inspectionRepository;

    @Autowired
    private QualityHandlingRepository handlingRepository;

    public List<QualityBatchDto> board(String batchStatus, String conclusion) {
        if (batchStatus != null && !batchStatus.isBlank() && !Dict.isValidHoneyBatchStatus(batchStatus)) {
            throw new BizException("批次状态非法：" + batchStatus);
        }
        if (conclusion != null && !conclusion.isBlank()
                && !"合格".equals(conclusion) && !"不合格".equals(conclusion) && !"待质检".equals(conclusion)) {
            throw new BizException("质检结论非法：" + conclusion);
        }

        List<HoneyBatch> batches = batchRepository.findAll(
                org.springframework.data.domain.Sort.by("id").ascending());
        Map<Long, List<QualityInspection>> inspectionMap = inspectionRepository.findAllByOrderByInspectedAtDescIdDesc()
                .stream().collect(Collectors.groupingBy(i -> i.batchId));
        Map<Long, List<QualityHandling>> handlingMap = handlingRepository.findAllByOrderByHandledAtDescIdDesc()
                .stream().collect(Collectors.groupingBy(h -> h.batchId));

        return batches.stream()
                .filter(b -> batchStatus == null || batchStatus.isBlank() || batchStatus.equals(b.status))
                .map(b -> toDto(b,
                        inspectionMap.getOrDefault(b.id, List.of()),
                        handlingMap.getOrDefault(b.id, List.of())))
                .filter(dto -> matchConclusion(dto, conclusion))
                .toList();
    }

    public QualityBatchDto detail(Long batchId) {
        HoneyBatch batch = getBatch(batchId);
        return toDto(batch,
                inspectionRepository.findByBatchIdOrderByInspectedAtDescIdDesc(batchId),
                handlingRepository.findByBatchIdOrderByHandledAtDescIdDesc(batchId));
    }

    /** 登记初检：合格即质检放行；任一不合格即隔离待整改。 */
    @Transactional
    public QualityBatchDto submitInitial(Long batchId, QualityInspectionRequest input) {
        HoneyBatch batch = lockBatch(batchId);
        if (!"待质检".equals(batch.status)) {
            throw new BizException(inspectReason(batch));
        }
        return submit(batch, input, "初检");
    }

    /** 整改后唯一一次复检；复检仍不合格则隔离拒收，禁止再送检。 */
    @Transactional
    public QualityBatchDto submitRecheck(Long batchId, QualityInspectionRequest input) {
        HoneyBatch batch = lockBatch(batchId);
        QualityInspection active = requireActive(batchId);
        if (!"隔离待整改".equals(batch.status) || !"不合格".equals(active.result) || !"初检".equals(active.roundType)) {
            throw new BizException(recheckReason(batch, active));
        }
        if (!hasRectification(batchId)) {
            throw new BizException("请先补充整改说明，整改完成后才能发起复检");
        }
        if (inspectionRepository.existsByBatchIdAndRoundType(batchId, "复检")) {
            throw new BizException("同一批次最多只能发起一次复检");
        }
        return submit(batch, input, "复检");
    }

    /** 隔离批次必须先登记整改说明，之后才允许发起唯一一次复检。 */
    @Transactional
    public QualityBatchDto addRectification(Long batchId, QualityRectificationRequest input) {
        HoneyBatch batch = lockBatch(batchId);
        QualityInspection active = inspectionRepository.findByBatchIdAndActiveTrue(batchId).orElse(null);
        if (!"隔离待整改".equals(batch.status) || active == null || !"不合格".equals(active.result)
                || !"初检".equals(active.roundType)) {
            throw new BizException(rectifyReason(batch, active));
        }
        if (hasHandlingType(batchId, "整改")) {
            throw new BizException("整改说明已登记，请发起复检；不能重复补充");
        }
        if (input == null || input.opinion == null || input.opinion.isBlank()) {
            throw new BizException("整改说明不能为空");
        }
        if (input.handler == null || input.handler.isBlank()) {
            throw new BizException("整改责任人不能为空");
        }

        QualityHandling handling = new QualityHandling();
        handling.batchId = batchId;
        handling.inspectionId = active.id;
        handling.handlingType = "整改";
        handling.opinion = normalize(input.opinion, 500);
        handling.handler = normalize(input.handler, 32);
        handling.handledAt = LocalDateTime.now();
        handlingRepository.save(handling);
        return detail(batchId);
    }

    private QualityBatchDto submit(HoneyBatch batch, QualityInspectionRequest input, String roundType) {
        validateInspection(input);
        if (inspectionRepository.findBySampleNo(input.sampleNo.trim()).isPresent()) {
            throw new BizException("抽样编号已存在，不能被不同批次重复使用：" + input.sampleNo.trim());
        }

        QualityInspection previous = inspectionRepository.findByBatchIdAndActiveTrue(batch.id).orElse(null);
        if (previous != null) {
            previous.active = false;
            inspectionRepository.saveAndFlush(previous);
        }

        boolean passed = input.moisturePercent.compareTo(Dict.HONEY_MOISTURE_LIMIT) <= 0
                && "合格".equals(input.sensoryResult);
        QualityInspection inspection = new QualityInspection();
        inspection.batchId = batch.id;
        inspection.sampleNo = input.sampleNo.trim();
        inspection.roundType = roundType;
        inspection.moisturePercent = input.moisturePercent;
        inspection.sensoryResult = input.sensoryResult;
        inspection.result = passed ? "合格" : "不合格";
        inspection.inspector = normalize(input.inspector, 32);
        inspection.inspectedAt = input.inspectedAt;
        inspection.note = normalize(input.note, 500);
        inspection.active = true;
        inspectionRepository.saveAndFlush(inspection);

        if (passed) {
            String releasedStatus = "初检".equals(roundType) ? "质检放行" : "复检合格";
            batch.status = releasedStatus;
            batch.qualityStatus = releasedStatus;
            if ("复检".equals(roundType)) {
                saveHandling(batch.id, inspection.id, "复检放行", "复检合格，同意放行入库", inspection.inspector,
                        inspection.inspectedAt);
            }
        } else {
            if ("初检".equals(roundType)) {
                batch.status = "隔离待整改";
                batch.qualityStatus = "隔离待整改";
                saveHandling(batch.id, inspection.id, "隔离处理", inspection.note, inspection.inspector,
                        inspection.inspectedAt);
            } else {
                batch.status = "隔离拒收";
                batch.qualityStatus = "隔离拒收";
                saveHandling(batch.id, inspection.id, "拒绝入库",
                        "复检仍不合格，明确拒绝入库。说明：" + inspection.note, inspection.inspector,
                        inspection.inspectedAt);
            }
        }
        batchRepository.save(batch);
        // 事务提交前重新读取本次改动，给调用方返回包含追溯记录的最新快照
        return detail(batch.id);
    }

    private void validateInspection(QualityInspectionRequest input) {
        if (input == null) {
            throw new BizException("质检结果不能为空");
        }
        if (input.sampleNo == null || input.sampleNo.isBlank()) {
            throw new BizException("抽样编号不能为空");
        }
        if (input.moisturePercent == null) {
            throw new BizException("含水率不能为空");
        }
        if (input.moisturePercent.compareTo(BigDecimal.ZERO) <= 0
                || input.moisturePercent.compareTo(new BigDecimal("100")) >= 0) {
            throw new BizException("含水率必须在 0% 到 100% 之间");
        }
        if (!Dict.isValidQualityResult(input.sensoryResult)) {
            throw new BizException("感官结论只能为「合格」或「不合格」");
        }
        if (input.inspector == null || input.inspector.isBlank()) {
            throw new BizException("检测人不能为空");
        }
        if (input.inspectedAt == null) {
            throw new BizException("检测时间不能为空");
        }
        if (input.note == null || input.note.isBlank()) {
            throw new BizException("检测说明不能为空");
        }
    }

    private QualityBatchDto toDto(HoneyBatch batch, List<QualityInspection> inspections,
                                  List<QualityHandling> handlings) {
        QualityBatchDto dto = new QualityBatchDto();
        dto.id = batch.id;
        dto.hiveId = batch.hiveId;
        dto.batchNo = batch.batchNo;
        dto.weightKg = batch.weightKg;
        dto.status = batch.status;
        dto.qualityStatus = batch.qualityStatus == null ? batch.status : batch.qualityStatus;
        dto.version = batch.version;
        dto.createdAt = batch.createdAt;

        dto.inspections = inspections.stream()
                .sorted(Comparator.comparing((QualityInspection i) -> i.inspectedAt).reversed()
                        .thenComparing(i -> i.id, Comparator.reverseOrder()))
                .toList();
        dto.handlings = handlings.stream()
                .sorted(Comparator.comparing((QualityHandling h) -> h.handledAt).reversed()
                        .thenComparing(h -> h.id, Comparator.reverseOrder()))
                .toList();
        dto.activeInspection = dto.inspections.stream().filter(i -> Boolean.TRUE.equals(i.active)).findFirst().orElse(null);
        dto.inspectionCount = dto.inspections.size();
        dto.recheckSubmitted = dto.inspections.stream().anyMatch(i -> "复检".equals(i.roundType));

        dto.inspectAction = inspectAction(batch, dto.activeInspection);
        dto.rectifyAction = rectifyAction(batch, dto.activeInspection, hasHandlingType(dto.handlings, "整改"));
        dto.recheckAction = recheckAction(batch, dto.activeInspection, hasHandlingType(dto.handlings, "整改"),
                dto.recheckSubmitted);
        dto.stockInAction = stockInAction(batch);
        return dto;
    }

    private boolean matchConclusion(QualityBatchDto dto, String conclusion) {
        if (conclusion == null || conclusion.isBlank()) {
            return true;
        }
        String actual = dto.activeInspection == null ? "待质检" : dto.activeInspection.result;
        return conclusion.equals(actual);
    }

    private ActionState inspectAction(HoneyBatch batch, QualityInspection active) {
        if ("待质检".equals(batch.status)) {
            return new ActionState(true, "可登记初检结果");
        }
        return new ActionState(false, inspectReason(batch, active));
    }

    private String inspectReason(HoneyBatch batch) {
        return inspectReason(batch, inspectionRepository.findByBatchIdAndActiveTrue(batch.id).orElse(null));
    }

    private String inspectReason(HoneyBatch batch, QualityInspection active) {
        if (batch.status == null) {
            return "批次状态异常，不能登记质检";
        }
        return switch (batch.status) {
            case "质检放行" -> "初检已合格放行，请执行入库；不能重复登记质检";
            case "复检合格" -> "复检已合格放行，请执行入库；不能重复登记质检";
            case "隔离待整改" -> hasRectification(batch.id)
                    ? "整改说明已补充，请发起唯一一次复检；不能重复初检"
                    : "初检不合格，批次已隔离；请先补充整改说明，不能再次送检";
            case "隔离拒收" -> "复检仍不合格，已明确拒绝入库，不能再次送检";
            case "已入库" -> "批次已入库，质检结果已锁定，不能覆盖";
            case "已作废" -> "批次已作废，不能质检";
            default -> "当前状态不能登记质检";
        };
    }

    private ActionState rectifyAction(HoneyBatch batch, QualityInspection active, boolean hasRectification) {
        if ("隔离待整改".equals(batch.status) && active != null && "不合格".equals(active.result)
                && "初检".equals(active.roundType) && !hasRectification) {
            return new ActionState(true, "可补充整改说明");
        }
        return new ActionState(false, rectifyReason(batch, active, hasRectification));
    }

    private String rectifyReason(HoneyBatch batch, QualityInspection active) {
        return rectifyReason(batch, active, hasRectification(batch.id));
    }

    private String rectifyReason(HoneyBatch batch, QualityInspection active, boolean hasRectification) {
        if ("隔离待整改".equals(batch.status) && hasRectification) {
            return "整改说明已登记，请发起复检";
        }
        if ("隔离拒收".equals(batch.status)) {
            return "复检仍不合格并已拒绝入库，整改流程已关闭";
        }
        if ("待质检".equals(batch.status)) {
            return "尚无不合格初检记录，无需整改";
        }
        if ("质检放行".equals(batch.status) || "复检合格".equals(batch.status)) {
            return "质检已合格，无需整改";
        }
        if ("已入库".equals(batch.status)) {
            return "批次已入库，不能补登记整改";
        }
        if ("已作废".equals(batch.status)) {
            return "批次已作废，不能整改";
        }
        return "当前状态不能补充整改说明";
    }

    private ActionState recheckAction(HoneyBatch batch, QualityInspection active, boolean hasRectification,
                                      boolean recheckSubmitted) {
        boolean allowed = "隔离待整改".equals(batch.status) && active != null
                && "不合格".equals(active.result) && "初检".equals(active.roundType)
                && hasRectification && !recheckSubmitted;
        return new ActionState(allowed, allowed ? "整改完成，可发起复检" : recheckReason(batch, active,
                hasRectification, recheckSubmitted));
    }

    private String recheckReason(HoneyBatch batch, QualityInspection active) {
        return recheckReason(batch, active, hasRectification(batch.id),
                inspectionRepository.existsByBatchIdAndRoundType(batch.id, "复检"));
    }

    private String recheckReason(HoneyBatch batch, QualityInspection active, boolean hasRectification,
                                 boolean recheckSubmitted) {
        if ("待质检".equals(batch.status)) {
            return "请先登记初检结果";
        }
        if ("质检放行".equals(batch.status)) {
            return "初检已合格放行，不能复检";
        }
        if ("复检合格".equals(batch.status)) {
            return "复检已合格，不能再次复检";
        }
        if ("隔离拒收".equals(batch.status) || recheckSubmitted) {
            return "已使用唯一一次复检机会；复检仍不合格时保持隔离并拒绝入库";
        }
        if ("已入库".equals(batch.status)) {
            return "批次已入库，不能复检";
        }
        if ("已作废".equals(batch.status)) {
            return "批次已作废，不能复检";
        }
        if ("隔离待整改".equals(batch.status) && !hasRectification) {
            return "请先补充整改说明，之后才能发起复检";
        }
        return "当前状态不能发起复检";
    }

    private ActionState stockInAction(HoneyBatch batch) {
        if (Dict.isReleasedHoneyBatchStatus(batch.status)) {
            return new ActionState(true, "质检已放行，可入库");
        }
        if (batch.status == null) {
            return new ActionState(false, "批次状态异常，不能入库");
        }
        return new ActionState(false, switch (batch.status) {
            case "待质检" -> "尚未质检合格，不能入库";
            case "隔离待整改" -> "批次隔离待整改，完成复检并合格前不能入库";
            case "隔离拒收" -> "复检不合格且已明确拒绝入库，禁止入库";
            case "已入库" -> "批次已入库，请勿重复操作";
            case "已作废" -> "批次已作废，不能入库";
            default -> "质检未放行，不能入库";
        });
    }

    private void saveHandling(Long batchId, Long inspectionId, String type, String opinion, String handler,
                              LocalDateTime handledAt) {
        QualityHandling handling = new QualityHandling();
        handling.batchId = batchId;
        handling.inspectionId = inspectionId;
        handling.handlingType = type;
        handling.opinion = normalize(opinion, 500);
        handling.handler = handler;
        handling.handledAt = handledAt;
        handlingRepository.save(handling);
    }

    private HoneyBatch lockBatch(Long batchId) {
        return batchRepository.findByIdForUpdate(batchId)
                .orElseThrow(() -> new BizException("采蜜批次不存在：" + batchId));
    }

    private HoneyBatch getBatch(Long batchId) {
        return batchRepository.findById(batchId)
                .orElseThrow(() -> new BizException("采蜜批次不存在：" + batchId));
    }

    private QualityInspection requireActive(Long batchId) {
        return inspectionRepository.findByBatchIdAndActiveTrue(batchId)
                .orElseThrow(() -> new BizException("该批次暂无生效中的质检记录"));
    }

    private boolean hasRectification(Long batchId) {
        return hasHandlingType(batchId, "整改");
    }

    private boolean hasHandlingType(Long batchId, String type) {
        return handlingRepository.findByBatchIdOrderByHandledAtDescIdDesc(batchId).stream()
                .anyMatch(h -> type.equals(h.handlingType));
    }

    private boolean hasHandlingType(List<QualityHandling> handlings, String type) {
        return handlings.stream().anyMatch(h -> type.equals(h.handlingType));
    }

    private String normalize(String value, int maxLength) {
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }
}
