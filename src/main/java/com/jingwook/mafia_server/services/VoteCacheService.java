package com.jingwook.mafia_server.services;

import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

/**
 * 투표를 Redis Hash로 관리하는 서비스
 * 실시간 투표 카운트를 효율적으로 처리
 */
@Service
public class VoteCacheService {
    private static final Logger log = LoggerFactory.getLogger(VoteCacheService.class);
    private static final String VOTE_PREFIX = "game:vote:";
    private static final String VOTE_COUNT_PREFIX = "game:vote:count:";
    private static final Duration VOTE_TTL = Duration.ofHours(1);

    private final ReactiveRedisTemplate<String, Object> redisTemplate;

    public VoteCacheService(ReactiveRedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 투표 등록 (기존 투표가 있으면 변경)
     * Hash key: game:vote:{gameId}:{dayCount}
     * Hash field: {voterUserId}
     * Hash value: {targetUserId}
     */
    public Mono<Void> vote(String gameId, int dayCount, String voterUserId, String targetUserId) {
        String voteKey = buildVoteKey(gameId, dayCount);
        String countKey = buildVoteCountKey(gameId, dayCount);

        // 1. 기존 투표 확인
        return redisTemplate.opsForHash().get(voteKey, voterUserId)
                .defaultIfEmpty("")
                .flatMap(oldTarget -> {
                    String oldTargetStr = oldTarget.toString();

                    // 2. 기존 투표가 있으면 카운트 감소
                    Mono<Void> decrementOld = Mono.empty();
                    if (!oldTargetStr.isEmpty() && !oldTargetStr.equals(targetUserId)) {
                        decrementOld = redisTemplate.opsForHash()
                                .increment(countKey, oldTargetStr, -1)
                                .then();
                    }

                    // 3. 새 투표 저장
                    Mono<Void> saveVote = redisTemplate.opsForHash()
                            .put(voteKey, voterUserId, targetUserId)
                            .then();

                    // 4. 새 타겟 카운트 증가
                    Mono<Void> incrementNew = redisTemplate.opsForHash()
                            .increment(countKey, targetUserId, 1)
                            .then();

                    // 5. TTL 설정
                    Mono<Void> setExpire = redisTemplate.expire(voteKey, VOTE_TTL)
                            .then(redisTemplate.expire(countKey, VOTE_TTL))
                            .then();

                    return decrementOld.then(saveVote).then(incrementNew).then(setExpire);
                })
                .doOnSuccess(v -> log.debug("Voted: {} -> {} (game:{}, day:{})",
                        voterUserId, targetUserId, gameId, dayCount))
                .doOnError(error -> log.error("Failed to vote", error))
                .then();
    }

    /**
     * 투표 카운트 조회
     */
    public Mono<Map<Object, Object>> getVoteCounts(String gameId, int dayCount) {
        String countKey = buildVoteCountKey(gameId, dayCount);

        return redisTemplate.opsForHash().entries(countKey)
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .doOnSuccess(counts -> log.debug("Retrieved vote counts for game:{}, day:{}", gameId, dayCount))
                .doOnError(error -> log.error("Failed to get vote counts", error));
    }

    /**
     * 모든 투표 정보 조회
     */
    public Mono<Map<Object, Object>> getAllVotes(String gameId, int dayCount) {
        String voteKey = buildVoteKey(gameId, dayCount);

        return redisTemplate.opsForHash().entries(voteKey)
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .doOnSuccess(votes -> log.debug("Retrieved all votes for game:{}, day:{}", gameId, dayCount))
                .doOnError(error -> log.error("Failed to get all votes", error));
    }

    /**
     * 투표 취소
     */
    public Mono<Void> cancelVote(String gameId, int dayCount, String voterUserId) {
        String voteKey = buildVoteKey(gameId, dayCount);
        String countKey = buildVoteCountKey(gameId, dayCount);

        return redisTemplate.opsForHash().get(voteKey, voterUserId)
                .flatMap(target -> {
                    // 카운트 감소
                    return redisTemplate.opsForHash()
                            .increment(countKey, target.toString(), -1)
                            .then(redisTemplate.opsForHash().remove(voteKey, voterUserId))
                            .then();
                })
                .doOnSuccess(v -> log.debug("Cancelled vote for voter: {}", voterUserId))
                .doOnError(error -> log.error("Failed to cancel vote", error))
                .then();
    }

    /**
     * 특정 일차 투표 데이터 삭제
     */
    public Mono<Void> clearVotes(String gameId, int dayCount) {
        String voteKey = buildVoteKey(gameId, dayCount);
        String countKey = buildVoteCountKey(gameId, dayCount);

        return redisTemplate.delete(voteKey)
                .then(redisTemplate.delete(countKey))
                .doOnSuccess(v -> log.debug("Cleared votes for game:{}, day:{}", gameId, dayCount))
                .doOnError(error -> log.error("Failed to clear votes", error))
                .then();
    }

    private String buildVoteKey(String gameId, int dayCount) {
        return VOTE_PREFIX + gameId + ":" + dayCount;
    }

    private String buildVoteCountKey(String gameId, int dayCount) {
        return VOTE_COUNT_PREFIX + gameId + ":" + dayCount;
    }
}
