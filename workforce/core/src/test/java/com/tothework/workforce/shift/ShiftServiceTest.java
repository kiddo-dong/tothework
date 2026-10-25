package com.tothework.workforce.shift;

import static com.tothework.workforce.support.TestWorkers.MEMBER_ID;
import static com.tothework.workforce.support.TestWorkers.STORE_ID;
import static com.tothework.workforce.support.TestWorkers.aWorker;
import static com.tothework.workforce.support.TestWorkers.closed;
import static com.tothework.workforce.support.TestWorkers.open;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tothework.shared.error.InvalidRequestException;
import com.tothework.store.api.StoreAccess;
import com.tothework.workforce.api.ShiftResponse;
import com.tothework.workforce.api.ShiftStatus;
import com.tothework.workforce.employment.Worker;
import com.tothework.workforce.employment.Workers;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** 스케줄 생성 시 근로기준법 검증. 기존 ShiftServiceTest의 시나리오를 그대로 옮겼다. */
@ExtendWith(MockitoExtension.class)
class ShiftServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate MONDAY = LocalDate.of(2026, 1, 5);

    @Mock private ShiftRepository shiftRepository;
    @Mock private StoreAccess storeAccess;
    @Mock private Workers workers;
    @InjectMocks private ShiftService shiftService;

    /** 쓰는 경로는 잠그며 읽고(lockAssignable), 검증만 하는 경로는 그냥 읽는다(assignable). 둘 다 같은 사람을 준다. */
    private void given(Worker worker) {
        lenient().when(workers.assignable(STORE_ID, MEMBER_ID)).thenReturn(worker);
        lenient().when(workers.lockAssignable(STORE_ID, MEMBER_ID)).thenReturn(worker);
    }

    @BeforeEach
    void setUp() {
        given(aWorker().build());
        lenient().when(shiftRepository.save(any(Shift.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(shiftRepository.findAllByMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(anyLong(),
                any(), any())).thenReturn(List.of());
    }

    private CreateShiftRequest request(LocalDate date, int startHour, int endHour, Integer breakMinutes) {
        return new CreateShiftRequest(MEMBER_ID, date, LocalTime.of(startHour, 0), LocalTime.of(endHour, 0),
                breakMinutes);
    }

    private Shift existing(long id, LocalDate date, LocalTime start, LocalTime end, int breakMinutes) {
        Shift shift = Shift.schedule(MEMBER_ID, STORE_ID, date, start, end, breakMinutes);
        ReflectionTestUtils.setField(shift, "id", id);
        return shift;
    }

    @Test
    void savesWhenThereIsNoConflict() {
        ShiftResponse response = shiftService.createShift(STORE_ID, USER_ID, request(MONDAY, 9, 18, null));

        assertThat(response.startTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(response.breakMinutes()).isEqualTo(60); // 9시간 체류, 법정 강제 정책 -> 자동 1시간
        assertThat(response.userName()).isEqualTo("직원10");
    }

    @Test
    void rejectsAnOverlappingSchedule() {
        when(shiftRepository.findAllByMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(MEMBER_ID,
                MONDAY.minusDays(1), MONDAY.plusDays(1)))
                .thenReturn(List.of(existing(99L, MONDAY, LocalTime.of(8, 0), LocalTime.of(15, 0), 60)));

        assertThatThrownBy(() -> shiftService.createShift(STORE_ID, USER_ID, request(MONDAY, 14, 20, 60)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("겹치는 스케줄");
    }

    /** 이번 주에 이미 50시간이 잡혀 있는 상태에서 6시간을 더 배정하면 주 52시간을 넘긴다. */
    @Test
    void rejectsWhenTheWeeklyLimitWouldBeExceeded() {
        List<Shift> weekShifts = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            weekShifts.add(existing(100L + i, MONDAY.plusDays(i), LocalTime.of(0, 0), LocalTime.of(8, 20), 0));
        }
        when(shiftRepository.findAllByMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(MEMBER_ID, MONDAY,
                MONDAY.plusDays(6))).thenReturn(weekShifts);

        assertThatThrownBy(() -> shiftService.createShift(STORE_ID, USER_ID, request(MONDAY.plusDays(6), 9, 15, 30)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("주");
    }

    @Test
    void rejectsAMinorWorkingLateAtNight() {
        given(aWorker().minor().build());

        assertThatThrownBy(() -> shiftService.createShift(STORE_ID, USER_ID, request(MONDAY, 18, 23, 30)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("연소근로자");
    }

    @Test
    void rejectsAMinorWorkingOverSevenHoursADay() {
        given(aWorker().minor().build());

        assertThatThrownBy(() -> shiftService.createShift(STORE_ID, USER_ID, request(MONDAY, 8, 17, 60)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("7시간");
    }

    @Test
    void rejectsAMinorWorkingOnTheirWeeklyHoliday() {
        given(aWorker().minor().weeklyHolidayOn(DayOfWeek.MONDAY).build());

        assertThatThrownBy(() -> shiftService.createShift(STORE_ID, USER_ID, request(MONDAY, 10, 14, 30)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("주휴일");
    }

    @Test
    void rejectsADayTheMemberIsNotAvailable() {
        given(aWorker().availableOn(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY).build());

        assertThatThrownBy(() -> shiftService.createShift(STORE_ID, USER_ID, request(MONDAY, 9, 18, 60)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("근무 가능");
    }

    @Test
    void rejectsADayTheStoreIsClosed() {
        given(aWorker().storeHours(Map.of(DayOfWeek.MONDAY, closed())).build());

        assertThatThrownBy(() -> shiftService.createShift(STORE_ID, USER_ID, request(MONDAY, 9, 18, 60)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("휴무");
    }

    @Test
    void rejectsHoursOutsideTheBusinessHours() {
        given(aWorker().storeHours(Map.of(DayOfWeek.MONDAY, open(9, 18))).build());

        assertThatThrownBy(() -> shiftService.createShift(STORE_ID, USER_ID, request(MONDAY, 7, 12, 30)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("영업시간");
    }

    /** 자정을 넘겨 영업하는 매장(18:00~02:00)에서도 저녁 근무는 영업시간 안이다. 마감 02:00과 그대로 비교하면 거부됐다. */
    @Test
    void acceptsAnEveningShiftInAStoreOpenPastMidnight() {
        given(aWorker().storeHours(Map.of(DayOfWeek.MONDAY, open(18, 2))).build());

        assertThat(shiftService.createShift(STORE_ID, USER_ID, request(MONDAY, 19, 23, 30))).isNotNull();
        assertThat(shiftService.createShift(STORE_ID, USER_ID, request(MONDAY, 0, 2, 0))).isNotNull();
    }

    @Test
    void stillRejectsHoursOutsideAStoreOpenPastMidnight() {
        given(aWorker().storeHours(Map.of(DayOfWeek.MONDAY, open(18, 2))).build());

        assertThatThrownBy(() -> shiftService.createShift(STORE_ID, USER_ID, request(MONDAY, 15, 20, 30)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("영업시간");
        assertThatThrownBy(() -> shiftService.createShift(STORE_ID, USER_ID, request(MONDAY, 1, 4, 0)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("영업시간");
    }

    /**
     * 스케줄을 잡은 뒤 그 요일을 휴무로 바꾸거나 근무 가능 요일에서 빼도, 이미 잡힌 스케줄은 취소할 수 있어야 한다.
     * 취소할 때 규칙을 다시 검사해 막혔었다.
     */
    @Test
    void aShiftCanBeCanceledAfterTheRulesChanged() {
        Shift shift = existing(55L, MONDAY, LocalTime.of(9, 0), LocalTime.of(13, 0), 0);
        when(shiftRepository.findByIdAndStoreId(55L, STORE_ID)).thenReturn(Optional.of(shift));
        when(workers.lockAny(STORE_ID, MEMBER_ID)).thenReturn(aWorker()
                .storeHours(Map.of(DayOfWeek.MONDAY, closed()))
                .availableOn(DayOfWeek.TUESDAY)
                .build());

        ShiftResponse canceled = shiftService.updateShift(STORE_ID, 55L, USER_ID, new UpdateShiftRequest(MONDAY,
                LocalTime.of(9, 0), LocalTime.of(13, 0), null, ShiftStatus.CANCELED));

        assertThat(canceled.status()).isEqualTo(ShiftStatus.CANCELED);
        assertThat(canceled.breakMinutes()).isZero(); // 안 보낸 휴게는 그대로 둔다
    }

    /** 취소가 아닌 수정은 지금 규칙을 그대로 검사한다. */
    @Test
    void updatingAShiftOnAClosedDayIsStillRejected() {
        Shift shift = existing(55L, MONDAY, LocalTime.of(9, 0), LocalTime.of(13, 0), 0);
        when(shiftRepository.findByIdAndStoreId(55L, STORE_ID)).thenReturn(Optional.of(shift));
        when(workers.lockAny(STORE_ID, MEMBER_ID)).thenReturn(aWorker()
                .storeHours(Map.of(DayOfWeek.MONDAY, closed())).build());

        assertThatThrownBy(() -> shiftService.updateShift(STORE_ID, 55L, USER_ID, new UpdateShiftRequest(MONDAY,
                LocalTime.of(10, 0), LocalTime.of(13, 0), null, ShiftStatus.SCHEDULED)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("휴무");
    }

    /** 몇 년치 조회는 DB를 읽기 전에 거절한다. */
    @Test
    void anOverlyLongQueryRangeIsRejectedBeforeReading() {
        assertThatThrownBy(() -> shiftService.getShifts(STORE_ID, USER_ID, null, MONDAY, MONDAY.plusYears(3)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("최대");
        verify(shiftRepository, never()).findAllByStoreIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(any(),
                any(), any());
    }

    /** 5인 미만 사업장의 성인은 주간 상한 검사 자체를 건너뛴다(그 주의 스케줄을 조회하지도 않는다). */
    @Test
    void aSmallBusinessSkipsTheWeeklyLimitForAdults() {
        given(aWorker().smallBusiness().build());

        assertThat(shiftService.createShift(STORE_ID, USER_ID, request(MONDAY.plusDays(6), 9, 15, 30))).isNotNull();
        verify(shiftRepository, never()).findAllByMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(MEMBER_ID,
                MONDAY, MONDAY.plusDays(6));
    }

    /** 쓰기는 반드시 잠그며 읽어야 한다. 잠그지 않으면 동시 요청이 둘 다 검사를 통과한다 (ConcurrencyControlTest). */
    @Test
    void creatingAShiftLocksTheMember() {
        shiftService.createShift(STORE_ID, USER_ID, request(MONDAY, 9, 18, null));

        verify(workers).lockAssignable(STORE_ID, MEMBER_ID);
        verify(workers, never()).assignable(STORE_ID, MEMBER_ID);
    }
}
