SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET character_set_client = utf8mb4;
SET character_set_connection = utf8mb4;
SET character_set_results = utf8mb4;

-- ---------------------------------------------------------------------
-- KTV 包厢运营 · 包厢 / 预订 / 消费 / 会员 / 歌曲
-- 库名 karaoke_lounge；application.yml 里 ddl-auto: none，表结构全部由本文件在 MySQL 镜像构建期导入。
-- 注意：mysql 容器是 UTC，种子数据里的时间一律写 UTC_TIMESTAMP() + INTERVAL 8 HOUR；
--      预订/消费等具体时段用北京时间字面量直接写入（serverTimezone=Asia/Shanghai）。

CREATE DATABASE IF NOT EXISTS karaoke_lounge
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE karaoke_lounge;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS consumption;
DROP TABLE IF EXISTS booking;
DROP TABLE IF EXISTS song;
DROP TABLE IF EXISTS member;
DROP TABLE IF EXISTS room;
SET FOREIGN_KEY_CHECKS = 1;

-- ---------------- 包厢 ----------------
CREATE TABLE room (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_code  VARCHAR(16)  NOT NULL UNIQUE,
    room_name  VARCHAR(64)  NOT NULL,
    capacity   INT          NOT NULL COMMENT '可容纳人数',
    min_spend  DECIMAL(10,2) NOT NULL COMMENT '最低消费（元）',
    status     VARCHAR(16)  NOT NULL COMMENT '空闲/预订/使用中'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 预订 ----------------
CREATE TABLE booking (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id    BIGINT       NOT NULL,
    member_id  BIGINT       NOT NULL,
    start_at   DATETIME     NOT NULL COMMENT '开始时间（北京时间）',
    end_at     DATETIME     NOT NULL COMMENT '结束时间（北京时间）',
    party_size INT          NOT NULL COMMENT '预订人数',
    status     VARCHAR(16)  NOT NULL COMMENT '已预订/已完成/已取消',
    CONSTRAINT fk_booking_room FOREIGN KEY (room_id) REFERENCES room (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 消费明细 ----------------
CREATE TABLE consumption (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT       NOT NULL,
    item_name  VARCHAR(64)  NOT NULL,
    amount     DECIMAL(10,2) NOT NULL,
    at         DATETIME,
    CONSTRAINT fk_consumption_booking FOREIGN KEY (booking_id) REFERENCES booking (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 会员 ----------------
CREATE TABLE member (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_no  VARCHAR(16)  NOT NULL UNIQUE,
    member_name VARCHAR(32) NOT NULL,
    phone      VARCHAR(24)  NOT NULL,
    level      VARCHAR(16)  NOT NULL COMMENT '普通/银卡/金卡/钻石',
    balance    DECIMAL(10,2) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 歌曲库 ----------------
CREATE TABLE song (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    song_code   VARCHAR(16)  NOT NULL UNIQUE,
    song_name   VARCHAR(64)  NOT NULL,
    singer      VARCHAR(32),
    language    VARCHAR(16)  COMMENT '国语/粤语/英文/日文',
    duration_sec INT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================================
-- 种子数据
-- =====================================================================

INSERT INTO room (room_code, room_name, capacity, min_spend, status) VALUES
('K01', '迷你包',   2,  188.00, '空闲'),
('K02', '小包',     4,  388.00, '预订'),
('K03', '中包',     8,  688.00, '使用中'),
('K04', '豪华中包', 10, 888.00, '空闲'),
('K05', '大包',     12, 988.00, '空闲'),
('K06', 'VIP大包',  20, 1888.00, '空闲');

INSERT INTO member (member_no, member_name, phone, level, balance) VALUES
('M001', '张伟', '13800001111', '普通', 200.00),
('M002', '李娜', '13800002222', '金卡', 1500.00),
('M003', '王芳', '13800003333', '银卡', 600.00),
('M004', '刘洋', '13800004444', '钻石', 5000.00);

INSERT INTO song (song_code, song_name, singer, language, duration_sec) VALUES
('S001', '海阔天空', 'Beyond',    '粤语', 320),
('S002', '七里香',   '周杰伦',     '国语', 299),
('S003', 'Hotel California', 'Eagles', '英文', 391),
('S004', '红',       '福山雅治',   '日文', 347),
('S005', '晴天',     '周杰伦',     '国语', 269);

INSERT INTO booking (room_id, member_id, start_at, end_at, party_size, status) VALUES
(2, 2, '2026-10-02 19:00:00', '2026-10-02 22:00:00', 6,  '已预订'),
(3, 4, '2026-10-02 20:00:00', '2026-10-02 23:30:00', 12, '使用中'),
(5, 1, '2026-10-03 18:00:00', '2026-10-03 21:00:00', 10, '已预订'),
(1, 3, '2026-10-01 14:00:00', '2026-10-01 16:00:00', 2,  '已完成');

INSERT INTO consumption (booking_id, item_name, amount, at) VALUES
(4, '欢唱套餐', 500.00, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 1 DAY),
(4, '精酿啤酒', 88.00,  UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 1 DAY),
(4, '缤纷果盘', 68.00,  UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 1 DAY);
