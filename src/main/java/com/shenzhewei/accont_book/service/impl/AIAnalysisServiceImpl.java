package com.shenzhewei.accont_book.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shenzhewei.accont_book.config.DeepSeekConfig;
import com.shenzhewei.accont_book.exception.BizException;
import com.shenzhewei.accont_book.common.ResultCode;
import com.shenzhewei.accont_book.model.dto.AIAnalysisResponse;
import com.shenzhewei.accont_book.model.dto.BudgetSuggestionDTO;
import com.shenzhewei.accont_book.model.entity.Asset;
import com.shenzhewei.accont_book.model.entity.Transaction;
import com.shenzhewei.accont_book.service.AIAnalysisService;
import com.shenzhewei.accont_book.service.AssetService;
import com.shenzhewei.accont_book.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * AI分析服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIAnalysisServiceImpl implements AIAnalysisService {

    private final DeepSeekConfig deepSeekConfig;
    private final AssetService assetService;
    private final TransactionService transactionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public AIAnalysisResponse analyzeUserData(Long userId, String type, String timeRange) {
        log.info("开始AI分析: userId={}, type={}, timeRange={}", userId, type, timeRange);

        // 获取用户数据
        List<Asset> assets = assetService.findByUserId(userId);
        List<Transaction> transactions = transactionService.listByUserId(userId);

        // 构建提示词
        String prompt = buildPrompt(assets, transactions, type, timeRange);

        // 调用DeepSeek API
        String aiResponse = callDeepSeekAPI(prompt);

        return AIAnalysisResponse.builder()
                .content(aiResponse)
                .type(type)
                .timestamp(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .build();
    }

    /**
     * 构建提示词
     */
    private String buildPrompt(List<Asset> assets, List<Transaction> transactions, String type, String timeRange) {
        StringBuilder prompt = new StringBuilder();
        
        // 计算总资产
        BigDecimal totalBalance = assets.stream()
                .map(Asset::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 计算收支情况
        BigDecimal totalIncome = transactions.stream()
                .filter(t -> t.getType() == 2)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalExpense = transactions.stream()
                .filter(t -> t.getType() == 1)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 分类统计
        Map<String, BigDecimal> categoryStats = transactions.stream()
                .filter(t -> t.getType() == 1)
                .collect(Collectors.groupingBy(
                        Transaction::getCategory,
                        Collectors.reducing(BigDecimal.ZERO, Transaction::getAmount, BigDecimal::add)
                ));

        prompt.append("我是一个个人记账助手，请根据以下数据进行分析：\n\n");
        prompt.append("【资产情况】\n");
        prompt.append("- 总资产：").append(totalBalance).append("元\n");
        prompt.append("- 资产账户数：").append(assets.size()).append("个\n");
        for (Asset asset : assets) {
            prompt.append("  * ").append(asset.getName()).append("：").append(asset.getBalance()).append("元\n");
        }

        prompt.append("\n【收支情况】\n");
        prompt.append("- 总收入：").append(totalIncome).append("元\n");
        prompt.append("- 总支出：").append(totalExpense).append("元\n");
        prompt.append("- 结余：").append(totalIncome.subtract(totalExpense)).append("元\n");
        prompt.append("- 交易记录数：").append(transactions.size()).append("笔\n");

        if (!categoryStats.isEmpty()) {
            prompt.append("\n【支出分类】\n");
            categoryStats.entrySet().stream()
                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                    .forEach(entry -> prompt.append("- ").append(entry.getKey())
                            .append("：").append(entry.getValue()).append("元\n"));
        }

        prompt.append("\n");

        // 根据分析类型添加具体要求
        switch (type) {
            case "summary":
                prompt.append("请用200字内总结财务状况：资产结构、收支平衡、主要开支。");
                break;
            case "advice":
                prompt.append("请给出3条理财建议，每条30字内，聚焦节流和资产配置。");
                break;
            case "forecast":
                prompt.append("请用150字内预测下月趋势并给建议。");
                break;
            default:
                prompt.append("请简要分析财务状况（150字内）。");
        }

        return prompt.toString();
    }

    /**
     * 根据描述分析分类
     */
    @Override
    public String analyzeCategory(String description, Integer type) {
        if (description == null || description.trim().isEmpty()) {
            return type == 1 ? "其他" : "其他收入";
        }

        log.info("AI分析分类: description={}, type={}", description, type);

        String typeDesc = type == 1 ? "支出" : "收入";
        String categories = type == 1 
            ? "餐饮、交通、购物、娱乐、居住、通讯、医疗、教育、其他"
            : "工资、奖金、理财、红包、退款、其他";

        String prompt = String.format(
            "你是一个记账分类助手。用户记了一笔%s，描述是：\"%s\"\n" +
            "请从以下分类中选择最合适的一个：%s\n" +
            "只回复分类名称，不要有任何其他内容。", 
            typeDesc, description, categories);

        try {
            String result = callDeepSeekAPIFast(prompt);
            // 清理结果，只保留分类名
            result = result.trim().replaceAll("[\"'。，]", "");
            log.info("AI分类结果: {}", result);
            return result.isEmpty() ? (type == 1 ? "其他" : "其他收入") : result;
        } catch (Exception e) {
            log.warn("AI分类失败，使用默认分类: {}", e.getMessage());
            return type == 1 ? "其他" : "其他收入";
        }
    }

    /**
     * 获取预算建议
     */
    @Override
    public BudgetSuggestionDTO suggestBudget(Long userId, String yearMonth) {
        log.info("AI预算建议: userId={}, yearMonth={}", userId, yearMonth);

        // 获取用户数据
        List<Asset> assets = assetService.findByUserId(userId);
        List<Transaction> transactions = transactionService.listByUserId(userId);

        // 计算历史月度支出
        Map<String, BigDecimal> monthlyExpenses = new HashMap<>();
        Map<String, BigDecimal> categoryExpenses = new HashMap<>();
        
        for (Transaction t : transactions) {
            if (t.getType() == 1) { // 支出
                String month = t.getTransTime().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
                monthlyExpenses.merge(month, t.getAmount(), BigDecimal::add);
                categoryExpenses.merge(t.getCategory(), t.getAmount(), BigDecimal::add);
            }
        }

        // 计算总资产
        BigDecimal totalBalance = assets.stream()
                .map(Asset::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 计算平均月支出
        BigDecimal avgMonthlyExpense = BigDecimal.ZERO;
        if (!monthlyExpenses.isEmpty()) {
            avgMonthlyExpense = monthlyExpenses.values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(monthlyExpenses.size()), 2, java.math.RoundingMode.HALF_UP);
        }

        // 构建提示词
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个专业的个人财务顾问。请根据以下信息为用户制定").append(yearMonth).append("的月度预算建议：\n\n");
        
        prompt.append("【账户总余额】").append(totalBalance).append("元\n\n");
        
        if (!monthlyExpenses.isEmpty()) {
            prompt.append("【历史月度支出】\n");
            monthlyExpenses.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByKey().reversed())
                .limit(6)
                .forEach(e -> prompt.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append("元\n"));
            prompt.append("\n平均月支出: ").append(avgMonthlyExpense).append("元\n");
        }

        if (!categoryExpenses.isEmpty()) {
            prompt.append("\n【分类支出统计】\n");
            categoryExpenses.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(8)
                .forEach(e -> prompt.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append("元\n"));
        }

        prompt.append("\n请给出以下内容（严格使用JSON格式）：\n");
        prompt.append("{\n");
        prompt.append("  \"totalSuggested\": 建议的月度总预算金额（数字）,\n");
        prompt.append("  \"categoryBudgets\": [\n");
        prompt.append("    {\"category\": \"餐饮\", \"amount\": 金额, \"percentage\": 占比},\n");
        prompt.append("    {\"category\": \"交通\", \"amount\": 金额, \"percentage\": 占比},\n");
        prompt.append("    {\"category\": \"购物\", \"amount\": 金额, \"percentage\": 占比},\n");
        prompt.append("    ... 其他相关分类\n");
        prompt.append("  ],\n");
        prompt.append("  \"reason\": \"建议理由（50字以内）\",\n");
        prompt.append("  \"tips\": [\"建议1\", \"建议2\", \"建议3\"]\n");
        prompt.append("}\n");
        prompt.append("\n注意：只返回JSON，不要其他内容。categoryBudgets至少包含3-5个主要分类。");

        try {
            String aiResponse = callDeepSeekAPI(prompt.toString());
            return parseAIResponse(aiResponse, totalBalance, avgMonthlyExpense);
        } catch (Exception e) {
            log.warn("AI预算建议获取失败，使用降级方案: {}", e.getMessage());
            return buildFallbackSuggestion(totalBalance, avgMonthlyExpense, categoryExpenses);
        }
    }

    /**
     * 解析AI响应为结构化DTO
     */
    private BudgetSuggestionDTO parseAIResponse(String aiResponse, BigDecimal totalBalance, BigDecimal avgMonthlyExpense) {
        try {
            // 提取JSON部分
            String jsonContent = aiResponse;
            int jsonStart = aiResponse.indexOf("{");
            int jsonEnd = aiResponse.lastIndexOf("}");
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                jsonContent = aiResponse.substring(jsonStart, jsonEnd + 1);
            }

            JsonNode root = objectMapper.readTree(jsonContent);
            
            // 解析总预算
            BigDecimal totalSuggested = BigDecimal.valueOf(root.path("totalSuggested").asDouble(0));
            if (totalSuggested.compareTo(BigDecimal.ZERO) <= 0) {
                totalSuggested = avgMonthlyExpense.compareTo(BigDecimal.ZERO) > 0 
                    ? avgMonthlyExpense : totalBalance.multiply(BigDecimal.valueOf(0.3));
            }

            // 解析分类预算
            List<BudgetSuggestionDTO.CategoryBudget> categoryBudgets = new java.util.ArrayList<>();
            JsonNode categoriesNode = root.path("categoryBudgets");
            if (categoriesNode.isArray()) {
                for (JsonNode cat : categoriesNode) {
                    categoryBudgets.add(BudgetSuggestionDTO.CategoryBudget.builder()
                            .category(cat.path("category").asText("其他"))
                            .amount(BigDecimal.valueOf(cat.path("amount").asDouble(0)))
                            .percentage(cat.path("percentage").asDouble(0))
                            .build());
                }
            }

            // 解析理由
            String reason = root.path("reason").asText("根据您的历史消费习惯和资产状况制定的预算建议");

            // 解析小贴士
            List<String> tips = new java.util.ArrayList<>();
            JsonNode tipsNode = root.path("tips");
            if (tipsNode.isArray()) {
                for (JsonNode tip : tipsNode) {
                    tips.add(tip.asText());
                }
            }
            if (tips.isEmpty()) {
                tips = List.of("记录每笔支出以建立消费习惯", "优先分配必要开支", "预留应急储备金");
            }

            return BudgetSuggestionDTO.builder()
                    .totalSuggested(totalSuggested)
                    .categoryBudgets(categoryBudgets)
                    .reason(reason)
                    .tips(tips)
                    .timestamp(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                    .isFallback(false)
                    .build();
        } catch (Exception e) {
            log.warn("解析AI响应失败: {}", e.getMessage());
            throw new RuntimeException("解析AI响应失败", e);
        }
    }

    /**
     * 构建降级预算建议（AI调用失败时）
     */
    private BudgetSuggestionDTO buildFallbackSuggestion(BigDecimal totalBalance, BigDecimal avgMonthlyExpense, 
                                                         Map<String, BigDecimal> categoryExpenses) {
        // 计算建议总预算
        BigDecimal totalSuggested;
        String reason;
        
        if (avgMonthlyExpense.compareTo(BigDecimal.ZERO) > 0) {
            totalSuggested = avgMonthlyExpense.multiply(BigDecimal.valueOf(1.1))
                    .setScale(0, java.math.RoundingMode.CEILING);
            reason = "基于历史月均支出并预留10%弹性空间";
        } else {
            totalSuggested = totalBalance.multiply(BigDecimal.valueOf(0.3))
                    .setScale(0, java.math.RoundingMode.CEILING);
            reason = "历史数据不足，建议以总余额的30%作为初始预算";
        }

        // 构建分类预算
        List<BudgetSuggestionDTO.CategoryBudget> categoryBudgets = new java.util.ArrayList<>();
        
        if (!categoryExpenses.isEmpty()) {
            // 根据历史数据分配
            BigDecimal totalCatExpense = categoryExpenses.values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            categoryExpenses.entrySet().stream()
                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                    .limit(5)
                    .forEach(e -> {
                        double pct = totalCatExpense.compareTo(BigDecimal.ZERO) > 0 
                            ? e.getValue().divide(totalCatExpense, 4, java.math.RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100)).doubleValue()
                            : 20.0;
                        categoryBudgets.add(BudgetSuggestionDTO.CategoryBudget.builder()
                                .category(e.getKey())
                                .amount(totalSuggested.multiply(BigDecimal.valueOf(pct / 100))
                                        .setScale(0, java.math.RoundingMode.HALF_UP))
                                .percentage(Math.round(pct * 10) / 10.0)
                                .build());
                    });
        } else {
            // 默认分类
            categoryBudgets.add(BudgetSuggestionDTO.CategoryBudget.builder()
                    .category("餐饮").amount(totalSuggested.multiply(BigDecimal.valueOf(0.35))).percentage(35.0).build());
            categoryBudgets.add(BudgetSuggestionDTO.CategoryBudget.builder()
                    .category("交通").amount(totalSuggested.multiply(BigDecimal.valueOf(0.15))).percentage(15.0).build());
            categoryBudgets.add(BudgetSuggestionDTO.CategoryBudget.builder()
                    .category("购物").amount(totalSuggested.multiply(BigDecimal.valueOf(0.20))).percentage(20.0).build());
            categoryBudgets.add(BudgetSuggestionDTO.CategoryBudget.builder()
                    .category("娱乐").amount(totalSuggested.multiply(BigDecimal.valueOf(0.15))).percentage(15.0).build());
            categoryBudgets.add(BudgetSuggestionDTO.CategoryBudget.builder()
                    .category("其他").amount(totalSuggested.multiply(BigDecimal.valueOf(0.15))).percentage(15.0).build());
        }

        return BudgetSuggestionDTO.builder()
                .totalSuggested(totalSuggested)
                .categoryBudgets(categoryBudgets)
                .reason(reason)
                .tips(List.of("记录每笔支出以建立消费习惯基线", "优先分配必要开支如食宿交通", "设置应急储备金避免超支"))
                .timestamp(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .isFallback(true)
                .build();
    }

    /**
     * 调用DeepSeek API
     */
    private String callDeepSeekAPI(String prompt) {
        return callDeepSeekAPIInternal(prompt, 500, 0.7);
    }

    /**
     * 快速调用DeepSeek API（用于分类等简单任务）
     */
    private String callDeepSeekAPIFast(String prompt) {
        return callDeepSeekAPIInternal(prompt, 50, 0.3);
    }

    /**
     * 内部调用DeepSeek API方法
     */
    private String callDeepSeekAPIInternal(String prompt, int maxTokens, double temperature) {
        try {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(deepSeekConfig.getTimeout(), TimeUnit.MILLISECONDS)
                    .readTimeout(deepSeekConfig.getTimeout(), TimeUnit.MILLISECONDS)
                    .writeTimeout(deepSeekConfig.getTimeout(), TimeUnit.MILLISECONDS)
                    .build();

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", deepSeekConfig.getModel());
            
            Map<String, String> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", prompt);
            requestBody.put("messages", List.of(message));
            
            requestBody.put("temperature", temperature);
            requestBody.put("max_tokens", maxTokens);

            String jsonBody = objectMapper.writeValueAsString(requestBody);

            Request request = new Request.Builder()
                    .url(deepSeekConfig.getBaseUrl() + "/chat/completions")
                    .addHeader("Authorization", "Bearer " + deepSeekConfig.getKey())
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    log.error("DeepSeek API调用失败: code={}, message={}", response.code(), errorBody);
                    
                    String errorMessage;
                    switch (response.code()) {
                        case 401:
                            errorMessage = "AI服务认证失败，请检查API Key配置";
                            break;
                        case 402:
                            errorMessage = "AI服务余额不足，请充值后重试";
                            break;
                        case 429:
                            errorMessage = "AI服务请求过于频繁，请稍后重试";
                            break;
                        case 500:
                        case 503:
                            errorMessage = "AI服务暂时不可用，请稍后重试";
                            break;
                        default:
                            errorMessage = "AI分析服务暂时不可用";
                    }
                    throw new BizException(ResultCode.SYSTEM_ERROR, errorMessage);
                }

                String responseBody = response.body().string();
                log.info("DeepSeek API响应成功，响应长度: {}", responseBody.length());
                
                JsonNode jsonNode = objectMapper.readTree(responseBody);
                
                return jsonNode.path("choices")
                        .get(0)
                        .path("message")
                        .path("content")
                        .asText();
            }
        } catch (IOException e) {
            log.error("调用DeepSeek API异常: {}", e.getMessage(), e);
            if (e.getMessage() != null && e.getMessage().contains("timeout")) {
                throw new BizException(ResultCode.SYSTEM_ERROR, "AI分析请求超时，请稍后重试");
            }
            throw new BizException(ResultCode.SYSTEM_ERROR, "AI分析服务异常: " + e.getMessage());
        }
    }
}

