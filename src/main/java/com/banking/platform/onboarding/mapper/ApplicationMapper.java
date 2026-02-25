package com.banking.platform.onboarding.mapper;

import com.banking.platform.onboarding.model.dto.ApplicationResponse;
import com.banking.platform.onboarding.model.dto.ApplicationSummaryResponse;
import com.banking.platform.onboarding.model.dto.CreateApplicationRequest;
import com.banking.platform.onboarding.model.entity.Application;
import com.banking.platform.onboarding.model.entity.ApplicationStatus;
import org.springframework.stereotype.Component;

@Component
public class ApplicationMapper {

    public Application toEntity(CreateApplicationRequest request) {
        return Application.builder()
            .firstName(request.firstName())
            .lastName(request.lastName())
            .email(request.email())
            .phone(maskPhone(request.phone()))
            .ssn(maskSsn(request.phone()))
            .dateOfBirth(request.dateOfBirth())
            .addressLine1(request.addressLine1())
            .addressLine2(request.addressLine2())
            .city(request.city())
            .state(request.state())
            .zipCode(request.zipCode())
            .country(request.country())
            .requestedAccountType(request.requestedAccountType())
            .referralCode(request.referralCode())
            .status(ApplicationStatus.SUBMITTED)
            .build();
    }

    public ApplicationResponse toResponse(Application application) {
        return new ApplicationResponse(
            application.getId(),
            application.getFirstName(),
            application.getLastName(),
            application.getEmail(),
            maskPhone(application.getPhone()),
            application.getStatus(),
            application.getRequestedAccountType(),
            application.getSubmittedAt(),
            application.getReviewedAt()
        );
    }

    public ApplicationSummaryResponse toSummary(Application application) {
        String fullName = application.getFirstName() + " " + application.getLastName();
        return new ApplicationSummaryResponse(
            application.getId(),
            fullName,
            application.getStatus(),
            application.getRequestedAccountType(),
            application.getSubmittedAt()
        );
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "****";
        }
        return "****" + phone.substring(phone.length() - 4);
    }

    private String maskSsn(String ssn) {
        if (ssn == null || ssn.length() < 4) {
            return "****";
        }
        return "***-**-" + ssn.substring(ssn.length() - 4);
    }
}
