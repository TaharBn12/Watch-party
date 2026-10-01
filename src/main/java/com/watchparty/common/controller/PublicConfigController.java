package com.watchparty.common.controller;

import com.watchparty.common.dto.PublicAppConfigResponse;
import com.watchparty.config.SupabaseProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/public")
public class PublicConfigController {

    private final SupabaseProperties supabaseProperties;
    private final String stunUrls;
    private final String turnUrls;
    private final String turnUsername;
    private final String turnPassword;

    public PublicConfigController(
            SupabaseProperties supabaseProperties,
            @Value("${watchparty.webrtc.stun-urls:}") String stunUrls,
            @Value("${watchparty.webrtc.turn-urls:}") String turnUrls,
            @Value("${watchparty.webrtc.turn-username:}") String turnUsername,
            @Value("${watchparty.webrtc.turn-password:}") String turnPassword
    ) {
        this.supabaseProperties = supabaseProperties;
        this.stunUrls = stunUrls;
        this.turnUrls = turnUrls;
        this.turnUsername = turnUsername;
        this.turnPassword = turnPassword;
    }

    @GetMapping("/config")
    public PublicAppConfigResponse config() {
        boolean supabaseConfigured = StringUtils.hasText(supabaseProperties.url())
                && StringUtils.hasText(supabaseProperties.anonKey());

        return new PublicAppConfigResponse(
                new PublicAppConfigResponse.SupabaseClientConfig(
                        emptyToNull(supabaseProperties.url()),
                        emptyToNull(supabaseProperties.anonKey()),
                        supabaseConfigured
                ),
                new PublicAppConfigResponse.WebSocketClientConfig(
                        "/ws",
                        "/ws-sockjs"
                ),
                new PublicAppConfigResponse.WebRtcClientConfig(
                        parseCsv(stunUrls),
                        parseCsv(turnUrls),
                        emptyToNull(turnUsername),
                        emptyToNull(turnPassword),
                        StringUtils.hasText(turnUrls)
                                && StringUtils.hasText(turnUsername)
                                && StringUtils.hasText(turnPassword)
                )
        );
    }

    private List<String> parseCsv(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
