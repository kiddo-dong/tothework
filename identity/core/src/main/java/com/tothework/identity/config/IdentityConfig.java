package com.tothework.identity.config;

import com.tothework.shared.security.PublicEndpoints;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityProperties.class)
class IdentityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** 가입·로그인·토큰 재발급·메일 링크는 로그인 전에 쓰는 경로다. */
    @Bean
    PublicEndpoints identityPublicEndpoints() {
        return PublicEndpoints.of("/api/v1/auth/**");
    }
}
