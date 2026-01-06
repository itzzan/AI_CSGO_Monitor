-- 1. 创建数据库
CREATE DATABASE IF NOT EXISTS `csgo_monitor` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

-- 2. 使用数据库
USE `csgo_monitor`;

-- 2.1 饰品基础信息表
CREATE TABLE IF NOT EXISTS `skin_item`
(
    `id`                    BIGINT              NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `skin_item_id`          BIGINT              NOT NULL DEFAULT 0 COMMENT '饰品基础信息ID（唯一）',
    `skin_market_hash_name` VARCHAR(255)        NOT NULL DEFAULT '' COMMENT '饰品市场哈希名称',
    `skin_name`             VARCHAR(100)        NOT NULL DEFAULT '' COMMENT '饰品名称',
    `skin_weapon`           VARCHAR(50)         NOT NULL DEFAULT '' COMMENT '饰品类型（手套、匕首、步枪等）',
    `skin_exterior`         VARCHAR(50)         NOT NULL DEFAULT '' COMMENT '饰品磨损等级（崭新出场、略有磨损等）',
    `skin_rarity`           VARCHAR(50)         NOT NULL DEFAULT '' COMMENT '饰品稀有度（隐秘、受限等）',
    `skin_image_url`        VARCHAR(1024)       NOT NULL DEFAULT '' COMMENT '饰品图片URL',
    `is_stattrak`           TINYINT(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '饰品是否暗金，0不是，1是',
    `is_souvenir`           TINYINT(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '饰品是否纪念品，0不是，1是',
    `version`               INT UNSIGNED        NOT NULL DEFAULT 0 COMMENT '版本号',
    `created_by`            BIGINT              NOT NULL DEFAULT 0 COMMENT '创建人',
    `created_at`            DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `changed_by`            BIGINT              NOT NULL DEFAULT 0 COMMENT '修改人',
    `changed_at`            DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `del_flag`              TINYINT(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除位：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_skin_item_id` (`skin_item_id`),
    KEY `uk_skin_market_hash_name` (`skin_market_hash_name`),
    KEY `idx_skin_name` (`skin_name`),
    KEY `idx_skin_weapon` (`skin_weapon`),
    KEY `idx_skin_exterior` (`skin_exterior`),
    KEY `idx_skin_rarity` (`skin_rarity`)
) COMMENT ='饰品基础信息表';

-- 2.2 饰品价格记录表
CREATE TABLE IF NOT EXISTS `skin_price_record`
(
    `id`            BIGINT              NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `skin_item_id`  BIGINT              NOT NULL DEFAULT 0 COMMENT '饰品基础信息ID（唯一）',
    `platform`      VARCHAR(50)         NOT NULL DEFAULT '' COMMENT '平台名称（Steam、BUFF、悠悠、C5、IGXE）',
    `current_price` DECIMAL(12, 2)      NOT NULL DEFAULT 0.00 COMMENT '饰品当前价格',
    `lowest_price`  DECIMAL(12, 2)      NOT NULL DEFAULT 0.00 COMMENT '饰品最低求购价',
    `highest_price` DECIMAL(12, 2)      NOT NULL DEFAULT 0.00 COMMENT '饰品最高出售价',
    `sell_listings` INT UNSIGNED        NOT NULL DEFAULT 0 COMMENT '在售数量',
    `volume_24h`    INT UNSIGNED        NOT NULL DEFAULT 0 COMMENT '24小时成交量',
    `change_24h`    DECIMAL(10, 4)      NOT NULl DEFAULT 0.00 COMMENT '24小时涨跌幅(%)',
    `record_time`   DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '价格记录时间',
    `version`       INT UNSIGNED        NOT NULL DEFAULT 0 COMMENT '版本号',
    `created_by`    BIGINT              NOT NULL DEFAULT 0 COMMENT '创建人',
    `created_at`    DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `changed_by`    BIGINT              NOT NULL DEFAULT 0 COMMENT '修改人',
    `changed_at`    DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `del_flag`      TINYINT(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除位：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_skin_platform_time` (`skin_market_hash_name`, `platform`, `record_time`),
    INDEX `idx_record_time` (`record_time`)
) COMMENT ='饰品价格记录表';
{
  "id": 0,
  "skinItemId": 0,
  "platform": "platform_9028f4485aab",
  "currentPrice": 0.00,
  "lowestPrice": 0.00,
  "highestPrice": 0.00,
  "volume24h": 0,
  "sellListings": 0,
  "priceDistribution": "priceDistribution_fc5962204c30",
  "recordTime": "2026-01-06 09:24:49",

}