package com.akhil.ledger.command;

import java.math.BigDecimal;

public sealed interface LedgerCommand {
    record OpenAccount(String ownerId, String currency) implements LedgerCommand {}
    record Deposit(String accountId, BigDecimal amount, String reference) implements LedgerCommand {}
    record Withdraw(String accountId, BigDecimal amount, String reference) implements LedgerCommand {}
    record Transfer(String fromAccountId, String toAccountId, BigDecimal amount) implements LedgerCommand {}
}
