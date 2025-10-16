package com.jingwook.mafia_server.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RegisterActionDto {
    @NotNull(message = "타입은 필수입니다")
    private String type;

    @NotNull(message = "대상 유저 ID는 필수입니다")
    private String targetUserId;

    private String actorUserId;  // 서버에서 세션으로 채움
}
