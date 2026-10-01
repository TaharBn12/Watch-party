package com.watchparty.common.dto;

import java.time.Instant;
import java.util.Map;

public record HealthResponse(
        String status,
        Instant checkedAt,
        ApplicationInfo application,
        SupabaseInfo supabase,
        DatabaseInfo database
) {
    public record ApplicationInfo(
            String name,
            String version,
            String javaVersion
    ) {
    }

    public record SupabaseInfo(
            boolean urlConfigured,
            boolean anonKeyConfigured,
            boolean jwtSecretConfigured
    ) {
    }

    public record DatabaseInfo(
            boolean connected,
            boolean schemaReady,
            String databaseName,
            String schemaName,
            String serverTime,
            long latencyMs,
            Map<String, Boolean> requiredTables,
            String error
    ) {
    }
}
