package com.banking.platform.account.model.dto;

import com.banking.platform.account.model.entity.AccountStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateAccountStatusRequest(
    @NotNull(message = "Account status is required")
    AccountStatus status,

    String reason
) {}
