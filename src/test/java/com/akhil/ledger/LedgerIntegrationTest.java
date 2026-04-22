package com.akhil.ledger;

import com.akhil.ledger.command.LedgerCommand;
import com.akhil.ledger.command.LedgerCommandHandler;
import com.akhil.ledger.projection.AccountProjection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class LedgerIntegrationTest {

    @Autowired LedgerCommandHandler commandHandler;
    @Autowired AccountProjection projection;

    @Test
    void shouldExecuteFullTransferFlow() {
        UUID alice = commandHandler.handle(new LedgerCommand.OpenAccount("alice", "USD"));
        UUID bob   = commandHandler.handle(new LedgerCommand.OpenAccount("bob", "USD"));

        commandHandler.handle(new LedgerCommand.Deposit(alice.toString(), new BigDecimal("500.00"), "seed"));
        commandHandler.handle(new LedgerCommand.Transfer(alice.toString(), bob.toString(), new BigDecimal("200.00")));

        assertThat(projection.getAccount(alice).getBalance()).isEqualByComparingTo("300.00");
        assertThat(projection.getAccount(bob).getBalance()).isEqualByComparingTo("200.00");
    }

    @Test
    void shouldRejectTransferWithInsufficientFunds() {
        UUID alice = commandHandler.handle(new LedgerCommand.OpenAccount("alice", "USD"));
        UUID bob   = commandHandler.handle(new LedgerCommand.OpenAccount("bob", "USD"));

        commandHandler.handle(new LedgerCommand.Deposit(alice.toString(), new BigDecimal("100.00"), "seed"));

        assertThatThrownBy(() ->
                commandHandler.handle(new LedgerCommand.Transfer(alice.toString(), bob.toString(), new BigDecimal("500.00"))))
                .hasMessageContaining("Insufficient funds");
    }
}
