SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET character_set_client = utf8mb4;
SET character_set_connection = utf8mb4;
SET character_set_results = utf8mb4;

-- ---------------------------------------------------------------------
-- 养蜂场 · 蜂箱 / 巡检 / 采蜜批次 / 蜂蜜质检 / 客户
-- 库名 bee_apiary；docker 构建期由本文件导入建库建表与种子。
-- 注意：mysql 容器是 UTC，时间戳写 NOW()，日期用 CURDATE()。

CREATE DATABASE IF NOT EXISTS bee_apiary
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE bee_apiary;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS quality_handling;
DROP TABLE IF EXISTS quality_inspection;
DROP TABLE IF EXISTS inspection_handling;
DROP TABLE IF EXISTS inspection;
DROP TABLE IF EXISTS honey_batch;
DROP TABLE IF EXISTS hive;
DROP TABLE IF EXISTS customer;
SET FOREIGN_KEY_CHECKS = 1;

-- ---------------- 蜂箱 ----------------
CREATE TABLE hive (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    code      VARCHAR(32) NOT NULL UNIQUE COMMENT '箱号 H-01',
    location  VARCHAR(64) COMMENT '摆放点',
    status    VARCHAR(16) COMMENT '正常 / 待检 / 患病',
    deleted   TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除归档',
    created_at DATETIME COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 巡检 ----------------
CREATE TABLE inspection (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    hive_id              BIGINT NOT NULL COMMENT '蜂箱 id',
    inspect_date         DATE NOT NULL COMMENT '巡检日期',
    honey_kg             DECIMAL(8,2) NOT NULL COMMENT '产蜜 kg',
    bee_health           VARCHAR(16) COMMENT '健康 / 一般 / 病态',
    note                 VARCHAR(255) COMMENT '现场说明',
    status               VARCHAR(16) NOT NULL COMMENT '已记录 / 待处置 / 待复检 / 风险关联 / 已解除',
    root_inspection_id   BIGINT COMMENT '异常链路首条巡检 id',
    active_handling_id   BIGINT COMMENT '当前生效处置项 id',
    created_at           DATETIME COMMENT '创建时间',
    UNIQUE KEY uk_inspection_hive_date (hive_id, inspect_date),
    INDEX idx_inspection_hive_date (hive_id, inspect_date),
    INDEX idx_inspection_status (status),
    INDEX idx_inspection_root (root_inspection_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 巡检异常处置与复检 ----------------
-- active_hive_id 为生成列：只有未完成处置携带 hive_id，配合唯一索引保证
-- 同一蜂箱同时最多一条待处置/待复检任务；跨月查看不会重复生成。
CREATE TABLE inspection_handling (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    hive_id                BIGINT NOT NULL COMMENT '蜂箱 id',
    root_inspection_id     BIGINT NOT NULL COMMENT '异常链路首条巡检 id',
    source_inspection_id   BIGINT NOT NULL COMMENT '本轮异常/复检来源巡检 id',
    previous_handling_id   BIGINT COMMENT '上一轮处置项 id',
    round_no               INT NOT NULL DEFAULT 1 COMMENT '处置轮次',
    action_type            VARCHAR(32) COMMENT '处置措施',
    site_note              VARCHAR(500) COMMENT '处置现场说明',
    owner_name             VARCHAR(32) COMMENT '负责人',
    deadline               DATE COMMENT '处置截止日期',
    completed_at           DATETIME COMMENT '处置完成时间',
    status                 VARCHAR(16) NOT NULL COMMENT '待处置 / 待复检 / 已解除 / 复检不合格',
    recheck_result         VARCHAR(16) COMMENT '合格 / 不合格',
    recheck_note           VARCHAR(500) COMMENT '复检说明',
    recheck_inspector      VARCHAR(32) COMMENT '复检人',
    recheck_at             DATETIME COMMENT '复检时间',
    active                 TINYINT(1) GENERATED ALWAYS AS (IF(status IN ('待处置','待复检'), 1, 0)) VIRTUAL COMMENT '未完成标记',
    active_hive_id         BIGINT GENERATED ALWAYS AS (IF(status IN ('待处置','待复检'), hive_id, NULL)) VIRTUAL COMMENT '未完成任务唯一约束辅助列',
    created_at             DATETIME COMMENT '创建时间',
    UNIQUE KEY uk_active_handling_hive (active_hive_id),
    INDEX idx_handling_hive (hive_id),
    INDEX idx_handling_root (root_inspection_id),
    INDEX idx_handling_source (source_inspection_id),
    INDEX idx_handling_status_deadline (status, deadline)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 采蜜批次 ----------------
CREATE TABLE honey_batch (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    hive_id        BIGINT NOT NULL COMMENT '蜂箱 id',
    batch_no       VARCHAR(32) NOT NULL UNIQUE COMMENT '批次号 B-2026-001',
    weight_kg      DECIMAL(8,2) NOT NULL COMMENT '重量 kg',
    status         VARCHAR(32) COMMENT '待质检 / 质检放行 / 隔离待整改 / 复检合格 / 隔离拒收 / 已入库 / 已作废',
    quality_status VARCHAR(32) COMMENT '质检状态，与批次状态同步',
    version        BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at     DATETIME COMMENT '创建时间',
    INDEX idx_honey_batch_status (status),
    INDEX idx_honey_batch_quality_status (quality_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 蜂蜜批次质检 ----------------
-- active_batch_id 为 MySQL 生成列：仅生效记录携带 batch_id，配合唯一索引确保
-- 同一批次最多只有一条生效中的质检记录；历史复检记录 active=0 保留可追溯。
CREATE TABLE quality_inspection (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id         BIGINT NOT NULL COMMENT '采蜜批次 id',
    sample_no        VARCHAR(64) NOT NULL COMMENT '抽样编号，全库唯一',
    round_type       VARCHAR(16) NOT NULL COMMENT '初检 / 复检',
    moisture_percent DECIMAL(6,2) NOT NULL COMMENT '含水率 %',
    sensory_result   VARCHAR(16) NOT NULL COMMENT '感官结论：合格 / 不合格',
    result           VARCHAR(16) NOT NULL COMMENT '质检结论：合格 / 不合格',
    inspector        VARCHAR(32) NOT NULL COMMENT '检测人',
    inspected_at     DATETIME NOT NULL COMMENT '检测时间',
    note             VARCHAR(500) NOT NULL COMMENT '检测说明 / 不合格处理意见',
    active           TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否生效中',
    active_batch_id  BIGINT GENERATED ALWAYS AS (IF(active = 1, batch_id, NULL)) VIRTUAL COMMENT '生效记录唯一约束辅助列',
    created_at       DATETIME COMMENT '创建时间',
    UNIQUE KEY uk_quality_sample_no (sample_no),
    UNIQUE KEY uk_quality_active_batch (active_batch_id),
    INDEX idx_quality_batch (batch_id),
    INDEX idx_quality_result (result),
    CONSTRAINT fk_quality_batch FOREIGN KEY (batch_id) REFERENCES honey_batch (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 蜂蜜质检处理意见 / 整改 ----------------
CREATE TABLE quality_handling (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id      BIGINT NOT NULL COMMENT '采蜜批次 id',
    inspection_id BIGINT COMMENT '关联质检记录 id',
    handling_type VARCHAR(32) NOT NULL COMMENT '隔离处理 / 整改 / 复检放行 / 拒绝入库',
    opinion       VARCHAR(500) NOT NULL COMMENT '处理或整改说明',
    handler       VARCHAR(32) NOT NULL COMMENT '登记人 / 检测人',
    handled_at    DATETIME NOT NULL COMMENT '处理时间',
    created_at    DATETIME COMMENT '创建时间',
    INDEX idx_handling_batch (batch_id),
    INDEX idx_handling_type (handling_type),
    CONSTRAINT fk_handling_batch FOREIGN KEY (batch_id) REFERENCES honey_batch (id),
    CONSTRAINT fk_handling_inspection FOREIGN KEY (inspection_id) REFERENCES quality_inspection (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 客户 ----------------
CREATE TABLE customer (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    name      VARCHAR(64) NOT NULL COMMENT '客户名',
    phone     VARCHAR(24) UNIQUE COMMENT '手机号',
    type      VARCHAR(16) COMMENT '商超 / 个人',
    created_at DATETIME COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================================
-- 种子数据
-- =====================================================================

INSERT INTO hive (code, location, status, deleted, created_at) VALUES
('H-01', '南侧坡地',   '正常', 0, NOW() - INTERVAL 60 DAY),
('H-02', '东侧果园',   '待检', 0, NOW() - INTERVAL 60 DAY),
('H-03', '北侧林缘',   '患病', 0, NOW() - INTERVAL 60 DAY),
('H-04', '西侧花田',   '患病', 0, NOW() - INTERVAL 60 DAY),
('H-05', '南侧坡地',   '正常', 0, NOW() - INTERVAL 55 DAY),
('H-06', '中央平台',   '患病', 0, NOW() - INTERVAL 55 DAY),
('H-07', '东侧果园',   '正常', 0, NOW() - INTERVAL 50 DAY),
('H-08', '北侧林缘',   '正常', 0, NOW() - INTERVAL 50 DAY);

INSERT INTO inspection
(id, hive_id, inspect_date, honey_kg, bee_health, note, status, root_inspection_id, active_handling_id, created_at) VALUES
(1, 1, CURDATE(),                                     8.50, '健康', NULL,                          '已记录', NULL, NULL, NOW()),
(2, 2, DATE_SUB(CURDATE(), INTERVAL 2 DAY),          6.20, '病态', '蜂王活动偏弱，工蜂出勤异常', '待复检', 2,    1,    NOW()),
(3, 3, CURDATE(),                                     0.00, '病态', '发现幼虫病疑似，立即隔离',     '待处置', 3,    2,    NOW()),
(4, 4, DATE_SUB(CURDATE(), INTERVAL 12 DAY),          3.10, '病态', '初次发现蜂螨超标',             '已解除', 4,    NULL, NOW() - INTERVAL 12 DAY),
(5, 5, DATE_SUB(CURDATE(), INTERVAL 3 DAY),           7.80, '健康', NULL,                          '已记录', NULL, NULL, NOW()),
(6, 6, DATE_SUB(CURDATE(), INTERVAL 4 DAY),           5.00, '一般', '蜂量下降，饲料不足（连续异常）','风险关联', 7,    4,    NOW() - INTERVAL 4 DAY),
(7, 6, DATE_SUB(CURDATE(), INTERVAL 1 DAY),           4.60, '病态', '幼虫腐烂，疑似幼虫病（连续异常）','待处置', 7,    4,    NOW()),
(8, 7, DATE_SUB(CURDATE(), INTERVAL 9 DAY),           8.20, '病态', '外勤蜂减少，巢门活动弱',       '已解除', 8,    NULL, NOW() - INTERVAL 9 DAY),
(9, 8, DATE_SUB(CURDATE(), INTERVAL 5 DAY),           2.40, '病态', '蜂螨严重，复检不合格后顺延',   '待处置', 9,    6,    NOW() - INTERVAL 5 DAY),
(10, 1, DATE_SUB(CURDATE(), INTERVAL 6 DAY),          9.10, '健康', NULL,                          '已记录', NULL, NULL, NOW() - INTERVAL 6 DAY);

INSERT INTO inspection_handling
(id, hive_id, root_inspection_id, source_inspection_id, previous_handling_id, round_no,
 action_type, site_note, owner_name, deadline, completed_at, status,
 recheck_result, recheck_note, recheck_inspector, recheck_at, created_at) VALUES
(1, 2, 2, 2, NULL, 1, '补饲强群', '已补充糖浆和蛋白饲料，继续观察蜂王产卵', '何强',
 DATE_ADD(CURDATE(), INTERVAL 2 DAY), NOW() - INTERVAL 1 DAY, '待复检', NULL, NULL, NULL, NULL, NOW() - INTERVAL 2 DAY),
(2, 3, 3, 3, NULL, 1, NULL, NULL, NULL, DATE_SUB(CURDATE(), INTERVAL 1 DAY), NULL, '待处置',
 NULL, NULL, NULL, NULL, NOW()),
(3, 4, 4, 4, NULL, 1, '换脾消毒', '完成换脾、蜂箱消毒和药物治疗，蜂群恢复', '陈晨',
 DATE_SUB(CURDATE(), INTERVAL 8 DAY), NOW() - INTERVAL 10 DAY, '已解除',
 '合格', '复查蜂群活动正常，未见病蜂', '周敏', NOW() - INTERVAL 7 DAY, NOW() - INTERVAL 12 DAY),
(4, 6, 7, 7, NULL, 1, NULL, NULL, NULL, DATE_ADD(CURDATE(), INTERVAL 5 DAY), NULL, '待处置',
 NULL, NULL, NULL, NULL, NOW() - INTERVAL 1 DAY),
(5, 8, 8, 8, NULL, 1, '隔离治疗', '隔离并用药治疗，清理病脾', '何强',
 DATE_SUB(CURDATE(), INTERVAL 3 DAY), NOW() - INTERVAL 4 DAY, '复检不合格',
 '不合格', '仍见病蜂和蜂螨，需继续处置', '周敏', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 9 DAY),
(6, 8, 9, 9, 5, 2, NULL, NULL, NULL, DATE_ADD(CURDATE(), INTERVAL 4 DAY), NULL, '待处置',
 NULL, NULL, NULL, NULL, NOW() - INTERVAL 2 DAY);

INSERT INTO honey_batch (hive_id, batch_no, weight_kg, status, quality_status, created_at) VALUES
(1, 'B-2026-001', 12.50, '已入库',       '已入库',       NOW()),
(2, 'B-2026-002', 9.80,  '已入库',       '已入库',       NOW()),
(3, 'B-2026-003', 5.30,  '待质检',       '待质检',       NOW()),
(4, 'B-2026-004', 3.10,  '隔离拒收',     '隔离拒收',     NOW()),
(5, 'B-2026-005', 15.00, '已入库',       '已入库',       NOW()),
(6, 'B-2026-006', 6.70,  '隔离待整改',   '隔离待整改',   NOW()),
(7, 'B-2026-007', 8.20,  '已入库',       '已入库',       NOW()),
(8, 'B-2026-008', 2.40,  '已作废',       '已作废',       NOW());

INSERT INTO quality_inspection
(id, batch_id, sample_no, round_type, moisture_percent, sensory_result, result, inspector, inspected_at, note, active, created_at) VALUES
(1, 1, 'S-2026-001', '初检', 17.50, '合格', '合格', '周敏', NOW() - INTERVAL 8 DAY, '色泽、气味正常，已放行入库', 1, NOW() - INTERVAL 8 DAY),
(2, 2, 'S-2026-002', '初检', 18.20, '合格', '合格', '周敏', NOW() - INTERVAL 7 DAY, '感官正常，封盖成熟蜜',       1, NOW() - INTERVAL 7 DAY),
(3, 5, 'S-2026-003', '初检', 16.80, '合格', '合格', '陈晨', NOW() - INTERVAL 6 DAY, '水分达标，同意放行',         1, NOW() - INTERVAL 6 DAY),
(4, 7, 'S-2026-004', '初检', 19.00, '合格', '合格', '陈晨', NOW() - INTERVAL 5 DAY, '气味清香，同意入库',         1, NOW() - INTERVAL 5 DAY),
(5, 6, 'S-2026-005', '初检', 21.20, '合格', '不合格', '周敏', NOW() - INTERVAL 2 DAY, '含水率超过 20%，需二次脱水并检查密封储存条件', 1, NOW() - INTERVAL 2 DAY),
(6, 4, 'S-2026-006', '初检', 21.50, '不合格', '不合格', '陈晨', NOW() - INTERVAL 4 DAY, '含水率超标且有轻微发酵味，立即隔离', 0, NOW() - INTERVAL 4 DAY),
(7, 4, 'S-2026-007', '复检', 20.80, '合格', '不合格', '陈晨', NOW() - INTERVAL 1 DAY, '整改后含水率仍超过 20%，拒绝入库', 1, NOW() - INTERVAL 1 DAY);

INSERT INTO quality_handling (batch_id, inspection_id, handling_type, opinion, handler, handled_at, created_at) VALUES
(6, 5, '隔离处理', '初检含水率 21.20%，批次移入隔离区，禁止入库', '周敏', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY),
(6, 5, '整改', '已降低取蜜频率，增加低温静置脱水并更换密封桶，待复检', '何强', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY),
(4, 6, '隔离处理', '初检含水率 21.50% 且感官不合格，批次隔离待整改', '陈晨', NOW() - INTERVAL 4 DAY, NOW() - INTERVAL 4 DAY),
(4, 6, '整改', '已重新过滤、通风降湿并单独密封存放，申请复检', '何强', NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY),
(4, 7, '拒绝入库', '复检含水率 20.80% 仍超过 20%，保持隔离并明确拒绝入库', '陈晨', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY);

INSERT INTO customer (name, phone, type, created_at) VALUES
('鲜丰超市',     '13800001111', '商超', NOW() - INTERVAL 40 DAY),
('李娟',         '13800002222', '个人', NOW() - INTERVAL 40 DAY),
('蜜语烘焙坊',   '13800003333', '商超', NOW() - INTERVAL 35 DAY),
('王强',         '13800004444', '个人', NOW() - INTERVAL 35 DAY),
('田园食品厂',   '13800005555', '商超', NOW() - INTERVAL 30 DAY),
('张敏',         '13800006666', '个人', NOW() - INTERVAL 30 DAY),
('陈昊',         '13912345678', '个人', NOW() - INTERVAL 25 DAY),
('甜源商行',     '13700007777', '商超', NOW() - INTERVAL 25 DAY);
