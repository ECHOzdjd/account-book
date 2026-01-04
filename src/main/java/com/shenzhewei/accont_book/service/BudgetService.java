package com.shenzhewei.accont_book.service;

import com.shenzhewei.accont_book.model.dto.BudgetDTO;
import com.shenzhewei.accont_book.model.dto.BudgetStatusDTO;
import com.shenzhewei.accont_book.model.entity.Budget;

import java.util.List;

/**
 * 预算服务接口
 */
public interface BudgetService {

    /**
     * 设置预算（创建或更新）
     */
    Budget setBudget(BudgetDTO dto);

    /**
     * 获取用户某月的预算列表
     */
    List<Budget> getBudgets(Long userId, String yearMonth);

    /**
     * 获取用户某月的总预算
     */
    Budget getTotalBudget(Long userId, String yearMonth);

    /**
     * 获取预算执行状态
     */
    BudgetStatusDTO getBudgetStatus(Long userId, String yearMonth);

    /**
     * 删除预算
     */
    void deleteBudget(Long id);
}
