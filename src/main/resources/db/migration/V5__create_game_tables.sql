-- Games 테이블
CREATE TABLE IF NOT EXISTS games (
    id VARCHAR(255) PRIMARY KEY,
    room_id VARCHAR(255) NOT NULL,
    current_phase VARCHAR(50) NOT NULL,
    day_count INT NOT NULL DEFAULT 1,
    phase_start_time TIMESTAMP NOT NULL,
    phase_duration_seconds INT NOT NULL,
    winner_team VARCHAR(50),
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP,

    INDEX idx_room_id (room_id),
    INDEX idx_finished_at (finished_at),
    FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Game Players 테이블
CREATE TABLE IF NOT EXISTS game_players (
    id VARCHAR(255) PRIMARY KEY,
    game_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    is_alive BOOLEAN NOT NULL DEFAULT TRUE,
    position INT NOT NULL,
    died_at TIMESTAMP,

    UNIQUE KEY unique_game_user (game_id, user_id),
    INDEX idx_game_id (game_id),
    INDEX idx_user_id (user_id),
    INDEX idx_game_alive (game_id, is_alive),
    INDEX idx_game_role (game_id, role),
    FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Game Actions 테이블
CREATE TABLE IF NOT EXISTS game_actions (
    id VARCHAR(255) PRIMARY KEY,
    game_id VARCHAR(255) NOT NULL,
    day_count INT NOT NULL,
    phase VARCHAR(50) NOT NULL,
    type VARCHAR(50) NOT NULL,
    actor_user_id VARCHAR(255) NOT NULL,
    target_user_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_game_day_type (game_id, day_count, type),
    INDEX idx_game_day_phase (game_id, day_count, phase),
    INDEX idx_actor (actor_user_id),
    INDEX idx_target (target_user_id),
    FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE,
    FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (target_user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
