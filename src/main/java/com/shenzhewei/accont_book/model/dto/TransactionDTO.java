package com.shenzhewei.accont_book.model.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 记账请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionDTO {

    /**
     * 用户ID（从Token中获取，前端不需要传递）
     */
    private Long userId;

    /**
     * 资产ID
     */
    @NotNull(message = "资产ID不能为空")
    private Long assetId;

    /**
     * 金额
     */
    @NotNull(message = "金额不能为空")
    @DecimalMin(value = "0.01", message = "金额必须大于0")
    private BigDecimal amount;

    /**
     * 类型：1-支出，2-收入
     */
    @NotNull(message = "类型不能为空")
    private Integer type;

    /**
     * 分类（可选，如不提供则由AI根据description自动分析）
     */
    private String category;

    /**
     * 详情描述（AI将根据此内容分析分类）
     */
    private String description;

    /**
     * 交易时间（不传则使用当前时间）
     */
    private LocalDateTime transTime;
}

