-- chat_messages 테이블을 room과 game 모두 지원하도록 리팩토링
-- 1. room_id를 context_id로 변경
ALTER TABLE chat_messages CHANGE COLUMN room_id context_id VARCHAR(255) NOT NULL;
-- 2. 기존 인덱스 삭제
DROP INDEX idx_room_created ON chat_messages;
DROP INDEX idx_room_type ON chat_messages;
-- 3. 새 인덱스 생성
CREATE INDEX idx_context_created ON chat_messages(context_id, created_at);
CREATE INDEX idx_context_type ON chat_messages(context_id, chat_type, created_at);