package com.jingwook.mafia_server.entities;

import com.jingwook.mafia_server.enums.ActionType;
import com.jingwook.mafia_server.enums.GamePhase;
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
@Table("game_actions")
public class GameActionEntity implements Persistable<String> {
    @Id
    private String id;

    @Transient
    @Builder.Default
    private boolean isNew = true;

    @Column("game_id")
    private String gameId;

    @Column("day_count")
    private Integer dayCount;

    @Column("phase")
    private String phase;

    @Column("type")
    private String type;

    @Column("actor_user_id")
    private String actorUserId;

    @Column("target_user_id")
    private String targetUserId;

    @Column("created_at")
    private LocalDateTime createdAt;

    public GamePhase getPhaseAsEnum() {
        return GamePhase.valueOf(this.phase);
    }

    public void setPhaseFromEnum(GamePhase gamePhase) {
        this.phase = gamePhase.toString();
    }

    public ActionType getTypeAsEnum() {
        return ActionType.valueOf(this.type);
    }

    public void setTypeFromEnum(ActionType actionType) {
        this.type = actionType.toString();
    }

    @Override
    public boolean isNew() {
        return this.isNew;
    }

    public void markAsNotNew() {
        this.isNew = false;
    }
}
