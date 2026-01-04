package com.shenzhewei.accont_book.model.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 预算请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetDTO {

    /**
     * 用户ID（从Token中获取，前端不需要传递）
     */
    private Long userId;

    /**
     * 年月 (格式: yyyy-MM)
     */
    @NotBlank(message = "年月不能为空")
    @Pattern(regexp = "^\\d{4}-\\d{2}$", message = "年月格式错误，应为 yyyy-MM")
    private String yearMonth;

    /**
     * 预算金额
     */
    @NotNull(message = "预算金额不能为空")
    @DecimalMin(value = "0.01", message = "预算金额必须大于0")
    private BigDecimal amount;

    /**
     * 分类（可选，空表示总预算）
     */
    private String category;

    /**
     * 是否AI建议
     */
    private Boolean aiSuggested;
}
