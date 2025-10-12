package com.jingwook.mafia_server.services;

import static com.jingwook.mafia_server.utils.Constants.MAX_PLAYERS;
import static com.jingwook.mafia_server.utils.Constants.MIN_PLAYERS;
import static com.jingwook.mafia_server.utils.Constants.ROOM_PREFIX;
import static com.jingwook.mafia_server.utils.Constants.USER_PREFIX;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveZSetOperations;
import org.springframework.stereotype.Service;

import com.github.f4b6a3.uuid.UuidCreator;
import com.jingwook.mafia_server.domains.Room;
import com.jingwook.mafia_server.domains.RoomMember;
import com.jingwook.mafia_server.dtos.CreateRoomDto;
import com.jingwook.mafia_server.dtos.GetRoomListQueryDto;
import com.jingwook.mafia_server.dtos.OffsetPaginationDto;
import com.jingwook.mafia_server.dtos.OffsetPaginationMetadata;
import com.jingwook.mafia_server.dtos.RoomDetailResponse;
import com.jingwook.mafia_server.dtos.RoomListResponse;
import com.jingwook.mafia_server.dtos.RoomMemberResponse;
import com.jingwook.mafia_server.enums.ParticipatingRole;
import com.jingwook.mafia_server.enums.RoomStatus;
import com.jingwook.mafia_server.repositories.UserRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class RoomService {
        private final ReactiveRedisTemplate<String, String> redisTemplate;
        private final ReactiveZSetOperations<String, String> zSetOperations;
        private final ReactiveHashOperations<String, Object, Object> hashOperations;
        private final UserRepository userRepository;

        public RoomService(ReactiveRedisTemplate<String, String> redisTemplate, UserRepository userRepository) {
                this.redisTemplate = redisTemplate;
                this.userRepository = userRepository;
                zSetOperations = redisTemplate.opsForZSet();
                hashOperations = redisTemplate.opsForHash();
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
                                                                        roomId -> hashOperations
                                                                                        .entries("room:" + roomId)
                                                                                        .collectMap(Map.Entry::getKey,
                                                                                                        Map.Entry::getValue)
                                                                                        .map(
                                                                                                        data -> new RoomListResponse(
                                                                                                                        roomId,
                                                                                                                        data.get("name").toString(),
                                                                                                                        Integer.parseInt(
                                                                                                                                        data.getOrDefault(
                                                                                                                                                        "currentPlayers",
                                                                                                                                                        "0")
                                                                                                                                                        .toString()),
                                                                                                                        Integer.parseInt(
                                                                                                                                        data.getOrDefault(
                                                                                                                                                        "maxPlayers",
                                                                                                                                                        "0")
                                                                                                                                                        .toString()),
                                                                                                                        RoomStatus.fromKorean(
                                                                                                                                        data.get("status")
                                                                                                                                                        .toString()))))
                                                        .collect(Collectors.toUnmodifiableList());

                                        return Flux.concat(roomMonos);
                                })
                                .collectList();

                return Mono.zip(roomListMono, totalCountMono)
                                .map(tuple -> {
                                        List<RoomListResponse> rooms = tuple.getT1();
                                        Long totalCount = tuple.getT2();
                                        int totalPage = (int) Math.ceil((double) totalCount / limit);

                                        OffsetPaginationMetadata meta = new OffsetPaginationMetadata((int) page,
                                                        totalPage, (int) limit);

                                        return new OffsetPaginationDto<>(rooms, meta);
                                });
        }

        public Mono<RoomDetailResponse> create(CreateRoomDto body) {
                String userName = body.getUsername();
                String roomName = body.getRoomName();

                return userRepository.findByUsername(userName)
                                .flatMap(user -> {
                                        String roomId = UuidCreator.getTimeOrderedEpoch().toString();
                                        Room room = new Room(roomId, roomName, MIN_PLAYERS, MAX_PLAYERS,
                                                        RoomStatus.AVAILABLE, user.getSessionId(), LocalDateTime.now());

                                        RoomMember roomMember = new RoomMember(user.getSessionId(), roomId,
                                                        ParticipatingRole.HOST);

                                        return redisTemplate.opsForValue()
                                                        .get(USER_PREFIX + user.getSessionId() + ":rooms")
                                                        .flatMap(exists -> {
                                                                if (exists != null) {
                                                                        return Mono.error(new RuntimeException(
                                                                                        "User is already in a room"));
                                                                }
                                                                return Mono.when(
                                                                                redisTemplate.opsForValue()
                                                                                                .set(USER_PREFIX + user
                                                                                                                .getSessionId()
                                                                                                                + ":rooms",
                                                                                                                roomId),
                                                                                redisTemplate.opsForHash().putAll(
                                                                                                ROOM_PREFIX + roomId,
                                                                                                room.toMap()),
                                                                                redisTemplate.opsForHash().putAll(
                                                                                                ROOM_PREFIX + roomId + ":member:" + roomMember.getPlayerId(),
                                                                                                roomMember.toMap()),
                                                                                redisTemplate.opsForZSet().add(
                                                                                                "rooms:",
                                                                                                roomId,
                                                                                                System.currentTimeMillis()),
                                                                                redisTemplate.opsForZSet().add(
                                                                                                ROOM_PREFIX + roomId
                                                                                                                + ":members",
                                                                                                roomMember.getPlayerId(),
                                                                                                System.currentTimeMillis()))
                                                                                .thenReturn(new RoomDetailResponse(
                                                                                                room.getId(),
                                                                                                room.getName(),
                                                                                                List.of(new RoomMemberResponse(
                                                                                                                roomMember.getPlayerId(),
                                                                                                                roomMember.getRole())),
                                                                                                room.getCurrentPlayers(),
                                                                                                room.getMaxPlayers()));
                                                        });
                                });

        }
}
