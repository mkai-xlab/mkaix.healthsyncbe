package com.g93.be.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import tools.jackson.core.JacksonException;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpringAiChatGatewayTest {

    private ChatClient chatClient;
    private ChatClient.CallResponseSpec callResponse;
    private SpringAiChatGateway gateway;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        callResponse = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(request);
        when(request.system(anyString())).thenReturn(request);
        when(request.user(anyString())).thenReturn(request);
        when(request.call()).thenReturn(callResponse);
        gateway = new SpringAiChatGateway(chatClient);
    }

    @Test
    void unparsableRouterReplyBecomesNullDecisionInsteadOfServerError() {
        when(callResponse.entity(ChatRoutingDecision.class))
                .thenThrow(new UnparsableReply("Unrecognized token 'Analyse'"));

        assertNull(gateway.route("ten cac benh nhan day la gi", "DOCTOR", "USER: thong ke ca kham KL3"));
    }

    @Test
    void unparsableClassifierReplyBecomesNullAssessmentInsteadOfServerError() {
        when(callResponse.entity(MedicalDocumentAssessment.class))
                .thenThrow(new UnparsableReply("Unrecognized token 'Here'"));

        assertNull(gateway.assessMedicalDocument("sample"));
    }

    @Test
    void providerFailuresStillPropagateSoQuotaErrorsKeepTheirStatus() {
        when(callResponse.entity(ChatRoutingDecision.class))
                .thenThrow(new RuntimeException("429 RESOURCE_EXHAUSTED: quota exceeded"));

        assertThrows(RuntimeException.class, () -> gateway.route("question", "DOCTOR", ""));
    }

    /** Stands in for the Jackson parse failure raised when the model answers with prose. */
    private static final class UnparsableReply extends JacksonException {
        private UnparsableReply(String message) {
            super(message);
        }
    }
}
