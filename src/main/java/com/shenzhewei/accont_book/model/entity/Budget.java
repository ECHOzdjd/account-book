package com.shenzhewei.accont_book.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 预算实体
 * 对应表: tb_budget
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Budget {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 年月 (如: 2026-01)
     */
    private String yearMonth;

    /**
     * 预算金额
     */
    private BigDecimal amount;

    /**
     * 分类（可选，空表示总预算）
     */
    private String category;

    /**
     * 是否AI建议
     */
    private Boolean aiSuggested;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
