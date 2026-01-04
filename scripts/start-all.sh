#!/bin/bash

# =============================================================================
# 记账系统一键启动脚本
# =============================================================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_DIR"

echo -e "${BLUE}=======================================${NC}"
echo -e "${BLUE}   记账系统一键启动${NC}"
echo -e "${BLUE}=======================================${NC}"
echo ""

# 检查 Docker
if ! command -v docker &> /dev/null; then
    echo -e "${RED}❌ Docker 未安装${NC}"
    exit 1
fi

# 检查 Docker Compose
if ! docker compose version &> /dev/null; then
    echo -e "${RED}❌ Docker Compose 未安装${NC}"
    exit 1
fi

echo -e "${GREEN}✓${NC} Docker 环境检查通过"
echo ""

# 启动 Docker 服务 (MySQL + Nginx)
echo -e "${YELLOW}[1/4]${NC} 启动 Docker 容器 (MySQL + Nginx)..."
docker compose up -d

# 等待 MySQL 就绪
echo -e "${YELLOW}[2/4]${NC} 等待 MySQL 就绪..."
MAX_WAIT=60
WAIT_COUNT=0
while ! docker exec accont-book-mysql mysqladmin ping -h localhost -uroot -proot123 --silent 2>/dev/null; do
    WAIT_COUNT=$((WAIT_COUNT + 1))
    if [ $WAIT_COUNT -ge $MAX_WAIT ]; then
        echo -e "${RED}❌ MySQL 启动超时${NC}"
        exit 1
    fi
    echo -n "."
    sleep 1
done
echo ""
echo -e "${GREEN}✓${NC} MySQL 已就绪"

# 启动后端
echo -e "${YELLOW}[3/4]${NC} 启动后端服务..."
cd "$PROJECT_DIR"

# 停止可能存在的旧进程
pkill -f "spring-boot:run" 2>/dev/null || true
pkill -f "AccontBookApplication" 2>/dev/null || true
sleep 1

# 后台启动后端
nohup mvn spring-boot:run > /tmp/backend.log 2>&1 &
BACKEND_PID=$!

# 等待后端启动
echo -n "等待后端启动"
for i in {1..30}; do
    if curl -s http://localhost:8080/api/assets/user/1 > /dev/null 2>&1; then
        echo ""
        echo -e "${GREEN}✓${NC} 后端已启动 (PID: $BACKEND_PID)"
        break
    fi
    echo -n "."
    sleep 2
done

if ! curl -s http://localhost:8080/api/assets/user/1 > /dev/null 2>&1; then
    echo ""
    echo -e "${RED}❌ 后端启动失败，查看日志: /tmp/backend.log${NC}"
    exit 1
fi

# 启动前端
echo -e "${YELLOW}[4/4]${NC} 启动前端服务..."
cd "$PROJECT_DIR/accont-boot-frontend"

# 停止可能存在的旧进程
pkill -f "vite" 2>/dev/null || true
sleep 1

# 后台启动前端
nohup npm run dev > /tmp/frontend.log 2>&1 &
FRONTEND_PID=$!

# 等待前端启动
echo -n "等待前端启动"
for i in {1..15}; do
    if curl -s http://localhost:5173 > /dev/null 2>&1; then
        echo ""
        echo -e "${GREEN}✓${NC} 前端已启动 (PID: $FRONTEND_PID)"
        break
    fi
    echo -n "."
    sleep 1
done

if ! curl -s http://localhost:5173 > /dev/null 2>&1; then
    echo ""
    echo -e "${YELLOW}⚠${NC} 前端启动中，可稍后检查"
fi

echo ""
echo -e "${GREEN}=======================================${NC}"
echo -e "${GREEN}   🎉 所有服务已启动！${NC}"
echo -e "${GREEN}=======================================${NC}"
echo ""
echo -e "访问地址:"
echo -e "  ${BLUE}•${NC} 应用入口: ${GREEN}http://localhost${NC} (通过Nginx)"
echo -e "  ${BLUE}•${NC} 前端直连: ${GREEN}http://localhost:5173${NC}"
echo -e "  ${BLUE}•${NC} 后端API:  ${GREEN}http://localhost:8080${NC}"
echo ""
echo -e "日志文件:"
echo -e "  ${BLUE}•${NC} 后端: /tmp/backend.log"
echo -e "  ${BLUE}•${NC} 前端: /tmp/frontend.log"
echo ""
echo -e "停止服务: ${YELLOW}./scripts/stop-all.sh${NC}"
