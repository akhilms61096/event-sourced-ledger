package com.akhil.ledger;

import com.akhil.ledger.aggregate.AccountAggregate;
import com.akhil.ledger.exception.InsufficientFundsException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class AccountAggregateTest {

    @Test
    void shouldOpenAccountWithZeroBalance() {
        AccountAggregate account = AccountAggregate.open(UUID.randomUUID(), "owner-1", "USD");
        assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(account.getUncommittedEvents()).hasSize(1);
    }

    @Test
    void shouldAccumulateBalanceOnDeposits() {
        AccountAggregate account = AccountAggregate.open(UUID.randomUUID(), "owner-1", "USD");
        account.markEventsCommitted();

        account.deposit(new BigDecimal("100.00"), "initial-load");
        account.deposit(new BigDecimal("50.00"), "top-up");

        assertThat(account.getBalance()).isEqualByComparingTo("150.00");
        assertThat(account.getUncommittedEvents()).hasSize(2);
    }

    @Test
    void shouldRejectWithdrawalWhenInsufficientFunds() {
        AccountAggregate account = AccountAggregate.open(UUID.randomUUID(), "owner-1", "USD");
        account.deposit(new BigDecimal("50.00"), "seed");

        assertThatThrownBy(() -> account.withdraw(new BigDecimal("100.00"), "overdraft-attempt"))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void shouldRehydrateFromEventHistory() {
        UUID id = UUID.randomUUID();
        AccountAggregate original = AccountAggregate.open(id, "owner-1", "USD");
        original.deposit(new BigDecimal("200.00"), "seed");
        original.withdraw(new BigDecimal("75.00"), "payout");

        var events = original.getUncommittedEvents();

        // Rehydrate fresh aggregate from events
        AccountAggregate rehydrated = new AccountAggregate();
        rehydrated.rehydrate(events);

        assertThat(rehydrated.getBalance()).isEqualByComparingTo("125.00");
        assertThat(rehydrated.getVersion()).isEqualTo(2);
    }
}
