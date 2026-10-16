package com.tothework.workforce.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class WorkforceAiConfig {

    /** 스케줄 초안용. laborqa의 상담용 ChatClient와 같은 타입이라 이름으로 구분해 주입한다. */
    @Bean
    ChatClient scheduleChatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder.build();
    }
}
