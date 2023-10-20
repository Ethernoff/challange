package com.dws.challenge.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Positive;
import java.math.BigDecimal;

@Data
public class TransferOperation {
    @NotEmpty
    private final String accountFromId;
    @NotEmpty
    private final String accountToId;
    @Positive
    private final BigDecimal amount;
}
