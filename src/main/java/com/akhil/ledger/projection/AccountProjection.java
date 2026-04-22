package com.akhil.ledger.projection;

import com.akhil.ledger.event.LedgerEvent;
import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Read model: denormalised account summary rebuilt from events.
 * This is the query side of CQRS — fast reads, no aggregate rehydration needed.
 *
 * Interview angle: what happens if projection falls behind?
 *   — Projection can be rebuilt by replaying all events from the event store.
 *     This is the "projection rebuild" pattern — the event log is the source of truth.
 */
@Component
public class AccountProjection {

    private final AccountSummaryRepository repository;

    public AccountProjection(AccountSummaryRepository repository) {
        this.repository = repository;
    }

    public void on(LedgerEvent event) {
        switch (event) {
            case LedgerEvent.AccountOpened e ->
                repository.save(new AccountSummary(e.aggregateId(), e.ownerId(), e.currency(), BigDecimal.ZERO));
            case LedgerEvent.MoneyDeposited e ->
                repository.findById(e.aggregateId()).ifPresent(a -> {
                    a.setBalance(a.getBalance().add(e.amount()));
                    repository.save(a);
                });
            case LedgerEvent.MoneyWithdrawn e ->
                repository.findById(e.aggregateId()).ifPresent(a -> {
                    a.setBalance(a.getBalance().subtract(e.amount()));
                    repository.save(a);
                });
            case LedgerEvent.TransferInitiated e ->
                repository.findById(e.aggregateId()).ifPresent(a -> {
                    BigDecimal delta = e.side() == LedgerEvent.TransferSide.DEBIT
                            ? e.amount().negate() : e.amount();
                    a.setBalance(a.getBalance().add(delta));
                    repository.save(a);
                });
        }
    }

    public AccountSummary getAccount(UUID accountId) {
        return repository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
    }

    @Data
    @Entity
    @Table(name = "account_summary")
    public static class AccountSummary {
        @Id
        private UUID id;
        private String ownerId;
        private String currency;
        private BigDecimal balance;

        public AccountSummary() {}
        public AccountSummary(UUID id, String ownerId, String currency, BigDecimal balance) {
            this.id = id; this.ownerId = ownerId; this.currency = currency; this.balance = balance;
        }
    }

    @Repository
    public interface AccountSummaryRepository extends JpaRepository<AccountSummary, UUID> {}
}
