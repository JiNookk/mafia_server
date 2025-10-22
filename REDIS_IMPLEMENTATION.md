# Redis 구현 내역

## 개요
마피아 게임 서버에 Redis를 도입하여 성능 향상 및 멀티 서버 환경 지원을 구현했습니다.

## 구현된 기능

### 1. WebSocket Pub/Sub (멀티 서버 대응) ⭐ HIGH PRIORITY

**파일**:
- `RedisMessageService.java`
- `RoomWebSocketHandler.java` (수정)
- `GameWebSocketHandler.java` (수정)

**주요 기능**:
- Redis Pub/Sub을 통해 여러 서버 인스턴스 간 메시지 브로드캐스트
- 모든 WebSocket 이벤트(방 업데이트, 채팅, 게임 이벤트)가 Redis를 통해 전파
- 클라이언트가 어느 서버에 연결되어 있든 모든 메시지를 수신 가능

**사용 토픽**:
- `room:update` - 방 업데이트 및 대기실 채팅
- `game:chat` - 게임 내 채팅 (전체/마피아/유령)
- `game:event` - 게임 이벤트 (페이즈 변경, 플레이어 사망, 게임 종료)

**장점**:
- 수평 확장 가능 (여러 서버 인스턴스 동시 운영)
- 서버 장애 시에도 다른 서버로 자동 연결 가능

---

### 2. 게임 상태 캐싱

**파일**: `GameCacheService.java`

**주요 기능**:
- 진행 중인 게임 상태를 Redis에 캐싱
- TTL 30분 설정으로 자동 만료
- DB 조회 부하 감소

**사용 케이스**:
```java
// 게임 상태 조회 시
gameCacheService.getGameStateFromCache(gameId)
    .switchIfEmpty(gameRepository.findById(gameId)
        .flatMap(game -> gameCacheService.cacheGameState(gameId, game)
            .thenReturn(game)));
```

**Key 패턴**: `game:state:{gameId}`

---

### 3. 플레이어 액션 임시 저장

**파일**: `GameActionCacheService.java`

**주요 기능**:
- 페이즈 진행 중 플레이어 액션을 Redis에 임시 저장
- 페이즈 종료 시 DB에 일괄 저장 가능
- 빠른 읽기/쓰기 성능

**Key 패턴**: `game:action:{gameId}:{dayCount}:{actionType}:{actorUserId}`

**API**:
- `saveAction()` - 액션 저장
- `getActionsByType()` - 특정 타입 액션 조회
- `deleteAction()` - 액션 삭제
- `clearDayActions()` - 하루치 액션 전체 삭제

**사용 예시**:
```java
// 마피아 킬 액션 저장
gameActionCacheService.saveAction(gameId, dayCount, ActionType.MAFIA_KILL, actorUserId, targetUserId);

// 모든 마피아 킬 액션 조회
gameActionCacheService.getActionsByType(gameId, dayCount, ActionType.MAFIA_KILL)
    .collectList();
```

---

### 4. 투표 집계 (Redis Hash 활용)

**파일**: `VoteCacheService.java`

**주요 기능**:
- Redis Hash를 사용한 실시간 투표 집계
- `HINCRBY` 명령으로 원자적 카운트 증가/감소
- 투표 변경 시 자동으로 이전 투표 카운트 감소

**Key 패턴**:
- `game:vote:{gameId}:{dayCount}` - 투표 정보 (누가 누구에게 투표했는지)
- `game:vote:count:{gameId}:{dayCount}` - 투표 카운트 (각 후보별 득표수)

**API**:
- `vote()` - 투표 등록/변경
- `getVoteCounts()` - 투표 카운트 조회
- `getAllVotes()` - 모든 투표 정보 조회
- `cancelVote()` - 투표 취소
- `clearVotes()` - 투표 데이터 삭제

**장점**:
- DB 조회 없이 실시간 투표 현황 확인
- 투표 변경 시에도 일관성 보장
- 높은 성능 (초당 수만 건 처리 가능)

---

### 5. 분산 락 (스케줄러 중복 방지)

**파일**:
- `RedisLockService.java`
- `GameSchedulerService.java` (수정)

**주요 기능**:
- Redis 기반 분산 락으로 여러 서버에서 동시 작업 방지
- 자동 재시도 (최대 50회, 100ms 간격)
- 락 만료 시간 10초 설정으로 데드락 방지

**사용 케이스**:
```java
// 페이즈 전환 시 여러 서버에서 중복 실행 방지
redisLockService.executeWithLock("phase:transition:" + gameId,
    gameService.nextPhase(gameId));
```

**Lock Key 패턴**: `lock:{작업명}:{리소스ID}`

**장점**:
- 멀티 서버 환경에서도 안전한 스케줄링
- 자동 락 해제 (작업 완료 후)
- 락 대기 중 자동 재시도

---

## 설정

### application.properties
```properties
# Redis 설정
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

### RedisConfig.java
- `ReactiveRedisTemplate` 설정
- JSON 직렬화 설정 (Jackson2JsonRedisSerializer)
- Pub/Sub 토픽 빈 등록

---

## 사용 시나리오

### 시나리오 1: 멀티 서버 환경
```
서버A: 유저1, 유저2 연결
서버B: 유저3, 유저4 연결

유저1이 채팅 전송 → 서버A가 Redis Pub/Sub으로 발행
→ 서버A와 서버B 모두 메시지 수신
→ 유저1,2,3,4 모두 채팅 수신
```

### 시나리오 2: 투표 시스템
```
유저1: A에게 투표 → Redis Hash에 저장, A의 카운트 +1
유저1: B로 변경 → A의 카운트 -1, B의 카운트 +1
모든 클라이언트: 실시간으로 투표 현황 조회 (DB 부하 없음)
```

### 시나리오 3: 스케줄러 중복 방지
```
서버A, 서버B 동시에 페이즈 만료 감지
서버A: 락 획득 성공 → 페이즈 전환 실행
서버B: 락 획득 실패 → 대기 후 다른 게임 처리
```

---

## 성능 개선 효과

1. **WebSocket 메시지**: DB 조회 없이 Redis Pub/Sub으로 즉시 전달
2. **게임 상태 조회**: 캐시 히트 시 DB 조회 0회
3. **투표 집계**: DB 쿼리 대신 Redis Hash 조회 (10배 이상 빠름)
4. **액션 저장**: 페이즈당 1회 DB 저장 (기존: 액션마다 저장)
5. **스케줄러**: 락 경합 감소로 CPU 사용률 감소

---

## 주의사항

1. **Redis 의존성**: Redis 장애 시 일부 기능 사용 불가
   - WebSocket Pub/Sub: 단일 서버 모드로 동작 (fallback 필요)
   - 투표/액션: DB에 직접 저장하는 방식으로 fallback

2. **메모리 관리**: TTL 설정으로 자동 삭제되지만, 과도한 데이터 저장 주의

3. **데이터 일관성**: Redis 데이터는 임시 저장용, 영구 데이터는 DB에 저장 필수

---

## 향후 개선 사항

1. **세션 관리**: 유저 세션을 Redis에 저장하여 인증 성능 향상
2. **방 목록 캐싱**: 활성 방 목록을 Redis Sorted Set으로 관리
3. **Redis Sentinel**: Redis 고가용성 구성
4. **Redis Cluster**: 데이터 분산 저장으로 확장성 향상

---

## 테스트 방법

### Redis 실행
```bash
docker run -d -p 6379:6379 redis:latest
```

### 멀티 서버 테스트
```bash
# 서버1
SERVER_PORT=8080 ./gradlew bootRun

# 서버2
SERVER_PORT=8081 ./gradlew bootRun
```

### Redis 모니터링
```bash
redis-cli MONITOR
```
