package com.g93.be.chat;

public interface AiChatGateway {
    MedicalDocumentAssessment assessMedicalDocument(String sampledContent);

    ChatRoutingDecision route(String question, String roleCode, String conversationHistory);

    GeneratedChatAnswer answerBusiness(String question, String businessContext, String conversationHistory);

    GeneratedChatAnswer answerMedical(String question, String medicalContext, String conversationHistory);

    GeneratedChatAnswer answerHybrid(
            String question,
            String businessContext,
            String medicalContext,
            String conversationHistory);
}
