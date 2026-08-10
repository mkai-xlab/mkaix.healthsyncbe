package com.g93.be.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KnowledgeControllerAuthorizationTest {

    private static final String KNOWLEDGE_MANAGEMENT =
            "hasAnyRole('ADMIN', 'DOCTOR', 'DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') and hasAuthority('MANAGE_MEDICAL_KNOWLEDGE')";

    @Test
    void sharedKnowledgeOperationsRequireAdminRoleAndManagementPermission() throws Exception {
        assertAuthorization("upload", KNOWLEDGE_MANAGEMENT);
        assertAuthorization("uploadBatch", KNOWLEDGE_MANAGEMENT);
        assertAuthorization("addUrl", KNOWLEDGE_MANAGEMENT);
        assertAuthorization("getAll", KNOWLEDGE_MANAGEMENT);
        assertAuthorization("reindex", KNOWLEDGE_MANAGEMENT);
        assertAuthorization("delete", KNOWLEDGE_MANAGEMENT);
    }

    @Test
    void reportSyncUsesClinicalRoleAndChatPermission() throws Exception {
        assertAuthorization("syncReport",
                "hasAnyRole('DOCTOR', 'DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') "
                        + "and hasAuthority('USE_AI_CHAT')");
    }

    private void assertAuthorization(String methodName, String expectedExpression) throws Exception {
        Method method = java.util.Arrays.stream(KnowledgeController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodException(methodName));
        assertEquals(expectedExpression, method.getAnnotation(PreAuthorize.class).value());
    }
}
