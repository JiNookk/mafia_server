package com.jingwook.mafia_server.entities;

import com.jingwook.mafia_server.enums.GameRole;
import com.jingwook.mafia_server.enums.ParticipatingRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("room_members")
public class RoomMemberEntity {
    @Id
    private String id;

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
}