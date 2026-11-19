package com.lifeledger.common.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit test (no Spring context): each exception becomes the right status and ProblemDetail JSON.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void notFoundException_returns404ProblemDetail() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value("Product 42 not found"));
    }

    @Test
    void conflictException_returns409() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Email already registered"));
    }

    @Test
    void forbiddenException_returns403() throws Exception {
        mockMvc.perform(get("/test/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Not the owner"));
    }

    @Test
    void accessDeniedException_returns403() throws Exception {
        mockMvc.perform(get("/test/access-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void businessRuleException_returns400() throws Exception {
        mockMvc.perform(get("/test/business-rule"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Cannot transfer to yourself"));
    }

    @Test
    void invalidRequestBody_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/test/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.name").value("must not be blank"));
    }

    @Test
    void dataIntegrityViolation_returns409WithoutDatabaseDetails() throws Exception {
        mockMvc.perform(get("/test/integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The request conflicts with existing data"))
                .andExpect(content().string(not(containsString("users_email_key"))));
    }

    @Test
    void unexpectedException_returns500WithoutInternalDetails() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andExpect(content().string(not(containsString("secret-internal-message"))));
    }

    record NameRequest(@NotBlank String name) {
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/test/not-found")
        void notFound() {
            throw NotFoundException.of("Product", 42);
        }

        @GetMapping("/test/conflict")
        void conflict() {
            throw new ConflictException("Email already registered");
        }

        @GetMapping("/test/forbidden")
        void forbidden() {
            throw new ForbiddenException("Not the owner");
        }

        @GetMapping("/test/access-denied")
        void accessDenied() {
            throw new AccessDeniedException("Access Denied");
        }

        @GetMapping("/test/business-rule")
        void businessRule() {
            throw new BusinessRuleException("Cannot transfer to yourself");
        }

        @PostMapping("/test/validated")
        void validated(@Valid @RequestBody NameRequest request) {
        }

        @GetMapping("/test/integrity")
        void integrity() {
            throw new DataIntegrityViolationException("duplicate key value violates unique constraint \"users_email_key\"");
        }

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("secret-internal-message");
        }
    }
}
