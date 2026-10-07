package com.bee.apiary.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bee.apiary.common.BizException;
import com.bee.apiary.common.Dict;
import com.bee.apiary.dto.HandlingBoardItem;
import com.bee.apiary.dto.InspectionBoardItem;
import com.bee.apiary.dto.InspectionHandlingRequest;
import com.bee.apiary.dto.InspectionRecheckRequest;
import com.bee.apiary.entity.Hive;
import com.bee.apiary.entity.Inspection;
import com.bee.apiary.entity.InspectionHandling;
import com.bee.apiary.repository.HiveRepository;
import com.bee.apiary.repository.InspectionHandlingRepository;
import com.bee.apiary.repository.InspectionRepository;

@Service
public class InspectionService {

    private static final List<String> OPEN_STATUSES = List.of("待处置", "待复检");

    @Autowired
    private InspectionRepository repo;

    @Autowired
    private InspectionHandlingRepository handlingRepository;

    @Autowired
    private HiveRepository hiveRepository;

    /** 巡检月历 / 列表：支持蜂箱、健康、处置状态、月份和处置截止日期筛选。 */
    public List<InspectionBoardItem> list(Long hiveId, String beeHealth, String handlingStatus,
                                          String month, LocalDate dueDate) {
        validateHiveFilter(hiveId);
        validateHealthFilter(beeHealth);
        validateHandlingViewStatus(handlingStatus);
        LocalDate[] range = month == null || month.isBlank() ? null : parseMonth(month);

        List<Inspection> inspections = loadInspections(hiveId);
        Map<Long, Hive> hives = loadActiveHives(inspections);
        Map<Long, Inspection> inspectionMap = inspections.stream()
                .collect(Collectors.toMap(i -> i.id, Function.identity()));
        Map<Long, List<Inspection>> byHive = inspections.stream()
                .collect(Collectors.groupingBy(i -> i.hiveId));
        List<InspectionHandling> handlings = loadHandlings(hiveId, inspections);
        Map<Long, HandlingBoardItem> handlingDtos = handlings.stream()
                .collect(Collectors.toMap(h -> h.id, h -> toHandlingDto(h, hives, inspectionMap)));
        Map<Long, InspectionHandling> latestOpen = latestOpenByHive(handlings);

        return inspections.stream()
                .sorted(Comparator.comparing((Inspection i) -> i.inspectDate).reversed()
                        .thenComparing(i -> i.id, Comparator.reverseOrder()))
                .map(i -> toInspectionDto(i, hives, handlingDtos, latestOpen, byHive))
                .filter(dto -> range == null || (!dto.inspectDate.isBefore(range[0]) && !dto.inspectDate.isAfter(range[1])))
                .filter(dto -> beeHealth == null || beeHealth.isBlank() || beeHealth.equals(dto.beeHealth))
                .filter(dto -> matchHandlingStatus(dto, handlingStatus))
                .filter(dto -> dueDate == null || (dto.activeHandling != null && dueDate.equals(dto.activeHandling.deadline)))
                .toList();
    }

    /** 处置记录列表：用于清晰区分逾期、待复检与已解除，并查看复检失败后的历史责任链。 */
    public List<HandlingBoardItem> listHandlings(Long hiveId, String beeHealth, String handlingStatus,
                                                 String month, LocalDate dueDate) {
        validateHiveFilter(hiveId);
        validateHealthFilter(beeHealth);
        validateHandlingViewStatus(handlingStatus);
        LocalDate[] range = month == null || month.isBlank() ? null : parseMonth(month);

        List<Inspection> inspections = loadInspections(hiveId);
        Map<Long, Hive> hives = loadActiveHives(inspections);
        Map<Long, Inspection> inspectionMap = inspections.stream()
                .collect(Collectors.toMap(i -> i.id, Function.identity()));
        List<InspectionHandling> handlings = loadHandlings(hiveId, inspections);

        return handlings.stream()
                .sorted(Comparator.comparing((InspectionHandling h) -> h.createdAt).reversed()
                        .thenComparing(h -> h.id, Comparator.reverseOrder()))
                .map(h -> toHandlingDto(h, hives, inspectionMap))
                .filter(dto -> hiveId == null || hiveId.equals(dto.hiveId))
                .filter(dto -> beeHealth == null || beeHealth.isBlank() || beeHealth.equals(dto.sourceBeeHealth))
                .filter(dto -> range == null
                        || (dto.sourceInspectDate != null && !dto.sourceInspectDate.isBefore(range[0])
                        && !dto.sourceInspectDate.isAfter(range[1])))
                .filter(dto -> dueDate == null || dueDate.equals(dto.deadline))
                .filter(dto -> matchHandlingStatus(dto, handlingStatus))
                .toList();
    }

    public InspectionBoardItem get(Long id) {
        Inspection inspection = getEntity(id);
        List<Inspection> all = repo.findAll();
        Map<Long, Hive> hives = loadActiveHives(all);
        Map<Long, Inspection> inspectionMap = all.stream()
                .collect(Collectors.toMap(i -> i.id, Function.identity()));
        Map<Long, List<Inspection>> byHive = all.stream().collect(Collectors.groupingBy(i -> i.hiveId));
        List<InspectionHandling> handlings = handlingRepository.findAll();
        Map<Long, HandlingBoardItem> handlingDtos = handlings.stream()
                .collect(Collectors.toMap(h -> h.id, h -> toHandlingDto(h, hives, inspectionMap)));
        return toInspectionDto(inspection, hives, handlingDtos, latestOpenByHive(handlings), byHive);
    }

    @Transactional
    public InspectionBoardItem create(Inspection input) {
        if (input.hiveId == null) {
            throw new BizException("请选择蜂箱");
        }
        Hive hive = hiveRepository.findById(input.hiveId)
                .orElseThrow(() -> new BizException("蜂箱不存在：" + input.hiveId));
        if (Boolean.TRUE.equals(hive.deleted)) {
            throw new BizException("蜂箱已删除归档，不能新增巡检");
        }
        if (input.inspectDate == null) {
            throw new BizException("巡检日期不能为空");
        }
        validateHoney(input.honeyKg);
        if (!Dict.isValidBeeHealth(input.beeHealth)) {
            throw new BizException("蜂群健康取值非法：" + input.beeHealth);
        }
        if (repo.findByHiveIdAndInspectDate(input.hiveId, input.inspectDate).isPresent()) {
            throw new BizException("该蜂箱 " + input.inspectDate + " 已存在巡检记录，月历日期必须唯一");
        }

        String note = normalize(input.note, 255);
        boolean unhealthy = !"健康".equals(input.beeHealth);
        if ("病态".equals(input.beeHealth) && (note == null || note.isBlank())) {
            throw new BizException("蜂群健康为病态时必须填写现场说明");
        }
        InspectionHandling open = findOpenHandling(input.hiveId);
        if (open != null && "健康".equals(input.beeHealth)) {
            throw new BizException("该蜂箱存在未完成处置，风险解除前不能把健康状态登记为健康");
        }

        Inspection saved = new Inspection();
        saved.hiveId = hive.id;
        saved.inspectDate = input.inspectDate;
        saved.honeyKg = input.honeyKg;
        saved.beeHealth = input.beeHealth;
        saved.note = note;
        saved.rootInspectionId = null;
        saved.activeHandlingId = null;
        saved.status = "已记录";
        saved = repo.saveAndFlush(saved);

        if (unhealthy) {
            List<Inspection> history = repo.findByHiveIdOrderByInspectDateAscIdAsc(hive.id);
            boolean consecutive = isConsecutiveAbnormal(history, saved.id, saved.inspectDate);
            boolean needsHandling = "病态".equals(saved.beeHealth) || consecutive;
            if (open != null) {
                // 风险期再次巡检异常时只挂到既有链路；跨月浏览/重复提交不能再生成第二个处置任务。
                saved.status = "风险关联";
                saved.rootInspectionId = open.rootInspectionId;
                saved.activeHandlingId = open.id;
                repo.save(saved);
            } else if (needsHandling) {
                if (consecutive) {
                    saved.note = appendConsecutiveFlag(note);
                }
                saved.status = "待处置";
                saved.rootInspectionId = saved.id;
                saved = repo.saveAndFlush(saved);
                InspectionHandling handling = createHandling(saved, saved, null, 1,
                        saved.inspectDate.plus(Dict.DEFAULT_HANDLING_DEADLINE));
                saved.activeHandlingId = handling.id;
                repo.save(saved);
            }
        }
        return get(saved.id);
    }

    @Transactional
    public InspectionBoardItem update(Long id, Inspection input) {
        if (input.status != null) {
            throw new BizException("巡检状态由处置和复检流程驱动，不能直接修改");
        }
        Inspection existing = getEntity(id);
        boolean chainLocked = existing.rootInspectionId != null
                || handlingRepository.existsByRootInspectionId(id)
                || handlingRepository.existsBySourceInspectionId(id);

        if (input.hiveId != null && !input.hiveId.equals(existing.hiveId)) {
            if (chainLocked) {
                throw new BizException("异常链路中的巡检不能移动到其他蜂箱");
            }
            Hive target = hiveRepository.findById(input.hiveId)
                    .orElseThrow(() -> new BizException("蜂箱不存在：" + input.hiveId));
            if (Boolean.TRUE.equals(target.deleted)) {
                throw new BizException("目标蜂箱已删除归档");
            }
            if (repo.findByHiveIdAndInspectDate(input.hiveId, existing.inspectDate).isPresent()) {
                throw new BizException("目标蜂箱该日期已存在巡检记录");
            }
            existing.hiveId = input.hiveId;
        }
        if (input.inspectDate != null && !input.inspectDate.equals(existing.inspectDate)) {
            if (chainLocked) {
                throw new BizException("异常链路中的巡检日期不能修改，以保留责任期限");
            }
            if (repo.findByHiveIdAndInspectDate(existing.hiveId, input.inspectDate).isPresent()) {
                throw new BizException("该蜂箱 " + input.inspectDate + " 已存在巡检记录，不可重复");
            }
            existing.inspectDate = input.inspectDate;
        }
        if (input.honeyKg != null) {
            validateHoney(input.honeyKg);
            existing.honeyKg = input.honeyKg;
        }

        String newHealth = input.beeHealth == null ? existing.beeHealth : input.beeHealth;
        if (!Dict.isValidBeeHealth(newHealth)) {
            throw new BizException("蜂群健康取值非法：" + newHealth);
        }
        String newNote = input.note == null ? existing.note : normalize(input.note, 255);
        if (chainLocked && (!newHealth.equals(existing.beeHealth) || !safeEquals(newNote, existing.note))) {
            throw new BizException("已进入处置/复检链路后不能改写原始异常健康状态和现场说明");
        }
        if ("病态".equals(newHealth) && (newNote == null || newNote.isBlank())) {
            throw new BizException("蜂群健康为病态时必须填写现场说明");
        }

        boolean wasAbnormal = "病态".equals(existing.beeHealth) || existing.rootInspectionId != null;
        boolean editingConsecutive = isConsecutiveAbnormal(
                repo.findByHiveIdOrderByInspectDateAscIdAsc(existing.hiveId), id, existing.inspectDate);
        if (!wasAbnormal && (newHealth.equals("病态") || ("一般".equals(newHealth) && editingConsecutive))) {
            InspectionHandling open = findOpenHandling(existing.hiveId);
            if (open != null) {
                throw new BizException("该蜂箱已有未完成处置，请在新增巡检中登记本次风险，不能改写历史记录");
            }
            existing.beeHealth = newHealth;
            existing.note = editingConsecutive ? appendConsecutiveFlag(newNote) : newNote;
            existing.status = "待处置";
            existing.rootInspectionId = existing.id;
            existing = repo.saveAndFlush(existing);
            InspectionHandling handling = createHandling(existing, existing, null, 1,
                    existing.inspectDate.plus(Dict.DEFAULT_HANDLING_DEADLINE));
            existing.activeHandlingId = handling.id;
        } else if (wasAbnormal && "健康".equals(newHealth)) {
            throw new BizException("病态或连续异常不能直接改回健康；处置完成且复检合格后才会解除");
        } else {
            existing.beeHealth = newHealth;
            existing.note = newNote;
        }
        repo.save(existing);
        return get(existing.id);
    }

    /** 登记措施、负责人、截止日期、现场说明后，待处置项才能完成并进入唯一一次复检。 */
    @Transactional
    public HandlingBoardItem completeHandling(Long handlingId, InspectionHandlingRequest input) {
        InspectionHandling handling = lockHandling(handlingId);
        if (!"待处置".equals(handling.status)) {
            throw new BizException("只有待处置项可以登记处置完成");
        }
        if (input == null) {
            throw new BizException("请填写处置信息");
        }
        if (!Dict.isValidInspectionActionType(input.actionType)) {
            throw new BizException("请选择处置措施");
        }
        if (input.ownerName == null || input.ownerName.isBlank()) {
            throw new BizException("负责人不能为空");
        }
        if (input.deadline == null) {
            throw new BizException("截止日期不能为空");
        }
        if (input.siteNote == null || input.siteNote.isBlank()) {
            throw new BizException("处置现场说明不能为空");
        }

        handling.actionType = input.actionType;
        handling.ownerName = normalize(input.ownerName, 32);
        handling.deadline = input.deadline;
        handling.siteNote = normalize(input.siteNote, 500);
        handling.status = "待复检";
        handling.completedAt = LocalDateTime.now();
        handlingRepository.save(handling);

        Inspection root = getEntity(handling.rootInspectionId);
        root.status = "待复检";
        root.activeHandlingId = handling.id;
        repo.save(root);
        markChainStatus(handling, i -> {
            if (!i.id.equals(handling.rootInspectionId) && i.rootInspectionId != null) {
                i.status = "待复检";
            }
            i.activeHandlingId = handling.id;
        });
        return getHandlingDto(handlingId);
    }

    /** 每个处置项只能发起一次复检；失败保留原异常并顺延期限生成新的待处置项。 */
    @Transactional
    public HandlingBoardItem submitRecheck(Long handlingId, InspectionRecheckRequest input) {
        InspectionHandling handling = lockHandling(handlingId);
        if (!"待复检".equals(handling.status)) {
            throw new BizException("只有处置完成、待复检的记录可以登记复检结果");
        }
        if (input == null) {
            throw new BizException("请填写复检结果");
        }
        if (!Dict.isValidBeeHealth(input.beeHealth)) {
            throw new BizException("复检蜂群健康取值非法：" + input.beeHealth);
        }
        if (input.inspector == null || input.inspector.isBlank()) {
            throw new BizException("复检人不能为空");
        }
        if (input.note == null || input.note.isBlank()) {
            throw new BizException("复检说明不能为空");
        }

        boolean passed = "健康".equals(input.beeHealth);
        LocalDateTime now = LocalDateTime.now();
        handling.recheckResult = passed ? "合格" : "不合格";
        handling.recheckNote = normalize(input.note, 500);
        handling.recheckInspector = normalize(input.inspector, 32);
        handling.recheckAt = now;
        handling.status = passed ? "已解除" : "复检不合格";
        handlingRepository.save(handling);

        Inspection root = getEntity(handling.rootInspectionId);
        if (passed) {
            root.status = "已解除";
            root.activeHandlingId = null;
            repo.save(root);
            markChainStatus(handling, i -> {
                if (i.rootInspectionId != null && !i.id.equals(root.id) && isAbnormal(i)) {
                    i.status = "已解除";
                }
                i.activeHandlingId = null;
            });
            return getHandlingDto(handling.id);
        }

        // 复检不合格：保留首条异常和上一轮处置责任，以原异常为来源顺延期限重新待处置。
        LocalDate extendedDeadline = (handling.deadline == null ? now.toLocalDate() : handling.deadline)
                .plus(Dict.RECHECK_EXTEND_PERIOD);
        InspectionHandling next = createHandling(root, root, handling,
                handling.roundNo + 1, extendedDeadline);

        root.status = "待处置";
        root.activeHandlingId = next.id;
        repo.save(root);
        markChainStatus(next, i -> {
            if (i.rootInspectionId != null && !i.id.equals(root.id) && isAbnormal(i)) {
                i.status = "风险关联";
            }
            i.activeHandlingId = next.id;
        });
        return getHandlingDto(next.id);
    }

    private InspectionHandling createHandling(Inspection root, Inspection source,
                                              InspectionHandling previous, int round, LocalDate deadline) {
        InspectionHandling handling = new InspectionHandling();
        handling.hiveId = root.hiveId;
        handling.rootInspectionId = root.id;
        handling.sourceInspectionId = source.id;
        handling.previousHandlingId = previous == null ? null : previous.id;
        handling.roundNo = round;
        handling.deadline = deadline;
        handling.status = "待处置";
        return handlingRepository.saveAndFlush(handling);
    }

    private void markChainStatus(InspectionHandling current, java.util.function.Consumer<Inspection> updater) {
        for (Inspection item : repo.findByRootInspectionId(current.rootInspectionId)) {
            updater.accept(item);
            repo.save(item);
        }
    }

    private InspectionBoardItem toInspectionDto(Inspection i, Map<Long, Hive> hives,
                                                Map<Long, HandlingBoardItem> handlingDtos,
                                                Map<Long, InspectionHandling> latestOpen,
                                                Map<Long, List<Inspection>> byHive) {
        InspectionBoardItem dto = new InspectionBoardItem();
        dto.id = i.id;
        dto.hiveId = i.hiveId;
        Hive hive = hives.get(i.hiveId);
        dto.hiveCode = hive == null ? ("#" + i.hiveId + "（已删除）") : hive.code;
        dto.hiveLocation = hive == null ? null : hive.location;
        dto.inspectDate = i.inspectDate;
        dto.honeyKg = i.honeyKg;
        dto.beeHealth = i.beeHealth;
        dto.note = i.note;
        dto.status = i.status;
        dto.rootInspectionId = i.rootInspectionId;
        dto.activeHandlingId = i.activeHandlingId;
        dto.createdAt = i.createdAt;
        dto.consecutiveAbnormal = isConsecutiveAbnormal(byHive.getOrDefault(i.hiveId, List.of()), i.id, i.inspectDate);

        InspectionHandling runtimeOpen = latestOpen.get(i.hiveId);
        Long chainRoot = i.rootInspectionId == null ? i.id : i.rootInspectionId;
        Long handlingId = i.activeHandlingId;
        if (runtimeOpen != null && chainRoot.equals(runtimeOpen.rootInspectionId)) {
            handlingId = runtimeOpen.id;
        }
        if (handlingId != null && handlingDtos.containsKey(handlingId)) {
            dto.activeHandling = handlingDtos.get(handlingId);
            dto.overdue = dto.activeHandling.overdue;
            dto.openRisk = OPEN_STATUSES.contains(dto.activeHandling.status);
            dto.riskState = dto.activeHandling.riskState;
            dto.hasFailedRecheck = handlingDtos.values().stream()
                    .anyMatch(h -> h.rootInspectionId.equals(
                            i.rootInspectionId == null ? i.id : i.rootInspectionId)
                            && "复检不合格".equals(h.status));
        } else {
            boolean inRiskChain = "已解除".equals(i.status)
                    || handlingDtos.values().stream().anyMatch(h -> h.rootInspectionId.equals(chainRoot));
            dto.riskState = inRiskChain ? "已解除" : null;
        }
        return dto;
    }

    private HandlingBoardItem toHandlingDto(InspectionHandling h, Map<Long, Hive> hives,
                                            Map<Long, Inspection> inspections) {
        HandlingBoardItem dto = new HandlingBoardItem();
        dto.id = h.id;
        dto.hiveId = h.hiveId;
        Hive hive = hives.get(h.hiveId);
        dto.hiveCode = hive == null ? ("#" + h.hiveId + "（已删除）") : hive.code;
        dto.hiveLocation = hive == null ? null : hive.location;
        dto.rootInspectionId = h.rootInspectionId;
        dto.sourceInspectionId = h.sourceInspectionId;
        dto.previousHandlingId = h.previousHandlingId;
        dto.roundNo = h.roundNo;
        dto.actionType = h.actionType;
        dto.siteNote = h.siteNote;
        dto.ownerName = h.ownerName;
        dto.deadline = h.deadline;
        dto.completedAt = h.completedAt;
        dto.status = h.status;
        dto.recheckResult = h.recheckResult;
        dto.recheckNote = h.recheckNote;
        dto.recheckInspector = h.recheckInspector;
        dto.recheckAt = h.recheckAt;
        dto.createdAt = h.createdAt;

        Inspection source = inspections.get(h.sourceInspectionId);
        if (source != null) {
            dto.sourceInspectDate = source.inspectDate;
            dto.sourceBeeHealth = source.beeHealth;
        }
        dto.overdue = OPEN_STATUSES.contains(h.status)
                && h.deadline != null && h.deadline.isBefore(LocalDate.now());
        dto.riskState = dto.overdue ? "逾期" : h.status;
        return dto;
    }

    private boolean matchHandlingStatus(InspectionBoardItem dto, String status) {
        if (status == null || status.isBlank()) {
            return true;
        }
        if ("逾期".equals(status)) {
            return dto.overdue;
        }
        if (status.equals("已解除")) {
            return "已解除".equals(dto.riskState);
        }
        return dto.activeHandling != null && status.equals(dto.activeHandling.status);
    }

    private boolean matchHandlingStatus(HandlingBoardItem dto, String status) {
        if (status == null || status.isBlank()) {
            return true;
        }
        if ("逾期".equals(status)) {
            return dto.overdue;
        }
        return status.equals(dto.status);
    }

    private List<Inspection> loadInspections(Long hiveId) {
        if (hiveId != null) {
            if (!hiveRepository.existsById(hiveId)) {
                throw new BizException("蜂箱不存在：" + hiveId);
            }
            return repo.findByHiveId(hiveId);
        }
        return repo.findAll();
    }

    private List<InspectionHandling> loadHandlings(Long hiveId, List<Inspection> inspections) {
        if (hiveId != null) {
            return handlingRepository.findByHiveIdOrderByCreatedAtDescIdDesc(hiveId);
        }
        Set<Long> rootIds = inspections.stream()
                .map(i -> i.rootInspectionId == null ? i.id : i.rootInspectionId)
                .collect(Collectors.toSet());
        return handlingRepository.findAll().stream()
                .filter(h -> rootIds.contains(h.rootInspectionId))
                .toList();
    }

    private Map<Long, Hive> loadActiveHives(List<Inspection> inspections) {
        Map<Long, Hive> map = new HashMap<>();
        Set<Long> ids = inspections.stream().map(i -> i.hiveId).collect(Collectors.toSet());
        hiveRepository.findAllById(ids).forEach(h -> map.put(h.id, h));
        return map;
    }

    private Map<Long, InspectionHandling> latestOpenByHive(List<InspectionHandling> handlings) {
        Map<Long, InspectionHandling> result = new HashMap<>();
        handlings.stream()
                .filter(h -> OPEN_STATUSES.contains(h.status))
                .sorted(Comparator.comparing((InspectionHandling h) -> h.createdAt).reversed()
                        .thenComparing(h -> h.id, Comparator.reverseOrder()))
                .forEach(h -> result.putIfAbsent(h.hiveId, h));
        return result;
    }

    private Inspection getEntity(Long id) {
        return repo.findById(id).orElseThrow(() -> new BizException("巡检记录不存在：" + id));
    }

    private InspectionHandling lockHandling(Long id) {
        return handlingRepository.findById(id)
                .orElseThrow(() -> new BizException("处置记录不存在：" + id));
    }

    private HandlingBoardItem getHandlingDto(Long id) {
        List<Inspection> inspections = repo.findAll();
        Map<Long, Hive> hives = loadActiveHives(inspections);
        Map<Long, Inspection> map = inspections.stream()
                .collect(Collectors.toMap(i -> i.id, Function.identity()));
        return toHandlingDto(lockHandling(id), hives, map);
    }

    private InspectionHandling findOpenHandling(Long hiveId) {
        return handlingRepository
                .findFirstByHiveIdAndStatusInOrderByCreatedAtDescIdDesc(hiveId, OPEN_STATUSES)
                .orElse(null);
    }

    private boolean isConsecutiveAbnormal(List<Inspection> ordered, Long currentId, LocalDate currentDate) {
        List<Inspection> before = ordered.stream()
                .filter(i -> !i.id.equals(currentId))
                .filter(i -> !i.inspectDate.isAfter(currentDate))
                .sorted(Comparator.comparing((Inspection i) -> i.inspectDate).reversed()
                        .thenComparing(i -> i.id, Comparator.reverseOrder()))
                .toList();
        Set<Long> counted = new HashSet<>();
        int count = 0;
        for (Inspection item : before) {
            if ("健康".equals(item.beeHealth) || item.rootInspectionId == null) {
                break;
            }
            Long root = item.rootInspectionId;
            if (counted.add(root)) {
                count++;
                if (count >= 2) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isAbnormal(Inspection inspection) {
        return !"健康".equals(inspection.beeHealth)
                && (inspection.rootInspectionId != null || "病态".equals(inspection.beeHealth));
    }

    private String appendConsecutiveFlag(String note) {
        String value = note == null ? "" : note.trim();
        if (value.contains("连续异常")) {
            return value;
        }
        return value.isBlank() ? "连续异常自动预警" : value + "（连续异常自动预警）";
    }

    private void validateHoney(BigDecimal honey) {
        if (honey == null) {
            throw new BizException("产蜜重量不能为空");
        }
        if (honey.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("产蜜重量不能为负数");
        }
    }

    private void validateHiveFilter(Long hiveId) {
        if (hiveId != null && !hiveRepository.existsById(hiveId)) {
            throw new BizException("蜂箱不存在：" + hiveId);
        }
    }

    private void validateHealthFilter(String health) {
        if (health != null && !health.isBlank() && !Dict.isValidBeeHealth(health)) {
            throw new BizException("蜂群健康状态非法：" + health);
        }
    }

    private void validateHandlingViewStatus(String status) {
        if (status != null && !status.isBlank() && !Dict.isValidHandlingViewStatus(status)) {
            throw new BizException("处置状态非法：" + status);
        }
    }

    private boolean safeEquals(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private String normalize(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private LocalDate[] parseMonth(String month) {
        int year = Integer.parseInt(month.substring(0, 4));
        int m = Integer.parseInt(month.substring(5, 7));
        LocalDate start = LocalDate.of(year, m, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        return new LocalDate[] { start, end };
    }
}
