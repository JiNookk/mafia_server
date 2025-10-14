package com.jingwook.mafia_server.services;

import static com.jingwook.mafia_server.utils.Constants.MAX_PLAYERS;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.github.f4b6a3.uuid.UuidCreator;
import com.jingwook.mafia_server.dtos.CreateRoomDto;
import com.jingwook.mafia_server.dtos.GetRoomListQueryDto;
import com.jingwook.mafia_server.dtos.JoinRoomDto;
import com.jingwook.mafia_server.dtos.OffsetPaginationDto;
import com.jingwook.mafia_server.dtos.OffsetPaginationMetadata;
import com.jingwook.mafia_server.dtos.RoomDetailResponse;
import com.jingwook.mafia_server.dtos.RoomListResponse;
import com.jingwook.mafia_server.dtos.RoomMemberResponse;
import com.jingwook.mafia_server.entities.RoomEntity;
import com.jingwook.mafia_server.entities.RoomMemberEntity;
import com.jingwook.mafia_server.enums.ParticipatingRole;
import com.jingwook.mafia_server.enums.RoomStatus;
import com.jingwook.mafia_server.events.RoomUpdateEvent;
import com.jingwook.mafia_server.exceptions.GameAlreadyStartedException;
import com.jingwook.mafia_server.exceptions.RoomFullException;
import com.jingwook.mafia_server.repositories.RoomMemberR2dbcRepository;
import com.jingwook.mafia_server.repositories.RoomR2dbcRepository;
import com.jingwook.mafia_server.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class RoomService {
        private static final Logger log = LoggerFactory.getLogger(RoomService.class);

        private final RoomR2dbcRepository roomR2dbcRepository;
        private final RoomMemberR2dbcRepository roomMemberR2dbcRepository;
        private final UserRepository userRepository;
        private final ApplicationEventPublisher eventPublisher;

        public RoomService(RoomR2dbcRepository roomR2dbcRepository,
                        RoomMemberR2dbcRepository roomMemberR2dbcRepository,
                        UserRepository userRepository,
                        ApplicationEventPublisher eventPublisher) {
                this.roomR2dbcRepository = roomR2dbcRepository;
                this.roomMemberR2dbcRepository = roomMemberR2dbcRepository;
                this.userRepository = userRepository;
                this.eventPublisher = eventPublisher;
        }

        public Mono<OffsetPaginationDto<RoomListResponse>> getList(GetRoomListQueryDto query) {
                long page = query.getPage();
                long limit = query.getLimit();
                long offset = page * limit;

                Mono<Long> totalCountMono = roomR2dbcRepository.countAll();

                Flux<RoomListResponse> roomListFlux = roomR2dbcRepository
                                .findAllWithPagination(offset, limit)
                                .flatMap(roomEntity -> roomMemberR2dbcRepository.countByRoomId(roomEntity.getRoomId())
                                                .map(currentPlayers -> new RoomListResponse(
                                                                roomEntity.getRoomId(),
                                                                roomEntity.getName(),
                                                                currentPlayers.intValue(),
                                                                roomEntity.getMaxPlayers(),
                                                                roomEntity.getStatusAsEnum())));

                return Mono.zip(roomListFlux.collectList(), totalCountMono)
                                .map(tuple -> {
                                        List<RoomListResponse> rooms = tuple.getT1();
                                        Long totalCount = tuple.getT2();
                                        int totalPage = (int) Math.ceil((double) totalCount / limit);

                                        OffsetPaginationMetadata meta = new OffsetPaginationMetadata((int) page,
                                                        totalPage, (int) limit);

                                        return new OffsetPaginationDto<>(rooms, meta);
                                });
        }

        private Mono<Boolean> checkUserInRoom(String userId) {
                return roomMemberR2dbcRepository.existsByUserId(userId);
        }

        @Transactional
        public Mono<RoomDetailResponse> create(CreateRoomDto body) {
                String userName = body.getUsername();
                String roomName = body.getRoomName();

                return userRepository.findByUsername(userName)
                                .flatMap(user -> checkUserInRoom(user.getId())
                                                .flatMap(exist -> exist
                                                                ? Mono.error(new ResponseStatusException(
                                                                                HttpStatus.BAD_REQUEST,
                                                                                "User is already in a room"))
                                                                : Mono.just(user)))
                                .flatMap(user -> createRoomWithHost(roomName, user));
        }

        private Mono<RoomDetailResponse> createRoomWithHost(String roomName,
                        com.jingwook.mafia_server.domains.User user) {
                String roomId = UuidCreator.getTimeOrderedEpoch().toString();
                LocalDateTime now = LocalDateTime.now();

                RoomEntity roomEntity = buildRoomEntity(roomId, roomName, user.getId(), now);
                RoomMemberEntity roomMemberEntity = buildHostMemberEntity(roomId, user.getId(), now);

                return saveRoomAndMember(roomEntity, roomMemberEntity)
                                .then(Mono.just(buildInitialRoomResponse(roomId, roomName, user)))
                                .doOnSuccess(response ->
                                        eventPublisher.publishEvent(new RoomUpdateEvent(roomId, response)));
        }

        private RoomEntity buildRoomEntity(String roomId, String roomName, String userId, LocalDateTime now) {
                return RoomEntity.builder()
                                .roomId(roomId)
                                .name(roomName)
                                .maxPlayers(MAX_PLAYERS)
                                .status(RoomStatus.AVAILABLE.toString())
                                .hostUserId(userId)
                                .createdAt(now)
                                .updatedAt(now)
                                .build();
        }

        private RoomMemberEntity buildHostMemberEntity(String roomId, String userId, LocalDateTime now) {
                return RoomMemberEntity.builder()
                                .roomId(roomId)
                                .userId(userId)
                                .role(ParticipatingRole.HOST.toString())
                                .joinedAt(now)
                                .build();
        }

        private Mono<Void> saveRoomAndMember(RoomEntity roomEntity, RoomMemberEntity roomMemberEntity) {
                return Mono.zip(
                                roomR2dbcRepository.save(roomEntity),
                                roomMemberR2dbcRepository.save(roomMemberEntity))
                                .then();
        }

        private RoomDetailResponse buildInitialRoomResponse(String roomId, String roomName,
                        com.jingwook.mafia_server.domains.User user) {
                return new RoomDetailResponse(
                                roomId,
                                roomName,
                                List.of(new RoomMemberResponse(
                                                user.getId(),
                                                user.getNickname(),
                                                ParticipatingRole.HOST)),
                                1,
                                MAX_PLAYERS);
        }

        private Mono<Void> validateRoomStatus(RoomEntity roomEntity) {
                if (!roomEntity.getStatusAsEnum().equals(RoomStatus.AVAILABLE)) {
                        return Mono.error(new GameAlreadyStartedException("Game has already started"));
                }
                return Mono.empty();
        }

        private Mono<Void> validateRoomCapacity(String roomId, int maxPlayers) {
                return roomMemberR2dbcRepository.countByRoomId(roomId)
                                .flatMap(currentPlayers -> {
                                        if (currentPlayers >= maxPlayers) {
                                                return Mono.error(new RoomFullException("Room is full"));
                                        }
                                        return Mono.empty();
                                });
        }

        private Mono<RoomMemberEntity> createAndSaveRoomMember(String roomId, String userId) {
                LocalDateTime now = LocalDateTime.now();
                RoomMemberEntity roomMemberEntity = RoomMemberEntity.builder()
                                .roomId(roomId)
                                .userId(userId)
                                .role(ParticipatingRole.PARTICIPANT.toString())
                                .joinedAt(now)
                                .build();

                return roomMemberR2dbcRepository.save(roomMemberEntity);
        }

        private Mono<RoomDetailResponse> buildRoomDetailResponse(RoomEntity roomEntity) {
                return roomMemberR2dbcRepository.findByRoomId(roomEntity.getRoomId())
                                .flatMap(member -> userRepository.findById(member.getUserId())
                                                .map(user -> new RoomMemberResponse(
                                                                member.getUserId(),
                                                                user.getNickname(),
                                                                member.getRoleAsEnum())))
                                .collectList()
                                .map(members -> new RoomDetailResponse(
                                                roomEntity.getRoomId(),
                                                roomEntity.getName(),
                                                members,
                                                members.size(),
                                                roomEntity.getMaxPlayers()));
        }

        @Transactional
        public Mono<RoomDetailResponse> joinRoom(JoinRoomDto body) {
                String userName = body.getUsername();
                String roomId = body.getRoomId();

                return userRepository.findByUsername(userName)
                                .flatMap(user -> checkUserInRoom(user.getId())
                                                .flatMap(exist -> exist
                                                                ? Mono.error(new ResponseStatusException(
                                                                                HttpStatus.BAD_REQUEST,
                                                                                "User is already in a room"))
                                                                : Mono.just(user)))
                                .flatMap(user -> roomR2dbcRepository.findByRoomIdForUpdate(roomId)
                                                .switchIfEmpty(Mono.error(new ResponseStatusException(
                                                                HttpStatus.NOT_FOUND,
                                                                "Room not found")))
                                                .flatMap(roomEntity -> validateRoomStatus(roomEntity)
                                                                .then(validateRoomCapacity(roomId,
                                                                                roomEntity.getMaxPlayers()))
                                                                .then(createAndSaveRoomMember(roomId, user.getId()))
                                                                .then(buildRoomDetailResponse(roomEntity))
                                                                .doOnSuccess(response ->
                                                                        eventPublisher.publishEvent(new RoomUpdateEvent(roomId, response)))));
        }

        public Mono<RoomDetailResponse> getDetail(String roomId) {
                return roomR2dbcRepository.findByRoomId(roomId)
                                .switchIfEmpty(Mono.error(new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Room not found")))
                                .flatMap(this::buildRoomDetailResponse);
        }

        private Mono<Void> validateUserInRoom(String roomId, String userId) {
                return roomMemberR2dbcRepository.existsByRoomIdAndUserId(roomId, userId)
                                .flatMap(exists -> {
                                        if (!exists) {
                                                return Mono.error(new ResponseStatusException(
                                                                HttpStatus.BAD_REQUEST,
                                                                "User is not in this room"));
                                        }
                                        return Mono.empty();
                                });
        }

        private Mono<Void> removeRoomMember(String roomId, String userId) {
                return roomMemberR2dbcRepository.deleteByRoomIdAndUserId(roomId, userId);
        }

        @Transactional
        public Mono<Void> leaveRoom(String roomId, String userId) {
                return roomR2dbcRepository.findByRoomIdForUpdate(roomId)
                                .switchIfEmpty(Mono.error(new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Room not found")))
                                .flatMap(roomEntity -> validateUserInRoom(roomId, userId)
                                                .then(removeRoomMember(roomId, userId))
                                                .then(roomMemberR2dbcRepository.countByRoomId(roomId))
                                                .flatMap(remainingMembers -> {
                                                        if (remainingMembers == 0) {
                                                                return roomR2dbcRepository.deleteById(roomEntity.getId())
                                                                                .then();
                                                        }
                                                        // 방이 남아있으면 업데이트 이벤트 발행
                                                        log.info("leaveRoom: Publishing room update event for roomId: {}", roomId);
                                                        return buildRoomDetailResponse(roomEntity)
                                                                .flatMap(detail -> {
                                                                        log.info("leaveRoom: Event publishing with {} members", detail.getMembers().size());
                                                                        eventPublisher.publishEvent(new RoomUpdateEvent(roomId, detail));
                                                                        return Mono.empty();
                                                                });
                                                }));
        }
}
