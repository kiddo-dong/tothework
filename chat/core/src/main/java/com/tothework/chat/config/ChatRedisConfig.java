package com.tothework.chat.config;

import com.tothework.chat.message.RedisChatSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 서버 간 채팅 전달. 서버가 뜰 때 패턴 하나({@code chat:stores:*})만 구독한다.
 *
 * <p>방(매장)마다 구독을 걸고 푸는 방식은 쓰지 않는다. 그 서버에 누가 어느 방에 있는지 추적해야 하고, 구독이 늦게
 * 걸리는 순간의 메시지를 놓칠 수 있다. 사용자가 적어 모든 서버가 모든 매장의 이벤트를 받아도 부담이 없다. 받은
 * 서버에 그 방 구독자가 없으면 SimpleBroker가 아무에게도 보내지 않을 뿐이다.
 */
@Configuration(proxyBeanMethods = false)
class ChatRedisConfig {

    static final PatternTopic ALL_STORE_CHATS = new PatternTopic("chat:stores:*");

    @Bean
    RedisMessageListenerContainer chatRedisListenerContainer(RedisConnectionFactory connectionFactory,
            RedisChatSubscriber subscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, ALL_STORE_CHATS);
        return container;
    }
}
