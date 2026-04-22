package com.akhil.ledger.command;

import com.akhil.ledger.aggregate.AccountAggregate;
import com.akhil.ledger.projection.AccountProjection;
import com.akhil.ledger.repository.EventStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Command side of CQRS.
 * Loads aggregate from event store, applies command, appends new events.
 * Never reads from the read model (projection) — those are separate.
 */
@Service
public class LedgerCommandHandler {

    private final EventStore eventStore;
    private final AccountProjection projection;

    public LedgerCommandHandler(EventStore eventStore, AccountProjection projection) {
        this.eventStore = eventStore;
        this.projection = projection;
    }

    @Transactional
    public UUID handle(LedgerCommand.OpenAccount cmd) {
        UUID accountId = UUID.randomUUID();
        AccountAggregate account = AccountAggregate.open(accountId, cmd.ownerId(), cmd.currency());
        eventStore.append(accountId, account.getUncommittedEvents(), -1L);
        account.getUncommittedEvents().forEach(projection::on);
        account.markEventsCommitted();
        return accountId;
    }

    @Transactional
    public void handle(LedgerCommand.Deposit cmd) {
        UUID accountId = UUID.fromString(cmd.accountId());
        AccountAggregate account = load(accountId);
        long versionBefore = account.getVersion();
        account.deposit(cmd.amount(), cmd.reference());
        eventStore.append(accountId, account.getUncommittedEvents(), versionBefore);
        account.getUncommittedEvents().forEach(projection::on);
        account.markEventsCommitted();
    }

    @Transactional
    public void handle(LedgerCommand.Withdraw cmd) {
        UUID accountId = UUID.fromString(cmd.accountId());
        AccountAggregate account = load(accountId);
        long versionBefore = account.getVersion();
        account.withdraw(cmd.amount(), cmd.reference());
        eventStore.append(accountId, account.getUncommittedEvents(), versionBefore);
        account.getUncommittedEvents().forEach(projection::on);
        account.markEventsCommitted();
    }

    @Transactional
    public void handle(LedgerCommand.Transfer cmd) {
        UUID fromId = UUID.fromString(cmd.fromAccountId());
        UUID toId   = UUID.fromString(cmd.toAccountId());
        UUID transferId = UUID.randomUUID();

        AccountAggregate from = load(fromId);
        AccountAggregate to   = load(toId);

        long fromVersion = from.getVersion();
        long toVersion   = to.getVersion();

        from.debit(transferId, toId, cmd.amount());
        to.credit(transferId, fromId, cmd.amount());

        // Both appends in same transaction — atomicity guaranteed
        eventStore.append(fromId, from.getUncommittedEvents(), fromVersion);
        eventStore.append(toId,   to.getUncommittedEvents(),   toVersion);

        from.getUncommittedEvents().forEach(projection::on);
        to.getUncommittedEvents().forEach(projection::on);

        from.markEventsCommitted();
        to.markEventsCommitted();
    }

    private AccountAggregate load(UUID accountId) {
        var events = eventStore.load(accountId);
        if (events.isEmpty()) throw new IllegalArgumentException("Account not found: " + accountId);
        AccountAggregate account = new AccountAggregate();
        account.rehydrate(events);
        return account;
    }
}
