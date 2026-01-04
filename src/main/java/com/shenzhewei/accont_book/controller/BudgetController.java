package com.shenzhewei.accont_book.controller;

import com.shenzhewei.accont_book.common.Result;
import com.shenzhewei.accont_book.model.dto.BudgetDTO;
import com.shenzhewei.accont_book.model.dto.BudgetStatusDTO;
import com.shenzhewei.accont_book.model.dto.BudgetSuggestionDTO;
import com.shenzhewei.accont_book.model.entity.Budget;
import com.shenzhewei.accont_book.service.AIAnalysisService;
import com.shenzhewei.accont_book.service.BudgetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 预算控制器
 */
@RestController
@RequestMapping("/api/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;
    private final AIAnalysisService aiAnalysisService;

    /**
     * 设置预算（创建或更新）
     */
    @PostMapping
    public Result<Budget> setBudget(@RequestAttribute("userId") Long userId, @Valid @RequestBody BudgetDTO dto) {
        dto.setUserId(userId);
        Budget budget = budgetService.setBudget(dto);
        return Result.success(budget);
    }

    /**
     * 获取某月的预算列表
     */
    @GetMapping("/{yearMonth}")
    public Result<List<Budget>> getBudgets(@RequestAttribute("userId") Long userId, @PathVariable String yearMonth) {
        List<Budget> budgets = budgetService.getBudgets(userId, yearMonth);
        return Result.success(budgets);
    }

    /**
     * 获取预算执行状态
     */
    @GetMapping("/status/{yearMonth}")
    public Result<BudgetStatusDTO> getBudgetStatus(@RequestAttribute("userId") Long userId, @PathVariable String yearMonth) {
        BudgetStatusDTO status = budgetService.getBudgetStatus(userId, yearMonth);
        return Result.success(status);
    }

    /**
     * 获取AI预算建议
     */
    @GetMapping("/suggestion")
    public Result<BudgetSuggestionDTO> getBudgetSuggestion(@RequestAttribute("userId") Long userId,
                                                          @RequestParam(defaultValue = "") String yearMonth) {
        // 如果没传年月，使用当前月份
        if (yearMonth == null || yearMonth.isEmpty()) {
            yearMonth = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
        }
        BudgetSuggestionDTO suggestion = aiAnalysisService.suggestBudget(userId, yearMonth);
        return Result.success(suggestion);
    }

    /**
     * 删除预算
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteBudget(@PathVariable Long id) {
        budgetService.deleteBudget(id);
        return Result.success();
    }
}
