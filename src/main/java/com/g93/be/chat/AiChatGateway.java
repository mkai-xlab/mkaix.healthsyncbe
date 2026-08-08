package com.g93.be.chat;

public interface AiChatGateway {
    ChatRoutingDecision route(String question, String roleCode);

    String answerBusiness(String question, String businessContext);

    String answerMedical(String question, String medicalContext);

    String answerHybrid(String question, String businessContext, String medicalContext);
}
