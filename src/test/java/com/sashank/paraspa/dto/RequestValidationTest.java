package com.sashank.paraspa.dto;

import com.sashank.paraspa.dto.request.CreateRescheduleRequest;
import com.sashank.paraspa.dto.request.CreateSessionRequest;
import com.sashank.paraspa.enums.SessionMode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private CreateSessionRequest.CreateSessionRequestBuilder validSession() {
        return CreateSessionRequest.builder()
                .dateTime(LocalDateTime.now().plusDays(1))
                .durationMinutes(60)
                .mode(SessionMode.ONLINE);
    }

    private Set<String> fieldsWithViolations(Object request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void createSession_shouldAcceptFutureDateAndPositiveDuration() {
        assertThat(validator.validate(validSession().build())).isEmpty();
    }

    @Test
    void createSession_shouldRejectZeroDuration() {
        assertThat(fieldsWithViolations(validSession().durationMinutes(0).build())).containsExactly("durationMinutes");
    }

    @Test
    void createSession_shouldRejectNegativeDuration() {
        assertThat(fieldsWithViolations(validSession().durationMinutes(-30).build())).containsExactly("durationMinutes");
    }

    @Test
    void createSession_shouldRejectDateInThePast() {
        assertThat(fieldsWithViolations(validSession().dateTime(LocalDateTime.now().minusMinutes(1)).build()))
                .containsExactly("dateTime");
    }

    @Test
    void createSession_shouldStillRequireDateAndDuration() {
        assertThat(fieldsWithViolations(validSession().dateTime(null).durationMinutes(null).build()))
                .containsExactlyInAnyOrder("dateTime", "durationMinutes");
    }

    @Test
    void createReschedule_shouldAcceptFutureDate() {
        CreateRescheduleRequest request = CreateRescheduleRequest.builder()
                .proposedDateTime(LocalDateTime.now().plusDays(2)).build();

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void createReschedule_shouldRejectDateInThePast() {
        CreateRescheduleRequest request = CreateRescheduleRequest.builder()
                .proposedDateTime(LocalDateTime.now().minusDays(1)).build();

        assertThat(fieldsWithViolations(request)).containsExactly("proposedDateTime");
    }
}
