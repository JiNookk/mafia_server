#!/bin/bash

# 설정
AWS_ACCOUNT_ID="307946641609"
AWS_REGION="ap-northeast-1"
ECR_REPOSITORY="jingwook/mafia-server"
IMAGE_TAG="${1:-latest}"
AWS_PROFILE="personal"

# 컬러 출력
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}🚀 Starting ECR deployment process...${NC}\n"

# ECR 로그인
echo -e "${BLUE}1. Logging in to ECR...${NC}"
aws ecr get-login-password --region ${AWS_REGION} --profile ${AWS_PROFILE} | docker login --username AWS --password-stdin ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ ECR login failed!${NC}"
    exit 1
fi
echo -e "${GREEN}✅ ECR login successful!${NC}\n"

# Docker 이미지 빌드
echo -e "${BLUE}2. Building Docker image...${NC}"
docker build -t mafia-server:${IMAGE_TAG} .

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Docker build failed!${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Docker build successful!${NC}\n"

# 이미지 태깅
echo -e "${BLUE}3. Tagging image...${NC}"
docker tag mafia-server:${IMAGE_TAG} ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPOSITORY}:${IMAGE_TAG}

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Docker tag failed!${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Image tagged!${NC}\n"

# ECR에 푸시
echo -e "${BLUE}4. Pushing to ECR...${NC}"
docker push ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPOSITORY}:${IMAGE_TAG}

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Push to ECR failed!${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Push to ECR successful!${NC}\n"

# 완료
echo -e "${GREEN}🎉 Deployment completed successfully!${NC}"
echo -e "${BLUE}Image URL: ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPOSITORY}:${IMAGE_TAG}${NC}"
