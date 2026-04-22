package com.akhil.ledger.controller;

import com.akhil.ledger.command.LedgerCommand;
import com.akhil.ledger.command.LedgerCommandHandler;
import com.akhil.ledger.event.LedgerEvent;
import com.akhil.ledger.projection.AccountProjection;
import com.akhil.ledger.repository.EventStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
public class LedgerController {

    private final LedgerCommandHandler commandHandler;
    private final AccountProjection projection;
    private final EventStore eventStore;

    public LedgerController(LedgerCommandHandler commandHandler,
                             AccountProjection projection,
                             EventStore eventStore) {
        this.commandHandler = commandHandler;
        this.projection = projection;
        this.eventStore = eventStore;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> openAccount(@RequestBody OpenAccountRequest req) {
        UUID id = commandHandler.handle(new LedgerCommand.OpenAccount(req.ownerId(), req.currency()));
        return ResponseEntity.ok(Map.of("accountId", id.toString()));
    }

    @PostMapping("/{accountId}/deposit")
    public ResponseEntity<Void> deposit(@PathVariable String accountId,
                                        @RequestBody AmountRequest req) {
        commandHandler.handle(new LedgerCommand.Deposit(accountId, req.amount(), req.reference()));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{accountId}/withdraw")
    public ResponseEntity<Void> withdraw(@PathVariable String accountId,
                                         @RequestBody AmountRequest req) {
        commandHandler.handle(new LedgerCommand.Withdraw(accountId, req.amount(), req.reference()));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/transfer")
    public ResponseEntity<Void> transfer(@RequestBody TransferRequest req) {
        commandHandler.handle(new LedgerCommand.Transfer(req.fromAccountId(), req.toAccountId(), req.amount()));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountProjection.AccountSummary> getAccount(@PathVariable UUID accountId) {
        return ResponseEntity.ok(projection.getAccount(accountId));
    }

    @GetMapping("/{accountId}/events")
    public ResponseEntity<List<LedgerEvent>> getEventHistory(@PathVariable UUID accountId) {
        return ResponseEntity.ok(eventStore.load(accountId));
    }

    record OpenAccountRequest(String ownerId, String currency) {}
    record AmountRequest(BigDecimal amount, String reference) {}
    record TransferRequest(String fromAccountId, String toAccountId, BigDecimal amount) {}
}
