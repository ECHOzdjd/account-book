package com.shenzhewei.accont_book.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI 预算建议响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetSuggestionDTO {

    /**
     * 建议的总预算
     */
    private BigDecimal totalSuggested;

    /**
     * 分类预算建议列表
     */
    private List<CategoryBudget> categoryBudgets;

    /**
     * 建议理由
     */
    private String reason;

    /**
     * 节省开支的小贴士
     */
    private List<String> tips;

    /**
     * 生成时间
     */
    private String timestamp;

    /**
     * 是否为降级建议（AI调用失败时的默认建议）
     */
    private Boolean isFallback;

    /**
     * 分类预算项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryBudget {
        /**
         * 分类名称
         */
        private String category;

        /**
         * 建议金额
         */
        private BigDecimal amount;

        /**
         * 占总预算百分比
         */
        private Double percentage;
    }
}
