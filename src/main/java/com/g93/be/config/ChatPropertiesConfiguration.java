package com.g93.be.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link ChatProperties} unconditionally.
 *
 * <p>The registration cannot live in {@code ChatAiConfiguration}, which is conditional on
 * {@code app.chat.enabled}: AI usage tracking reads the token pricing from these properties on
 * every deployment, chat switched on or not. While the registration sat behind that condition, a
 * deployment with chat disabled could not start its application context at all.
 */
@Configuration
@EnableConfigurationProperties(ChatProperties.class)
public class ChatPropertiesConfiguration {
}
