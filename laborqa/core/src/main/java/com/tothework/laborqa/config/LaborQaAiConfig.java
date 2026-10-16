package com.tothework.laborqa.config;

import javax.sql.DataSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import com.tothework.shared.security.PublicEndpoints;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LaborQaProperties.class)
class LaborQaAiConfig {

    private static final int EMBEDDING_DIMENSIONS = 1536; // text-embedding-3-small

    /**
     * 지식베이스 적재 API는 매장 역할로 보호할 수 없는 전역 자원이라, 로그인 대신 컨트롤러가 X-Admin-Token을 직접
     * 검사한다. 와일드카드라 이 경로 아래 엔드포인트를 추가하면 인증 없이 열리니 토큰 검사를 함께 넣을 것.
     */
    @Bean
    PublicEndpoints laborQaAdminEndpoints() {
        return PublicEndpoints.of("/api/v1/labor-qa/admin/**");
    }

    @Bean
    ChatClient laborQaChatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder.build();
    }

    /**
     * 근거 문서용 벡터 저장소. 업무 DB와 엔진이 달라(PostgreSQL + pgvector) 별도 DataSource를 쓰며, 그 DataSource는
     * 빈으로 등록하지 않고 여기서만 만든다. 등록하면 Spring Data JDBC가 어느 DB를 써야 할지 헷갈린다.
     *
     * <p>{@code @Lazy}: 생성될 때 실제로 접속한다. pgvector가 없는 로컬에서도 앱 전체가 뜨도록, 상담 기능이 처음
     * 쓰일 때까지 접속을 미룬다. 주입받는 쪽도 {@code ObjectProvider}로 받아야 지연이 유지된다.
     */
    @Bean
    @Lazy
    VectorStore laborQaVectorStore(EmbeddingModel embeddingModel, LaborQaProperties properties) {
        LaborQaProperties.VectorStore connection = properties.vectorStore();
        DataSource vectorDataSource = DataSourceBuilder.create()
                .driverClassName("org.postgresql.Driver")
                .url(connection.url())
                .username(connection.username())
                .password(connection.password())
                .build();
        return PgVectorStore.builder(new JdbcTemplate(vectorDataSource), embeddingModel)
                .dimensions(EMBEDDING_DIMENSIONS)
                .initializeSchema(true)
                // 적재는 관리자 API로만 하므로 재시작할 때 테이블을 지우지 않는다.
                .removeExistingVectorStoreTable(false)
                .build();
    }
}
