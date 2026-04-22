package com.akhil.ledger.exception;

import java.math.BigDecimal;
import java.util.UUID;

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(UUID accountId, BigDecimal balance, BigDecimal requested) {
        super("Insufficient funds on account %s: balance=%s, requested=%s"
                .formatted(accountId, balance, requested));
    }
}
