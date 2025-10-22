-- 게임 테이블에 재판 대상자 컬럼 추가
ALTER TABLE games ADD COLUMN defendant_user_id VARCHAR(255);

-- 인덱스 추가 (조회 성능 향상)
CREATE INDEX idx_games_defendant_user_id ON games(defendant_user_id);
