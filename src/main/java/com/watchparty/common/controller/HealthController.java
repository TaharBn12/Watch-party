package com.watchparty.common.controller;

import com.watchparty.common.dto.HealthResponse;
import com.watchparty.config.SupabaseProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final SupabaseProperties supabaseProperties;
    private final String applicationName;
    private final String applicationVersion;

    public HealthController(
            JdbcTemplate jdbcTemplate,
            SupabaseProperties supabaseProperties,
            @Value("${spring.application.name:watch-party}") String applicationName,
            @Value("${info.app.version:dev}") String applicationVersion
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.supabaseProperties = supabaseProperties;
        this.applicationName = applicationName;
        this.applicationVersion = applicationVersion;
    }

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        HealthResponse.DatabaseInfo database = checkDatabase();
        String status = resolveStatus(database);

        HealthResponse response = new HealthResponse(
                status,
                Instant.now(),
                new HealthResponse.ApplicationInfo(
                        applicationName,
                        applicationVersion,
                        Runtime.version().toString()
                ),
                new HealthResponse.SupabaseInfo(
                        isConfigured(supabaseProperties.url()),
                        isConfigured(supabaseProperties.anonKey()),
                        isConfigured(supabaseProperties.jwtSecret())
                ),
                database
        );

        HttpStatus httpStatus = database.connected() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(httpStatus).body(response);
    }

    private HealthResponse.DatabaseInfo checkDatabase() {
        long startedAt = System.nanoTime();

        try {
            Map<String, Object> info = jdbcTemplate.queryForMap("""
                    select
                      now()::text as server_time,
                      current_database() as database_name,
                      current_schema() as schema_name
                    """);

            Map<String, Boolean> tables = requiredTables();
            boolean schemaReady = tables.values().stream().allMatch(Boolean::booleanValue);
            long latencyMs = elapsedMs(startedAt);

            return new HealthResponse.DatabaseInfo(
                    true,
                    schemaReady,
                    Objects.toString(info.get("database_name"), null),
                    Objects.toString(info.get("schema_name"), null),
                    Objects.toString(info.get("server_time"), null),
                    latencyMs,
                    tables,
                    null
            );
        } catch (Exception exception) {
            long latencyMs = elapsedMs(startedAt);

            return new HealthResponse.DatabaseInfo(
                    false,
                    false,
                    null,
                    null,
                    null,
                    latencyMs,
                    Map.of(),
                    sanitizeError(exception)
            );
        }
    }

    private Map<String, Boolean> requiredTables() {
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                select
                  to_regclass('public.profiles') is not null as profiles,
                  to_regclass('public.rooms') is not null as rooms,
                  to_regclass('public.room_members') is not null as room_members,
                  to_regclass('public.messages') is not null as messages,
                  to_regclass('public.playlist_items') is not null as playlist_items
                """);

        Map<String, Boolean> tables = new LinkedHashMap<>();
        tables.put("profiles", asBoolean(row.get("profiles")));
        tables.put("rooms", asBoolean(row.get("rooms")));
        tables.put("room_members", asBoolean(row.get("room_members")));
        tables.put("messages", asBoolean(row.get("messages")));
        tables.put("playlist_items", asBoolean(row.get("playlist_items")));
        return tables;
    }

    private String resolveStatus(HealthResponse.DatabaseInfo database) {
        if (!database.connected()) {
            return "DOWN";
        }

        return database.schemaReady() ? "UP" : "DEGRADED";
    }

    private boolean asBoolean(Object value) {
        return value instanceof Boolean booleanValue && booleanValue;
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private boolean isConfigured(String value) {
        return StringUtils.hasText(value) && !value.startsWith("${");
    }

    private String sanitizeError(Exception exception) {
        String message = exception.getMessage();
        if (!StringUtils.hasText(message)) {
            return exception.getClass().getSimpleName();
        }

        // التعليق بالعربية: لا نعيد stacktrace أو أي تفاصيل قد تكشف كلمة المرور.
        String firstLine = message.lines().findFirst().orElse(exception.getClass().getSimpleName());
        return firstLine.length() > 220 ? firstLine.substring(0, 220) + "..." : firstLine;
    }
}
