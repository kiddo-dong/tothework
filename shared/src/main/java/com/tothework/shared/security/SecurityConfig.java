package com.tothework.shared.security;

import com.tothework.shared.web.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

/**
 * 모든 서비스에 공통인 보안 설정. 상태 없는 JWT 인증, CORS, 401·403 응답 형식.
 *
 * <p>로그인 없이 열 경로는 여기 적지 않는다. 각 서비스가 {@link PublicEndpoints} 빈으로 등록한다.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableConfigurationProperties({JwtProperties.class, SecurityConfig.CorsProperties.class})
class SecurityConfig {

    /** @param allowedOrigins 리프레시 토큰 쿠키를 주고받을 프론트 오리진. 쿠키 때문에 와일드카드를 쓸 수 없다 */
    @ConfigurationProperties("tothework.web.cors")
    record CorsProperties(List<String> allowedOrigins) {
    }

    @Bean
    JwtTokens jwtTokens(JwtProperties properties) {
        return new JwtTokens(properties);
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtTokens jwtTokens,
            AuthenticatedUserLoader userLoader, ObjectProvider<PublicEndpoints> publicEndpoints,
            CorsProperties corsProperties, JsonMapper jsonMapper) throws Exception {
        String[] publicPatterns = publicEndpoints.orderedStream()
                .flatMap(endpoints -> endpoints.patterns().stream())
                .toArray(String[]::new);
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource(corsProperties)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(publicPatterns.length == 0 ? new String[] {"/__none__"} : publicPatterns)
                        .permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, e) -> writeError(response, jsonMapper,
                                HttpServletResponse.SC_UNAUTHORIZED, "인증이 필요합니다. 로그인 후 이용해 주세요."))
                        .accessDeniedHandler((request, response, e) -> writeError(response, jsonMapper,
                                HttpServletResponse.SC_FORBIDDEN, "접근 권한이 없습니다.")))
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokens, userLoader),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void writeError(HttpServletResponse response, JsonMapper jsonMapper, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(jsonMapper.writeValueAsString(ApiResponse.error(message)));
    }

    private static CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
