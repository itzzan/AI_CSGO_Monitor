-- 1. 创建数据库
CREATE DATABASE IF NOT EXISTS `csgo_monitor` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

-- 2. 使用数据库
USE `csgo_monitor`;

-- 2.1 饰品基础信息表
USE `csgo_monitor`;

CREATE TABLE IF NOT EXISTS `skin_item`
(
    `id`                    BIGINT              NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `skin_item_id`          BIGINT              NOT NULL DEFAULT 0 COMMENT '饰品基础信息ID',

    -- 核心识别信息
    `skin_market_hash_name` VARCHAR(255)        NOT NULL DEFAULT '' COMMENT '饰品英文市场全名 (唯一标识)',
    `skin_name`             VARCHAR(255)        NOT NULL DEFAULT '' COMMENT '饰品中文全名',

    -- 分类体系 (为了筛选和分析)
    `skin_category`         VARCHAR(50)         NOT NULL DEFAULT '' COMMENT '饰品大类（Rifle, Knife, Sticker, Container）',
    `skin_weapon`           VARCHAR(100)        NOT NULL DEFAULT '' COMMENT '饰品具体类型（AK-47, Karambit, Music Kit）',
    `skin_pattern`          VARCHAR(100)        NOT NULL DEFAULT '' COMMENT '英文皮肤/图案名（Redline, Doppler, Vanilla）',

    -- 属性信息 (注意：部分物品无磨损或无稀有度，建议允许 NULL)
    `skin_exterior`         VARCHAR(50)         NOT NULL DEFAULT '' COMMENT '磨损等级（Factory New...），印花/音乐盒该字段为空',

    -- 资源
    `skin_image_url`        VARCHAR(1024)       NOT NULL DEFAULT '' COMMENT '饰品图片URL',

    -- 标记位 (Boolean)
    `is_stattrak`           TINYINT(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否暗金/计数器',
    `is_souvenir`           TINYINT(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否纪念品',
    `is_star`               TINYINT(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否带星标(★)，通常为刀/手套',

    -- 审计字段
    `version`               INT UNSIGNED        NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `created_by`            BIGINT              NOT NULL DEFAULT 0 COMMENT '创建人',
    `created_at`            DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `changed_by`            BIGINT              NOT NULL DEFAULT 0 COMMENT '修改人',
    `changed_at`            DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `del_flag`              TINYINT(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除位',

    PRIMARY KEY (`id`),
    -- 唯一索引（饰品英文市场全名）
    UNIQUE KEY `uk_skin_market_hash_name` (`skin_market_hash_name`),
    KEY `idx_skin_item_id` (`skin_item_id`),
    KEY `idx_skin_category` (`skin_category`),
    KEY `idx_skin_weapon` (`skin_weapon`),
    KEY `idx_skin_pattern` (`skin_pattern`),
    KEY `idx_skin_name` (`skin_name`),
    KEY `idx_skin_exterior` (`skin_exterior`)
) COMMENT ='饰品基础信息表';


-- 2.2 饰品价格历史表 (核心时序表)
CREATE TABLE IF NOT EXISTS `skin_price_history`
(
    `id`              BIGINT              NOT NULL AUTO_INCREMENT,
    `skin_id`         BIGINT              NOT NULL DEFAULT 0 COMMENT '对应 skin_item 表的 id',
    `platform`        VARCHAR(20)         NOT NULL DEFAULT '' COMMENT '平台：STEAM, BUFF, IGXE',

    -- 核心价格数据
    `price`           DECIMAL(10, 2)      NOT NULL COMMENT '当前最低售价 (CNY)',
    `buy_order_price` DECIMAL(10, 2)               DEFAULT NULL COMMENT '最高求购价 (CNY)',
    `volume`          INT                          DEFAULT NULL COMMENT '当前在售数量/24h销量',

    `capture_time`    DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '抓取时间',

    -- 审计字段
    `version`         INT UNSIGNED        NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `created_by`      BIGINT              NOT NULL DEFAULT 0 COMMENT '创建人',
    `created_at`      DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `changed_by`      BIGINT              NOT NULL DEFAULT 0 COMMENT '修改人',
    `changed_at`      DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `del_flag`        TINYINT(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除位',

    PRIMARY KEY (`id`),
    -- 联合索引：查询某饰品在某平台的历史走势
    KEY `idx_skin_platform_time` (`skin_id`, `platform`, `created_at`)
) COMMENT ='饰品价格历史表';