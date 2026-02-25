package com.banking.platform.onboarding.service;

import com.banking.platform.onboarding.mapper.ApplicationMapper;
import com.banking.platform.onboarding.model.dto.ApplicationResponse;
import com.banking.platform.onboarding.model.dto.CreateApplicationRequest;
import com.banking.platform.onboarding.model.dto.UpdateApplicationStatusRequest;
import com.banking.platform.onboarding.model.entity.AccountType;
import com.banking.platform.onboarding.model.entity.Application;
import com.banking.platform.onboarding.model.entity.ApplicationStatus;
import com.banking.platform.onboarding.repository.ApplicationDocumentRepository;
import com.banking.platform.onboarding.repository.ApplicationRepository;
import com.banking.platform.shared.exception.BusinessException;
import com.banking.platform.shared.exception.DuplicateResourceException;
import com.banking.platform.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private ApplicationDocumentRepository applicationDocumentRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private ApplicationMapper applicationMapper;

    @InjectMocks
    private ApplicationService applicationService;

    private CreateApplicationRequest createApplicationRequest;
    private Application application;
    private UUID applicationId;

    @BeforeEach
    void setUp() {
        applicationId = UUID.randomUUID();

        createApplicationRequest = new CreateApplicationRequest(
            "John",
            "Doe",
            "john.doe@example.com",
            "+12025551234",
            LocalDate.of(1990, 1, 1),
            "123 Main St",
            "Apt 4B",
            "New York",
            "NY",
            "10001",
            "USA",
            AccountType.CHECKING,
            "REF123"
        );

        application = Application.builder()
            .id(applicationId)
            .firstName("John")
            .lastName("Doe")
            .email("john.doe@example.com")
            .phone("+12025551234")
            .ssn("123-45-6789")
            .dateOfBirth(LocalDate.of(1990, 1, 1))
            .addressLine1("123 Main St")
            .addressLine2("Apt 4B")
            .city("New York")
            .state("NY")
            .zipCode("10001")
            .country("USA")
            .requestedAccountType(AccountType.CHECKING)
            .referralCode("REF123")
            .status(ApplicationStatus.SUBMITTED)
            .build();
    }

    @Test
    void testSubmitApplication_success() {
        ApplicationResponse expectedResponse = new ApplicationResponse(
            applicationId, "John", "Doe", "john.doe@example.com", "+12025551234",
            ApplicationStatus.SUBMITTED, AccountType.CHECKING, Instant.now(), null
        );

        when(applicationRepository.findByEmail(createApplicationRequest.email()))
            .thenReturn(Optional.empty());
        when(applicationMapper.toEntity(createApplicationRequest))
            .thenReturn(application);
        when(applicationRepository.save(any(Application.class)))
            .thenReturn(application);
        when(applicationMapper.toResponse(any(Application.class)))
            .thenReturn(expectedResponse);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
            .thenReturn(null);

        var response = applicationService.submitApplication(createApplicationRequest);

        assertNotNull(response);
        assertEquals(applicationId, response.id());
        assertEquals("john.doe@example.com", response.email());
        assertEquals(ApplicationStatus.SUBMITTED, response.status());
        verify(applicationRepository, times(1)).save(any(Application.class));
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any());
    }

    @Test
    void testSubmitApplication_duplicateEmail_throwsException() {
        when(applicationRepository.findByEmail(createApplicationRequest.email()))
            .thenReturn(Optional.of(application));

        assertThrows(DuplicateResourceException.class, () ->
            applicationService.submitApplication(createApplicationRequest));
        verify(applicationRepository, never()).save(any(Application.class));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void testGetApplication_notFound_throwsException() {
        when(applicationRepository.findById(applicationId))
            .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
            applicationService.getApplication(applicationId));
        verify(applicationRepository, times(1)).findById(applicationId);
    }

    @Test
    void testUpdateStatus_invalidTransition() {
        application.setStatus(ApplicationStatus.REJECTED);
        UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest(
            ApplicationStatus.APPROVED,
            null,
            UUID.randomUUID()
        );

        when(applicationRepository.findById(applicationId))
            .thenReturn(Optional.of(application));

        assertThrows(BusinessException.class, () ->
            applicationService.updateStatus(applicationId, request));
        verify(applicationRepository, never()).save(any(Application.class));
    }

    @Test
    void testUpdateStatus_validTransition() {
        application.setStatus(ApplicationStatus.SUBMITTED);
        UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest(
            ApplicationStatus.UNDER_REVIEW,
            null,
            UUID.randomUUID()
        );

        when(applicationRepository.findById(applicationId))
            .thenReturn(Optional.of(application));
        when(applicationRepository.save(any(Application.class)))
            .thenReturn(application);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
            .thenReturn(null);

        applicationService.updateStatus(applicationId, request);

        verify(applicationRepository, times(1)).save(any(Application.class));
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any());
    }
}
