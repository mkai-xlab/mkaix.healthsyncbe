package com.g93.be.config;

import com.g93.be.service.OverlappingTokenTextSplitter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@ConditionalOnProperty(name = "app.chat.enabled", havingValue = "true")
public class ChatAiConfiguration {

    @Bean
    ChatClient healthSyncChatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    TextSplitter medicalKnowledgeSplitter() {
        // 120-token overlap (~17% of chunk size) so a sentence or table row that
        // lands on a chunk boundary still survives whole in at least one chunk.
        return OverlappingTokenTextSplitter.builder()
                .chunkSize(700)
                .chunkOverlap(120)
                .minChunkSizeChars(250)
                .minChunkLengthToEmbed(20)
                .maxNumChunks(10_000)
                .keepSeparator(true)
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
