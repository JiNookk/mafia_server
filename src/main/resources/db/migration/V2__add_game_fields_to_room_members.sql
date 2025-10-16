-- room_members 테이블에 게임 관련 컬럼 추가
ALTER TABLE room_members
ADD COLUMN game_role VARCHAR(20) DEFAULT NULL,
ADD COLUMN is_alive BOOLEAN DEFAULT TRUE;
