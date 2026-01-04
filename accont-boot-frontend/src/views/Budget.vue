<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getBudgetStatus, setBudget, getBudgetSuggestion, deleteBudget } from '@/api'

const loading = ref(true)
const saving = ref(false)
const aiLoading = ref(false)
const budgetStatus = ref(null)
const showCategoryDialog = ref(false)

// 当前选择的月份
const currentMonth = ref('')

// 分类预算表单
const categoryForm = ref({
    category: '',
    amount: null
})

// AI建议数据
const aiSuggestion = ref(null)

// 预定义的分类列表
const categories = ['餐饮', '交通', '购物', '娱乐', '居住', '通讯', '医疗', '教育', '其他']

// 初始化当前月份
const initCurrentMonth = () => {
    const now = new Date()
    currentMonth.value = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

// 加载预算状态
const loadBudgetStatus = async () => {
    if (!currentMonth.value) return
    loading.value = true
    try {
        const res = await getBudgetStatus(currentMonth.value)
        budgetStatus.value = res.data
    } catch (e) {
        console.error('加载预算状态失败', e)
        budgetStatus.value = null
    } finally {
        loading.value = false
    }
}

// 进度条颜色
const progressColor = computed(() => {
    if (!budgetStatus.value) return '#c17c4a'
    const status = budgetStatus.value.status
    if (status === 'exceeded') return '#dc2626'
    if (status === 'warning') return '#f59e0b'
    return '#22c55e'
})

// 进度百分比
const progressPercentage = computed(() => {
    if (!budgetStatus.value) return 0
    return Math.min(budgetStatus.value.usagePercentage, 100)
})

// 格式化金额
const formatMoney = (val) => {
    if (val === null || val === undefined) return '0.00'
    return Number(val).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// 获取分类状态颜色
const getCategoryColor = (status) => {
    if (status === 'exceeded') return '#dc2626'
    if (status === 'warning') return '#f59e0b'
    return '#22c55e'
}

// 打开分类预算对话框
const openCategoryDialog = (category = '') => {
    categoryForm.value = {
        category: category,
        amount: null
    }
    // 如果是编辑，填入现有金额
    if (category && budgetStatus.value?.categoryDetails) {
        const existing = budgetStatus.value.categoryDetails.find(c => c.category === category)
        if (existing) {
            categoryForm.value.amount = existing.budgetAmount
        }
    }
    showCategoryDialog.value = true
}

// 获取AI建议
const getAISuggestion = async () => {
    aiLoading.value = true
    aiSuggestion.value = null
    try {
        const res = await getBudgetSuggestion(currentMonth.value)
        aiSuggestion.value = res.data
        ElMessage.success('已获取AI预算建议')
    } catch (e) {
        console.error('获取AI建议失败', e)
        ElMessage.error('获取AI建议失败，请稍后重试')
    } finally {
        aiLoading.value = false
    }
}

// 采纳单个分类预算建议
const applyCategorySuggestion = async (cat) => {
    saving.value = true
    try {
        await setBudget({
            yearMonth: currentMonth.value,
            amount: cat.amount,
            category: cat.category,
            aiSuggested: true
        })
        ElMessage.success(`已设置 ${cat.category} 预算`)
        loadBudgetStatus()
    } catch (e) {
        ElMessage.error('设置预算失败')
    } finally {
        saving.value = false
    }
}

// 一键采纳所有分类预算
const applyAllCategorySuggestions = async () => {
    if (!aiSuggestion.value?.categoryBudgets?.length) return

    saving.value = true
    try {
        for (const cat of aiSuggestion.value.categoryBudgets) {
            await setBudget({
                yearMonth: currentMonth.value,
                amount: cat.amount,
                category: cat.category,
                aiSuggested: true
            })
        }
        ElMessage.success('已采纳所有分类预算建议')
        aiSuggestion.value = null
        loadBudgetStatus()
    } catch (e) {
        ElMessage.error('设置预算失败')
    } finally {
        saving.value = false
    }
}

// 保存分类预算
const saveCategoryBudget = async () => {
    if (!categoryForm.value.category) {
        ElMessage.warning('请选择分类')
        return
    }
    if (!categoryForm.value.amount || categoryForm.value.amount <= 0) {
        ElMessage.warning('请输入有效的预算金额')
        return
    }
    saving.value = true
    try {
        await setBudget({
            yearMonth: currentMonth.value,
            amount: categoryForm.value.amount,
            category: categoryForm.value.category,
            aiSuggested: false
        })
        ElMessage.success('分类预算设置成功')
        showCategoryDialog.value = false
        loadBudgetStatus()
    } catch (e) {
        console.error('保存分类预算失败', e)
    } finally {
        saving.value = false
    }
}

// 删除分类预算
const removeCategoryBudget = async (cat) => {
    try {
        await ElMessageBox.confirm(`确定删除 ${cat.category} 的预算吗？`, '提示', {
            confirmButtonText: '删除',
            cancelButtonText: '取消',
            type: 'warning'
        })
        await deleteBudget(cat.id)
        ElMessage.success('预算已删除')
        loadBudgetStatus()
    } catch (e) {
        if (e !== 'cancel') {
            console.error('删除分类预算失败', e)
        }
    }
}

// 监听月份变化
watch(currentMonth, () => {
    loadBudgetStatus()
    aiSuggestion.value = null
})

onMounted(() => {
    initCurrentMonth()
    loadBudgetStatus()
})
</script>

<template>
    <div class="budget-page">
        <!-- 月份选择和AI建议 -->
        <div class="top-bar animate-in">
            <el-date-picker v-model="currentMonth" type="month" placeholder="选择月份" format="YYYY年MM月"
                value-format="YYYY-MM" style="width: 160px" />
            <div class="actions">
                <el-button @click="getAISuggestion" :loading="aiLoading" type="success" plain>
                    <el-icon>
                        <MagicStick />
                    </el-icon>
                    AI智能建议
                </el-button>
                <el-button type="primary" @click="openCategoryDialog()">
                    <el-icon>
                        <Plus />
                    </el-icon>
                    添加分类预算
                </el-button>
            </div>
        </div>

        <!-- AI建议卡片 -->
        <div v-if="aiSuggestion" class="content-card ai-card animate-in" style="animation-delay: 0.05s">
            <div class="card-header">
                <span class="card-title">
                    <el-icon>
                        <MagicStick />
                    </el-icon>
                    AI 预算建议
                </span>
                <el-tag v-if="aiSuggestion.isFallback" type="info" size="small">基于规则</el-tag>
            </div>
            <div class="card-body">
                <!-- 建议理由 -->
                <div class="ai-reason">
                    <el-icon>
                        <InfoFilled />
                    </el-icon>
                    <span>{{ aiSuggestion.reason }}</span>
                </div>

                <!-- 分类预算建议 -->
                <div class="ai-categories">
                    <div class="ai-categories-header">
                        <span>点击采纳分类预算</span>
                        <el-button type="primary" link size="small" @click="applyAllCategorySuggestions"
                            :loading="saving">
                            一键全部采纳
                        </el-button>
                    </div>
                    <div class="ai-category-grid">
                        <div v-for="cat in aiSuggestion.categoryBudgets" :key="cat.category" class="ai-category-item"
                            @click="applyCategorySuggestion(cat)">
                            <div class="cat-name">{{ cat.category }}</div>
                            <div class="cat-amount">¥{{ formatMoney(cat.amount) }}</div>
                            <div class="cat-percent">{{ cat.percentage }}%</div>
                        </div>
                    </div>
                    <div class="ai-total">
                        建议总预算：<strong>¥{{ formatMoney(aiSuggestion.totalSuggested) }}</strong>
                    </div>
                </div>

                <!-- 小贴士 -->
                <div v-if="aiSuggestion.tips?.length" class="ai-tips">
                    <div class="tips-title"><el-icon>
                            <Lightbulb />
                        </el-icon> 省钱小贴士</div>
                    <ul class="tips-list">
                        <li v-for="(tip, idx) in aiSuggestion.tips" :key="idx">{{ tip }}</li>
                    </ul>
                </div>
            </div>
        </div>

        <!-- 预算总览卡片 -->
        <div class="content-card budget-card animate-in" style="animation-delay: 0.1s" v-loading="loading">
            <div class="card-header">
                <span class="card-title">{{ currentMonth }} 预算总览</span>
                <span v-if="budgetStatus?.isExceeded" class="exceeded-badge">
                    <el-icon>
                        <Warning />
                    </el-icon>
                    超预算
                </span>
            </div>
            <div class="card-body">
                <div v-if="!budgetStatus || budgetStatus.status === 'no_budget'" class="no-budget">
                    <el-empty description="暂未设置预算" :image-size="100">
                        <template #default>
                            <p class="hint">按分类设置预算，总预算将自动汇总</p>
                            <el-button type="primary" @click="openCategoryDialog()">添加分类预算</el-button>
                        </template>
                    </el-empty>
                </div>

                <div v-else class="budget-content">
                    <!-- 进度环 -->
                    <div class="progress-section">
                        <el-progress type="dashboard" :percentage="progressPercentage" :color="progressColor"
                            :stroke-width="12" :width="180">
                            <template #default>
                                <div class="progress-inner">
                                    <div class="percentage" :class="{ exceeded: budgetStatus.isExceeded }">
                                        {{ budgetStatus.usagePercentage.toFixed(1) }}%
                                    </div>
                                    <div class="label">已使用</div>
                                </div>
                            </template>
                        </el-progress>
                    </div>

                    <!-- 汇总数据 -->
                    <div class="budget-summary">
                        <div class="summary-item">
                            <span class="label">总预算</span>
                            <span class="value">¥{{ formatMoney(budgetStatus.budgetAmount) }}</span>
                        </div>
                        <div class="summary-item">
                            <span class="label">已支出</span>
                            <span class="value expense">¥{{ formatMoney(budgetStatus.usedAmount) }}</span>
                        </div>
                        <div class="summary-item">
                            <span class="label">剩余</span>
                            <span class="value" :class="{ exceeded: budgetStatus.isExceeded }">
                                {{ budgetStatus.remainingAmount < 0 ? '-' : '' }}¥{{
                                    formatMoney(Math.abs(budgetStatus.remainingAmount)) }} </span>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <!-- 分类预算详情 -->
        <div v-if="budgetStatus?.categoryDetails?.length" class="content-card category-card animate-in"
            style="animation-delay: 0.2s">
            <div class="card-header">
                <span class="card-title">分类预算明细</span>
            </div>
            <div class="card-body">
                <div class="category-list">
                    <div v-for="cat in budgetStatus.categoryDetails" :key="cat.id" class="category-item">
                        <div class="category-main">
                            <div class="category-info">
                                <span class="category-name">{{ cat.category }}</span>
                                <el-tag
                                    :type="cat.status === 'exceeded' ? 'danger' : (cat.status === 'warning' ? 'warning' : 'success')"
                                    size="small">
                                    {{ cat.usagePercentage.toFixed(0) }}%
                                </el-tag>
                            </div>
                            <div class="category-amounts">
                                <span class="used">¥{{ formatMoney(cat.usedAmount) }}</span>
                                <span class="divider">/</span>
                                <span class="budget">¥{{ formatMoney(cat.budgetAmount) }}</span>
                            </div>
                        </div>
                        <el-progress :percentage="Math.min(cat.usagePercentage, 100)"
                            :color="getCategoryColor(cat.status)" :stroke-width="6" :show-text="false" />
                        <div class="category-actions">
                            <el-button type="primary" @click="openCategoryDialog(cat.category)">
                                <el-icon>
                                    <Edit />
                                </el-icon>
                            </el-button>
                            <el-button type="danger" @click="removeCategoryBudget(cat)">
                                <el-icon>
                                    <Delete />
                                </el-icon>
                            </el-button>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <!-- 分类预算对话框 -->
        <el-dialog v-model="showCategoryDialog" title="设置分类预算" width="400px">
            <el-form :model="categoryForm" label-width="80px">
                <el-form-item label="分类" required>
                    <el-select v-model="categoryForm.category" placeholder="选择分类" style="width: 100%">
                        <el-option v-for="cat in categories" :key="cat" :label="cat" :value="cat" />
                    </el-select>
                </el-form-item>
                <el-form-item label="预算金额" required>
                    <el-input-number v-model="categoryForm.amount" :min="0.01" :precision="2" :step="50"
                        placeholder="请输入预算金额" style="width: 100%" />
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="showCategoryDialog = false">取消</el-button>
                <el-button type="primary" @click="saveCategoryBudget" :loading="saving">确认</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<style scoped>
.budget-page {
    min-height: 100%;
}

.top-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 20px;
    flex-wrap: wrap;
    gap: 12px;
}

.actions {
    display: flex;
    gap: 12px;
}

/* AI建议卡片 */
.ai-card {
    margin-bottom: 20px;
    border: 1px solid var(--accent-gold);
    background: linear-gradient(135deg, #fdf8f3 0%, #fef9f5 100%);
}

.ai-card .card-title {
    display: flex;
    align-items: center;
    gap: 6px;
    color: var(--accent-gold);
}

.ai-reason {
    display: flex;
    align-items: flex-start;
    gap: 8px;
    padding: 12px;
    background: rgba(194, 124, 74, 0.08);
    border-radius: 8px;
    margin-bottom: 16px;
    font-size: 14px;
    color: var(--text-main);
}

.ai-reason .el-icon {
    color: var(--accent-gold);
    margin-top: 2px;
}

.ai-categories-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
    font-size: 13px;
    color: var(--text-muted);
}

.ai-category-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(90px, 1fr));
    gap: 10px;
    margin-bottom: 12px;
}

.ai-category-item {
    padding: 12px 8px;
    background: white;
    border-radius: 10px;
    text-align: center;
    cursor: pointer;
    transition: all 0.2s;
    border: 1px solid transparent;
}

.ai-category-item:hover {
    border-color: var(--accent-gold);
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(194, 124, 74, 0.15);
}

.cat-name {
    font-size: 13px;
    color: var(--text-main);
    margin-bottom: 4px;
}

.cat-amount {
    font-size: 14px;
    font-weight: 600;
    color: var(--accent-gold);
}

.cat-percent {
    font-size: 11px;
    color: var(--text-muted);
}

.ai-total {
    text-align: right;
    font-size: 14px;
    color: var(--text-main);
    padding-top: 8px;
    border-top: 1px dashed rgba(194, 124, 74, 0.3);
}

.ai-total strong {
    color: var(--accent-gold);
    font-size: 16px;
}

.ai-tips {
    margin-top: 16px;
    padding: 12px;
    background: #fffbeb;
    border-radius: 8px;
}

.tips-title {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    font-weight: 500;
    color: #b45309;
    margin-bottom: 8px;
}

.tips-list {
    margin: 0;
    padding-left: 18px;
    font-size: 13px;
    color: var(--text-main);
    line-height: 1.7;
}

/* 预算卡片 */
.budget-card,
.category-card {
    margin-bottom: 20px;
}

.exceeded-badge {
    display: flex;
    align-items: center;
    gap: 4px;
    padding: 4px 12px;
    background: #fef2f2;
    color: #dc2626;
    border-radius: 20px;
    font-size: 13px;
    font-weight: 500;
}

.no-budget {
    padding: 40px 20px;
    text-align: center;
}

.no-budget .hint {
    color: var(--text-muted);
    font-size: 14px;
    margin-bottom: 16px;
}

.budget-content {
    display: flex;
    align-items: center;
    gap: 32px;
    padding: 16px 0;
}

.progress-section {
    flex-shrink: 0;
}

.progress-inner {
    text-align: center;
}

.progress-inner .percentage {
    font-size: 28px;
    font-weight: 700;
    color: var(--text-main);
}

.progress-inner .percentage.exceeded {
    color: #dc2626;
}

.progress-inner .label {
    font-size: 13px;
    color: var(--text-muted);
    margin-top: 4px;
}

.budget-summary {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 16px;
}

.summary-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
}

.summary-item .label {
    font-size: 14px;
    color: var(--text-muted);
}

.summary-item .value {
    font-size: 18px;
    font-weight: 600;
    color: var(--text-main);
}

.summary-item .value.expense {
    color: #22c55e;
}

.summary-item .value.exceeded {
    color: #dc2626;
}

/* 分类列表 */
.category-list {
    display: flex;
    flex-direction: column;
    gap: 16px;
}

.category-item {
    padding: 12px 16px;
    background: #fdfbf7;
    border-radius: 10px;
}

.category-main {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;
}

.category-info {
    display: flex;
    align-items: center;
    gap: 10px;
}

.category-name {
    font-size: 14px;
    font-weight: 500;
    color: var(--text-main);
}

.category-amounts {
    font-size: 14px;
    color: var(--text-main);
}

.category-amounts .used {
    font-weight: 600;
}

.category-amounts .divider {
    color: var(--text-muted);
    margin: 0 4px;
}

.category-amounts .budget {
    color: var(--text-muted);
}

.category-actions {
    display: flex;
    justify-content: flex-end;
    gap: 4px;
    margin-top: 8px;
}

@media (max-width: 600px) {
    .budget-content {
        flex-direction: column;
    }

    .budget-summary {
        width: 100%;
    }
}
</style>
