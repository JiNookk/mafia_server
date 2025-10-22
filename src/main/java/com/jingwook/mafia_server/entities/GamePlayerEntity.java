package com.jingwook.mafia_server.entities;

import com.jingwook.mafia_server.enums.PlayerRole;
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
@Table("game_players")
public class GamePlayerEntity implements Persistable<String> {
    @Id
    private String id;

    @Transient
    @Builder.Default
    private boolean isNew = true;

    @Column("game_id")
    private String gameId;

    @Column("user_id")
    private String userId;

    @Column("role")
    private String role;

    @Column("is_alive")
    private Boolean isAlive;

    @Column("position")
    private Integer position;

    @Column("died_at")
    private LocalDateTime diedAt;

    public PlayerRole getRoleAsEnum() {
        return PlayerRole.valueOf(this.role);
    }

    public void setRoleFromEnum(PlayerRole playerRole) {
        this.role = playerRole.toString();
    }

    @Override
    public boolean isNew() {
        return this.isNew;
    }

    public void markAsNotNew() {
        this.isNew = false;
    }

    /**
     * Entity를 Domain 객체로 변환
     */
    public com.jingwook.mafia_server.domains.GamePlayer toDomain() {
        return new com.jingwook.mafia_server.domains.GamePlayer(
                this.id,
                this.gameId,
                this.userId,
                this.getRoleAsEnum(),
                this.isAlive,
                this.position,
                this.diedAt
        );
    }
}
