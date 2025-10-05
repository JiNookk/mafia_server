package com.jingwook.mafia_server.services;

import com.jingwook.mafia_server.dtos.GetRoomListQueryDto;
import com.jingwook.mafia_server.dtos.OffsetPaginationDto;
import com.jingwook.mafia_server.dtos.OffsetPaginationMetadata;
import com.jingwook.mafia_server.dtos.RoomListResponse;
import com.jingwook.mafia_server.enums.RoomStatus;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveZSetOperations;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RoomService {
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ReactiveZSetOperations<String, String> zSetOperations;
    private final ReactiveHashOperations<String, Object, Object> hashOperations;

    public RoomService(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        zSetOperations= redisTemplate.opsForZSet();
        hashOperations= redisTemplate.opsForHash();
    }


    public Mono<OffsetPaginationDto<RoomListResponse>> getList(GetRoomListQueryDto query) {
        long page = query.getPage();
        long limit = query.getLimit();
        long start = page * limit;
        long end = (page + 1) * limit - 1;

        Mono<Long> totalCountMono = zSetOperations.size("rooms:");

        Mono<List<RoomListResponse>> roomListMono = zSetOperations
                .reverseRange("rooms:", Range.leftOpen(start, end))
                .collectList()
                .flatMapMany(roomIds -> {
                    if (roomIds.isEmpty()) {
                        return Flux.empty();
                    }

                    List<Mono<RoomListResponse>> roomMonos = roomIds
                            .stream()
                            .map(
                                    roomId ->
                                            hashOperations
                                                    .entries("room:" + roomId)
                                                    .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                                                    .map(
                                                            data -> new RoomListResponse(
                                                                    roomId,
                                                                    data.get("name").toString(),
                                                                    Integer.parseInt(data.getOrDefault("currentPlayers", "0").toString()),
                                                                    Integer.parseInt(data.getOrDefault("maxPlayers", "0").toString()),
                                                                    RoomStatus.fromKorean(data.get("status").toString())
                                                            )
                                                    )
                            )
                            .collect(Collectors.toUnmodifiableList());

                    return Flux.concat(roomMonos);
                })
                .collectList();



        return Mono.zip(roomListMono, totalCountMono)
                .map(tuple -> {
                    List<RoomListResponse> rooms = tuple.getT1();
                    Long totalCount = tuple.getT2();
                    int totalPage = (int) Math.ceil((double) totalCount / limit);

                    OffsetPaginationMetadata meta  = new OffsetPaginationMetadata((int) page, totalPage, (int) limit);

                    return new OffsetPaginationDto<>(rooms, meta);
                });
    }
}
