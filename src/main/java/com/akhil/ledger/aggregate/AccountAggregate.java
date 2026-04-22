package com.akhil.ledger.aggregate;

import com.akhil.ledger.event.LedgerEvent;
import com.akhil.ledger.exception.InsufficientFundsException;
import com.akhil.ledger.exception.InvalidOperationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Account aggregate — the only place that enforces invariants.
 * Rebuilt by replaying events from the event store (no mutable DB state).
 *
 * Interview angle: why is apply() separate from business logic methods?
 *   — apply() is pure state mutation, no validation. This lets us replay
 *     historical events without re-running business rules that may have changed.
 */
public class AccountAggregate {

    private UUID id;
    private String ownerId;
    private String currency;
    private BigDecimal balance = BigDecimal.ZERO;
    private long version = -1;
    private boolean opened = false;

    private final List<LedgerEvent> uncommittedEvents = new ArrayList<>();

    // ---- Command handlers (validate → raise event) ----

    public static AccountAggregate open(UUID accountId, String ownerId, String currency) {
        AccountAggregate account = new AccountAggregate();
        account.apply(new LedgerEvent.AccountOpened(accountId, 0, Instant.now(), ownerId, currency));
        return account;
    }

    public void deposit(BigDecimal amount, String reference) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new InvalidOperationException("Deposit amount must be positive");
        apply(new LedgerEvent.MoneyDeposited(id, version + 1, Instant.now(), amount, reference));
    }

    public void withdraw(BigDecimal amount, String reference) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new InvalidOperationException("Withdrawal amount must be positive");
        if (balance.compareTo(amount) < 0)
            throw new InsufficientFundsException(id, balance, amount);
        apply(new LedgerEvent.MoneyWithdrawn(id, version + 1, Instant.now(), amount, reference));
    }

    public void debit(UUID transferId, UUID counterpart, BigDecimal amount) {
        if (balance.compareTo(amount) < 0)
            throw new InsufficientFundsException(id, balance, amount);
        apply(new LedgerEvent.TransferInitiated(id, version + 1, Instant.now(),
                transferId, counterpart, amount, LedgerEvent.TransferSide.DEBIT));
    }

    public void credit(UUID transferId, UUID counterpart, BigDecimal amount) {
        apply(new LedgerEvent.TransferInitiated(id, version + 1, Instant.now(),
                transferId, counterpart, amount, LedgerEvent.TransferSide.CREDIT));
    }

    // ---- Event sourcing machinery ----

    private void apply(LedgerEvent event) {
        mutate(event);
        uncommittedEvents.add(event);
    }

    public void rehydrate(List<LedgerEvent> history) {
        history.forEach(this::mutate);
    }

    private void mutate(LedgerEvent event) {
        version = event.version();
        switch (event) {
            case LedgerEvent.AccountOpened e -> {
                id = e.aggregateId();
                ownerId = e.ownerId();
                currency = e.currency();
                opened = true;
            }
            case LedgerEvent.MoneyDeposited e -> balance = balance.add(e.amount());
            case LedgerEvent.MoneyWithdrawn e -> balance = balance.subtract(e.amount());
            case LedgerEvent.TransferInitiated e -> {
                if (e.side() == LedgerEvent.TransferSide.DEBIT) balance = balance.subtract(e.amount());
                else balance = balance.add(e.amount());
            }
        }
    }

    public List<LedgerEvent> getUncommittedEvents() {
        return Collections.unmodifiableList(uncommittedEvents);
    }

    public void markEventsCommitted() {
        uncommittedEvents.clear();
    }

    public UUID getId()        { return id; }
    public BigDecimal getBalance() { return balance; }
    public long getVersion()   { return version; }
    public String getCurrency() { return currency; }
    public String getOwnerId() { return ownerId; }
}
