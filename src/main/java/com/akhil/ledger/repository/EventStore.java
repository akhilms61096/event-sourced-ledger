package com.akhil.ledger.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.akhil.ledger.event.LedgerEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Append-only event store backed by a relational DB.
 * Optimistic concurrency: expectedVersion prevents lost-update anomalies
 * when two processes try to append to the same aggregate simultaneously.
 *
 * Interview angle: why not use JPA entities here?
 *   — The event store is append-only; ORM is optimized for CRUD.
 *     Direct JDBC gives us full control over the INSERT and the version check.
 */
@Repository
public class EventStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public EventStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional
    public void append(UUID aggregateId, List<LedgerEvent> events, long expectedVersion) {
        long currentVersion = getCurrentVersion(aggregateId);
        if (currentVersion != expectedVersion) {
            throw new OptimisticConcurrencyException(aggregateId, expectedVersion, currentVersion);
        }
        for (LedgerEvent event : events) {
            String payload = serialize(event);
            jdbc.update("""
                INSERT INTO event_store (aggregate_id, version, event_type, payload, occurred_at)
                VALUES (?, ?, ?, ?::jsonb, ?)
                """,
                    aggregateId, event.version(), event.getClass().getSimpleName(),
                    payload, event.occurredAt());
        }
    }

    public List<LedgerEvent> load(UUID aggregateId) {
        return jdbc.query("""
                SELECT payload FROM event_store
                WHERE aggregate_id = ?
                ORDER BY version ASC
                """,
                (rs, _) -> deserialize(rs.getString("payload")),
                aggregateId);
    }

    public List<LedgerEvent> loadFromVersion(UUID aggregateId, long fromVersion) {
        return jdbc.query("""
                SELECT payload FROM event_store
                WHERE aggregate_id = ? AND version >= ?
                ORDER BY version ASC
                """,
                (rs, _) -> deserialize(rs.getString("payload")),
                aggregateId, fromVersion);
    }

    private long getCurrentVersion(UUID aggregateId) {
        Long v = jdbc.queryForObject(
                "SELECT MAX(version) FROM event_store WHERE aggregate_id = ?",
                Long.class, aggregateId);
        return v == null ? -1L : v;
    }

    private String serialize(LedgerEvent event) {
        try { return mapper.writeValueAsString(event); }
        catch (Exception e) { throw new RuntimeException("Event serialization failed", e); }
    }

    private LedgerEvent deserialize(String json) {
        try { return mapper.readValue(json, LedgerEvent.class); }
        catch (Exception e) { throw new RuntimeException("Event deserialization failed", e); }
    }

    public static class OptimisticConcurrencyException extends RuntimeException {
        public OptimisticConcurrencyException(UUID id, long expected, long actual) {
            super("Concurrency conflict on aggregate %s: expected version %d but was %d"
                    .formatted(id, expected, actual));
        }
    }
}
