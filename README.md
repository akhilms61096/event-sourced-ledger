# Event-Sourced Ledger

Double-entry banking core built with Event Sourcing + CQRS in Java 21 + Spring Boot 3.x.

## Architecture

```
HTTP → Controller → CommandHandler → AccountAggregate → EventStore (append-only)
                                                ↓
                                        AccountProjection (read model)
                                                ↓
                                        HTTP Query Responses
```

### Core patterns implemented

**Event Sourcing** — Account state is never stored directly. Every state change is an immutable event appended to the event store. Balance is derived by replaying events.

**CQRS** — Write path (commands) and read path (queries) are fully separated. Commands mutate the aggregate; queries hit the denormalised `account_summary` projection.

**Double-entry accounting** — Every transfer produces two events: a `DEBIT` on the source account and a `CREDIT` on the destination, linked by a shared `transferId`. The sum of all entries always equals zero.

**Optimistic concurrency** — The event store checks `expectedVersion` before appending. If two concurrent requests try to modify the same account, one gets a 409 Conflict rather than a silent lost update.

**Aggregate pattern** — `AccountAggregate` is the consistency boundary. All business rules live here. The `apply()` / `mutate()` split ensures that business logic is not re-run during event replay.

### Why `mutate()` is separate from `apply()`
Business rules (balance checks, validation) run only when new commands are processed. During rehydration from history, only `mutate()` is called — pure state reconstruction, no validation. This means historical events are always replayable even if business rules have changed since they were written.

### Projection rebuild
If the read model becomes corrupted or a new projection is needed, replay all events from the `event_store` table through `AccountProjection.on()`. The event log is always the authoritative source.

## API

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/accounts` | Open a new account |
| POST | `/api/accounts/{id}/deposit` | Deposit funds |
| POST | `/api/accounts/{id}/withdraw` | Withdraw funds |
| POST | `/api/accounts/transfer` | Transfer between accounts |
| GET | `/api/accounts/{id}` | Get account summary (read model) |
| GET | `/api/accounts/{id}/events` | Get full event history |

## Running

```bash
# H2 in-memory (dev)
mvn spring-boot:run

# PostgreSQL
docker-compose up
```

## Interview talking points
- Event sourcing vs traditional CRUD — audit log, temporal queries, event replay
- CQRS — why separate read/write models, eventual consistency trade-offs
- Optimistic concurrency — why not pessimistic locking at scale
- Double-entry invariant — how transfers maintain zero-sum via paired events
- Projection rebuild — the event store as the source of truth
- Aggregate boundaries — why `AccountAggregate` owns all consistency rules
- Snapshotting — how you'd add snapshots to avoid replaying 1M events
