package com.shenzhewei.accont_book.service.impl;

import com.shenzhewei.accont_book.common.ResultCode;
import com.shenzhewei.accont_book.exception.BizException;
import com.shenzhewei.accont_book.model.dto.TransactionDTO;
import com.shenzhewei.accont_book.model.entity.Transaction;
import com.shenzhewei.accont_book.repository.TransactionMapper;
import com.shenzhewei.accont_book.service.AIAnalysisService;
import com.shenzhewei.accont_book.service.AssetService;
import com.shenzhewei.accont_book.service.TransactionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 流水服务实现
 */
@Slf4j
@Service
public class TransactionServiceImpl implements TransactionService {

    private final TransactionMapper transactionMapper;
    private final AssetService assetService;
    private final AIAnalysisService aiAnalysisService;

    public TransactionServiceImpl(TransactionMapper transactionMapper, 
                                   AssetService assetService,
                                   @Lazy AIAnalysisService aiAnalysisService) {
        this.transactionMapper = transactionMapper;
        this.assetService = assetService;
        this.aiAnalysisService = aiAnalysisService;
    }

    /**
     * 新增记账
     * 核心逻辑：先插入流水记录，再更新资产余额
     * 如果没有提供分类但有描述，则使用AI自动分析分类
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Transaction addTransaction(TransactionDTO dto) {
        // 1. 校验交易类型
        if (dto.getType() != Transaction.TYPE_EXPENSE && dto.getType() != Transaction.TYPE_INCOME) {
            throw new BizException(ResultCode.PARAM_ERROR, "交易类型无效，1-支出，2-收入");
        }

        // 2. 校验资产是否存在
        assetService.findById(dto.getAssetId())
                .orElseThrow(() -> new BizException(ResultCode.NOT_FOUND, "资产账户不存在"));

        // 3. AI自动分类：如果没有提供分类但有描述，使用AI分析
        String category = dto.getCategory();
        if ((category == null || category.trim().isEmpty()) && dto.getDescription() != null && !dto.getDescription().trim().isEmpty()) {
            log.info("使用AI自动分类，描述: {}", dto.getDescription());
            category = aiAnalysisService.analyzeCategory(dto.getDescription(), dto.getType());
            log.info("AI分类结果: {}", category);
        } else if (category == null || category.trim().isEmpty()) {
            // 既没有分类也没有描述，使用默认分类
            category = dto.getType() == Transaction.TYPE_EXPENSE ? "其他" : "其他收入";
        }

        // 4. 构建流水实体
        Transaction transaction = Transaction.builder()
                .userId(dto.getUserId())
                .assetId(dto.getAssetId())
                .amount(dto.getAmount())
                .type(dto.getType())
                .category(category)
                .description(dto.getDescription())
                .transTime(dto.getTransTime() != null ? dto.getTransTime() : LocalDateTime.now())
                .build();

        // 5. 插入流水记录
        transactionMapper.insert(transaction);
        log.info("流水记录插入成功: id={}", transaction.getId());

        // 6. 计算余额变动金额
        BigDecimal balanceChange = calculateBalanceChange(dto.getAmount(), dto.getType());

        // 7. 更新资产余额（使用乐观锁）
        assetService.updateBalance(dto.getAssetId(), balanceChange);

        log.info("记账成功: transactionId={}, assetId={}, type={}, amount={}, category={}", 
                transaction.getId(), dto.getAssetId(), dto.getType(), dto.getAmount(), category);

        return transaction;
    }

    @Override
    public List<Transaction> listByUserId(Long userId) {
        return transactionMapper.findByUserId(userId);
    }

    @Override
    public List<Transaction> listByAssetId(Long assetId) {
        return transactionMapper.findByAssetId(assetId);
    }

    /**
     * 删除流水并回滚余额
     * 核心逻辑：反向操作余额（删支出=加余额，删收入=减余额）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTransaction(Long id) {
        // 1. 查询该笔流水
        Transaction transaction = transactionMapper.findById(id)
                .orElseThrow(() -> new BizException(ResultCode.NOT_FOUND, "流水记录不存在"));

        // 2. 计算反向余额变动（回滚）
        // 删除支出 -> 余额增加（原来减少的要加回来）
        // 删除收入 -> 余额减少（原来增加的要减回去）
        BigDecimal rollbackAmount = calculateRollbackAmount(transaction.getAmount(), transaction.getType());

        // 3. 更新资产余额（使用乐观锁）
        assetService.updateBalance(transaction.getAssetId(), rollbackAmount);

        // 4. 删除流水记录
        transactionMapper.deleteById(id);

        log.info("流水删除成功，余额已回滚: transactionId={}, assetId={}, rollbackAmount={}", 
                id, transaction.getAssetId(), rollbackAmount);
    }

    @Override
    public int countByAssetId(Long assetId) {
        return transactionMapper.countByAssetId(assetId);
    }

    /**
     * 根据交易类型计算余额变动
     * 支出(1)：余额减少，返回负数
     * 收入(2)：余额增加，返回正数
     */
    private BigDecimal calculateBalanceChange(BigDecimal amount, Integer type) {
        if (type == Transaction.TYPE_EXPENSE) {
            return amount.negate();  // 支出，余额减少
        } else {
            return amount;            // 收入，余额增加
        }
    }

    /**
     * 计算回滚金额（与原操作相反）
     * 删除支出(1)：余额增加，返回正数
     * 删除收入(2)：余额减少，返回负数
     */
    private BigDecimal calculateRollbackAmount(BigDecimal amount, Integer type) {
        if (type == Transaction.TYPE_EXPENSE) {
            return amount;            // 删除支出，余额恢复（加回来）
        } else {
            return amount.negate();  // 删除收入，余额恢复（减回去）
        }
    }
}
