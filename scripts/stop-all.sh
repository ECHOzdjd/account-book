#!/bin/bash

# =============================================================================
# 记账系统一键停止脚本
# =============================================================================

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_DIR"

echo -e "${BLUE}=======================================${NC}"
echo -e "${BLUE}   记账系统一键停止${NC}"
echo -e "${BLUE}=======================================${NC}"
echo ""

# 停止前端
echo -e "${YELLOW}[1/3]${NC} 停止前端服务..."
pkill -f "vite" 2>/dev/null && echo -e "${GREEN}✓${NC} 前端已停止" || echo -e "${YELLOW}•${NC} 前端未运行"

# 停止后端
echo -e "${YELLOW}[2/3]${NC} 停止后端服务..."
pkill -f "spring-boot:run" 2>/dev/null || true
pkill -f "AccontBookApplication" 2>/dev/null && echo -e "${GREEN}✓${NC} 后端已停止" || echo -e "${YELLOW}•${NC} 后端未运行"

# 停止 Docker 容器
echo -e "${YELLOW}[3/3]${NC} 停止 Docker 容器..."
docker compose down 2>/dev/null && echo -e "${GREEN}✓${NC} Docker 容器已停止" || echo -e "${YELLOW}•${NC} Docker 容器未运行"

echo ""
echo -e "${GREEN}=======================================${NC}"
echo -e "${GREEN}   所有服务已停止${NC}"
echo -e "${GREEN}=======================================${NC}"
