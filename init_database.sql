-- ========================================
-- 마피아 게임 서버 데이터베이스 초기화 스크립트
-- ========================================

-- 데이터베이스 생성 (존재하지 않을 경우)
CREATE DATABASE IF NOT EXISTS mafia_game
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE mafia_game;

-- ========================================
-- 1. Users 테이블
-- ========================================
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(255) PRIMARY KEY COMMENT '사용자 고유 ID',
    nickname VARCHAR(100) UNIQUE NOT NULL COMMENT '사용자 닉네임',
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '가입 시간',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시간',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시간',
    INDEX idx_nickname (nickname)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- 2. Rooms 테이블
-- ========================================
CREATE TABLE IF NOT EXISTS rooms (
    id VARCHAR(255) PRIMARY KEY COMMENT '방 고유 ID',
    name VARCHAR(255) NOT NULL COMMENT '방 이름',
    max_players INT NOT NULL COMMENT '최대 플레이어 수',
    status VARCHAR(50) NOT NULL COMMENT '방 상태 (AVAILABLE, FULL, STARTED)',
    host_user_id VARCHAR(255) NOT NULL COMMENT '방장 유저 ID',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시간',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시간',
    INDEX idx_status (status),
    INDEX idx_created_at (created_at DESC),
    FOREIGN KEY (host_user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- 3. Room Members 테이블 (Room과 User의 Many-to-Many 관계)
-- ========================================
CREATE TABLE IF NOT EXISTS room_members (
    id VARCHAR(255) PRIMARY KEY COMMENT '방 멤버 고유 ID',
    room_id VARCHAR(255) NOT NULL COMMENT '방 ID',
    user_id VARCHAR(255) NOT NULL COMMENT '유저 ID',
    role VARCHAR(50) NOT NULL COMMENT '방 내 역할 (HOST, MEMBER)',
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '참여 시간',
    game_role VARCHAR(20) DEFAULT NULL COMMENT '게임 역할 (MAFIA, DOCTOR, POLICE, CITIZEN)',
    is_alive BOOLEAN DEFAULT TRUE COMMENT '생존 여부',
    UNIQUE KEY unique_room_member (room_id, user_id),
    INDEX idx_room_id (room_id),
    INDEX idx_user_id (user_id),
    FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- 4. Games 테이블
-- ========================================
CREATE TABLE IF NOT EXISTS games (
    id VARCHAR(255) PRIMARY KEY COMMENT '게임 고유 ID',
    room_id VARCHAR(255) NOT NULL COMMENT '방 ID',
    current_phase VARCHAR(50) NOT NULL COMMENT '현재 게임 페이즈 (NIGHT, DAY, VOTE, DEFENSE, RESULT)',
    day_count INT NOT NULL DEFAULT 1 COMMENT '현재 날짜',
    phase_start_time TIMESTAMP NOT NULL COMMENT '페이즈 시작 시간',
    phase_duration_seconds INT NOT NULL COMMENT '페이즈 지속 시간(초)',
    winner_team VARCHAR(50) COMMENT '승리 팀 (MAFIA, CITIZEN)',
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '게임 시작 시간',
    finished_at TIMESTAMP COMMENT '게임 종료 시간',
    defendant_user_id VARCHAR(255) COMMENT '재판 대상자 ID',
    INDEX idx_room_id (room_id),
    INDEX idx_finished_at (finished_at),
    INDEX idx_defendant_user_id (defendant_user_id),
    FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- 5. Game Players 테이블
-- ========================================
CREATE TABLE IF NOT EXISTS game_players (
    id VARCHAR(255) PRIMARY KEY COMMENT '게임 플레이어 고유 ID',
    game_id VARCHAR(255) NOT NULL COMMENT '게임 ID',
    user_id VARCHAR(255) NOT NULL COMMENT '유저 ID',
    role VARCHAR(50) NOT NULL COMMENT '게임 역할 (MAFIA, DOCTOR, POLICE, CITIZEN)',
    is_alive BOOLEAN NOT NULL DEFAULT TRUE COMMENT '생존 여부',
    position INT NOT NULL COMMENT '플레이어 위치',
    died_at TIMESTAMP COMMENT '사망 시간',
    UNIQUE KEY unique_game_user (game_id, user_id),
    INDEX idx_game_id (game_id),
    INDEX idx_user_id (user_id),
    INDEX idx_game_alive (game_id, is_alive),
    INDEX idx_game_role (game_id, role),
    FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- 6. Game Actions 테이블
-- ========================================
CREATE TABLE IF NOT EXISTS game_actions (
    id VARCHAR(255) PRIMARY KEY COMMENT '게임 행동 고유 ID',
    game_id VARCHAR(255) NOT NULL COMMENT '게임 ID',
    day_count INT NOT NULL COMMENT '날짜',
    phase VARCHAR(50) NOT NULL COMMENT '페이즈',
    type VARCHAR(50) NOT NULL COMMENT '행동 타입 (VOTE, MAFIA_KILL, DOCTOR_HEAL, POLICE_CHECK, FINAL_VOTE)',
    actor_user_id VARCHAR(255) NOT NULL COMMENT '행동 주체 유저 ID',
    target_user_id VARCHAR(255) NOT NULL COMMENT '행동 대상 유저 ID',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '행동 시간',
    INDEX idx_game_day_type (game_id, day_count, type),
    INDEX idx_game_day_phase (game_id, day_count, phase),
    INDEX idx_actor (actor_user_id),
    INDEX idx_target (target_user_id),
    FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE,
    FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (target_user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- 7. Chat Messages 테이블
-- ========================================
CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '채팅 메시지 고유 ID',
    context_id VARCHAR(255) NOT NULL COMMENT '컨텍스트 ID (room_id 또는 game_id)',
    user_id VARCHAR(255) NOT NULL COMMENT '유저 ID',
    chat_type VARCHAR(20) NOT NULL COMMENT '채팅 타입 (ROOM, GAME, MAFIA)',
    message TEXT NOT NULL COMMENT '메시지 내용',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시간',
    INDEX idx_context_created (context_id, created_at),
    INDEX idx_context_type (context_id, chat_type, created_at),
    FOREIGN KEY (context_id) REFERENCES rooms(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
