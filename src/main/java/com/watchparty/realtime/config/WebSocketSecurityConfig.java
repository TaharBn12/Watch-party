package com.watchparty.realtime.config;

import com.watchparty.config.CorsConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketSecurityConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthHandshakeInterceptor handshakeInterceptor;
    private final WebSocketAuthChannelInterceptor channelInterceptor;
    private final String allowedOrigins;

    public WebSocketSecurityConfig(
            WebSocketAuthHandshakeInterceptor handshakeInterceptor,
            WebSocketAuthChannelInterceptor channelInterceptor,
            @Value("${watchparty.cors.allowed-origins:http://localhost:8080}") String allowedOrigins
    ) {
        this.handshakeInterceptor = handshakeInterceptor;
        this.channelInterceptor = channelInterceptor;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(CorsConfig.parseCsv(allowedOrigins).toArray(String[]::new))
                .addInterceptors(handshakeInterceptor);

        registry.addEndpoint("/ws-sockjs")
                .setAllowedOriginPatterns(CorsConfig.parseCsv(allowedOrigins).toArray(String[]::new))
                .addInterceptors(handshakeInterceptor)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(channelInterceptor);
    }

    /**
     * إعداد بسيط فقط حتى تكون طبقة أمان WebSocket قابلة للتفعيل الآن.
     * تفاصيل بروتوكول الرسائل والوجهات النهائية ستُوثق وتُستكمل في المرحلة 7.
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }
}
