package com.jingwook.mafia_server.config;

import com.jingwook.mafia_server.handlers.GameWebSocketHandler;
import com.jingwook.mafia_server.handlers.RoomWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class WebSocketConfig {

    @Bean
    public HandlerMapping webSocketHandlerMapping(
            RoomWebSocketHandler roomWebSocketHandler,
            GameWebSocketHandler gameWebSocketHandler) {

        Map<String, WebSocketHandler> map = new HashMap<>();
        map.put("/ws/rooms/*", roomWebSocketHandler);
        map.put("/ws/games/*/all", gameWebSocketHandler);
        map.put("/ws/games/*/mafia", gameWebSocketHandler);
        map.put("/ws/games/*/dead", gameWebSocketHandler);

        SimpleUrlHandlerMapping handlerMapping = new SimpleUrlHandlerMapping();
        handlerMapping.setOrder(1);
        handlerMapping.setUrlMap(map);
        return handlerMapping;
    }

    @Bean
    public WebSocketHandlerAdapter handlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
