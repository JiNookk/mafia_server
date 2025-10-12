-- MySQL 데이터베이스 생성 스크립트
-- 이 스크립트를 MySQL에서 실행하여 데이터베이스를 생성합니다.

CREATE DATABASE IF NOT EXISTS mafia_game
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE mafia_game;

-- 권한 부여 (필요시 사용자명과 비밀번호 변경)
-- GRANT ALL PRIVILEGES ON mafia_game.* TO 'root'@'localhost' IDENTIFIED BY 'password';
-- FLUSH PRIVILEGES;