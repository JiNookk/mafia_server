# 🎭 Mafia Game Server

실시간 마피아 게임을 위한 Reactive 백엔드 서버

## 📋 목차

- [프로젝트 개요](#-프로젝트-개요)
- [기술 스택](#-기술-스택)
- [서버 아키텍처](#-서버-아키텍처)
- [주요 기능](#-주요-기능)
- [상태 관리 전략](#-상태-관리-전략)
- [인프라 구성](#️-인프라-구성)
- [실행 방법](#-실행-방법)
- [API 문서](#-api-문서)

## 🎯 프로젝트 개요

8인 실시간 마피아 게임 서버로, WebFlux 기반의 비동기/논블로킹 아키텍처를 통해 높은 동시성을 처리합니다.
WebSocket을 활용한 실시간 양방향 통신과 Redis 기반 상태 관리로 확장 가능한 게임 서비스를 제공합니다.

### 게임 규칙
- **플레이어 구성**: 마피아 2명, 의사 1명, 경찰 1명, 시민 4명
- **게임 페이즈**: NIGHT → DAY → VOTE → DEFENSE → RESULT (순환)
- **승리 조건**:
  - 시민팀: 모든 마피아 제거
  - 마피아팀: 마피아 수 ≥ 시민팀 수

## 🛠 기술 스택

### Backend Framework
- **Spring Boot 3.5.6** - 최신 Spring 프레임워크
- **Spring WebFlux** - Reactive 웹 프레임워크 (비동기/논블로킹)
- **Project Reactor** - Reactive Streams 구현체
- **Java 21** - 최신 LTS 버전

### Database & Persistence
- **Spring Data R2DBC** - Reactive 관계형 DB 접근
- **MySQL 8.x** - 주 데이터베이스 (AWS RDS)
- **Flyway** - 데이터베이스 마이그레이션 관리
- **R2DBC MySQL Driver** - MySQL용 Reactive 드라이버

### Cache & Message Broker
- **Spring Data Redis Reactive** - Reactive Redis 클라이언트
- **AWS ElastiCache (Valkey)** - Redis 호환 인메모리 캐시
- **Redis Pub/Sub** - 실시간 메시지 브로드캐스팅

### WebSocket
- **Spring WebFlux WebSocket** - Reactive WebSocket 지원
- 실시간 양방향 통신 (게임 상태, 채팅)

### Monitoring & Documentation
- **Spring Actuator** - 애플리케이션 헬스체크 및 메트릭
- **Micrometer + Prometheus** - 메트릭 수집 및 모니터링
- **SpringDoc OpenAPI** - API 문서 자동 생성 (Swagger UI)

### Build & DevOps
- **Gradle 8.5** - 빌드 자동화
- **Docker** - 컨테이너화
- **GitHub Actions** - CI (빌드 체크)
- **AWS ECR** - 도커 이미지 레지스트리
- **Watchtower** - 자동 컨테이너 업데이트

## 🏗 서버 아키텍처

```
┌─────────────────────────────────────────────────────────────────┐
│                         Client Layer                             │
│                    (WebSocket + REST API)                        │
└───────────────────────────┬─────────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────────┐
│                      Controller Layer                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │   Room       │  │    Game      │  │    Chat      │          │
│  │ Controller   │  │  Controller  │  │  Controller  │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
└───────────────────────────┬─────────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────────┐
│                   WebSocket Handler Layer                        │
│  ┌──────────────────────┐       ┌──────────────────────┐        │
│  │  RoomWebSocket       │       │  GameWebSocket       │        │
│  │  Handler             │       │  Handler             │        │
│  │  (방 실시간 상태)      │       │  (게임 실시간 상태)    │        │
│  └──────────────────────┘       └──────────────────────┘        │
└───────────────────────────┬─────────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────────┐
│                       Service Layer                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │ RoomService  │  │ GameService  │  │ ChatService  │          │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘          │
│  ┌──────┴─────────────────┴─────────────────┴───────┐          │
│  │        GameSchedulerService (페이즈 관리)         │          │
│  └───────────────────────────────────────────────────┘          │
└───────────────────────────┬─────────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────────┐
│                  Cache & Lock Layer                              │
│  ┌──────────────────┐  ┌──────────────────┐                    │
│  │  GameCache       │  │  VoteCache       │                    │
│  │  Service         │  │  Service         │                    │
│  └──────────────────┘  └──────────────────┘                    │
│  ┌──────────────────┐  ┌──────────────────┐                    │
│  │  RedisLock       │  │  RedisMessage    │                    │
│  │  Service         │  │  Service         │                    │
│  │  (분산 락)        │  │  (Pub/Sub)       │                    │
│  └──────────────────┘  └──────────────────┘                    │
└───────────────────────────┬─────────────────────────────────────┘
                            │
        ┌───────────────────┴────────────────────┐
        │                                        │
┌───────▼──────────┐                 ┌──────────▼─────────┐
│   Repository     │                 │   Redis (Valkey)   │
│   Layer          │                 │   - 게임 상태       │
│  ┌────────────┐  │                 │   - 투표 집계       │
│  │ R2DBC      │  │                 │   - 분산 락         │
│  │ Repository │  │                 │   - Pub/Sub        │
│  └────────────┘  │                 └────────────────────┘
└───────┬──────────┘
        │
┌───────▼──────────┐
│   MySQL (RDS)    │
│   - 사용자 정보   │
│   - 방 정보       │
│   - 게임 기록     │
│   - 채팅 로그     │
└──────────────────┘
```

### 계층별 역할

#### 1. **Controller Layer**
- REST API 엔드포인트 제공
- 요청 검증 및 응답 변환
- Reactive 스트림 반환 (Mono/Flux)

#### 2. **WebSocket Handler Layer**
- 실시간 양방향 통신 처리
- 세션 관리 및 메시지 라우팅
- Redis Pub/Sub을 통한 브로드캐스팅

#### 3. **Service Layer**
- 비즈니스 로직 처리
- 트랜잭션 관리
- 이벤트 기반 페이즈 전환 (GameSchedulerService)

#### 4. **Cache & Lock Layer**
- Redis 기반 상태 캐싱
- 분산 락을 통한 동시성 제어
- Pub/Sub 메시지 브로커

#### 5. **Repository Layer**
- R2DBC를 통한 Reactive DB 접근
- 쿼리 최적화 및 인덱스 활용

## 🎮 주요 기능

### 1. 방 관리
- ✅ 방 생성/조회/참여/퇴장
- ✅ 실시간 방 목록 업데이트
- ✅ 방장 권한 관리
- ✅ 최대 인원 제한 (8명)

### 2. 게임 진행
- ✅ 자동 역할 배정 (랜덤 셔플)
- ✅ 페이즈 자동 전환 (스케줄러 기반)
- ✅ 투표 시스템 (과반수 득표)
- ✅ 직업별 행동 처리
  - 마피아: 밤에 시민 제거
  - 의사: 밤에 플레이어 보호
  - 경찰: 밤에 마피아 조사
- ✅ 최후 변론 (DEFENSE 페이즈)
- ✅ 승리 조건 판정

### 3. 실시간 채팅
- ✅ 방 채팅 (대기실)
- ✅ 게임 전체 채팅
- ✅ 마피아 전용 채팅
- ✅ WebSocket 기반 실시간 전송

### 4. 인증 및 세션
- ✅ 닉네임 기반 간편 인증
- ✅ UUID 기반 사용자 식별

## 🔄 상태 관리 전략

### 1. **데이터베이스 (MySQL)**
영속성이 필요한 데이터 저장
```
- 사용자 정보 (users)
- 방 정보 (rooms, room_members)
- 게임 기록 (games, game_players, game_actions)
- 채팅 로그 (chat_messages)
```

### 2. **Redis Cache (ElastiCache - Valkey)**
빠른 읽기/쓰기가 필요한 휘발성 데이터

#### 캐시 전략
```java
// 게임 상태 캐싱 (TTL: 30분)
game:state:{gameId} → GameEntity

// 투표 집계 (TTL: 페이즈 종료 시)
vote:{gameId}:{dayCount}:{phase} → Set<VoteEntity>

// 행동 캐싱 (마피아 킬, 의사 힐, 경찰 체크)
action:{gameId}:{dayCount}:{phase}:{actionType} → Set<ActionEntity>
```

#### 분산 락 (Distributed Lock)
```java
// Redis SETNX를 활용한 분산 락
lock:{resourceKey} → lockToken (TTL: 10초)

// 동시성 제어가 필요한 작업
- 게임 시작
- 페이즈 전환
- 투표 집계
- 플레이어 사망 처리
```

#### Pub/Sub 메시지 브로커
```java
// 실시간 이벤트 브로드캐스팅
room:{roomId} → 방 상태 변경
game:{gameId} → 게임 상태 변경
chat:{contextId} → 채팅 메시지
```

### 3. **In-Memory (Application State)**
- WebSocket 세션 관리 (ConcurrentHashMap)
- 페이즈 스케줄러 태스크

### 데이터 흐름 예시 (투표 처리)

```
1. Client → WebSocket → GameWebSocketHandler
   사용자가 투표 메시지 전송

2. GameService.vote()
   ├─ RedisLockService.acquireLock("vote:{gameId}")  # 분산 락 획득
   ├─ VoteCacheService.addVote(vote)                 # Redis에 투표 저장
   ├─ GameActionRepository.save(action)              # MySQL에 투표 기록
   └─ RedisMessageService.publishVoteUpdate()        # Pub/Sub으로 브로드캐스트

3. GameSchedulerService (페이즈 종료 시)
   ├─ VoteCacheService.getVotes()                    # Redis에서 투표 집계
   ├─ Game.selectTargetFromVotesWithMajority()       # 과반수 득표자 선출
   ├─ GameService.transitionPhase()                  # 다음 페이즈 전환
   └─ GameCacheService.cacheGameState()              # Redis에 게임 상태 캐싱
```

## ☁️ 인프라 구성

### AWS 아키텍처

```
                                    ┌─────────────────┐
                                    │   Developer     │
                                    └────────┬────────┘
                                             │ Push Code
                                    ┌────────▼────────┐
                                    │  GitHub Repo    │
                                    └────────┬────────┘
                                             │
                        ┌────────────────────┴────────────────────┐
                        │                                         │
                ┌───────▼───────┐                        ┌────────▼────────┐
                │ GitHub Actions│                        │  Manual Deploy  │
                │   (CI Build)  │                        │   to ECR        │
                └───────┬───────┘                        └────────┬────────┘
                        │                                         │
                        │ Build Success                           │ Push Image
                        └────────────────────┬────────────────────┘
                                             │
                                    ┌────────▼────────┐
                                    │   AWS ECR       │
                                    │ (Docker Image)  │
                                    └────────┬────────┘
                                             │
                                             │ Pull Image (Every 5min)
                        ┌────────────────────▼────────────────────┐
                        │                                         │
                ┌───────▼───────┐                        ┌────────▼────────┐
                │  Watchtower   │                        │                 │
                │  (Auto Update)│                        │                 │
                └───────┬───────┘                        │                 │
                        │                                │                 │
                ┌───────▼──────────────────────────┐     │                 │
                │         EC2 Instance             │     │                 │
                │  ┌────────────────────────────┐  │     │                 │
                │  │   Spring Boot App          │  │     │                 │
                │  │   (Docker Container)       │◄─┼─────┤  Target Group   │
                │  └────────┬────────────────┬──┘  │     │                 │
                │           │                │     │     │                 │
                │           │                │     │     │                 │
                └───────────┼────────────────┼─────┘     └────────┬────────┘
                            │                │                    │
                            │                │                    │ Health Check
                            │                │                    │
                ┌───────────▼──────┐  ┌──────▼──────┐     ┌──────▼────────┐
                │   AWS RDS        │  │ ElastiCache │     │  CloudWatch   │
                │   (MySQL)        │  │  (Valkey)   │     │    Alarm      │
                │                  │  │             │     └──────┬────────┘
                │ - users          │  │ - Cache     │            │
                │ - rooms          │  │ - Lock      │            │ Unhealthy
                │ - games          │  │ - Pub/Sub   │            │
                │ - chat_messages  │  │             │     ┌──────▼────────┐
                └──────────────────┘  └─────────────┘     │   SNS Topic   │
                                                           └──────┬────────┘
                                                                  │
                                                    ┌─────────────┴─────────────┐
                                                    │                           │
                                            ┌───────▼───────┐           ┌───────▼───────┐
                                            │  Email Alert  │           │    Lambda     │
                                            │  to Developer │           │ (EC2 Reboot)  │
                                            └───────────────┘           └───────────────┘
```

### 컴퓨팅
- **AWS EC2**: Spring Boot 애플리케이션 호스팅 (Docker)
- **Docker**: 컨테이너 기반 배포
- **Watchtower**: ECR 이미지 5분마다 폴링하여 자동 업데이트

### 데이터베이스
- **AWS RDS (MySQL 8.x)**: 주 데이터베이스
  - Multi-AZ 배포 (고가용성)
  - 자동 백업 및 스냅샷
- **AWS ElastiCache (Valkey)**: Redis 호환 인메모리 캐시
  - 게임 상태 캐싱
  - 분산 락
  - Pub/Sub 메시지 브로커

### CI/CD 파이프라인

#### CI (Continuous Integration)
```yaml
GitHub Actions
├─ Build Check (Gradle)
├─ Unit Test
└─ Code Quality Check (Spotless)
```

#### CD (Continuous Deployment)
```bash
1. 개발자가 ECR에 도커 이미지 푸시
   └─ docker build -t mafia-server:latest .
   └─ docker push {ecr-repo-url}

2. EC2에서 Watchtower가 5분마다 ECR 폴링
   └─ 새 이미지 감지 시 자동 Pull & Restart

3. 무중단 배포
   └─ 기존 컨테이너 graceful shutdown
   └─ 새 컨테이너 시작
```

### 모니터링 & 에러 핸들링

#### Health Check
```
Target Group → EC2:8080/actuator/health
└─ Interval: 30초
└─ Timeout: 5초
└─ Unhealthy Threshold: 2회 연속 실패
```

#### 장애 복구 플로우
```
1. Target Group이 Unhealthy 감지
   └─ 2회 연속 헬스체크 실패

2. CloudWatch Alarm 트리거
   └─ Alarm State: "InAlarm"

3. SNS Topic으로 알림 전송
   ├─ Email: 개발자에게 이메일 알림
   └─ Lambda: EC2 자동 재부팅 함수 호출

4. Lambda 실행
   └─ EC2 인스턴스 재부팅 (boto3 API)
   └─ 재부팅 후 자동으로 Docker 컨테이너 재시작
```

### 보안
- VPC Private Subnet (RDS, ElastiCache)
- Security Group 기반 접근 제어
- IAM Role 기반 권한 관리
- SSL/TLS 암호화 (RDS, ElastiCache)

## 🚀 실행 방법

### 로컬 개발 환경

#### 1. Prerequisites
```bash
- Java 21
- Docker & Docker Compose
- Gradle 8.5+
```

#### 2. 의존성 실행 (MySQL, Redis)
```bash
docker-compose up -d
```

#### 3. 데이터베이스 초기화
```bash
mysql -h localhost -P 3307 -u root -p < init_database.sql
```

#### 4. 애플리케이션 실행
```bash
./gradlew bootRun
```

### Docker로 실행

#### 1. 이미지 빌드
```bash
docker build -t mafia-server:latest .
```

#### 2. 컨테이너 실행
```bash
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e SPRING_R2DBC_URL=r2dbc:mysql://host.docker.internal:3307/mafia_game \
  -e SPRING_DATA_REDIS_HOST=host.docker.internal \
  mafia-server:latest
```

### Production 배포 (AWS)

#### 1. ECR에 이미지 푸시
```bash
# ECR 로그인
aws ecr get-login-password --region ap-northeast-2 | \
  docker login --username AWS --password-stdin {account-id}.dkr.ecr.ap-northeast-2.amazonaws.com

# 이미지 태깅
docker tag mafia-server:latest {ecr-repo-url}:latest

# 푸시
docker push {ecr-repo-url}:latest
```

#### 2. Watchtower가 자동으로 감지 및 배포 (5분 이내)

## 📚 API 문서

### Swagger UI
```
http://localhost:8080/swagger-ui.html
```

### 주요 엔드포인트

#### 인증
```http
POST /api/auth/login
Content-Type: application/json

{
  "nickname": "player1"
}
```

#### 방 관리
```http
# 방 생성
POST /api/rooms
Content-Type: application/json

{
  "name": "마피아 게임방",
  "maxPlayers": 8
}

# 방 목록 조회
GET /api/rooms?page=0&size=10&sort=createdAt&order=desc

# 방 참여
POST /api/rooms/{roomId}/join
```

#### 게임
```http
# 게임 시작
POST /api/games/start/{roomId}

# 게임 상태 조회
GET /api/games/{gameId}

# 투표
POST /api/games/{gameId}/vote
Content-Type: application/json

{
  "targetUserId": "user-uuid"
}
```

### WebSocket 엔드포인트

#### 방 WebSocket
```javascript
// 연결
ws://localhost:8080/ws/rooms/{roomId}

// 메시지 수신 (방 상태 업데이트)
{
  "type": "ROOM_UPDATE",
  "data": { ... }
}
```

#### 게임 WebSocket
```javascript
// 연결
ws://localhost:8080/ws/games/{gameId}

// 메시지 수신 (게임 상태 업데이트)
{
  "type": "GAME_STATE_UPDATE",
  "data": {
    "currentPhase": "DAY",
    "dayCount": 2,
    "remainingSeconds": 120,
    "players": [ ... ]
  }
}

// 채팅 메시지 수신
{
  "type": "CHAT_MESSAGE",
  "data": {
    "userId": "user-uuid",
    "nickname": "player1",
    "message": "안녕하세요",
    "chatType": "GAME"
  }
}
```

## 📊 성능 최적화

### 1. Reactive Programming
- Non-blocking I/O로 높은 동시성 처리
- Backpressure를 통한 리소스 관리
- 이벤트 기반 비동기 처리

### 2. 캐싱 전략
- Redis를 활용한 게임 상태 캐싱 (TTL: 30분)
- 투표/행동 데이터 임시 저장 (페이즈별 TTL)
- Cache-Aside 패턴 적용

### 3. 데이터베이스 최적화
- R2DBC를 통한 Reactive DB 접근
- 복합 인덱스 활용 (게임 ID + 날짜 + 페이즈)
- Connection Pool 최적화 (초기: 5, 최대: 20)

### 4. 분산 락
- Redis SETNX 기반 분산 락
- 동시 페이즈 전환 방지
- 락 타임아웃 자동 해제 (TTL: 10초)

## 🔧 개발 환경

### 코드 품질
- **Spotless**: 코드 포맷팅 자동화
- **Lombok**: 보일러플레이트 코드 제거
- **Bean Validation**: 요청 데이터 검증

### 로깅
- SLF4J + Logback
- 구조화된 로깅 (날짜, 스레드, 레벨, 메시지)
- 파일 로깅 (최대 10MB, 10개 파일 로테이션)

## 📝 라이선스

This project is licensed under the MIT License.

## 👤 Author

**Jingwook Oh**

- GitHub: [@jingwook-oh](https://github.com/jingwook-oh)
- Email: your.email@example.com

---

⭐ Star this repository if you find it helpful!
