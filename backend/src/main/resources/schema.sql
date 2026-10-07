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
    created_at DATETIME COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 巡检 ----------------
CREATE TABLE inspection (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    hive_id     BIGINT NOT NULL COMMENT '蜂箱 id',
    inspect_date DATE NOT NULL COMMENT '巡检日期',
    honey_kg    DECIMAL(8,2) NOT NULL COMMENT '产蜜 kg',
    bee_health  VARCHAR(16) COMMENT '健康 / 一般 / 病态',
    note        VARCHAR(255) COMMENT '备注',
    status      VARCHAR(16) COMMENT '已记录 / 已复检',
    created_at  DATETIME COMMENT '创建时间'
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

INSERT INTO hive (code, location, status, created_at) VALUES
('H-01', '南侧坡地',   '正常', NOW() - INTERVAL 60 DAY),
('H-02', '东侧果园',   '正常', NOW() - INTERVAL 60 DAY),
('H-03', '北侧林缘',   '待检', NOW() - INTERVAL 60 DAY),
('H-04', '西侧花田',   '患病', NOW() - INTERVAL 60 DAY),
('H-05', '南侧坡地',   '正常', NOW() - INTERVAL 55 DAY),
('H-06', '中央平台',   '待检', NOW() - INTERVAL 55 DAY),
('H-07', '东侧果园',   '正常', NOW() - INTERVAL 50 DAY),
('H-08', '北侧林缘',   '患病', NOW() - INTERVAL 50 DAY);

INSERT INTO inspection (hive_id, inspect_date, honey_kg, bee_health, note, status, created_at) VALUES
(1, CURDATE(),         8.50, '健康', NULL,                 '已记录', NOW()),
(2, DATE_SUB(CURDATE(), INTERVAL 1 DAY),     6.20, '一般', '偶有分蜂迹象',        '已记录', NOW()),
(3, CURDATE(),         0.00, '病态', '发现幼虫病疑似',      '已记录', NOW()),
(4, DATE_SUB(CURDATE(), INTERVAL 2 DAY),     3.10, '病态', '已隔离治疗',          '已复检', NOW()),
(5, DATE_SUB(CURDATE(), INTERVAL 3 DAY),     7.80, '健康', NULL,                 '已记录', NOW()),
(6, CURDATE(),         5.00, '一般', '蜂王偏老',            '已复检', NOW()),
(1, DATE_SUB(CURDATE(), INTERVAL 5 DAY),     9.10, '健康', NULL,                 '已复检', NOW()),
(2, DATE_SUB(CURDATE(), INTERVAL 6 DAY),     4.40, '健康', NULL,                 '已记录', NOW());

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
