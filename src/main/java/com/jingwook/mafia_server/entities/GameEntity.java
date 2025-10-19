package com.jingwook.mafia_server.entities;

import com.jingwook.mafia_server.domains.Game;
import com.jingwook.mafia_server.enums.GamePhase;
import com.jingwook.mafia_server.enums.Team;
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
@Table("games")
public class GameEntity implements Persistable<String> {
    @Id
    private String id;

    @Transient
    @Builder.Default
    private boolean isNew = true;

    @Column("room_id")
    private String roomId;

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

    /**
     * Entity를 Domain 객체로 변환
     */
    public Game toDomain() {
        return new Game(
                this.id,
                this.roomId,
                this.getCurrentPhaseAsEnum(),
                this.dayCount,
                this.phaseStartTime,
                this.phaseDurationSeconds,
                this.winnerTeam != null ? Team.valueOf(this.winnerTeam) : null,
                this.startedAt,
                this.finishedAt
        );
    }

    /**
     * Domain 객체의 상태를 Entity에 반영
     */
    public void updateFromDomain(Game domain) {
        this.setCurrentPhaseFromEnum(domain.getCurrentPhase());
        this.dayCount = domain.getDayCount();
        this.phaseStartTime = domain.getPhaseStartTime();
        this.phaseDurationSeconds = domain.getPhaseDurationSeconds();
        this.winnerTeam = domain.getWinnerTeam() != null ? domain.getWinnerTeam().toString() : null;
        this.finishedAt = domain.getFinishedAt();
    }

    @Override
    public boolean isNew() {
        return this.isNew;
    }

    public void markAsNotNew() {
        this.isNew = false;
    }
}
