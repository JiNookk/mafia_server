package com.jingwook.mafia_server.dtos;

import com.jingwook.mafia_server.enums.ChatType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SendChatDto {
    @NotBlank(message = "User ID is required")
    @NotNull(message = "User ID is required")
    private String userId;

    @NotNull(message = "Chat type is required")
    private ChatType chatType;

    @NotBlank(message = "Message is required")
    @NotNull(message = "Message is required")
    private String message;
}
