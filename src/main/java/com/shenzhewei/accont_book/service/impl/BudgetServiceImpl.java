package com.shenzhewei.accont_book.service.impl;

import com.shenzhewei.accont_book.common.ResultCode;
import com.shenzhewei.accont_book.exception.BizException;
import com.shenzhewei.accont_book.model.dto.BudgetDTO;
import com.shenzhewei.accont_book.model.dto.BudgetStatusDTO;
import com.shenzhewei.accont_book.model.entity.Budget;
import com.shenzhewei.accont_book.model.entity.Transaction;
import com.shenzhewei.accont_book.repository.BudgetMapper;
import com.shenzhewei.accont_book.repository.TransactionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 预算服务实现
 * 只使用分类预算，总预算由分类预算之和计算得出
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetServiceImpl implements com.shenzhewei.accont_book.service.BudgetService {

    private final BudgetMapper budgetMapper;
    private final TransactionMapper transactionMapper;

    @Override
    public Budget setBudget(BudgetDTO dto) {
        log.info("设置预算: userId={}, yearMonth={}, amount={}, category={}", 
                dto.getUserId(), dto.getYearMonth(), dto.getAmount(), dto.getCategory());

        // 分类预算必须指定分类
        if (dto.getCategory() == null || dto.getCategory().trim().isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "请指定预算分类");
        }

        // 查找是否已有该分类预算
        var existing = budgetMapper.findByUserIdAndYearMonthAndCategory(
                dto.getUserId(), dto.getYearMonth(), dto.getCategory());

        Budget budget;
        if (existing.isPresent()) {
            // 更新已有预算
            budget = existing.get();
            budget.setAmount(dto.getAmount());
            budget.setAiSuggested(dto.getAiSuggested() != null && dto.getAiSuggested());
            budgetMapper.update(budget);
            log.info("预算已更新: id={}", budget.getId());
        } else {
            // 创建新预算
            budget = Budget.builder()
                    .userId(dto.getUserId())
                    .yearMonth(dto.getYearMonth())
                    .amount(dto.getAmount())
                    .category(dto.getCategory())
                    .aiSuggested(dto.getAiSuggested() != null && dto.getAiSuggested())
                    .build();
            budgetMapper.insert(budget);
            log.info("预算已创建: id={}", budget.getId());
        }

        return budget;
    }

    @Override
    public List<Budget> getBudgets(Long userId, String yearMonth) {
        // 只返回有分类的预算
        return budgetMapper.findByUserIdAndYearMonth(userId, yearMonth).stream()
                .filter(b -> b.getCategory() != null && !b.getCategory().isEmpty())
                .collect(Collectors.toList());
    }

    @Override
    public Budget getTotalBudget(Long userId, String yearMonth) {
        // 已废弃：总预算由分类预算之和计算
        return null;
    }

    @Override
    public BudgetStatusDTO getBudgetStatus(Long userId, String yearMonth) {
        log.info("获取预算状态: userId={}, yearMonth={}", userId, yearMonth);

        // 获取所有分类预算
        List<Budget> categoryBudgets = getBudgets(userId, yearMonth);
        
        if (categoryBudgets.isEmpty()) {
            return BudgetStatusDTO.builder()
                    .yearMonth(yearMonth)
                    .budgetAmount(BigDecimal.ZERO)
                    .usedAmount(BigDecimal.ZERO)
                    .remainingAmount(BigDecimal.ZERO)
                    .usagePercentage(0.0)
                    .status("no_budget")
                    .isExceeded(false)
                    .categoryDetails(new ArrayList<>())
                    .build();
        }

        // 获取当月所有支出交易
        List<Transaction> monthlyTransactions = transactionMapper.findByUserIdAndMonth(userId, yearMonth);
        
        // 按分类统计支出
        Map<String, BigDecimal> categoryExpenses = monthlyTransactions.stream()
                .filter(t -> t.getType() == Transaction.TYPE_EXPENSE)
                .collect(Collectors.groupingBy(
                        Transaction::getCategory,
                        Collectors.reducing(BigDecimal.ZERO, Transaction::getAmount, BigDecimal::add)
                ));

        // 计算总预算（所有分类预算之和）
        BigDecimal totalBudgetAmount = categoryBudgets.stream()
                .map(Budget::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 计算总支出
        BigDecimal totalUsedAmount = monthlyTransactions.stream()
                .filter(t -> t.getType() == Transaction.TYPE_EXPENSE)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 构建每个分类的状态
        List<BudgetStatusDTO.CategoryBudgetStatus> categoryDetails = new ArrayList<>();
        for (Budget budget : categoryBudgets) {
            BigDecimal catBudget = budget.getAmount();
            BigDecimal catUsed = categoryExpenses.getOrDefault(budget.getCategory(), BigDecimal.ZERO);
            BigDecimal catRemaining = catBudget.subtract(catUsed);
            
            double catPercentage = 0.0;
            if (catBudget.compareTo(BigDecimal.ZERO) > 0) {
                catPercentage = catUsed.divide(catBudget, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .doubleValue();
            }

            String catStatus = catPercentage > 100 ? "exceeded" : (catPercentage >= 80 ? "warning" : "normal");
            
            categoryDetails.add(BudgetStatusDTO.CategoryBudgetStatus.builder()
                    .id(budget.getId())
                    .category(budget.getCategory())
                    .budgetAmount(catBudget)
                    .usedAmount(catUsed)
                    .remainingAmount(catRemaining)
                    .usagePercentage(Math.round(catPercentage * 100.0) / 100.0)
                    .status(catStatus)
                    .isExceeded(catPercentage > 100)
                    .build());
        }

        // 计算总体状态
        BigDecimal totalRemaining = totalBudgetAmount.subtract(totalUsedAmount);
        double totalPercentage = 0.0;
        if (totalBudgetAmount.compareTo(BigDecimal.ZERO) > 0) {
            totalPercentage = totalUsedAmount.divide(totalBudgetAmount, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }

        String status = totalPercentage > 100 ? "exceeded" : (totalPercentage >= 80 ? "warning" : "normal");

        return BudgetStatusDTO.builder()
                .yearMonth(yearMonth)
                .budgetAmount(totalBudgetAmount)
                .usedAmount(totalUsedAmount)
                .remainingAmount(totalRemaining)
                .usagePercentage(Math.round(totalPercentage * 100.0) / 100.0)
                .status(status)
                .isExceeded(totalPercentage > 100)
                .categoryDetails(categoryDetails)
                .build();
    }

    @Override
    public void deleteBudget(Long id) {
        if (budgetMapper.findById(id).isEmpty()) {
            throw new BizException(ResultCode.NOT_FOUND, "预算不存在");
        }
        budgetMapper.deleteById(id);
        log.info("预算已删除: id={}", id);
    }
}
