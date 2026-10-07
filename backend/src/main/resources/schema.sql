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
DROP TABLE IF EXISTS abnormal_recheck;
DROP TABLE IF EXISTS abnormal_handling;
DROP TABLE IF EXISTS quality_handling;
DROP TABLE IF EXISTS quality_inspection;
DROP TABLE IF EXISTS inspection;
DROP TABLE IF EXISTS honey_batch;
DROP TABLE IF EXISTS hive;
DROP TABLE IF EXISTS customer;
SET FOREIGN_KEY_CHECKS = 1;

-- ---------------- 蜂箱 ----------------
-- deleted=1 为软删除：删除/编辑蜂箱都保留关联巡检与处置历史；
-- active_code 为生成列，仅未删除蜂箱占用箱号唯一索引，箱号删除后可重新使用。
CREATE TABLE hive (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(32) NOT NULL COMMENT '箱号 H-01',
    location    VARCHAR(64) COMMENT '摆放点',
    status      VARCHAR(16) COMMENT '正常 / 待检 / 患病',
    deleted     TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已删除（软删保留历史）',
    active_code VARCHAR(32) GENERATED ALWAYS AS (IF(deleted = 0, code, NULL)) VIRTUAL COMMENT '未删除箱号唯一约束辅助列',
    created_at  DATETIME COMMENT '创建时间',
    UNIQUE KEY uk_hive_active_code (active_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 巡检 ----------------
-- status：健康巡检为「已记录」；异常（一般/病态）巡检进入处置链：
-- 待处置 → 待复检 → 已解除；复检不合格回退为新一轮「待处置」。
-- (hive_id, inspect_date) 唯一：月历按蜂箱+日期唯一，跨月查看不会重复生成处置任务。
CREATE TABLE inspection (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    hive_id      BIGINT NOT NULL COMMENT '蜂箱 id',
    inspect_date DATE NOT NULL COMMENT '巡检日期',
    honey_kg     DECIMAL(8,2) NOT NULL COMMENT '产蜜 kg',
    bee_health   VARCHAR(16) COMMENT '健康 / 一般 / 病态',
    note         VARCHAR(255) COMMENT '备注 / 现场异常描述',
    status       VARCHAR(16) COMMENT '已记录 / 待处置 / 待复检 / 已解除',
    created_at   DATETIME COMMENT '创建时间',
    UNIQUE KEY uk_inspection_hive_date (hive_id, inspect_date),
    INDEX idx_inspection_date (inspect_date),
    INDEX idx_inspection_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 异常处置 ----------------
-- 每条异常巡检至少一个处置项；复检不合格时旧处置置为「复检不合格」并保留，
-- 另起一轮新的待处置项（round_no 递增、source_handling_id 指向上轮、截止日期顺延）。
-- active_inspection_id 生成列 + 唯一索引：同一巡检同时只有一条生效处置，
-- 跨月重复查看/并发登记都不会产生第二条待办。
CREATE TABLE abnormal_handling (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    inspection_id         BIGINT NOT NULL COMMENT '异常巡检 id',
    hive_id               BIGINT NOT NULL COMMENT '蜂箱 id（冗余便于按箱预警）',
    round_no              INT NOT NULL DEFAULT 1 COMMENT '处置轮次，复检不合格顺延 +1',
    source_handling_id    BIGINT COMMENT '上一轮处置 id（顺延链路）',
    measure               VARCHAR(255) COMMENT '处置措施（处置说明）',
    handler               VARCHAR(32) COMMENT '负责人',
    due_date              DATE COMMENT '处置截止日期',
    site_note             VARCHAR(500) COMMENT '现场说明',
    status                VARCHAR(16) NOT NULL COMMENT '待处置 / 待复检 / 复检不合格 / 已解除',
    registered_at         DATETIME COMMENT '处置信息登记时间',
    completed_at          DATETIME COMMENT '处置完成时间',
    active                TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否当前生效处置',
    active_inspection_id  BIGINT GENERATED ALWAYS AS (IF(active = 1, inspection_id, NULL)) VIRTUAL COMMENT '生效处置唯一约束辅助列',
    created_at            DATETIME COMMENT '创建时间',
    UNIQUE KEY uk_abnormal_active_inspection (active_inspection_id),
    INDEX idx_abnormal_handling_hive (hive_id, active),
    INDEX idx_abnormal_handling_status (status),
    INDEX idx_abnormal_handling_due (due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 异常处置复检 ----------------
-- 一条处置完成后最多一次复检（handling_id 唯一）：合格解除提醒，
-- 不合格保留原异常并由处置表重新生成待处置项。
CREATE TABLE abnormal_recheck (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    handling_id   BIGINT NOT NULL COMMENT '对应处置项 id',
    inspection_id BIGINT NOT NULL COMMENT '异常巡检 id',
    hive_id       BIGINT NOT NULL COMMENT '蜂箱 id',
    result        VARCHAR(16) NOT NULL COMMENT '复检结论：合格 / 不合格',
    inspector     VARCHAR(32) NOT NULL COMMENT '复检人',
    recheck_date  DATE NOT NULL COMMENT '复检日期',
    note          VARCHAR(500) COMMENT '复检说明',
    created_at    DATETIME COMMENT '创建时间',
    UNIQUE KEY uk_recheck_handling (handling_id),
    INDEX idx_recheck_inspection (inspection_id)
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

INSERT INTO hive (id, code, location, status, deleted, created_at) VALUES
(1, 'H-01', '南侧坡地',   '正常', 0, NOW() - INTERVAL 60 DAY),
(2, 'H-02', '东侧果园',   '正常', 0, NOW() - INTERVAL 60 DAY),
(3, 'H-03', '北侧林缘',   '患病', 0, NOW() - INTERVAL 60 DAY),
(4, 'H-04', '西侧花田',   '患病', 0, NOW() - INTERVAL 60 DAY),
(5, 'H-05', '南侧坡地',   '正常', 0, NOW() - INTERVAL 55 DAY),
(6, 'H-06', '中央平台',   '待检', 0, NOW() - INTERVAL 55 DAY),
(7, 'H-07', '东侧果园',   '正常', 0, NOW() - INTERVAL 50 DAY),
(8, 'H-08', '北侧林缘',   '正常', 0, NOW() - INTERVAL 50 DAY);

-- 巡检种子覆盖：健康已记录 / 待处置(逾期) / 待处置 / 待复检 / 复检不合格后新一轮待处置(连续异常) / 已解除
INSERT INTO inspection (id, hive_id, inspect_date, honey_kg, bee_health, note, status, created_at) VALUES
(1, 1, CURDATE(),                                       8.50, '健康', NULL,                 '已记录', NOW()),
(2, 2, DATE_SUB(CURDATE(), INTERVAL 1 DAY),             6.20, '一般', '偶有分蜂迹象',        '待复检', NOW()),
(3, 3, CURDATE(),                                       0.00, '病态', '发现幼虫病疑似',      '待处置', NOW()),
(4, 4, DATE_SUB(CURDATE(), INTERVAL 2 DAY),             3.10, '病态', '成蜂大量死亡',        '待处置', NOW()),
(5, 5, DATE_SUB(CURDATE(), INTERVAL 3 DAY),             7.80, '健康', NULL,                 '已记录', NOW()),
(6, 6, DATE_SUB(CURDATE(), INTERVAL 6 DAY),             5.00, '病态', '蜂王偏老且产卵异常',  '待处置', NOW()),
(7, 1, DATE_SUB(CURDATE(), INTERVAL 5 DAY),             9.10, '健康', NULL,                 '已记录', NOW()),
(8, 2, DATE_SUB(CURDATE(), INTERVAL 6 DAY),             4.40, '健康', NULL,                 '已记录', NOW()),
(9, 8, DATE_SUB(CURDATE(), INTERVAL 12 DAY),            3.60, '一般', '封盖蜜偏稀，需观察',  '已解除', NOW() - INTERVAL 12 DAY);

INSERT INTO abnormal_handling
(id, inspection_id, hive_id, round_no, source_handling_id, measure, handler, due_date, site_note, status, registered_at, completed_at, active, created_at) VALUES
(1, 2, 2, 1, NULL, '加隔王板控制分蜂热，补充饲喂', '何强', DATE_ADD(CURDATE(), INTERVAL 2 DAY), '巢门拥挤，午后挂须',
 '待复检', NOW() - INTERVAL 1 DAY, NOW(), 1, NOW() - INTERVAL 1 DAY),
(2, 3, 3, 1, NULL, NULL, NULL, NULL, NULL,
 '待处置', NULL, NULL, 1, NOW()),
(3, 4, 4, 1, NULL, '隔离病群，按剂量施药治疗', '陈晨', DATE_SUB(CURDATE(), INTERVAL 1 DAY), '箱底见死蜂，蜂量下降约三成',
 '待处置', NOW() - INTERVAL 2 DAY, NULL, 1, NOW() - INTERVAL 2 DAY),
(4, 6, 6, 1, NULL, '更换蜂王并合并弱群', '何强', DATE_SUB(CURDATE(), INTERVAL 3 DAY), '子脾花子，雄蜂房偏多',
 '复检不合格', NOW() - INTERVAL 6 DAY, NOW() - INTERVAL 4 DAY, 0, NOW() - INTERVAL 6 DAY),
(5, 6, 6, 2, 4, '二次换王，全场消毒并补饲', '周敏', DATE_ADD(CURDATE(), INTERVAL 1 DAY), '首轮换王未成功，仍见花子',
 '待处置', NOW() - INTERVAL 2 DAY, NULL, 1, NOW() - INTERVAL 2 DAY),
(6, 9, 8, 1, NULL, '收紧巢脾、保持通风并复查储蜜', '陈晨', DATE_SUB(CURDATE(), INTERVAL 8 DAY), '隔板外蜜圈偏薄',
 '已解除', NOW() - INTERVAL 11 DAY, NOW() - INTERVAL 10 DAY, 0, NOW() - INTERVAL 11 DAY);

INSERT INTO abnormal_recheck (handling_id, inspection_id, hive_id, result, inspector, recheck_date, note, created_at) VALUES
(4, 6, 6, '不合格', '周敏', DATE_SUB(CURDATE(), INTERVAL 2 DAY), '新王未开产，子脾仍异常，原异常保留并顺延处置期限', NOW() - INTERVAL 2 DAY),
(6, 9, 8, '合格',   '陈晨', DATE_SUB(CURDATE(), INTERVAL 9 DAY), '蜂群恢复正常，风险解除', NOW() - INTERVAL 9 DAY);

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
