package com.jingwook.mafia_server.entities;

import com.jingwook.mafia_server.enums.RoomStatus;
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
@Table("rooms")
public class RoomEntity implements Persistable<String> {
    @Id
    private String id;

    @Transient
    @Builder.Default
    private boolean isNew = true;

    @Column("name")
    private String name;

    @Column("max_players")
    private Integer maxPlayers;

    @Column("status")
    private String status;

    @Column("host_user_id")
    private String hostUserId;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    public RoomStatus getStatusAsEnum() {
        return RoomStatus.valueOf(this.status);
    }

    public void setStatusFromEnum(RoomStatus roomStatus) {
        this.status = roomStatus.toString();
    }

    @Override
    public boolean isNew() {
        return this.isNew;
    }

    public void markAsNotNew() {
        this.isNew = false;
    }
}