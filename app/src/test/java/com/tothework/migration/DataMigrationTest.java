package com.tothework.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.tothework.shared.persistence.ServiceSchema;
import com.tothework.support.TestDatabase;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 기존 al-bam DB → 서비스별 스키마 이전 스크립트({@code migration/})를 검증한다.
 *
 * <p>원본은 기존 스키마의 고정본({@code albam-schema.sql})으로 만들고, 대상은 각 서비스의 Flyway 마이그레이션을 다른
 * 이름의 스키마({@code twmig_*})에 적용해 만든다. 그래서 테스트가 쓰는 {@code tw_*} 스키마와 섞이지 않고, 새 마이그레이션이
 * 바뀌면 이 테스트가 이전 스크립트와 어긋난 곳을 바로 드러낸다.
 *
 * <p>픽스처는 모든 테이블에 한 행 이상을 넣고, 이름만 바뀌는 열 외에 모양이 바뀌는 곳을 일부러 건드린다: 소속/고용 조건
 * 분리, NULL 요일 행, 순번에 구멍이 있는 매뉴얼 이미지, id 순서와 입력 순서가 다른 레시피 줄, 근무 중 출근 기록.
 */
@SpringBootTest
@Import(TestDatabase.class)
class DataMigrationTest {

    private static final Path MIGRATION = Path.of("..", "migration");
    private static final String SOURCE = "twmig_src";
    private static final String PREFIX = "twmig_";
    private static final List<String> SERVICES = List.of("identity", "store", "workforce", "operations", "laborqa");

    @Autowired private DataSource dataSource;
    @MockitoBean private JavaMailSender mailSender;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() throws Exception {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP DATABASE IF EXISTS " + SOURCE);
        jdbc.execute("CREATE DATABASE " + SOURCE);
        runIn(SOURCE, Files.readString(MIGRATION.resolve("albam-schema.sql")));
        runIn(SOURCE, FIXTURE);
        prepareTargets(PREFIX);
    }

    /** 리허설 대상은 결과를 들여다볼 수 있게 남기고, 픽스처용 스키마만 지운다. */
    @AfterEach
    void dropFixtureSchemas() {
        jdbc.execute("DROP DATABASE IF EXISTS " + SOURCE);
        SERVICES.forEach(service -> jdbc.execute("DROP DATABASE IF EXISTS " + PREFIX + service));
    }

    @Test
    void movesEveryRowAndReshapesWhatChanged() throws Exception {
        Map<String, Object> before = jdbc.queryForMap(render("precheck.sql", SOURCE, PREFIX));
        assertThat(((Number) before.get("source_version")).intValue()).isEqualTo(12);
        assertThat(((Number) before.get("target_rows")).longValue()).isZero();

        run(render("albam-to-tothework.sql", SOURCE, PREFIX));

        // 대조: 모든 테이블의 행 수와 돈·시간 합계가 원본과 같다
        for (String statement : statements(render("verify.sql", SOURCE, PREFIX))) {
            for (Map<String, Object> row : jdbc.queryForList(statement)) {
                List<Object> values = new ArrayList<>(row.values());
                assertThat(values.get(2)).as("%s", values.get(0)).hasToString(String.valueOf(values.get(1)));
            }
        }
        // 다시 실행하면 전제 확인에서 멈춘다
        assertThat(((Number) jdbc.queryForMap(render("precheck.sql", SOURCE, PREFIX)).get("target_rows")).longValue())
                .isPositive();

        // 소속과 고용 조건 분리. 퇴사자도 고용 조건을 갖는다(지난 급여 재계산용)
        assertThat(jdbc.queryForMap("SELECT store_id, hourly_wage, tax_mode, weekly_holiday_day FROM "
                + "twmig_workforce.employment_terms WHERE member_id = 11"))
                .containsEntry("store_id", 1L).containsEntry("hourly_wage", 12000)
                .containsEntry("tax_mode", "WITHHOLDING_3_3").containsEntry("weekly_holiday_day", "SUNDAY");
        assertThat(jdbc.queryForObject("SELECT hourly_wage FROM twmig_workforce.employment_terms WHERE member_id = 12",
                Integer.class)).isEqualTo(10030);
        assertThat(jdbc.queryForMap("SELECT role, title, status FROM twmig_store.store_members WHERE id = 11"))
                .containsEntry("role", "STAFF").containsEntry("title", "바리스타").containsEntry("status", "ACTIVE");
        // NULL 요일 행은 뺀다
        assertThat(jdbc.queryForList("SELECT day_of_week FROM twmig_workforce.employment_available_days "
                + "WHERE member_id = 11 ORDER BY day_of_week", String.class)).containsExactly("MONDAY", "TUESDAY");

        // 매장별 조회용 store_id 복사
        assertThat(jdbc.queryForList("SELECT DISTINCT store_id FROM twmig_workforce.shifts", Long.class))
                .containsExactly(1L);
        assertThat(jdbc.queryForObject("SELECT store_id FROM twmig_operations.checklist_completions WHERE id = 1",
                Long.class)).isEqualTo(1L);
        // 근무 중 기록의 생성 열이 새 열(member_id)을 따른다 — 동시 출근을 막는 유니크 키가 그대로 동작한다
        assertThat(jdbc.queryForObject("SELECT working_member_id FROM twmig_workforce.attendances WHERE id = 2",
                Long.class)).isEqualTo(11L);

        // 매뉴얼 이미지 순번은 0부터 빈틈없이, 순서는 그대로
        assertThat(jdbc.queryForList("SELECT CONCAT(sort_order, ':', image_key) FROM twmig_operations.manual_images "
                + "WHERE manual_id = 1 ORDER BY sort_order", String.class))
                .containsExactly("0:manual-images/1/a.png", "1:manual-images/1/b.png");
        // 레시피 줄 순서는 예전 화면 순서(id 순)
        assertThat(jdbc.queryForList("SELECT CONCAT(line_order, ':', ingredient_id) FROM "
                + "twmig_operations.menu_recipe_items WHERE menu_id = 1 ORDER BY line_order", String.class))
                .containsExactly("0:2", "1:1");
        assertThat(jdbc.queryForObject("SELECT member_id FROM twmig_operations.notice_reads WHERE id = 1", Long.class))
                .isEqualTo(11L);

        // 소프트 삭제된 매장과 탈퇴한 사용자도 그대로 옮긴다(정리는 새 앱의 일이다)
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM twmig_store.stores WHERE id = 2",
                Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM twmig_identity.users WHERE id = 4",
                Boolean.class)).isTrue();

        // id를 그대로 넣었어도 다음 자동 증가는 최대 id 뒤에서 시작한다
        jdbc.update("INSERT INTO twmig_identity.users (email, name, provider, email_verified) "
                + "VALUES ('new@example.com', '새 사용자', 'LOCAL', 1)");
        assertThat(jdbc.queryForObject("SELECT id FROM twmig_identity.users WHERE email = 'new@example.com'",
                Long.class)).isGreaterThan(4L);
    }

    /**
     * 실제 원본으로 리허설한다. 기본 빌드에서는 돌지 않는다.
     *
     * <pre>MIGRATION_REHEARSAL_SOURCE=al-bam SPRING_PROFILES_ACTIVE=localdb DB_PASSWORD=... \
     *     ./gradlew :app:test --tests '*DataMigrationTest.rehearsal*'</pre>
     *
     * 대상은 {@code twrehearsal_*} 스키마에 새로 만들고, 운영과 같은 {@code migrate.sh}로 옮긴다. 원본은 읽기만 한다.
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "MIGRATION_REHEARSAL_SOURCE", matches = ".+")
    void rehearsalWithTheRealSource() throws Exception {
        prepareTargets("twrehearsal_");
        ProcessBuilder process = new ProcessBuilder("bash", MIGRATION.resolve("migrate.sh").toString(),
                "--source", System.getenv("MIGRATION_REHEARSAL_SOURCE"), "--target-prefix", "twrehearsal_")
                .redirectErrorStream(true);
        process.environment().put("MYSQL_PWD", System.getenv("DB_PASSWORD"));
        Process running = process.start();
        String output = new String(running.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        System.out.println(output);
        assertThat(running.waitFor()).as(output).isZero();
    }

    /** 각 서비스의 Flyway 마이그레이션을 이름만 다른 스키마에 적용한다. 이미 있으면 지우고 새로 만든다. */
    private void prepareTargets(String prefix) {
        for (String service : SERVICES) {
            jdbc.execute("DROP DATABASE IF EXISTS " + prefix + service);
            new ServiceSchema(prefix + service, "classpath:db/" + service).migrate(dataSource);
        }
    }

    private static String render(String file, String source, String prefix) throws IOException {
        String sql = Files.readString(MIGRATION.resolve(file)).replace("${source}", source);
        for (String service : SERVICES) {
            sql = sql.replace("${" + service + "}", prefix + service);
        }
        return sql;
    }

    /** 주석 줄을 빼고 ';'로 나눈다. 대조 SQL에는 문자열 안의 ';'가 없다. */
    private static List<String> statements(String script) {
        String withoutComments = script.lines().filter(line -> !line.strip().startsWith("--"))
                .collect(Collectors.joining("\n"));
        return Arrays.stream(withoutComments.split(";")).map(String::strip)
                .filter(statement -> !statement.isEmpty()).toList();
    }

    private void run(String script) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ByteArrayResource(script.getBytes(StandardCharsets.UTF_8)));
        }
    }

    /** 스키마 이름 없이 쓴 SQL(덤프·픽스처)을 그 스키마에서 실행한다. 풀로 돌아가는 연결의 기본 스키마는 되돌린다. */
    private void runIn(String schema, String script) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String original = connection.getCatalog();
            connection.setCatalog(schema);
            try {
                ScriptUtils.executeSqlScript(connection,
                        new ByteArrayResource(script.getBytes(StandardCharsets.UTF_8)));
            } finally {
                connection.setCatalog(original);
            }
        }
    }

    /** 기존 스키마에 넣는 픽스처. 매장 1(운영 중)·2(소프트 삭제), 소속 10(사장)·11(직원)·12(퇴사자)·20(매장 2 사장). */
    private static final String FIXTURE = """
            INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum,
                installed_by, installed_on, execution_time, success)
            VALUES (1, '12', 'drop labor qa session user fk', 'SQL', 'V12__x.sql', 0, 'root', NOW(), 1, 1);

            INSERT INTO users (id, email, password, name, phone, birth_date, provider, provider_id, email_verified,
                deleted_at, created_at, updated_at) VALUES
              (1, 'owner@example.com', '{bcrypt}hash', '사장', '010-1', '1980-01-01', 'LOCAL', NULL, 1, NULL, NOW(), NOW()),
              (2, 'staff@example.com', NULL, '직원', '010-2', '2000-01-01', 'KAKAO', 'k-2', 1, NULL, NOW(), NOW()),
              (3, 'left@example.com', NULL, '퇴사자', NULL, NULL, 'NAVER', 'n-3', 1, NULL, NOW(), NOW()),
              (4, 'withdrawn-4@deleted', NULL, '탈퇴한 사용자', NULL, NULL, 'LOCAL', NULL, 0, NOW(), NOW(), NOW());
            INSERT INTO email_tokens (id, user_id, token, type, expires_at, used, created_at, updated_at)
              VALUES (1, 1, 'tok-1', 'VERIFY_EMAIL', NOW(), 1, NOW(), NOW());
            INSERT INTO refresh_tokens (id, user_id, token_hash, expires_at, created_at, updated_at)
              VALUES (1, 2, REPEAT('a', 64), NOW() + INTERVAL 14 DAY, NOW(), NOW());

            INSERT INTO stores (id, name, invite_code, break_policy, small_business, payday, plan, deleted_at,
                created_at, updated_at) VALUES
              (1, '카페', 'AAAAAA', 'STATUTORY', 0, 10, 'BASIC', NULL, NOW(), NOW()),
              (2, '닫은 가게', 'BBBBBB', 'FLEXIBLE', 1, NULL, 'BASIC', NOW(), NOW(), NOW());
            INSERT INTO store_business_hours (store_id, day_of_week, open_time, close_time, closed)
              VALUES (1, 'MONDAY', '08:00', '22:00', 0);
            INSERT INTO store_onboarding_steps (store_id, step_key) VALUES (1, 'checklist');
            INSERT INTO store_members (id, store_id, user_id, role, title, status, joined_at, resigned_at, hourly_wage,
                tax_mode, weekly_holiday_day, created_at, updated_at) VALUES
              (10, 1, 1, 'OWNER', NULL, 'ACTIVE', NOW(), NULL, 0, 'NONE', NULL, NOW(), NOW()),
              (11, 1, 2, 'STAFF', '바리스타', 'ACTIVE', NOW(), NULL, 12000, 'WITHHOLDING_3_3', 'SUNDAY', NOW(), NOW()),
              (12, 1, 3, 'STAFF', NULL, 'INACTIVE', NOW(), NOW(), 10030, 'NONE', NULL, NOW(), NOW()),
              (20, 2, 1, 'OWNER', NULL, 'ACTIVE', NOW(), NULL, 0, 'NONE', NULL, NOW(), NOW());
            INSERT INTO store_member_available_days (store_member_id, day_of_week)
              VALUES (11, 'MONDAY'), (11, 'TUESDAY'), (11, NULL);
            INSERT INTO join_requests (id, store_id, user_id, status, requested_at, version, created_at, updated_at)
              VALUES (1, 1, 4, 'PENDING', NOW(), 0, NOW(), NOW());

            INSERT INTO shifts (id, store_member_id, work_date, start_time, end_time, status, break_minutes,
                created_at, updated_at) VALUES
              (1, 11, '2026-09-01', '09:00', '13:30', 'CONFIRMED', 30, NOW(), NOW()),
              (2, 11, '2026-09-02', '09:00', '13:30', 'CANCELED', 30, NOW(), NOW());
            INSERT INTO shift_templates (id, store_id, name, start_time, end_time, break_minutes, display_order,
                created_at, updated_at) VALUES (1, 1, '오픈', '08:00', '12:00', NULL, 0, NOW(), NOW());
            INSERT INTO attendances (id, store_member_id, work_date, clock_in_at, clock_out_at, status, break_minutes,
                created_at, updated_at) VALUES
              (1, 11, '2026-09-01', '2026-09-01 09:00', '2026-09-01 13:30', 'DONE', 30, NOW(), NOW()),
              (2, 11, CURDATE(), NOW(), NULL, 'WORKING', 0, NOW(), NOW());
            INSERT INTO leave_usages (id, store_member_id, leave_date, created_at, updated_at)
              VALUES (1, 11, '2026-09-03', NOW(), NOW());
            INSERT INTO payrolls (id, store_member_id, target_year, target_month, regular_pay, overtime_pay, night_pay,
                holiday_work_pay, weekly_holiday_pay, leave_pay, total_pay, deduction, net_pay, generated_at,
                created_at, updated_at)
              VALUES (1, 11, 2026, 9, 48000, 0, 0, 0, 0, 96000, 144000, 4752, 139248, NOW(), NOW(), NOW());

            INSERT INTO checklist_items (id, store_id, type, content, display_order, created_at, updated_at)
              VALUES (1, 1, 'OPEN', '청소', 0, NOW(), NOW());
            INSERT INTO checklist_completions (id, item_id, work_date, checked_by, checked_at)
              VALUES (1, 1, '2026-09-01', 11, NOW());
            INSERT INTO notices (id, store_id, author_id, title, content, created_at, updated_at)
              VALUES (1, 1, 10, '공지', '내용', NOW(), NOW());
            INSERT INTO notice_reads (id, notice_id, store_member_id, read_at) VALUES (1, 1, 11, NOW());
            INSERT INTO handover_notes (id, store_id, author_id, content, work_date, created_at, updated_at)
              VALUES (1, 1, 11, '우유 떨어짐', '2026-09-01', NOW(), NOW());
            INSERT INTO manuals (id, store_id, author_id, category, title, content, display_order, created_at,
                updated_at) VALUES (1, 1, 10, '조리', '라떼', '우유 200ml', 0, NOW(), NOW());
            INSERT INTO manual_images (manual_id, sort_order, image_key)
              VALUES (1, 2, 'manual-images/1/b.png'), (1, 0, 'manual-images/1/a.png');
            INSERT INTO suppliers (id, store_id, name, category, display_order, created_at, updated_at)
              VALUES (1, 1, '우유상사', '유제품', 0, NOW(), NOW());
            INSERT INTO supplier_items (id, supplier_id, name, spec, display_order, created_at, updated_at)
              VALUES (1, 1, '우유', '1L', 0, NOW(), NOW());
            INSERT INTO supplier_item_quantities (item_id, day_of_week, quantity)
              VALUES (1, 'MONDAY', '10개'), (1, 'THURSDAY', '2박스');
            INSERT INTO menu_ingredients (id, store_id, name, price, package_qty, unit, loss_rate, category,
                created_at, updated_at) VALUES
              (1, 1, '우유', 3000, 1000, 'ML', 0, '유제품', NOW(), NOW()),
              (2, 1, '원두', 20000, 1000, 'G', 0, '원두', NOW(), NOW());
            INSERT INTO store_menus (id, store_id, name, selling_price, category, created_at, updated_at)
              VALUES (1, 1, '라떼', 5000, '커피', NOW(), NOW());
            INSERT INTO menu_recipe_items (id, menu_id, ingredient_id, amount) VALUES (7, 1, 1, 200), (3, 1, 2, 20);

            INSERT INTO labor_qa_sessions (id, user_id, title, created_at, updated_at)
              VALUES (1, 2, '주휴수당', NOW(), NOW());
            INSERT INTO labor_qa_messages (id, session_id, role, content, sources, created_at, updated_at) VALUES
              (1, 1, 'USER', '주휴수당 받을 수 있나요?', NULL, NOW(), NOW()),
              (2, 1, 'ASSISTANT', '주 15시간 이상이면...', '[]', NOW(), NOW());
            """;
}
