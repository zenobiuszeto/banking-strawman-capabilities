package com.banking.platform.onboarding.model.dto;

import com.banking.platform.onboarding.model.entity.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UploadDocumentRequest(
    @NotNull(message = "Document type is required")
    DocumentType type,

    @NotBlank(message = "File name is required")
    String fileName,

    @NotBlank(message = "File URL is required")
    String fileUrl
) {
}
