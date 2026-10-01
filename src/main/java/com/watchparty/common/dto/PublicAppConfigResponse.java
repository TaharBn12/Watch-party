package com.watchparty.common.dto;

import java.util.List;

public record PublicAppConfigResponse(
        SupabaseClientConfig supabase,
        WebSocketClientConfig webSocket,
        WebRtcClientConfig webRtc
) {
    public record SupabaseClientConfig(
            String url,
            String anonKey,
            boolean configured
    ) {
    }

    public record WebSocketClientConfig(
            String endpoint,
            String sockJsEndpoint
    ) {
    }

    public record WebRtcClientConfig(
            List<String> stunUrls,
            List<String> turnUrls,
            String turnUsername,
            String turnCredential,
            boolean turnConfigured
    ) {
    }
}
