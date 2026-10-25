package com.tothework.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Properties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.core.io.ClassPathResource;

/**
 * 외부 호출이 끝없이 매달리지 않게 하는 운영 설정. 빠지면 에러 없이 기본값(메일 무한 대기, AI 4분)으로 돌아가 운영에서만
 * 드러나므로 값이 있는지 지킨다. 스프링을 띄우지 않고 설정 파일만 읽는다.
 */
class OperationalSettingsTest {

    /** nginx의 proxy_read_timeout (deploy/nginx/albam.conf). */
    private static final Duration NGINX_READ_TIMEOUT = Duration.ofSeconds(120);

    private static Properties settings;

    @BeforeAll
    static void load() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        settings = yaml.getObject();
    }

    @Test
    void mailHasTimeouts() {
        for (String key : new String[] {"connectiontimeout", "timeout", "writetimeout"}) {
            String value = settings.getProperty("spring.mail.properties.mail.smtp." + key);
            assertThat(value).as("mail.smtp." + key).isNotNull();
            assertThat(Long.parseLong(value)).isBetween(1L, 30_000L);
        }
    }

    /** 재시도까지 합친 최악의 시간이 nginx가 끊기 전에 끝나야 한다. 아니면 사용자는 504를 받고 서버는 계속 비용을 낸다. */
    @Test
    void anAiCallFinishesBeforeNginxGivesUp() {
        for (String model : new String[] {"chat", "embedding"}) {
            Duration timeout = DurationStyle.detectAndParse(settings.getProperty("spring.ai.openai." + model + ".timeout"));
            int attempts = 1 + Integer.parseInt(settings.getProperty("spring.ai.openai." + model + ".max-retries"));

            assertThat(timeout.multipliedBy(attempts)).as(model).isLessThan(NGINX_READ_TIMEOUT);
        }
    }

    @Test
    void chatAnswersHaveATokenCap() {
        assertThat(settings.getProperty("spring.ai.openai.chat.options.max-tokens")).isNotNull();
    }
}
