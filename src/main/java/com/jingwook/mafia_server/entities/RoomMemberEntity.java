package com.jingwook.mafia_server.entities;

import com.jingwook.mafia_server.enums.GameRole;
import com.jingwook.mafia_server.enums.ParticipatingRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("room_members")
public class RoomMemberEntity implements Persistable<String> {
    @Id
    private String id;

    @Transient
    @Builder.Default
    private boolean isNew = true;

    @Column("room_id")
    private String roomId;

    @Column("user_id")
    private String userId;

    @Column("role")
    private String role;

    @Column("game_role")
    private String gameRole;

    @Column("is_alive")
    private Boolean isAlive;

    @Column("joined_at")
    private LocalDateTime joinedAt;

    public ParticipatingRole getRoleAsEnum() {
        return ParticipatingRole.valueOf(this.role);
    }

    public void setRoleFromEnum(ParticipatingRole participatingRole) {
        this.role = participatingRole.toString();
    }

    public GameRole getGameRoleAsEnum() {
        return gameRole != null ? GameRole.valueOf(this.gameRole) : null;
    }

    public void setGameRoleFromEnum(GameRole gameRole) {
        this.gameRole = gameRole != null ? gameRole.toString() : null;
    }

    @Override
    public boolean isNew() {
        return this.isNew;
    }

    public void markAsNotNew() {
        this.isNew = false;
    }
}