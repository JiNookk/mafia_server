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
import com.jingwook.mafia_server.exceptions.GameAlreadyStartedException;
import com.jingwook.mafia_server.exceptions.RoomFullException;
import com.jingwook.mafia_server.repositories.RoomMemberR2dbcRepository;
import com.jingwook.mafia_server.repositories.RoomR2dbcRepository;
import com.jingwook.mafia_server.repositories.UserRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class RoomService {
        private final RoomR2dbcRepository roomR2dbcRepository;
        private final RoomMemberR2dbcRepository roomMemberR2dbcRepository;
        private final UserRepository userRepository;

        public RoomService(RoomR2dbcRepository roomR2dbcRepository,
                        RoomMemberR2dbcRepository roomMemberR2dbcRepository,
                        UserRepository userRepository) {
                this.roomR2dbcRepository = roomR2dbcRepository;
                this.roomMemberR2dbcRepository = roomMemberR2dbcRepository;
                this.userRepository = userRepository;
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
                                .flatMap(user -> {
                                        String roomId = UuidCreator.getTimeOrderedEpoch().toString();
                                        LocalDateTime now = LocalDateTime.now();

                                        // Room Entity 생성 및 저장
                                        RoomEntity roomEntity = RoomEntity.builder()
                                                        .roomId(roomId)
                                                        .name(roomName)
                                                        .maxPlayers(MAX_PLAYERS)
                                                        .status(RoomStatus.AVAILABLE.toString())
                                                        .hostUserId(user.getId())
                                                        .createdAt(now)
                                                        .updatedAt(now)
                                                        .build();

                                        // Room Member Entity 생성 및 저장
                                        RoomMemberEntity roomMemberEntity = RoomMemberEntity.builder()
                                                        .roomId(roomId)
                                                        .userId(user.getId())
                                                        .role(ParticipatingRole.HOST.toString())
                                                        .joinedAt(now)
                                                        .build();

                                        return Mono.zip(
                                                        roomR2dbcRepository.save(roomEntity),
                                                        roomMemberR2dbcRepository.save(roomMemberEntity))
                                                        .then(Mono.just(new RoomDetailResponse(
                                                                        roomId,
                                                                        roomName,
                                                                        List.of(new RoomMemberResponse(
                                                                                        user.getId(),
                                                                                        ParticipatingRole.HOST)),
                                                                        1,
                                                                        MAX_PLAYERS)));
                                });
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
                                .collectList()
                                .map(members -> new RoomDetailResponse(
                                                roomEntity.getRoomId(),
                                                roomEntity.getName(),
                                                members.stream()
                                                                .map(m -> new RoomMemberResponse(
                                                                                m.getUserId(),
                                                                                m.getRoleAsEnum()))
                                                                .toList(),
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
                                                                .then(buildRoomDetailResponse(roomEntity))));
        }

        public Mono<RoomDetailResponse> getDetail(String roomId) {
                return roomR2dbcRepository.findByRoomId(roomId)
                                .switchIfEmpty(Mono.error(new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Room not found")))
                                .flatMap(roomEntity -> roomMemberR2dbcRepository.findByRoomId(roomId)
                                                .collectList()
                                                .map(members -> new RoomDetailResponse(
                                                                roomEntity.getRoomId(),
                                                                roomEntity.getName(),
                                                                members.stream()
                                                                                .map(m -> new RoomMemberResponse(
                                                                                                m.getUserId(),
                                                                                                m.getRoleAsEnum()))
                                                                                .toList(),
                                                                members.size(),
                                                                roomEntity.getMaxPlayers())));
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

        private Mono<Void> deleteRoomIfEmpty(Long roomEntityId, String roomId) {
                return roomMemberR2dbcRepository.countByRoomId(roomId)
                                .flatMap(remainingMembers -> {
                                        if (remainingMembers == 0) {
                                                return roomR2dbcRepository.deleteById(roomEntityId).then();
                                        }
                                        return Mono.empty();
                                });
        }

        @Transactional
        public Mono<Void> leaveRoom(String roomId, String userId) {
                return roomR2dbcRepository.findByRoomIdForUpdate(roomId)
                                .switchIfEmpty(Mono.error(new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Room not found")))
                                .flatMap(roomEntity -> validateUserInRoom(roomId, userId)
                                                .then(removeRoomMember(roomId, userId))
                                                .then(deleteRoomIfEmpty(roomEntity.getId(), roomId)));
        }
}
