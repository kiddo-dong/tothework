package com.tothework.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tothework.support.TestAccounts;
import com.tothework.support.TestAccounts.Account;
import com.tothework.support.TestDatabase;
import com.tothework.support.TestStores;
import com.tothework.workforce.attendance.AttendanceService;
import com.tothework.workforce.attendance.CreateAttendanceRequest;
import com.tothework.workforce.shift.CreateShiftRequest;
import com.tothework.workforce.shift.ShiftService;
import java.time.LocalDate;
import java.time.LocalTime;
import javax.sql.DataSource;
import net.ttddyy.dsproxy.QueryCountHolder;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 목록 API가 보내는 SQL 수가 행 수에 따라 늘지 않는지 확인한다.
 *
 * <p>서비스를 나누면서 예전엔 JOIN 한 번이던 조회가 "store에 소속을, identity에 이름을, workforce에 고용 조건을"
 * 묻는 여러 호출로 바뀌었다. 한 명씩 물으면 직원 수만큼 쿼리가 늘어난다(N+1). 그래서 데이터를 늘리기 전과 후에 같은
 * 요청의 쿼리 수를 재서 같아야 통과시킨다. 절대값이 아니라 증가 여부를 보므로 구현을 바꿔도 테스트를 고칠 일이 적다.
 *
 * <p>카운터는 스레드 단위라, 비동기 이벤트 리스너가 다른 스레드에서 보낸 쿼리는 섞이지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestDatabase.class, QueryCountTest.CountingDataSource.class})
class QueryCountTest {

    private static final int MORE = 3;

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private ShiftService shiftService;
    @Autowired private AttendanceService attendanceService;
    @MockitoBean private JavaMailSender mailSender;

    private TestAccounts accounts;
    private TestStores stores;
    private Account owner;
    private long storeId;
    private final LocalDate day = LocalDate.now().plusDays(10);
    private final LocalDate yesterday = LocalDate.now().minusDays(1);

    @BeforeEach
    void setUp() throws Exception {
        accounts = new TestAccounts(mockMvc, jdbcClient);
        stores = new TestStores(mockMvc, jdbcClient);
        owner = accounts.create("사장");
        storeId = stores.open(owner);
        addWorkerWithRecords();
    }

    @Test
    void myStoresDoesNotGrowWithTheNumberOfStores() throws Exception {
        String url = "/api/v1/stores";
        int before = queriesOf(url);
        for (int i = 0; i < MORE; i++) {
            stores.open(owner);
        }
        assertThat(queriesOf(url)).isEqualTo(before);
    }

    @Test
    void memberListDoesNotGrowWithTheNumberOfMembers() throws Exception {
        assertConstantAsWorkersGrow("/api/v1/stores/" + storeId + "/members");
    }

    @Test
    void shiftListDoesNotGrowWithTheNumberOfShifts() throws Exception {
        assertConstantAsWorkersGrow("/api/v1/stores/" + storeId + "/shifts?from=" + day + "&to=" + day);
    }

    @Test
    void attendanceListDoesNotGrowWithTheNumberOfRecords() throws Exception {
        assertConstantAsWorkersGrow("/api/v1/stores/" + storeId + "/attendance?from=" + yesterday + "&to=" + yesterday);
    }

    @Test
    void attendanceReportDoesNotGrowWithTheNumberOfMembers() throws Exception {
        assertConstantAsWorkersGrow("/api/v1/stores/" + storeId + "/attendance/report?from=" + yesterday + "&to="
                + yesterday);
    }

    @Test
    void monthlyDashboardDoesNotGrowWithTheNumberOfMembers() throws Exception {
        assertConstantAsWorkersGrow("/api/v1/stores/" + storeId + "/dashboard?year=" + yesterday.getYear() + "&month="
                + yesterday.getMonthValue());
    }

    @Test
    void dailyAndWeeklyDashboardsDoNotGrowWithTheNumberOfMembers() throws Exception {
        String daily = "/api/v1/stores/" + storeId + "/dashboard/daily?date=" + yesterday;
        String weekly = "/api/v1/stores/" + storeId + "/dashboard/weekly?date=" + yesterday;
        int dailyBefore = queriesOf(daily);
        int weeklyBefore = queriesOf(weekly);
        for (int i = 0; i < MORE; i++) {
            addWorkerWithRecords();
        }
        assertThat(queriesOf(daily)).isEqualTo(dailyBefore);
        assertThat(queriesOf(weekly)).isEqualTo(weeklyBefore);
    }

    /** 홈은 세 서비스를 조합한다. 오늘 근무자가 늘어도 각 서비스에 한 번씩만 물어야 한다. */
    @Test
    void homeDoesNotGrowWithTodaysWorkers() throws Exception {
        addTodayShiftForNewWorker();
        String url = "/api/v1/stores/" + storeId + "/home";
        int before = queriesOf(url);
        for (int i = 0; i < MORE; i++) {
            addTodayShiftForNewWorker();
        }
        assertThat(queriesOf(url)).isEqualTo(before);
    }

    private void addTodayShiftForNewWorker() throws Exception {
        Account worker = accounts.create("알바");
        long memberId = stores.join(storeId, owner, worker);
        shiftService.createShift(storeId, owner.userId(), new CreateShiftRequest(memberId, LocalDate.now(),
                LocalTime.of(8, 0), LocalTime.of(12, 30), null));
    }

    private void assertConstantAsWorkersGrow(String url) throws Exception {
        int before = queriesOf(url);
        for (int i = 0; i < MORE; i++) {
            addWorkerWithRecords();
        }
        assertThat(queriesOf(url)).as("직원 %d명을 더한 뒤 %s의 쿼리 수", MORE, url).isEqualTo(before);
    }

    /** 직원 한 명을 합류시키고 스케줄·출퇴근 기록을 하나씩 남긴다. */
    private void addWorkerWithRecords() throws Exception {
        Account worker = accounts.create("알바");
        long memberId = stores.join(storeId, owner, worker);
        shiftService.createShift(storeId, owner.userId(),
                new CreateShiftRequest(memberId, day, LocalTime.of(10, 0), LocalTime.of(14, 0), null));
        attendanceService.createAttendance(storeId, owner.userId(), new CreateAttendanceRequest(memberId,
                yesterday.atTime(10, 0), yesterday.atTime(14, 0), null));
    }

    private int queriesOf(String url) throws Exception {
        QueryCountHolder.clear();
        mockMvc.perform(get(url).header("Authorization", owner.bearer())).andExpect(status().isOk());
        int total = (int) QueryCountHolder.getGrandTotal().getTotal();
        QueryCountHolder.clear();
        return total;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CountingDataSource {

        @Bean
        static BeanPostProcessor countQueries() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    return bean instanceof DataSource dataSource
                            ? ProxyDataSourceBuilder.create(dataSource).name("count").countQuery().build()
                            : bean;
                }
            };
        }
    }
}
