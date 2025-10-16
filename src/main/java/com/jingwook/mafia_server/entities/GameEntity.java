package com.jingwook.mafia_server.entities;

import com.jingwook.mafia_server.enums.GamePhase;
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
@Table("games")
public class GameEntity {
    @Id
    private Long id;

    @Column("room_id")
    private Long roomId;

    @Column("current_phase")
    private String currentPhase;

    @Column("day_count")
    private Integer dayCount;

    @Column("phase_start_time")
    private LocalDateTime phaseStartTime;

    @Column("phase_duration_seconds")
    private Integer phaseDurationSeconds;

    @Column("winner_team")
    private String winnerTeam;

    @Column("started_at")
    private LocalDateTime startedAt;

    @Column("finished_at")
    private LocalDateTime finishedAt;

    public GamePhase getCurrentPhaseAsEnum() {
        return GamePhase.valueOf(this.currentPhase);
    }

    public void setCurrentPhaseFromEnum(GamePhase gamePhase) {
        this.currentPhase = gamePhase.toString();
    }

    public boolean isFinished() {
        return this.finishedAt != null;
    }
}
