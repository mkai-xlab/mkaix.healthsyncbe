package com.g93.be.config.openapi;

import com.g93.be.controller.AiController;
import com.g93.be.controller.AuditLogController;
import com.g93.be.controller.AuthController;
import com.g93.be.controller.DiagnosisReviewController;
import com.g93.be.controller.DicomController;
import com.g93.be.controller.DicomVerifyController;
import com.g93.be.controller.DoctorController;
import com.g93.be.controller.ExaminationController;
import com.g93.be.controller.FeatureController;
import com.g93.be.controller.FileUploadController;
import com.g93.be.controller.MailTestController;
import com.g93.be.controller.NotificationController;
import com.g93.be.controller.PatientController;
import com.g93.be.controller.PermissionController;
import com.g93.be.controller.ReportController;
import com.g93.be.controller.TestS3Controller;
import com.g93.be.controller.UserController;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OperationCustomizer;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiDocumentationTest {

    private static final List<Class<?>> CONTROLLERS = List.of(
            AiController.class,
            AuditLogController.class,
            AuthController.class,
            DiagnosisReviewController.class,
            DicomController.class,
            DicomVerifyController.class,
            DoctorController.class,
            ExaminationController.class,
            FeatureController.class,
            FileUploadController.class,
            MailTestController.class,
            NotificationController.class,
            PatientController.class,
            PermissionController.class,
            ReportController.class,
            TestS3Controller.class,
            UserController.class);

    @Test
    void everyEndpointHasCompleteDocumentation() {
        ApiDocumentationRegistry registry = new ApiDocumentationRegistry();
        Set<String> endpointKeys = endpointKeys();

        assertEquals(endpointKeys, registry.all().keySet(),
                "OpenAPI registry must document every controller endpoint exactly once");

        registry.all().forEach((key, document) -> {
            assertFalse(document.tag().isBlank(), key + " is missing a tag");
            assertFalse(document.summary().isBlank(), key + " is missing a summary");
            assertFalse(document.description().isBlank(), key + " is missing a description");
            assertTrue(document.successCode().matches("2\\d{2}"), key + " has an invalid success status");
            assertFalse(document.successDescription().isBlank(), key + " is missing a success description");

            if (document.requestExampleKey() != null) {
                assertNotNull(ApiExamples.get(document.requestExampleKey()), key + " has an invalid request example");
            }
            if (document.responseExampleKey() != null) {
                assertNotNull(ApiExamples.get(document.responseExampleKey()), key + " has an invalid response example");
            }
            if (document.responseKind() == ApiDocumentationRegistry.ResponseKind.JSON
                    || document.responseKind() == ApiDocumentationRegistry.ResponseKind.TEXT) {
                assertNotNull(document.responseExampleKey(), key + " requires a visible response example");
            }
        });
    }

    @Test
    void swaggerDefinesBearerJwtAuthorizationInput() {
        ApiDocumentationRegistry registry = new ApiDocumentationRegistry();
        OpenAPI openAPI = new OpenApiConfig().healthSyncOpenApi();

        assertNotNull(openAPI.getComponents());
        assertNotNull(openAPI.getComponents().getSecuritySchemes().get(OpenApiConfig.BEARER_AUTH));
        assertEquals("bearer", openAPI.getComponents().getSecuritySchemes()
                .get(OpenApiConfig.BEARER_AUTH).getScheme());
        assertEquals("JWT", openAPI.getComponents().getSecuritySchemes()
                .get(OpenApiConfig.BEARER_AUTH).getBearerFormat());
        assertEquals(
                registry.all().values().stream()
                        .map(ApiDocumentationRegistry.EndpointDoc::tag)
                        .collect(java.util.stream.Collectors.toSet()),
                openAPI.getTags().stream()
                        .map(io.swagger.v3.oas.models.tags.Tag::getName)
                        .collect(java.util.stream.Collectors.toSet()),
                "Every endpoint group must have exactly one visible tag description");
    }

    @Test
    void everyEndpointProducesSuccessAndErrorResponses() throws ReflectiveOperationException {
        ApiDocumentationRegistry registry = new ApiDocumentationRegistry();
        OperationCustomizer customizer = new OpenApiConfig().documentedOperationCustomizer(registry);

        for (Class<?> controller : CONTROLLERS) {
            Object controllerBean = createControllerWithoutDependencies(controller);
            for (Method method : controller.getDeclaredMethods()) {
                if (!isEndpoint(method)) {
                    continue;
                }

                Operation generatedOperation = new Operation()
                        .responses(new ApiResponses().addApiResponse("200", new ApiResponse()
                                .description("Generated response")
                                .content(new Content().addMediaType(
                                        "application/json",
                                        new MediaType().schema(new ObjectSchema())))));
                Operation operation = customizer.customize(
                        generatedOperation,
                        new HandlerMethod(controllerBean, method));
                ApiDocumentationRegistry.EndpointDoc document = registry
                        .find(controller.getSimpleName(), method.getName())
                        .orElseThrow();
                String key = controller.getSimpleName() + "#" + method.getName();

                assertNotNull(operation.getResponses().get(document.successCode()),
                        key + " is missing its success response");
                assertNotNull(operation.getResponses().get("400"), key + " is missing HTTP 400");
                assertNotNull(operation.getResponses().get("500"), key + " is missing HTTP 500");
                if (document.publicEndpoint()) {
                    assertTrue(operation.getSecurity().isEmpty(), key + " must be public");
                } else {
                    assertFalse(operation.getSecurity().isEmpty(), key + " must use Bearer JWT");
                    assertNotNull(operation.getResponses().get("401"), key + " is missing HTTP 401");
                    assertNotNull(operation.getResponses().get("403"), key + " is missing HTTP 403");
                }
            }
        }
    }

    private Set<String> endpointKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (Class<?> controller : CONTROLLERS) {
            for (Method method : controller.getDeclaredMethods()) {
                if (isEndpoint(method)) {
                    assertTrue(keys.add(controller.getSimpleName() + "#" + method.getName()),
                            "Overloaded endpoint methods need a unique OpenAPI registry key");
                }
            }
        }
        return keys;
    }

    private boolean isEndpoint(Method method) {
        return method.isAnnotationPresent(GetMapping.class)
                || method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PutMapping.class)
                || method.isAnnotationPresent(PatchMapping.class)
                || method.isAnnotationPresent(DeleteMapping.class);
    }

    private Object createControllerWithoutDependencies(Class<?> controller)
            throws ReflectiveOperationException {
        Constructor<?> constructor = Arrays.stream(controller.getDeclaredConstructors())
                .max(Comparator.comparingInt(Constructor::getParameterCount))
                .orElseThrow();
        constructor.setAccessible(true);
        return constructor.newInstance(new Object[constructor.getParameterCount()]);
    }
}
