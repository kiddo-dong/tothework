package com.tothework.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tothework.shared.error.InvalidRequestException;
import com.tothework.store.api.MemberRole;
import com.tothework.store.invite.JoinRequest;
import com.tothework.store.invite.JoinRequestRepository;
import com.tothework.store.invite.JoinRequestService;
import com.tothework.store.invite.JoinRequestStatus;
import com.tothework.support.TestAccounts;
import com.tothework.support.TestAccounts.Account;
import com.tothework.support.TestDatabase;
import com.tothework.support.TestStores;
import com.tothework.workforce.attendance.AttendanceService;
import com.tothework.workforce.shift.ConfirmScheduleDraftRequest;
import com.tothework.workforce.shift.ConfirmScheduleDraftResult;
import com.tothework.workforce.shift.CreateShiftRequest;
import com.tothework.workforce.shift.ScheduleAiService;
import com.tothework.workforce.shift.ScheduleDraftItem;
import com.tothework.workforce.shift.ShiftService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 동시에 들어온 요청을 실제 DB 트랜잭션으로 겨루게 해서, 락과 제약이 정말 결과를 한 줄로 세우는지 확인한다.
 *
 * <p>테스트 자체에 트랜잭션을 두지 않는다. 두면 서비스 호출이 모두 그 하나에 합류해 요청마다 따로 커밋되는 실제
 * 상황을 재현하지 못한다. 기존 ConcurrencyControlTest를 옮겼고, 스케줄 락의 대상이 store의 소속 행에서 workforce의
 * 고용 조건 행으로 바뀐 뒤에도 같은 보장이 유지되는지가 핵심이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabase.class)
class WorkforceConcurrencyTest {

    private static final int ATTEMPTS = 8;

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private ShiftService shiftService;
    @Autowired private ScheduleAiService scheduleAiService;
    @Autowired private AttendanceService attendanceService;
    @Autowired private JoinRequestService joinRequestService;
    @Autowired private JoinRequestRepository joinRequestRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private JavaMailSender mailSender;

    private TestAccounts accounts;
    private Account owner;
    private Account worker;
    private long storeId;
    private long workerMemberId;

    @BeforeEach
    void setUp() throws Exception {
        accounts = new TestAccounts(mockMvc, jdbcClient);
        TestStores stores = new TestStores(mockMvc, jdbcClient);
        owner = accounts.create("사장");
        worker = accounts.create("알바");
        storeId = stores.open(owner);
        workerMemberId = stores.join(storeId, owner, worker);
    }

    /**
     * 같은 멤버의 같은 시간대에 스케줄 여러 건이 동시에 들어오면 하나만 저장돼야 한다. 락이 없으면 모든 요청이 "겹치는
     * 스케줄 없음"을 보고 전부 저장하고, 락만 있고 격리 수준이 REPEATABLE READ면 앞 요청이 넣은 스케줄이 옛 스냅샷에
     * 안 보여서 역시 여러 건이 저장된다.
     */
    @Test
    void onlyOneOfManySimultaneousRequestsForTheSameSlotIsSaved() throws Exception {
        LocalDate day = LocalDate.now().plusDays(7);

        int saved = race(() -> {
            shiftService.createShift(storeId, owner.userId(), new CreateShiftRequest(workerMemberId, day,
                    LocalTime.of(10, 0), LocalTime.of(14, 0), null));
            return true;
        });

        assertThat(saved).as("동시에 들어온 %d건 중 저장된 수", ATTEMPTS).isEqualTo(1);
        assertThat(shiftsOn(day)).isEqualTo(1);
    }

    /**
     * 초안 중 한 건이 규칙에 걸려도 나머지는 저장돼야 한다. 확정 전체가 하나의 트랜잭션이던 때는 거절된 항목의 예외가
     * 트랜잭션을 롤백 전용으로 만들어, 통과한 항목까지 사라지고 500이 났다.
     */
    @Test
    void aRejectedDraftItemDoesNotDiscardTheAcceptedOnes() {
        LocalDate day = LocalDate.now().plusDays(8);
        ScheduleDraftItem accepted = new ScheduleDraftItem(workerMemberId, "알바", day, LocalTime.of(10, 0),
                LocalTime.of(14, 0), null);
        ScheduleDraftItem overlapping = new ScheduleDraftItem(workerMemberId, "알바", day, LocalTime.of(12, 0),
                LocalTime.of(16, 0), null);

        ConfirmScheduleDraftResult result = scheduleAiService.confirmDraft(storeId, owner.userId(),
                new ConfirmScheduleDraftRequest(List.of(accepted, overlapping)));

        assertThat(result.created()).hasSize(1);
        assertThat(result.rejected()).hasSize(1);
        assertThat(shiftsOn(day)).isEqualTo(1);
    }

    /**
     * 출근 버튼을 여러 번 동시에 눌러도 근무 중 기록은 하나만 생긴다. 앞의 조회를 모두 통과한 요청은 DB의 유니크 제약이
     * 막고, 그 위반은 사용자에게 같은 문구로 바뀐다(500이 아니라).
     */
    @Test
    void simultaneousClockInsLeaveExactlyOneOpenRecord() throws Exception {
        int clockedIn = race(() -> {
            attendanceService.clockIn(storeId, worker.userId());
            return true;
        });

        assertThat(clockedIn).isEqualTo(1);
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM tw_workforce.attendances WHERE member_id = ? AND status = 'WORKING'")
                .param(workerMemberId).query(Integer.class).single()).isEqualTo(1);
    }

    /**
     * 매니저 A가 대기 중인 신청을 보고 있는 사이 매니저 B가 먼저 거절하면, A의 승인은 실패해야 한다. 버전이 없으면 A의
     * 저장이 B의 결정을 조용히 덮어써 "승인"으로 남는다. Spring Data JDBC에서도 같은 예외 타입으로 나와야 전역 핸들러가
     * 409로 바꾼다.
     */
    @Test
    void approvingARequestSomeoneElseAlreadyDecidedFailsInsteadOfOverwriting() throws Exception {
        Account applicant = accounts.create("지원자");
        String code = jdbcClient.sql("SELECT invite_code FROM tw_store.stores WHERE id = ?").param(storeId)
                .query(String.class).single();
        long requestId = joinRequestService.requestJoin(applicant.userId(), code).id();

        TransactionTemplate managerA = new TransactionTemplate(transactionManager);
        TransactionTemplate managerB = new TransactionTemplate(transactionManager);
        managerB.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> managerA.executeWithoutResult(status -> {
            JoinRequest seenByA = joinRequestRepository.findById(requestId).orElseThrow();
            managerB.executeWithoutResult(inner -> {
                JoinRequest seenByB = joinRequestRepository.findById(requestId).orElseThrow();
                seenByB.reject();
                joinRequestRepository.save(seenByB);
            });
            seenByA.approve(MemberRole.STAFF);
            joinRequestRepository.save(seenByA);
        })).isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(joinRequestRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(JoinRequestStatus.REJECTED);
    }

    /** 같은 작업을 ATTEMPTS개 스레드에서 동시에 시작하고, 거절(InvalidRequestException)되지 않은 수를 센다. */
    private int race(Callable<Boolean> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(ATTEMPTS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < ATTEMPTS; i++) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    return task.call();
                } catch (InvalidRequestException rejected) {
                    return false;
                }
            }));
        }
        start.countDown();
        int succeeded = 0;
        for (Future<Boolean> result : results) {
            if (result.get(60, TimeUnit.SECONDS)) {
                succeeded++;
            }
        }
        pool.shutdown();
        return succeeded;
    }

    private int shiftsOn(LocalDate day) {
        return jdbcClient.sql("SELECT COUNT(*) FROM tw_workforce.shifts WHERE member_id = ? AND work_date = ?")
                .params(workerMemberId, day).query(Integer.class).single();
    }
}
