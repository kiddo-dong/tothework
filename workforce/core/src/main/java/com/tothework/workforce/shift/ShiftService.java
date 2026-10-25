package com.tothework.workforce.shift;

import com.tothework.identity.api.UserSummary;
import com.tothework.shared.error.InvalidRequestException;
import com.tothework.shared.error.NotFoundException;
import com.tothework.shared.web.DateRanges;
import com.tothework.store.api.BreakPolicy;
import com.tothework.store.api.StoreAccess;
import com.tothework.store.api.StorePolicy;
import com.tothework.workforce.api.ShiftResponse;
import com.tothework.workforce.api.ShiftStatus;
import com.tothework.workforce.employment.Worker;
import com.tothework.workforce.employment.Workers;
import com.tothework.workforce.labor.LaborStandards;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스케줄 생성·조회·수정·삭제와 근로기준법 검증(근무 가능 요일·영업시간·연소자 보호·중복·주간 상한). 규칙과 메시지는
 * 기존 백엔드와 같다. 판단에 필요한 정보는 {@link Worker}로 받는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShiftService {

    private static final int MAX_RECURRING_PERIOD_DAYS = 92;

    private final ShiftRepository shiftRepository;
    private final StoreAccess storeAccess;
    private final Workers workers;

    /** 쓰기는 대상 멤버의 고용 조건 행을 잠가 한 줄로 세운다. READ COMMITTED여야 앞 요청의 커밋이 보인다 (Workers 참고). */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ShiftResponse createShift(Long storeId, Long userId, CreateShiftRequest request) {
        storeAccess.requireOwnerOrManager(storeId, userId);

        Worker target = workers.lockAssignable(storeId, request.storeMemberId());

        int breakMinutes = validateAndResolveBreak(target, request.workDate(), request.startTime(), request.endTime(),
                request.breakMinutes(), null, null);

        Shift shift = shiftRepository.save(Shift.schedule(target.memberId(), storeId, request.workDate(),
                request.startTime(), request.endTime(), breakMinutes));

        return shift.toResponse(target.user());
    }

    /**
     * AI가 제안한 초안이 유효한지 검증만 하고 저장하지 않는다. createShift와 같은 규칙을 쓰므로, 여기서 통과하면 실제
     * 생성도 통과한다.
     */
    public int validateDraftShift(Long storeId, Long userId, Long storeMemberId, LocalDate workDate,
            LocalTime startTime, LocalTime endTime) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        return validateAndResolveBreak(workers.assignable(storeId, storeMemberId), workDate, startTime, endTime, null,
                null, null);
    }

    /**
     * memberShiftsCache가 주어지면 겹침·주간 상한 검증에 DB 조회 대신 이 목록을 쓴다. 반복 생성 때 매번 DB를
     * 왕복하지 않기 위함이며, 호출한 쪽이 새로 만든 스케줄을 계속 추가해 줘야 한다.
     */
    private int validateAndResolveBreak(Worker target, LocalDate workDate, LocalTime startTime, LocalTime endTime,
            Integer requestedBreakMinutes, Long excludeShiftId, List<Shift> memberShiftsCache) {
        int breakMinutes = resolveBreakMinutes(target.policy(), startTime, endTime, requestedBreakMinutes);
        validateAvailability(target, workDate, startTime, endTime);
        validateMinorProtection(target, workDate, startTime, endTime, breakMinutes);
        validateNoOverlap(target, workDate, startTime, endTime, excludeShiftId, memberShiftsCache);
        validateWeeklyLimit(target, workDate, startTime, endTime, breakMinutes, excludeShiftId, memberShiftsCache);
        return breakMinutes;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RecurringShiftResult createRecurringShifts(Long storeId, Long userId, CreateRecurringShiftRequest request) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        Worker target = workers.lockAssignable(storeId, request.storeMemberId());
        if (request.periodEnd().isBefore(request.periodStart())) {
            throw new InvalidRequestException("종료일은 시작일 이후여야 합니다.");
        }
        long periodDays = ChronoUnit.DAYS.between(request.periodStart(), request.periodEnd()) + 1;
        if (periodDays > MAX_RECURRING_PERIOD_DAYS) {
            throw new InvalidRequestException("반복 스케줄 생성 기간은 최대 " + MAX_RECURRING_PERIOD_DAYS + "일까지 가능합니다.");
        }

        int breakMinutes = resolveBreakMinutes(target.policy(), request.startTime(), request.endTime(),
                request.breakMinutes());
        List<ShiftResponse> created = new ArrayList<>();
        List<SkippedShiftDate> skipped = new ArrayList<>();
        List<Shift> memberShiftsCache = new ArrayList<>(shiftRepository
                .findAllByMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(target.memberId(),
                        request.periodStart().minusDays(1), request.periodEnd().plusDays(1)));
        for (LocalDate date = request.periodStart(); !date.isAfter(request.periodEnd()); date = date.plusDays(1)) {
            if (!request.daysOfWeek().contains(date.getDayOfWeek())) {
                continue;
            }
            try {
                validateAndResolveBreak(target, date, request.startTime(), request.endTime(), breakMinutes, null,
                        memberShiftsCache);
                Shift shift = shiftRepository.save(Shift.schedule(target.memberId(), storeId, date,
                        request.startTime(), request.endTime(), breakMinutes));
                memberShiftsCache.add(shift);
                created.add(shift.toResponse(target.user()));
            } catch (InvalidRequestException e) {
                skipped.add(new SkippedShiftDate(date, e.getMessage()));
            }
        }
        return new RecurringShiftResult(created, skipped);
    }

    public List<ShiftResponse> getShifts(Long storeId, Long userId, Long storeMemberId, LocalDate from, LocalDate to) {
        DateRanges.requireValid(from, to);
        storeAccess.requireMember(storeId, userId);
        List<Shift> shifts;
        if (storeMemberId != null) {
            workers.assignable(storeId, storeMemberId);
            shifts = shiftRepository.findAllByMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(storeMemberId,
                    from, to);
        } else {
            shifts = shiftRepository.findAllByStoreIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(storeId, from, to);
        }
        Map<Long, UserSummary> people = workers.profilesOf(storeId,
                shifts.stream().map(Shift::getMemberId).distinct().toList());
        return shifts.stream().map(shift -> shift.toResponse(people.get(shift.getMemberId()))).toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ShiftResponse updateShift(Long storeId, Long shiftId, Long userId, UpdateShiftRequest request) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        Shift shift = getShiftInStore(storeId, shiftId);
        // 겹침과 주간 상한을 다시 검사하기 전에 이 멤버의 스케줄 쓰기를 줄 세운다. 퇴사한 멤버의 스케줄도 취소는 할 수
        // 있어야 하므로 재직 여부는 보지 않고 잠그기만 한다.
        Worker member = workers.lockAny(storeId, shift.getMemberId());
        int breakMinutes;
        if (request.status() == ShiftStatus.CANCELED) {
            // 취소된 스케줄은 근무 예정이 아니라 어떤 규칙에도 세지 않는다. 그런데 규칙을 다시 검사하면, 스케줄을 잡은
            // 뒤에 매장 휴무일·근무 가능 요일·휴게 정책을 바꾼 경우 이미 잡힌 스케줄을 취소할 수 없게 된다.
            breakMinutes = request.breakMinutes() != null ? request.breakMinutes() : shift.getBreakMinutes();
        } else {
            breakMinutes = validateAndResolveBreak(member, request.workDate(), request.startTime(), request.endTime(),
                    request.breakMinutes(), shiftId, null);
        }
        shift.update(request.workDate(), request.startTime(), request.endTime(), breakMinutes, request.status());
        return shiftRepository.save(shift).toResponse(member.user());
    }

    @Transactional
    public void deleteShift(Long storeId, Long shiftId, Long userId) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        shiftRepository.delete(getShiftInStore(storeId, shiftId));
    }

    private Shift getShiftInStore(Long storeId, Long shiftId) {
        return shiftRepository.findByIdAndStoreId(shiftId, storeId)
                .orElseThrow(() -> new NotFoundException("스케줄을 찾을 수 없습니다."));
    }

    private void validateAvailability(Worker member, LocalDate workDate, LocalTime startTime, LocalTime endTime) {
        DayOfWeek dayOfWeek = workDate.getDayOfWeek();
        Set<DayOfWeek> availableDays = member.availableDays();
        if (!availableDays.isEmpty() && !availableDays.contains(dayOfWeek)) {
            throw new InvalidRequestException(member.name() + "님은 해당 요일에 근무 가능으로 설정되어 있지 않습니다.");
        }
        validateStoreBusinessHours(member.policy(), dayOfWeek, startTime, endTime);
    }

    private int resolveBreakMinutes(StorePolicy policy, LocalTime startTime, LocalTime endTime, Integer requested) {
        return LaborStandards.resolveBreakMinutes(policy.breakPolicy() == BreakPolicy.STATUTORY,
                spanMinutes(startTime, endTime), requested);
    }

    /**
     * 해당 주(월~일)의 스케줄 합산 근무시간 상한. 연소근로자는 주 35시간, 성인은 주 52시간이며, 5인 미만 사업장의
     * 성인은 상한이 적용되지 않는다.
     */
    private void validateWeeklyLimit(Worker member, LocalDate workDate, LocalTime startTime, LocalTime endTime,
            int breakMinutes, Long excludeShiftId, List<Shift> memberShiftsCache) {
        boolean minor = LaborStandards.isMinor(member.birthDate(), workDate);
        if (!minor && member.policy().smallBusiness()) {
            return;
        }
        int capMinutes = minor ? LaborStandards.MINOR_MAX_WEEKLY_WORK_MINUTES : LaborStandards.MAX_WEEKLY_WORK_MINUTES;
        LocalDate weekStart = LaborStandards.mondayOfWeek(workDate);
        LocalDate weekEnd = LaborStandards.sundayOfWeek(workDate);
        long weeklyMinutes = spanMinutes(startTime, endTime) - breakMinutes;
        List<Shift> candidates = memberShiftsCache != null
                ? filterByDateRange(memberShiftsCache, weekStart, weekEnd)
                : shiftRepository.findAllByMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(member.memberId(),
                        weekStart, weekEnd);
        for (Shift existing : candidates) {
            if (existing.getId().equals(excludeShiftId) || existing.isCanceled()) {
                continue;
            }
            weeklyMinutes += existing.workMinutes();
        }
        if (weeklyMinutes > capMinutes) {
            throw new InvalidRequestException("해당 주의 스케줄 합계가 주 " + capMinutes / 60 + "시간 상한을 초과합니다. (현재 배정 시 "
                    + weeklyMinutes / 60 + "시간 " + weeklyMinutes % 60 + "분)");
        }
    }

    /** 연소근로자(18세 미만) 보호: 1일 7시간 초과, 야간(22:00~06:00)·주휴일 근로 스케줄을 금지한다. */
    private void validateMinorProtection(Worker member, LocalDate workDate, LocalTime startTime, LocalTime endTime,
            int breakMinutes) {
        if (!LaborStandards.isMinor(member.birthDate(), workDate)) {
            return;
        }
        long workMinutes = spanMinutes(startTime, endTime) - breakMinutes;
        if (workMinutes > LaborStandards.MINOR_MAX_DAILY_WORK_MINUTES) {
            throw new InvalidRequestException("연소근로자(18세 미만)는 1일 7시간을 초과해 근무할 수 없습니다.");
        }
        boolean touchesNight = endTime.isBefore(startTime)
                || startTime.isBefore(LocalTime.of(6, 0)) || endTime.isAfter(LocalTime.of(22, 0));
        if (touchesNight) {
            throw new InvalidRequestException("연소근로자(18세 미만)는 야간(22:00~06:00)에 근무할 수 없습니다.");
        }
        if (member.weeklyHolidayDay() != null && workDate.getDayOfWeek() == member.weeklyHolidayDay()) {
            throw new InvalidRequestException("연소근로자(18세 미만)는 주휴일에 근무할 수 없습니다.");
        }
    }

    /** 체류시간(분). 자정을 넘는 근무는 다음날 종료로 계산한다. */
    private long spanMinutes(LocalTime startTime, LocalTime endTime) {
        long minutes = Duration.between(startTime, endTime).toMinutes();
        return minutes <= 0 ? minutes + 24 * 60 : minutes;
    }

    /**
     * 같은 멤버에게 시간이 겹치는 스케줄이 있으면 거부한다. 자정을 넘는 야간 스케줄까지 비교하려고 전날~다음날을 함께
     * 보고, 날짜+시간으로 정규화해 구간 겹침을 판정한다. 취소된 스케줄과 자기 자신(수정 시)은 제외한다.
     */
    private void validateNoOverlap(Worker member, LocalDate workDate, LocalTime startTime, LocalTime endTime,
            Long excludeShiftId, List<Shift> memberShiftsCache) {
        LocalDateTime newStart = workDate.atTime(startTime);
        LocalDateTime newEnd = (endTime.isBefore(startTime) ? workDate.plusDays(1) : workDate).atTime(endTime);
        List<Shift> candidates = memberShiftsCache != null
                ? filterByDateRange(memberShiftsCache, workDate.minusDays(1), workDate.plusDays(1))
                : shiftRepository.findAllByMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(member.memberId(),
                        workDate.minusDays(1), workDate.plusDays(1));
        for (Shift existing : candidates) {
            if (existing.getId().equals(excludeShiftId) || existing.isCanceled()) {
                continue;
            }
            if (newStart.isBefore(existing.endDateTime()) && existing.startDateTime().isBefore(newEnd)) {
                throw new InvalidRequestException("겹치는 스케줄이 이미 있습니다: " + existing.getWorkDate() + " "
                        + existing.getStartTime() + "~" + existing.getEndTime());
            }
        }
    }

    private List<Shift> filterByDateRange(List<Shift> shifts, LocalDate from, LocalDate to) {
        return shifts.stream()
                .filter(shift -> !shift.getWorkDate().isBefore(from) && !shift.getWorkDate().isAfter(to))
                .toList();
    }

    private void validateStoreBusinessHours(StorePolicy policy, DayOfWeek dayOfWeek, LocalTime startTime,
            LocalTime endTime) {
        StorePolicy.Hours hours = policy.businessHours().get(dayOfWeek);
        if (hours == null) {
            return;
        }
        if (hours.closed()) {
            throw new InvalidRequestException("매장이 해당 요일은 휴무로 설정되어 있습니다.");
        }
        // 자정을 넘는 스케줄은 다음날 영업시간까지 함께 봐야 정확하므로, 여기서는 범위 검증을 생략한다.
        if (endTime.isBefore(startTime)) {
            return;
        }
        LocalTime openTime = hours.openTime();
        LocalTime closeTime = hours.closeTime();
        boolean outside = openTime != null && closeTime != null
                ? !withinBusinessHours(openTime, closeTime, startTime, endTime)
                : (openTime != null && startTime.isBefore(openTime)) || (closeTime != null && endTime.isAfter(closeTime));
        if (outside) {
            throw new InvalidRequestException("매장 영업시간(" + openTime + "~" + closeTime + ")을 벗어난 스케줄입니다.");
        }
    }

    /**
     * 자정을 넘지 않는 스케줄이 영업시간 안에 있는지. 시각을 "오픈 후 몇 분"으로 바꿔 비교한다. 그래야 18:00~02:00처럼
     * 자정을 넘겨 영업하는 매장에서도 19:00~23:00이 영업시간 안으로 판정된다(종료 시각 02:00과 그대로 비교하면 23:00이
     * 더 늦어 밖으로 나왔다). 오픈과 마감이 같으면 24시간 영업으로 본다.
     */
    private static boolean withinBusinessHours(LocalTime openTime, LocalTime closeTime, LocalTime startTime,
            LocalTime endTime) {
        long businessMinutes = minutesSinceOpen(openTime, closeTime);
        if (businessMinutes == 0) {
            return true;
        }
        long start = minutesSinceOpen(openTime, startTime);
        long end = minutesSinceOpen(openTime, endTime);
        return start < end && end <= businessMinutes;
    }

    private static long minutesSinceOpen(LocalTime openTime, LocalTime time) {
        return Math.floorMod(Duration.between(openTime, time).toMinutes(), 24 * 60);
    }
}
