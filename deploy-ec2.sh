#!/bin/bash

# 이 스크립트는 EC2 인스턴스에서 실행됩니다
# EC2 IAM Role에 AmazonEC2ContainerRegistryReadOnly 권한 필요

# 설정
AWS_ACCOUNT_ID="307946641609"
AWS_REGION="ap-northeast-1"
ECR_REPOSITORY="jingwook/mafia-server"
IMAGE_TAG="${1:-latest}"
CONTAINER_NAME="mafia-server"

# 환경변수 (RDS, ElastiCache 정보를 여기에 입력하세요)
# 실행 전에 export로 환경변수를 설정하세요:
# export RDS_ENDPOINT="your-rds.ap-northeast-1.rds.amazonaws.com"
# export RDS_USERNAME="admin"
# export RDS_PASSWORD="your-password"
# export REDIS_ENDPOINT="your-redis.cache.amazonaws.com"
RDS_ENDPOINT="${RDS_ENDPOINT:-your-rds-endpoint.ap-northeast-1.rds.amazonaws.com}"
RDS_USERNAME="${RDS_USERNAME:-admin}"
RDS_PASSWORD="${RDS_PASSWORD:-your-password}"
REDIS_ENDPOINT="${REDIS_ENDPOINT:-your-elasticache-endpoint.cache.amazonaws.com}"

# 컬러 출력
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${BLUE}🚀 Starting EC2 deployment...${NC}\n"

# ECR 로그인
echo -e "${BLUE}1. Logging in to ECR...${NC}"
aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ ECR login failed!${NC}"
    exit 1
fi
echo -e "${GREEN}✅ ECR login successful!${NC}\n"

# 기존 컨테이너 중지 및 제거
echo -e "${BLUE}2. Stopping existing container...${NC}"
if docker ps -a | grep -q ${CONTAINER_NAME}; then
    docker stop ${CONTAINER_NAME}
    docker rm ${CONTAINER_NAME}
    echo -e "${GREEN}✅ Old container removed!${NC}\n"
else
    echo -e "${BLUE}No existing container found.${NC}\n"
fi

# 최신 이미지 풀
echo -e "${BLUE}3. Pulling latest image from ECR...${NC}"
docker pull ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPOSITORY}:${IMAGE_TAG}

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Pull from ECR failed!${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Image pulled successfully!${NC}\n"

# 새 컨테이너 실행
echo -e "${BLUE}4. Starting new container...${NC}"
docker run -d \
  --name ${CONTAINER_NAME} \
  -p 80:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e SPRING_R2DBC_URL="r2dbc:mysql://${RDS_ENDPOINT}:3306/mafia_game?useUnicode=true&characterEncoding=utf8&useSSL=true&serverTimezone=Asia/Seoul" \
  -e SPRING_R2DBC_USERNAME="${RDS_USERNAME}" \
  -e SPRING_R2DBC_PASSWORD="${RDS_PASSWORD}" \
  -e SPRING_DATASOURCE_URL="jdbc:mysql://${RDS_ENDPOINT}:3306/mafia_game?useUnicode=true&characterEncoding=utf8&useSSL=true&serverTimezone=Asia/Seoul" \
  -e SPRING_DATASOURCE_USERNAME="${RDS_USERNAME}" \
  -e SPRING_DATASOURCE_PASSWORD="${RDS_PASSWORD}" \
  -e SPRING_DATA_REDIS_HOST="${REDIS_ENDPOINT}" \
  -e SPRING_DATA_REDIS_PORT="6379" \
  --restart unless-stopped \
  ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPOSITORY}:${IMAGE_TAG}

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Container start failed!${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Container started successfully!${NC}\n"

# 컨테이너 상태 확인
echo -e "${BLUE}5. Checking container status...${NC}"
sleep 5
docker ps | grep ${CONTAINER_NAME}

# 헬스체크
echo -e "\n${BLUE}6. Running health check...${NC}"
sleep 10
HEALTH_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost/actuator/health)

if [ "$HEALTH_STATUS" = "200" ]; then
    echo -e "${GREEN}✅ Health check passed! (HTTP ${HEALTH_STATUS})${NC}\n"
else
    echo -e "${RED}⚠️  Health check returned HTTP ${HEALTH_STATUS}${NC}"
    echo -e "${BLUE}Check logs with: docker logs ${CONTAINER_NAME}${NC}\n"
fi

# 이전 이미지 정리
echo -e "${BLUE}7. Cleaning up old images...${NC}"
docker image prune -f
echo -e "${GREEN}✅ Cleanup complete!${NC}\n"

echo -e "${GREEN}🎉 Deployment completed!${NC}"
echo -e "${BLUE}View logs: docker logs -f ${CONTAINER_NAME}${NC}"
