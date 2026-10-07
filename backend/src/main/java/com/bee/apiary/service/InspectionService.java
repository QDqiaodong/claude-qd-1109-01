package com.bee.apiary.service;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import com.bee.apiary.dto.HandlingRequest;
import com.bee.apiary.dto.InspectionDto;
import com.bee.apiary.dto.RecheckRequest;
import com.bee.apiary.entity.AbnormalHandling;
import com.bee.apiary.entity.AbnormalRecheck;
import com.bee.apiary.entity.Hive;
import com.bee.apiary.entity.Inspection;
import com.bee.apiary.repository.AbnormalHandlingRepository;
import com.bee.apiary.repository.AbnormalRecheckRepository;
import com.bee.apiary.repository.HiveRepository;
import com.bee.apiary.repository.InspectionRepository;

@Service
public class InspectionService {

    @Autowired
    private InspectionRepository repo;

    @Autowired
    private HiveRepository hiveRepository;

    @Autowired
    private AbnormalHandlingRepository handlingRepository;

    @Autowired
    private AbnormalRecheckRepository recheckRepository;

    /**
     * 看板：支持按蜂箱、巡检月份、健康状态、处置状态、截止日期区间过滤。
     * 处置状态支持「逾期 / 待处置 / 待复检 / 已解除 / 复检不合格(连续异常链路)」。
     */
    public List<InspectionDto> list(Long hiveId, String status, String month, String beeHealth,
                                    String handlingStatus, LocalDate dueFrom, LocalDate dueTo) {
        List<Inspection> inspections;
        if (hiveId != null) {
            if (!hiveRepository.existsById(hiveId)) {
                throw new BizException("蜂箱不存在：" + hiveId);
            }
            inspections = repo.findByHiveId(hiveId);
        } else {
            inspections = repo.findAll();
        }
        if (month != null && !month.isEmpty()) {
            LocalDate[] range = parseMonth(month);
            inspections = inspections.stream()
                    .filter(i -> !i.inspectDate.isBefore(range[0]) && !i.inspectDate.isAfter(range[1]))
                    .toList();
        }

        Map<Long, Hive> hiveMap = hiveRepository.findAll().stream()
                .collect(Collectors.toMap(h -> h.id, h -> h));
        Map<Long, List<AbnormalHandling>> handlingMap = handlingRepository.findAll().stream()
                .collect(Collectors.groupingBy(h -> h.inspectionId));
        Map<Long, List<AbnormalRecheck>> recheckMap = recheckRepository.findAll().stream()
                .collect(Collectors.groupingBy(r -> r.inspectionId));

        return inspections.stream()
                .sorted(Comparator.comparing((Inspection i) -> i.inspectDate).reversed()
                        .thenComparing(i -> i.id, Comparator.reverseOrder()))
                .map(i -> toDto(i, hiveMap,
                        handlingMap.getOrDefault(i.id, List.of()),
                        recheckMap.getOrDefault(i.id, List.of())))
                .filter(dto -> status == null || status.isEmpty() || status.equals(dto.status))
                .filter(dto -> beeHealth == null || beeHealth.isEmpty() || beeHealth.equals(dto.beeHealth))
                .filter(dto -> matchHandlingStatus(dto, handlingStatus))
                .filter(dto -> matchDueRange(dto, dueFrom, dueTo))
                .toList();
    }

    public InspectionDto detail(Long id) {
        Inspection inspection = get(id);
        Map<Long, Hive> hiveMap = hiveRepository.findAll().stream()
                .collect(Collectors.toMap(h -> h.id, h -> h));
        return toDto(inspection, hiveMap,
                handlingRepository.findByInspectionIdOrderByRoundNoDescIdDesc(id),
                recheckRepository.findByInspectionIdOrderByRecheckDateDescIdDesc(id));
    }

    public Inspection get(Long id) {
        return repo.findById(id).orElseThrow(() -> new BizException("巡检记录不存在：" + id));
    }

    /** 新增巡检：异常（一般/病态）即进入「待处置」并生成唯一一条处置项；健康为「已记录」。 */
    @Transactional
    public InspectionDto create(Inspection input) {
        if (input.hiveId == null) {
            throw new BizException("请选择蜂箱");
        }
        Hive hive = hiveRepository.findById(input.hiveId)
                .orElseThrow(() -> new BizException("蜂箱不存在：" + input.hiveId));
        if (Boolean.TRUE.equals(hive.deleted)) {
            throw new BizException("蜂箱 " + hive.code + " 已删除，不能再登记巡检；历史巡检与处置仍保留可查");
        }
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
            throw new BizException("蜂群健康为病态时必须填写现场异常说明");
        }
        if (repo.findByHiveIdAndInspectDate(input.hiveId, input.inspectDate).isPresent()) {
            throw new BizException("该蜂箱 " + input.inspectDate + " 已存在巡检记录，月历按蜂箱+日期唯一，不可重复");
        }
        // 该蜂箱存在未完成处置：新巡检必须继续显示风险，不能直接改回「健康」
        if ("健康".equals(input.beeHealth) && hasOpenHandling(input.hiveId, null)) {
            throw new BizException("该蜂箱仍有未完成的异常处置，新巡检不能直接登记为「健康」，请按实际风险登记或先完成处置复检");
        }

        Inspection saved = new Inspection();
        saved.hiveId = hive.id;
        saved.inspectDate = input.inspectDate;
        saved.honeyKg = input.honeyKg;
        saved.beeHealth = input.beeHealth;
        saved.note = input.note;
        boolean abnormal = Dict.isAbnormalHealth(input.beeHealth);
        saved.status = abnormal ? "待处置" : "已记录";
        saved = repo.save(saved);

        if (abnormal) {
            AbnormalHandling handling = new AbnormalHandling();
            handling.inspectionId = saved.id;
            handling.hiveId = hive.id;
            handling.roundNo = 1;
            handling.status = "待处置";
            handling.active = true;
            handlingRepository.save(handling);
        }
        return detail(saved.id);
    }

    @Transactional
    public InspectionDto update(Long id, Inspection input) {
        Inspection existing = get(id);

        // 状态只能由处置/复检流程推进，禁止通用编辑接口直接改写
        if (input.status != null && !input.status.equals(existing.status)) {
            throw new BizException("巡检状态由「处置登记 / 处置复检」流程驱动，不能直接修改为：" + input.status);
        }

        Long targetHiveId = existing.hiveId;
        if (input.hiveId != null && !input.hiveId.equals(existing.hiveId)) {
            Hive target = hiveRepository.findById(input.hiveId)
                    .orElseThrow(() -> new BizException("蜂箱不存在：" + input.hiveId));
            if (Boolean.TRUE.equals(target.deleted)) {
                throw new BizException("蜂箱 " + target.code + " 已删除，不能把巡检移入；历史记录仍保留");
            }
            targetHiveId = input.hiveId;
            if (repo.findByHiveIdAndInspectDate(targetHiveId, existing.inspectDate).isPresent()) {
                throw new BizException("目标蜂箱 " + existing.inspectDate + " 已存在巡检记录，不可重复");
            }
        }
        if (input.inspectDate != null && !input.inspectDate.equals(existing.inspectDate)) {
            if (repo.findByHiveIdAndInspectDate(targetHiveId, input.inspectDate).isPresent()) {
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
        if (input.note != null) {
            existing.note = input.note;
        }

        if (input.beeHealth != null && !input.beeHealth.equals(existing.beeHealth)) {
            if (!Dict.isValidBeeHealth(input.beeHealth)) {
                throw new BizException("蜂群健康取值非法：" + input.beeHealth);
            }
            if ("已解除".equals(existing.status)) {
                throw new BizException("该异常已处置并复检解除，巡检结果已归档，不能改判；再次出现异常请新增巡检");
            }
            boolean wasAbnormal = Dict.isAbnormalHealth(existing.beeHealth);
            boolean toAbnormal = Dict.isAbnormalHealth(input.beeHealth);
            AbnormalHandling active = handlingRepository
                    .findByInspectionIdAndActiveTrue(existing.id).orElse(null);

            if (wasAbnormal && "健康".equals(input.beeHealth)) {
                if ("已解除".equals(existing.status)) {
                    throw new BizException("该异常已处置并复检解除，巡检健康结果已归档；再次异常请新增巡检");
                }
                if (active != null) {
                    throw new BizException("该蜂箱存在未完成处置，不能把健康状态直接改回「健康」；请完成处置并复检合格后解除风险");
                }
            }
            if ("病态".equals(input.beeHealth)
                    && (input.note == null || input.note.isBlank())
                    && (existing.note == null || existing.note.isBlank())) {
                throw new BizException("蜂群健康为病态时必须填写现场异常说明");
            }
            if (!wasAbnormal && toAbnormal) {
                // 健康巡检首次确认为异常：进入待处置并生成处置项
                existing.beeHealth = input.beeHealth;
                existing.status = "待处置";
                repo.save(existing);
                AbnormalHandling handling = new AbnormalHandling();
                handling.inspectionId = existing.id;
                handling.hiveId = targetHiveId;
                handling.roundNo = 1;
                handling.status = "待处置";
                handling.active = true;
                handlingRepository.save(handling);
            } else {
                existing.beeHealth = input.beeHealth;
            }
        }

        if (!targetHiveId.equals(existing.hiveId)) {
            // 巡检移到另一蜂箱：处置与复检历史整体跟随，链路不断
            existing.hiveId = targetHiveId;
            for (AbnormalHandling h : handlingRepository.findByInspectionIdOrderByRoundNoDescIdDesc(existing.id)) {
                h.hiveId = targetHiveId;
                handlingRepository.save(h);
            }
            for (AbnormalRecheck r : recheckRepository.findByInspectionIdOrderByRecheckDateDescIdDesc(existing.id)) {
                r.hiveId = targetHiveId;
                recheckRepository.save(r);
            }
        }
        repo.save(existing);
        return detail(existing.id);
    }

    /**
     * 登记处置措施 / 负责人 / 截止日期 / 现场说明。
     * keepPending=true 仅保存登记（仍待处置）；否则措施、负责人、截止日期齐全后
     * 才能完成处置进入「待复检」。
     */
    @Transactional
    public InspectionDto registerHandling(Long handlingId, HandlingRequest input) {
        AbnormalHandling handling = getActiveHandling(handlingId);
        if (!"待处置".equals(handling.status)) {
            throw new BizException("只有「待处置」的处置项可以登记处置信息，当前状态：" + handling.status);
        }
        if (input == null) {
            throw new BizException("处置信息不能为空");
        }
        String measure = input.measure == null ? null : normalize(input.measure, 255);
        String handler = input.handler == null ? null : normalize(input.handler, 32);
        String siteNote = input.siteNote == null ? null : normalize(input.siteNote, 500);

        if (measure == null || measure.isBlank()) {
            throw new BizException("请填写处置措施（处置说明）");
        }
        if (handler == null || handler.isBlank()) {
            throw new BizException("请填写负责人");
        }
        if (input.dueDate == null) {
            throw new BizException("请选择处置截止日期");
        }

        handling.measure = measure;
        handling.handler = handler;
        handling.dueDate = input.dueDate;
        handling.siteNote = siteNote;
        if (handling.registeredAt == null) {
            handling.registeredAt = LocalDateTime.now();
        }

        boolean keepPending = Boolean.TRUE.equals(input.keepPending);
        if (keepPending) {
            handling.status = "待处置";
        } else {
            // 完成处置：处置说明、负责人、截止日期缺一不可（上面已校验）
            handling.status = "待复检";
            handling.completedAt = LocalDateTime.now();
            Inspection inspection = get(handling.inspectionId);
            inspection.status = "待复检";
            repo.save(inspection);
        }
        handlingRepository.save(handling);
        return detail(handling.inspectionId);
    }

    /**
     * 处置复检：一条处置完成后仅允许一次。
     * 合格 → 处置/巡检「已解除」；不合格 → 旧处置保留为「复检不合格」，
     * 原异常保留并重新生成待处置项，风险截止日期顺延。
     */
    @Transactional
    public InspectionDto recheck(Long handlingId, RecheckRequest input) {
        AbnormalHandling handling = getActiveHandling(handlingId);
        if (!"待复检".equals(handling.status)) {
            throw new BizException("只有已完成处置、处于「待复检」的处置项才能发起复检，当前状态：" + handling.status);
        }
        if (recheckRepository.findByHandlingId(handlingId).isPresent()) {
            throw new BizException("该处置已使用唯一一次复检机会，不能重复复检");
        }
        if (input == null) {
            throw new BizException("复检结果不能为空");
        }
        if (!Dict.isValidAbnormalRecheckResult(input.result)) {
            throw new BizException("复检结论只能为「合格」或「不合格」");
        }
        if (input.inspector == null || input.inspector.isBlank()) {
            throw new BizException("请填写复检人");
        }
        if (input.recheckDate == null) {
            throw new BizException("请选择复检日期");
        }
        Inspection inspection = get(handling.inspectionId);

        AbnormalRecheck recheck = new AbnormalRecheck();
        recheck.handlingId = handling.id;
        recheck.inspectionId = inspection.id;
        recheck.hiveId = handling.hiveId;
        recheck.result = input.result;
        recheck.inspector = normalize(input.inspector, 32);
        recheck.recheckDate = input.recheckDate;
        recheck.note = input.note == null ? null : normalize(input.note, 500);
        recheckRepository.save(recheck);

        if ("合格".equals(input.result)) {
            handling.active = false;
            handling.status = "已解除";
            handlingRepository.save(handling);
            inspection.status = "已解除";
            repo.save(inspection);
        } else {
            // 复检不合格：保留原异常与上一轮处置，重新生成待处置项并顺延风险期限
            handling.active = false;
            handling.status = "复检不合格";
            handlingRepository.saveAndFlush(handling);

            LocalDate base = handling.dueDate != null && handling.dueDate.isAfter(input.recheckDate)
                    ? handling.dueDate : input.recheckDate;
            AbnormalHandling next = new AbnormalHandling();
            next.inspectionId = inspection.id;
            next.hiveId = handling.hiveId;
            next.roundNo = handling.roundNo + 1;
            next.sourceHandlingId = handling.id;
            next.status = "待处置";
            next.active = true;
            next.dueDate = base.plusDays(Dict.ABNORMAL_EXTEND_DAYS);
            handlingRepository.save(next);

            inspection.status = "待处置";
            repo.save(inspection);
        }
        return detail(inspection.id);
    }

    // ------------------------------------------------------------------

    private AbnormalHandling getActiveHandling(Long handlingId) {
        AbnormalHandling handling = handlingRepository.findById(handlingId)
                .orElseThrow(() -> new BizException("处置项不存在：" + handlingId));
        if (!Boolean.TRUE.equals(handling.active)) {
            throw new BizException("该处置轮次已结束（" + handling.status + "），请在最新一轮处置上操作");
        }
        return handling;
    }

    /** 该蜂箱是否存在未完成（生效中）处置；excludeInspectionId 用于排除巡检自身 */
    private boolean hasOpenHandling(Long hiveId, Long excludeInspectionId) {
        return handlingRepository.findByHiveIdAndActiveTrue(hiveId).stream()
                .anyMatch(h -> excludeInspectionId == null || !excludeInspectionId.equals(h.inspectionId));
    }

    private boolean matchHandlingStatus(InspectionDto dto, String handlingStatus) {
        if (handlingStatus == null || handlingStatus.isEmpty()) {
            return true;
        }
        return switch (handlingStatus) {
            case "逾期" -> Boolean.TRUE.equals(dto.overdue);
            case "待复检" -> "待复检".equals(dto.status);
            case "已解除" -> "已解除".equals(dto.status);
            case "待处置" -> "待处置".equals(dto.status);
            // 复检不合格为历史轮次：链路中出现过且当前仍在处置（连续异常）或已解除的都算有该轮次
            case "复检不合格" -> dto.handlings != null
                    && dto.handlings.stream().anyMatch(h -> "复检不合格".equals(h.status));
            default -> throw new BizException("处置状态筛选非法：" + handlingStatus);
        };
    }

    private boolean matchDueRange(InspectionDto dto, LocalDate dueFrom, LocalDate dueTo) {
        if (dueFrom == null && dueTo == null) {
            return true;
        }
        if (dto.dueDate == null) {
            return false;
        }
        if (dueFrom != null && dto.dueDate.isBefore(dueFrom)) {
            return false;
        }
        if (dueTo != null && dto.dueDate.isAfter(dueTo)) {
            return false;
        }
        return true;
    }

    private InspectionDto toDto(Inspection inspection, Map<Long, Hive> hiveMap,
                                List<AbnormalHandling> handlings, List<AbnormalRecheck> rechecks) {
        InspectionDto dto = new InspectionDto();
        dto.id = inspection.id;
        dto.hiveId = inspection.hiveId;
        dto.honeyKg = inspection.honeyKg;
        dto.inspectDate = inspection.inspectDate;
        dto.beeHealth = inspection.beeHealth;
        dto.note = inspection.note;
        dto.status = inspection.status;
        dto.createdAt = inspection.createdAt;

        Hive hive = hiveMap.get(inspection.hiveId);
        dto.hiveCode = hive != null ? hive.code : ("#" + inspection.hiveId);
        dto.hiveDeleted = hive != null && Boolean.TRUE.equals(hive.deleted);

        List<AbnormalHandling> sorted = handlings.stream()
                .sorted(Comparator.comparing((AbnormalHandling h) -> h.roundNo).reversed()
                        .thenComparing(h -> h.id, Comparator.reverseOrder()))
                .toList();
        List<AbnormalRecheck> sortedRechecks = rechecks.stream()
                .sorted(Comparator.comparing((AbnormalRecheck r) -> r.recheckDate).reversed()
                        .thenComparing(r -> r.id, Comparator.reverseOrder()))
                .toList();
        dto.handlings = sorted;
        dto.rechecks = sortedRechecks;

        AbnormalHandling active = sorted.stream().filter(h -> Boolean.TRUE.equals(h.active)).findFirst().orElse(null);
        dto.activeHandling = active;
        dto.dueDate = active == null ? null : active.dueDate;
        dto.handlingRound = sorted.stream().map(h -> h.roundNo).max(Integer::compareTo).orElse(0);
        dto.continuousAbnormal = dto.handlingRound >= 2;
        dto.overdue = active != null && "待处置".equals(active.status)
                && active.dueDate != null && active.dueDate.isBefore(LocalDate.now());
        return dto;
    }

    private LocalDate[] parseMonth(String month) {
        // 期望格式 yyyy-MM
        int year = Integer.parseInt(month.substring(0, 4));
        int m = Integer.parseInt(month.substring(5, 7));
        LocalDate start = LocalDate.of(year, m, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        return new LocalDate[] { start, end };
    }

    private String normalize(String value, int maxLength) {
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }
}
