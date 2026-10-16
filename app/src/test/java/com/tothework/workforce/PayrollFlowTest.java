package com.tothework.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tothework.store.store.StorePurgeJob;
import com.tothework.support.TestAccounts;
import com.tothework.support.TestAccounts.Account;
import com.tothework.support.TestDatabase;
import com.tothework.support.TestStores;
import com.tothework.workforce.attendance.AttendanceService;
import com.tothework.workforce.attendance.CreateAttendanceRequest;
import com.tothework.workforce.payroll.PayrollService;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 급여·대시보드를 실제 API로 끝까지 돌린다. 계산 규칙은 단위 테스트가 보고, 여기서는 서비스 경계를 넘는 조합
 * (store의 소속·매장 이름, identity의 이름, workforce의 근태·고용 조건)과 저장·권한·삭제를 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabase.class)
class PayrollFlowTest {

    private static final int WAGE = 10_000;

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private AttendanceService attendanceService;
    @Autowired private PayrollService payrollService;
    @Autowired private StorePurgeJob storePurgeJob;
    @MockitoBean private JavaMailSender mailSender;

    private Account owner;
    private Account worker;
    private Account colleague;
    private long storeId;
    private long workerMemberId;
    private long colleagueMemberId;
    /** 지난달 15일. 이번 달 날짜를 쓰면 예상 급여·주 경계가 오늘 날짜에 따라 달라진다. */
    private final YearMonth lastMonth = YearMonth.now().minusMonths(1);
    private final LocalDate workDay = lastMonth.atDay(15);

    @BeforeEach
    void setUp() throws Exception {
        TestAccounts accounts = new TestAccounts(mockMvc, jdbcClient);
        TestStores stores = new TestStores(mockMvc, jdbcClient);
        owner = accounts.create("사장");
        worker = accounts.create("알바");
        colleague = accounts.create("동료");
        storeId = stores.open(owner);
        workerMemberId = stores.join(storeId, owner, worker);
        colleagueMemberId = stores.join(storeId, owner, colleague);
        jdbcClient.sql("UPDATE tw_workforce.employment_terms SET hourly_wage = ? WHERE member_id = ?")
                .params(WAGE, workerMemberId).update();
        // 10:00~14:30, 법정 휴게 30분 → 실근무 4시간, 기본급 40,000원 (주 15시간 미만이라 주휴수당 없음)
        attendanceService.createAttendance(storeId, owner.userId(), new CreateAttendanceRequest(workerMemberId,
                workDay.atTime(10, 0), workDay.atTime(14, 30), 30));
    }

    @Test
    void payrollIsCalculatedStoredOnceAndShownOnTheDashboard() throws Exception {
        perform(payroll(workerMemberId), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userName").value("알바"))
                .andExpect(jsonPath("$.data.regularPay").value(40_000))
                .andExpect(jsonPath("$.data.totalPay").value(40_000))
                .andExpect(jsonPath("$.data.taxMode").value("NONE"));
        perform(payroll(workerMemberId), worker).andExpect(status().isOk());
        assertThat(payrollRows()).isEqualTo(1);

        perform(get("/api/v1/stores/" + storeId + "/payroll/payslip").param("memberId", "" + workerMemberId)
                .param("year", "" + lastMonth.getYear()).param("month", "" + lastMonth.getMonthValue()), worker)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.storeName").value("테스트 매장"))
                .andExpect(jsonPath("$.data.totalWorkMinutes").value(240))
                .andExpect(jsonPath("$.data.dailyRecords.length()").value(1));

        perform(get("/api/v1/stores/" + storeId + "/payroll/me/estimate").param("year", "" + lastMonth.getYear())
                .param("month", "" + lastMonth.getMonthValue()), worker)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.storeMemberId").value(workerMemberId))
                .andExpect(jsonPath("$.data.netPay").value(40_000));

        perform(get("/api/v1/stores/" + storeId + "/dashboard").param("year", "" + lastMonth.getYear())
                .param("month", "" + lastMonth.getMonthValue()), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeMemberCount").value(3))
                .andExpect(jsonPath("$.data.totalLaborCost").value(40_000))
                .andExpect(jsonPath("$.data.totalWorkMinutes").value(240))
                .andExpect(jsonPath("$.data.members[0].storeMemberId").value(workerMemberId))
                .andExpect(jsonPath("$.data.members[0].userName").value("알바"));
        perform(get("/api/v1/stores/" + storeId + "/dashboard/daily").param("date", workDay.toString()), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalWorkMinutes").value(240))
                .andExpect(jsonPath("$.data.workedMemberCount").value(1));
        perform(get("/api/v1/stores/" + storeId + "/dashboard/weekly").param("date", workDay.toString()), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalActualMinutes").value(240));
    }

    @Test
    void onlyTheMemberThemselfOrAManagerSeesAPayroll() throws Exception {
        perform(payroll(workerMemberId), colleague)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("본인 또는 매장 관리자만 조회할 수 있습니다."));
        perform(get("/api/v1/stores/" + storeId + "/dashboard").param("year", "2026").param("month", "1"),
                colleague).andExpect(status().isForbidden());
        perform(get("/api/v1/stores/" + storeId + "/dashboard").param("year", "2026").param("month", "13"), owner)
                .andExpect(status().isBadRequest());
    }

    /**
     * 같은 달 급여를 처음 조회하는 요청이 겹쳐도 모두 성공하고 행은 하나만 남는다. 예전의 "찾아서 없으면 저장"은
     * 둘 다 새 행을 넣으려다 유니크 키 위반으로 500이 났다.
     */
    @Test
    void simultaneousFirstLookupsAllSucceedAndLeaveOneRow() throws Exception {
        int attempts = 8;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> results = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return payrollService.getPayroll(storeId, workerMemberId, owner.userId(), lastMonth.getYear(),
                        lastMonth.getMonthValue()).totalPay();
            }));
        }
        start.countDown();
        for (Future<Long> result : results) {
            assertThat(result.get(60, TimeUnit.SECONDS)).isEqualTo(40_000L);
        }
        pool.shutdown();
        assertThat(payrollRows()).isEqualTo(1);
    }

    @Test
    void purgingTheStoreRemovesItsPayrollsAndWorkRecords() throws Exception {
        perform(payroll(workerMemberId), owner).andExpect(status().isOk());

        jdbcClient.sql("UPDATE tw_store.stores SET deleted_at = NOW() - INTERVAL 91 DAY WHERE id = ?")
                .param(storeId).update();
        storePurgeJob.purgeExpiredStores();

        await().atMost(Duration.ofSeconds(5)).until(() -> payrollRows() == 0 && countOf("attendances") == 0);
        assertThat(colleagueMemberId).isPositive();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder payroll(long memberId) {
        return get("/api/v1/stores/" + storeId + "/payroll").param("memberId", "" + memberId)
                .param("year", "" + lastMonth.getYear()).param("month", "" + lastMonth.getMonthValue());
    }

    private ResultActions perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            Account as) throws Exception {
        return mockMvc.perform(request.header("Authorization", as.bearer()));
    }

    private int payrollRows() {
        return countOf("payrolls");
    }

    private int countOf(String table) {
        return jdbcClient.sql("SELECT COUNT(*) FROM tw_workforce." + table + " WHERE store_id = ?")
                .param(storeId).query(Integer.class).single();
    }
}
