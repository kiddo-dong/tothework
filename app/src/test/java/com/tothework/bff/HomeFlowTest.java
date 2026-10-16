package com.tothework.bff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.tothework.shared.storage.S3Uploader;
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
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 홈 화면은 store·workforce·operations 세 서비스의 계약을 조합한다. 각 칸이 제 서비스의 값으로 채워지는지, 금액은 원래
 * API(예상 급여, 월간 대시보드)와 같은 숫자인지, 관리자 섹션은 관리자에게만 나가는지를 본다. 근태 판정(지각 등)은
 * 실행 시각에 따라 달라지므로 여기서 단정하지 않는다(리포트 단위 테스트가 본다).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabase.class)
class HomeFlowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private ShiftService shiftService;
    @Autowired private AttendanceService attendanceService;
    @MockitoBean private JavaMailSender mailSender;
    @MockitoBean private S3Uploader s3Uploader;

    private Account owner;
    private Account staff;
    private long storeId;
    private long staffMemberId;
    private String base;
    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() throws Exception {
        TestAccounts accounts = new TestAccounts(mockMvc, jdbcClient);
        TestStores stores = new TestStores(mockMvc, jdbcClient);
        owner = accounts.create("사장");
        staff = accounts.create("알바");
        storeId = stores.open(owner);
        staffMemberId = stores.join(storeId, owner, staff);
        base = "/api/v1/stores/" + storeId;
        jdbcClient.sql("UPDATE tw_workforce.employment_terms SET hourly_wage = 10320 WHERE member_id = ?")
                .param(staffMemberId).update();

        shiftService.createShift(storeId, owner.userId(), new CreateShiftRequest(staffMemberId, today,
                LocalTime.of(8, 0), LocalTime.of(12, 30), null));
        attendanceService.createAttendance(storeId, owner.userId(), new CreateAttendanceRequest(staffMemberId,
                today.atTime(8, 0), today.atTime(12, 30), 30));
        send(owner, base + "/notices", "{\"title\":\"공지\",\"content\":\"내용\"}");
        send(owner, base + "/checklist-items", "{\"type\":\"OPEN\",\"content\":\"청소\"}");
        send(staff, base + "/handover-notes", "{\"content\":\"우유 떨어짐\"}");
        long supplierId = idOf(send(owner, base + "/suppliers", "{\"name\":\"우유상사\",\"category\":\"유제품\"}"));
        send(owner, base + "/suppliers/" + supplierId + "/items",
                "{\"name\":\"우유\",\"spec\":\"1L\",\"weeklyQuantities\":{\"" + today.getDayOfWeek() + "\":\"10개\"}}");
        Account applicant = accounts.create("지원자");
        String code = jdbcClient.sql("SELECT invite_code FROM tw_store.stores WHERE id = ?").param(storeId)
                .query(String.class).single();
        send(applicant, "/api/v1/join-requests", "{\"code\":\"" + code + "\"}");
    }

    @Test
    void staffSeesTheirDayButNotTheManagerSection() throws Exception {
        String home = body(perform(get(base + "/home"), staff)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myRole").value("STAFF"))
                .andExpect(jsonPath("$.data.myStoreMemberId").value(staffMemberId))
                .andExpect(jsonPath("$.data.myDay.todayShifts.length()").value(1))
                .andExpect(jsonPath("$.data.myDay.todayShifts[0].userName").value("알바"))
                .andExpect(jsonPath("$.data.myDay.todayShifts[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.myDay.working").value(false))
                .andExpect(jsonPath("$.data.myDay.unreadNoticeCount").value(1))
                .andExpect(jsonPath("$.data.myDay.openChecklist.total").value(1))
                .andExpect(jsonPath("$.data.myDay.openChecklist.done").value(0))
                .andExpect(jsonPath("$.data.myDay.recentHandoverNotes[0].authorName").value("알바"))
                .andExpect(jsonPath("$.data.managerSection").doesNotExist()));

        String estimate = body(perform(get(base + "/payroll/me/estimate").param("year", "" + today.getYear())
                .param("month", "" + today.getMonthValue()), staff));
        assertThat(((Number) JsonPath.read(home, "$.data.myDay.estimatedMonthNetPay")).longValue())
                .isEqualTo(((Number) JsonPath.read(estimate, "$.data.netPay")).longValue())
                .isPositive();

        long noticeId = idOf(body(perform(get(base + "/notices"), staff)), "$.data[0].id");
        send(staff, base + "/notices/" + noticeId + "/read", null);
        perform(get(base + "/home"), staff).andExpect(jsonPath("$.data.myDay.unreadNoticeCount").value(0));
    }

    @Test
    void managerSectionMatchesTheDashboardAndListsTodaysWork() throws Exception {
        String home = body(perform(get(base + "/home"), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myRole").value("OWNER"))
                .andExpect(jsonPath("$.data.managerSection.todayRoster.length()").value(1))
                .andExpect(jsonPath("$.data.managerSection.todayRoster[0].storeMemberId").value(staffMemberId))
                .andExpect(jsonPath("$.data.managerSection.todayRoster[0].userName").value("알바"))
                .andExpect(jsonPath("$.data.managerSection.todayOrderItems[0].itemName").value("우유"))
                .andExpect(jsonPath("$.data.managerSection.todayOrderItems[0].quantity").value("10개"))
                .andExpect(jsonPath("$.data.managerSection.pendingJoinRequestCount").value(1)));

        YearMonth month = YearMonth.from(today);
        String dashboard = body(perform(get(base + "/dashboard").param("year", "" + month.getYear())
                .param("month", "" + month.getMonthValue()), owner));
        assertThat(((Number) JsonPath.read(home, "$.data.managerSection.monthLaborCost")).longValue())
                .isEqualTo(((Number) JsonPath.read(dashboard, "$.data.totalLaborCost")).longValue())
                .isPositive();
        assertThat(((Number) JsonPath.read(home, "$.data.managerSection.monthNetPay")).longValue())
                .isEqualTo(((Number) JsonPath.read(dashboard, "$.data.totalNetPay")).longValue());
    }

    @Test
    void clockingInShowsUpAsWorking() throws Exception {
        send(staff, base + "/attendance/clock-in", null);

        perform(get(base + "/home"), staff)
                .andExpect(jsonPath("$.data.myDay.working").value(true))
                .andExpect(jsonPath("$.data.myDay.clockInAt").isNotEmpty());
    }

    @Test
    void outsidersCannotSeeTheHome() throws Exception {
        Account outsider = new TestAccounts(mockMvc, jdbcClient).create("외부인");
        perform(get(base + "/home"), outsider).andExpect(status().isForbidden());
    }

    private String send(Account as, String url, String json) throws Exception {
        var request = post(url).header("Authorization", as.bearer());
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request).andExpect(status().is2xxSuccessful()).andReturn().getResponse()
                .getContentAsString();
    }

    private ResultActions perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            Account as) throws Exception {
        return mockMvc.perform(request.header("Authorization", as.bearer()));
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private static long idOf(String json) {
        return idOf(json, "$.data.id");
    }

    private static long idOf(String json, String path) {
        return ((Number) JsonPath.read(json, path)).longValue();
    }
}
