package com.shenzhewei.accont_book.service;

import com.shenzhewei.accont_book.model.dto.AIAnalysisResponse;
import com.shenzhewei.accont_book.model.dto.BudgetSuggestionDTO;

/**
 * AI分析服务接口
 */
public interface AIAnalysisService {

    /**
     * 分析用户账本数据
     *
     * @param userId 用户ID
     * @param type 分析类型
     * @param timeRange 时间范围
     * @return AI分析结果
     */
    AIAnalysisResponse analyzeUserData(Long userId, String type, String timeRange);

    /**
     * 根据描述分析分类
     *
     * @param description 交易详情描述
     * @param type 交易类型 (1-支出, 2-收入)
     * @return 推荐的分类名称
     */
    String analyzeCategory(String description, Integer type);

    /**
     * 获取预算建议
     *
     * @param userId 用户ID
     * @param yearMonth 年月 (如: 2026-01)
     * @return 结构化的预算建议（包含分类预算、理由和小贴士）
     */
    BudgetSuggestionDTO suggestBudget(Long userId, String yearMonth);
}

