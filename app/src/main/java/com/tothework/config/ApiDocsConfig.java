package com.tothework.config;

import com.tothework.shared.security.PublicEndpoints;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API 문서(springdoc). 모든 서비스의 API를 한 문서로 보여 주므로 서비스가 아니라 조립 모듈(app)에 둔다.
 *
 * <p>문서를 실제로 막는 것은 {@code springdoc.*.enabled}(기본 false, {@code SWAGGER_ENABLED=true}일 때만 켜짐)다. 아래
 * 공개 경로는 켰을 때 로그인 없이 열리게 할 뿐이다.
 */
@Configuration(proxyBeanMethods = false)
class ApiDocsConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ToTheWork API")
                        .description("자영업자를 위한 알바생·매장 관리 서비스 API. "
                                + "로그인 후 발급받은 Access Token을 우측 상단 Authorize 버튼에 입력하세요.")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .name(BEARER_AUTH)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }

    @Bean
    PublicEndpoints apiDocsPublicEndpoints() {
        return PublicEndpoints.of("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**");
    }
}
