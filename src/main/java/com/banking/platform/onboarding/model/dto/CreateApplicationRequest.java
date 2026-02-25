package com.banking.platform.onboarding.model.dto;

import com.banking.platform.onboarding.model.entity.AccountType;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record CreateApplicationRequest(
    @NotBlank(message = "First name is required")
    String firstName,

    @NotBlank(message = "Last name is required")
    String lastName,

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    String email,

    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "^\\+?1?\\d{9,15}$", message = "Phone must be valid")
    String phone,

    @NotNull(message = "Date of birth is required")
    LocalDate dateOfBirth,

    @NotBlank(message = "Address line 1 is required")
    String addressLine1,

    String addressLine2,

    @NotBlank(message = "City is required")
    String city,

    @NotBlank(message = "State is required")
    String state,

    @NotBlank(message = "Zip code is required")
    String zipCode,

    @NotBlank(message = "Country is required")
    String country,

    @NotNull(message = "Account type is required")
    AccountType requestedAccountType,

    String referralCode
) {
}
