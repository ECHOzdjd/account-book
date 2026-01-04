package com.shenzhewei.accont_book.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 预算状态响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetStatusDTO {

    /**
     * 年月
     */
    private String yearMonth;

    /**
     * 预算金额（分类预算之和）
     */
    private BigDecimal budgetAmount;

    /**
     * 已使用金额（当月支出）
     */
    private BigDecimal usedAmount;

    /**
     * 剩余金额
     */
    private BigDecimal remainingAmount;

    /**
     * 使用百分比 (0-100)
     */
    private Double usagePercentage;

    /**
     * 状态: normal(正常), warning(接近预算>80%), exceeded(超预算), no_budget(未设置)
     */
    private String status;

    /**
     * 是否超预算
     */
    private Boolean isExceeded;

    /**
     * 分类预算详情列表
     */
    private List<CategoryBudgetStatus> categoryDetails;

    /**
     * 分类预算状态
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryBudgetStatus {
        private Long id;
        private String category;
        private BigDecimal budgetAmount;
        private BigDecimal usedAmount;
        private BigDecimal remainingAmount;
        private Double usagePercentage;
        private String status;
        private Boolean isExceeded;
    }
}
