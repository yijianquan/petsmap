package com.wujia.pet.config;

import com.wujia.pet.service.MiniAppRealtimeService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final MiniAppRealtimeService realtime;
    public WebSocketConfig(MiniAppRealtimeService realtime){this.realtime=realtime;}
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry){registry.addHandler(realtime,"/miniapp/ws").setAllowedOriginPatterns("*");}
}
