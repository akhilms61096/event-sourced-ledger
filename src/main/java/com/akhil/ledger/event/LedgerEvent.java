package com.akhil.ledger.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = LedgerEvent.AccountOpened.class, name = "ACCOUNT_OPENED"),
    @JsonSubTypes.Type(value = LedgerEvent.MoneyDeposited.class, name = "MONEY_DEPOSITED"),
    @JsonSubTypes.Type(value = LedgerEvent.MoneyWithdrawn.class, name = "MONEY_WITHDRAWN"),
    @JsonSubTypes.Type(value = LedgerEvent.TransferInitiated.class, name = "TRANSFER_INITIATED"),
})
public sealed interface LedgerEvent {

    UUID aggregateId();
    long version();
    Instant occurredAt();

    record AccountOpened(UUID aggregateId, long version, Instant occurredAt,
                         String ownerId, String currency) implements LedgerEvent {}

    record MoneyDeposited(UUID aggregateId, long version, Instant occurredAt,
                          BigDecimal amount, String reference) implements LedgerEvent {}

    record MoneyWithdrawn(UUID aggregateId, long version, Instant occurredAt,
                          BigDecimal amount, String reference) implements LedgerEvent {}

    /**
     * Double-entry: one event on the debit account, one on the credit account.
     * Both share the same transferId to correlate the pair.
     */
    record TransferInitiated(UUID aggregateId, long version, Instant occurredAt,
                             UUID transferId, UUID counterpartAccountId,
                             BigDecimal amount, TransferSide side) implements LedgerEvent {}

    enum TransferSide { DEBIT, CREDIT }
}
