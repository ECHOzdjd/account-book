-- Migration script to add new fields to existing database
-- Run this if you have existing data in the database

-- Add description field to tb_transaction if it doesn't exist
ALTER TABLE tb_transaction ADD COLUMN IF NOT EXISTS description VARCHAR(500) COMMENT '详情描述';

-- Create tb_budget table if it doesn't exist
CREATE TABLE IF NOT EXISTS tb_budget (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    year_month VARCHAR(7) NOT NULL COMMENT '年月: 2026-01',
    amount DECIMAL(15, 2) NOT NULL COMMENT '预算金额',
    category VARCHAR(50) COMMENT '分类（可选，空表示总预算）',
    ai_suggested BOOLEAN DEFAULT FALSE COMMENT '是否AI建议',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_user_month_category (user_id, year_month, category),
    INDEX idx_user_id (user_id),
    INDEX idx_year_month (year_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预算表';
