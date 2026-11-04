package com.sashank.paraspa.exception;

import com.sashank.paraspa.dto.response.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private WebRequest request() {
        WebRequest request = mock(WebRequest.class);
        when(request.getDescription(false)).thenReturn("uri=/api/sessions/1/ratings");
        return request;
    }

    @Test
    void dataIntegrityViolation_shouldBecome409WithoutLeakingSql() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "could not execute statement; constraint [uk_ratings_session_rated_by]");

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolationException(ex, request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getStatus()).isEqualTo(409);
        assertThat(response.getBody().getError()).isEqualTo("Conflict");
        assertThat(response.getBody().getMessage()).doesNotContain("uk_ratings").doesNotContain("constraint");
        assertThat(response.getBody().getPath()).isEqualTo("/api/sessions/1/ratings");
    }

    @Test
    void maxUploadSizeExceeded_shouldBecome400() {
        ResponseEntity<ErrorResponse> response =
                handler.handleMaxUploadSizeExceededException(new MaxUploadSizeExceededException(2L * 1024 * 1024), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).contains("2 MB");
    }

    @Test
    void unsupportedContentType_shouldBecome415NotServerError() {
        ResponseEntity<ErrorResponse> response = handler.handleHttpMediaTypeNotSupportedException(
                new org.springframework.web.HttpMediaTypeNotSupportedException("text/plain not supported"), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody().getStatus()).isEqualTo(415);
    }

    @Test
    void unsupportedHttpMethod_shouldBecome405NotServerError() {
        ResponseEntity<ErrorResponse> response = handler.handleHttpRequestMethodNotSupportedException(
                new org.springframework.web.HttpRequestMethodNotSupportedException("POST"), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody().getStatus()).isEqualTo(405);
        assertThat(response.getBody().getMessage()).contains("POST");
    }

    @Test
    void missingStaticResource_shouldBecome404NotServerError() {
        ResponseEntity<ErrorResponse> response = handler.handleNoResourceFoundException(
                new org.springframework.web.servlet.resource.NoResourceFoundException(
                        org.springframework.http.HttpMethod.GET, "avatars/nope.png"), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getStatus()).isEqualTo(404);
    }

    @Test
    void missingMultipartPart_shouldBecome400() {
        ResponseEntity<ErrorResponse> response =
                handler.handleMissingServletRequestPartException(new MissingServletRequestPartException("file"), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).contains("file");
    }
}
