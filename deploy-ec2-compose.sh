#!/bin/bash

# 이 스크립트는 EC2 인스턴스에서 실행됩니다
# EC2 IAM Role에 AmazonEC2ContainerRegistryReadOnly 권한 필요

# 설정
AWS_ACCOUNT_ID="307946641609"
AWS_REGION="ap-northeast-1"
ECR_REPOSITORY="jingwook/mafia-server"
IMAGE_TAG="${1:-latest}"

# 컬러 출력
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

echo -e "${BLUE}🚀 Starting EC2 deployment with docker-compose...${NC}\n"

# 환경변수 검증
echo -e "${BLUE}1. Checking environment variables...${NC}"

if [ -z "$RDS_ENDPOINT" ]; then
    echo -e "${RED}❌ RDS_ENDPOINT is not set!${NC}"
    echo -e "${YELLOW}Please run: export RDS_ENDPOINT=\"your-rds-endpoint\"${NC}"
    exit 1
fi

if [ -z "$RDS_USERNAME" ]; then
    echo -e "${RED}❌ RDS_USERNAME is not set!${NC}"
    exit 1
fi

if [ -z "$RDS_PASSWORD" ]; then
    echo -e "${RED}❌ RDS_PASSWORD is not set!${NC}"
    exit 1
fi

if [ -z "$REDIS_ENDPOINT" ]; then
    echo -e "${RED}❌ REDIS_ENDPOINT is not set!${NC}"
    echo -e "${YELLOW}Please run: export REDIS_ENDPOINT=\"your-redis-endpoint\"${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Environment variables:${NC}"
echo -e "  RDS_ENDPOINT: ${RDS_ENDPOINT}"
echo -e "  RDS_USERNAME: ${RDS_USERNAME}"
echo -e "  REDIS_ENDPOINT: ${REDIS_ENDPOINT}"
echo -e ""

# ECR 로그인
echo -e "${BLUE}2. Logging in to ECR...${NC}"
aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ ECR login failed!${NC}"
    exit 1
fi
echo -e "${GREEN}✅ ECR login successful!${NC}\n"

# 최신 이미지 풀
echo -e "${BLUE}3. Pulling latest image from ECR...${NC}"
docker pull ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPOSITORY}:${IMAGE_TAG}

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Pull from ECR failed!${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Image pulled successfully!${NC}\n"

# docker-compose로 서비스 시작
echo -e "${BLUE}4. Starting services with docker-compose...${NC}"
docker-compose -f docker-compose.prod.yml up -d

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ docker-compose up failed!${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Services started successfully!${NC}\n"

# 컨테이너 상태 확인
echo -e "${BLUE}5. Checking container status...${NC}"
sleep 5
docker-compose -f docker-compose.prod.yml ps

# 헬스체크
echo -e "\n${BLUE}6. Running health check...${NC}"
sleep 10
HEALTH_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost/actuator/health)

if [ "$HEALTH_STATUS" = "200" ]; then
    echo -e "${GREEN}✅ Health check passed! (HTTP ${HEALTH_STATUS})${NC}\n"
else
    echo -e "${RED}⚠️  Health check returned HTTP ${HEALTH_STATUS}${NC}"
    echo -e "${BLUE}Check logs with: docker-compose -f docker-compose.prod.yml logs mafia-server${NC}\n"
fi

# 이전 이미지 정리
echo -e "${BLUE}7. Cleaning up old images...${NC}"
docker image prune -f
echo -e "${GREEN}✅ Cleanup complete!${NC}\n"

echo -e "${GREEN}🎉 Deployment completed!${NC}"
echo -e "${BLUE}View logs: docker-compose -f docker-compose.prod.yml logs -f mafia-server${NC}"
echo -e "${BLUE}Watchtower will check for updates every 5 minutes${NC}"
