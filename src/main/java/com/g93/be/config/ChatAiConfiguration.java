package com.g93.be.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(ChatProperties.class)
@ConditionalOnProperty(name = "app.chat.enabled", havingValue = "true")
public class ChatAiConfiguration {

    @Bean
    ChatClient healthSyncChatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    TokenTextSplitter medicalKnowledgeSplitter() {
        return TokenTextSplitter.builder()
                .withChunkSize(700)
                .withMinChunkSizeChars(250)
                .withMinChunkLengthToEmbed(20)
                .withMaxNumChunks(10_000)
                .withKeepSeparator(true)
                .build();
    }

    @Bean
    RestClient knowledgeRestClient(RestClient.Builder builder) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        return builder.requestFactory(requestFactory).build();
    }
}
