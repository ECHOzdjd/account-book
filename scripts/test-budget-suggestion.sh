#!/bin/bash

# 测试AI预算建议功能

BASE_URL="http://localhost:8080/api"
YEAR_MONTH=$(date +"%Y-%m")

echo "========================================"
echo "   测试 AI 预算建议 API"
echo "========================================"
echo ""
echo "目标月份: $YEAR_MONTH"
echo ""

# 尝试注册用户（如果已存在会失败，没关系）
echo "=== 0. 准备测试用户 ==="
REGISTER_RESP=$(curl -s -X POST "${BASE_URL}/users/register" \
  -H "Content-Type: application/json" \
  -d '{"username":"testbudget","password":"123456","nickname":"测试用户"}')
echo "注册响应: $REGISTER_RESP"
echo ""

echo "=== 1. 登录获取Token ==="
LOGIN_RESPONSE=$(curl -s -X POST "${BASE_URL}/users/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"testbudget","password":"123456"}')

echo "登录响应: $LOGIN_RESPONSE"
echo ""

TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.data.token')

if [ "$TOKEN" = "null" ] || [ -z "$TOKEN" ]; then
  echo "❌ 登录失败，无法获取Token"
  echo ""
  echo "尝试检查后端是否运行..."
  curl -s --connect-timeout 3 "${BASE_URL}/users/login" > /dev/null 2>&1
  if [ $? -ne 0 ]; then
    echo "⚠️  后端服务未启动！请运行: mvn spring-boot:run"
  fi
  exit 1
fi

echo "✅ Token获取成功: ${TOKEN:0:40}..."
echo ""

echo "=== 2. 调用AI预算建议接口 ==="
echo "请求: GET ${BASE_URL}/budgets/suggestion?yearMonth=$YEAR_MONTH"
echo ""

# 增加超时时间，AI可能需要较长时间
BUDGET_RESPONSE=$(curl -s -w "\n\nHTTP_STATUS:%{http_code}" \
  --max-time 90 \
  -X GET "${BASE_URL}/budgets/suggestion?yearMonth=$YEAR_MONTH" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json")

# 分离响应体和状态码
HTTP_STATUS=$(echo "$BUDGET_RESPONSE" | grep "HTTP_STATUS:" | sed 's/HTTP_STATUS://')
RESPONSE_BODY=$(echo "$BUDGET_RESPONSE" | sed '/HTTP_STATUS:/d')

echo "HTTP状态码: $HTTP_STATUS"
echo ""
echo "=== 原始响应 ==="
echo "$RESPONSE_BODY" | jq '.' 2>/dev/null || echo "$RESPONSE_BODY"
echo ""

# 检查响应
if echo "$RESPONSE_BODY" | jq -e '.code == 200' > /dev/null 2>&1; then
  echo "✅ API调用成功！"
  echo ""
  
  echo "=== 解析响应数据 ==="
  echo ""
  
  # 总预算
  TOTAL=$(echo "$RESPONSE_BODY" | jq -r '.data.totalSuggested')
  echo "💰 建议总预算: ¥$TOTAL"
  echo ""
  
  # 理由
  REASON=$(echo "$RESPONSE_BODY" | jq -r '.data.reason')
  echo "📝 建议理由: $REASON"
  echo ""
  
  # 是否为降级建议
  IS_FALLBACK=$(echo "$RESPONSE_BODY" | jq -r '.data.isFallback')
  if [ "$IS_FALLBACK" = "true" ]; then
    echo "⚠️  这是降级建议 (AI调用失败，使用规则生成)"
  else
    echo "✅ 这是AI生成的建议"
  fi
  echo ""
  
  # 分类预算
  echo "=== 分类预算建议 ==="
  echo "$RESPONSE_BODY" | jq -r '.data.categoryBudgets[] | "  \(.category): ¥\(.amount) (\(.percentage)%)"' 2>/dev/null
  echo ""
  
  # 小贴士
  echo "=== 省钱小贴士 ==="
  echo "$RESPONSE_BODY" | jq -r '.data.tips[] | "  • \(.)"' 2>/dev/null
  echo ""
  
else
  echo "❌ API调用失败"
  echo ""
  ERROR_MSG=$(echo "$RESPONSE_BODY" | jq -r '.message' 2>/dev/null)
  echo "错误信息: $ERROR_MSG"
  echo ""
  
  # 检查常见问题
  if [ "$HTTP_STATUS" = "401" ]; then
    echo "⚠️  可能原因: Token无效或过期"
  elif [ "$HTTP_STATUS" = "500" ]; then
    echo "⚠️  可能原因: 服务器内部错误"
    echo ""
    echo "请查看后端日志获取详细错误信息"
  fi
fi

echo ""
echo "========================================"
echo "   测试完成"
echo "========================================"
