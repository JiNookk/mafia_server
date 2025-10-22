-- Room 테이블 리팩토링: room_id 컬럼명을 id로 변경
-- 1. 외래키 제약 제거 (room_members, chat_messages)
ALTER TABLE room_members DROP FOREIGN KEY room_members_ibfk_1;
ALTER TABLE chat_messages DROP FOREIGN KEY chat_messages_ibfk_1;

-- 2. AUTO_INCREMENT id 컬럼 제거
ALTER TABLE rooms MODIFY COLUMN id BIGINT NOT NULL;
ALTER TABLE rooms DROP PRIMARY KEY;
ALTER TABLE rooms DROP COLUMN id;

-- 3. room_id를 id로 변경하고 PK 설정
ALTER TABLE rooms CHANGE COLUMN room_id id VARCHAR(255) NOT NULL;
ALTER TABLE rooms ADD PRIMARY KEY (id);

-- 4. 외래키 제약 재설정
ALTER TABLE room_members ADD CONSTRAINT room_members_ibfk_1
    FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE;
ALTER TABLE chat_messages ADD CONSTRAINT chat_messages_ibfk_1
    FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE;

-- Room Members 테이블 리팩토링: id를 VARCHAR(255)로 변경
-- 1. AUTO_INCREMENT 속성 제거
ALTER TABLE room_members MODIFY COLUMN id BIGINT NOT NULL;

-- 2. 기존 PK 제거
ALTER TABLE room_members DROP PRIMARY KEY;

-- 3. id를 VARCHAR(255)로 변경
ALTER TABLE room_members MODIFY COLUMN id VARCHAR(255) NOT NULL;

-- 4. PK 재설정
ALTER TABLE room_members ADD PRIMARY KEY (id);
