package com.tothework.workforce.attendance;

import com.tothework.identity.api.UserSummary;
import com.tothework.shared.error.InvalidRequestException;
import com.tothework.shared.error.NotFoundException;
import com.tothework.store.api.BreakPolicy;
import com.tothework.store.api.Membership;
import com.tothework.store.api.Memberships;
import com.tothework.store.api.StoreAccess;
import com.tothework.store.api.StorePolicies;
import com.tothework.workforce.employment.Workers;
import com.tothework.workforce.labor.LaborStandards;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 출퇴근과 근태 기록 관리. 규칙과 메시지는 기존 백엔드와 같다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceService {

    /** V2가 건 유니크 제약의 이름. 다른 무결성 위반까지 "이미 출근 중"으로 바꾸지 않도록 이름으로 가린다. */
    private static final String ONE_WORKING_CONSTRAINT = "uk_attendances_one_working";

    private final AttendanceRepository attendanceRepository;
    private final StoreAccess storeAccess;
    private final Memberships memberships;
    private final StorePolicies storePolicies;
    private final Workers workers;

    /** 관리자가 누락된 근태를 직접 등록한다 (직원이 출근을 아예 찍지 못한 경우). */
    @Transactional
    public AttendanceResponse createAttendance(Long storeId, Long userId, CreateAttendanceRequest request) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        Membership target = memberships.getMemberOfStore(storeId, request.storeMemberId());
        if (!target.isActive()) {
            throw new InvalidRequestException("퇴사 처리된 멤버에게는 근태를 등록할 수 없습니다.");
        }
        Attendance attendance = Attendance.clockIn(target.memberId(), storeId, request.clockInAt());
        if (request.clockOutAt() != null) {
            attendance.correctTimes(request.clockInAt(), request.clockOutAt(),
                    resolveBreakMinutes(storeId, request.clockInAt(), request.clockOutAt(), request.breakMinutes()));
        }
        return toResponse(storeId, saveUnlessAlreadyWorking(attendance));
    }

    @Transactional
    public AttendanceResponse clockIn(Long storeId, Long userId) {
        Membership member = storeAccess.requireMember(storeId, userId);
        attendanceRepository.findFirstByMemberIdAndStatus(member.memberId(), AttendanceStatus.WORKING)
                .ifPresent(working -> {
                    throw new InvalidRequestException("이미 출근 중입니다.");
                });
        return toResponse(storeId,
                saveUnlessAlreadyWorking(Attendance.clockIn(member.memberId(), storeId, LocalDateTime.now())));
    }

    @Transactional
    public AttendanceResponse clockOut(Long storeId, Long userId) {
        Membership member = storeAccess.requireMember(storeId, userId);
        Attendance attendance = attendanceRepository.findFirstByMemberIdAndStatus(member.memberId(),
                        AttendanceStatus.WORKING)
                .orElseThrow(() -> new InvalidRequestException("출근 중인 근무가 없습니다."));
        LocalDateTime now = LocalDateTime.now();
        attendance.clockOut(now, resolveBreakMinutes(storeId, attendance.getClockInAt(), now, null));
        return toResponse(storeId, attendanceRepository.save(attendance));
    }

    public List<AttendanceResponse> getMyAttendance(Long storeId, Long userId, LocalDate from, LocalDate to) {
        Membership member = storeAccess.requireMember(storeId, userId);
        return toResponses(storeId,
                attendanceRepository.findAllByMemberIdAndWorkDateBetweenOrderByWorkDateDesc(member.memberId(), from, to));
    }

    public List<AttendanceResponse> getStoreAttendance(Long storeId, Long userId, LocalDate from, LocalDate to) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        return toResponses(storeId,
                attendanceRepository.findAllByStoreIdAndWorkDateBetweenOrderByWorkDateDesc(storeId, from, to));
    }

    @Transactional
    public AttendanceResponse correctAttendance(Long storeId, Long attendanceId, Long userId,
            CorrectAttendanceRequest request) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        Attendance attendance = getAttendanceInStore(storeId, attendanceId);
        int breakMinutes = request.clockOutAt() == null ? 0
                : resolveBreakMinutes(storeId, request.clockInAt(), request.clockOutAt(), request.breakMinutes());
        attendance.correctTimes(request.clockInAt(), request.clockOutAt(), breakMinutes);
        return toResponse(storeId, attendanceRepository.save(attendance));
    }

    @Transactional
    public void deleteAttendance(Long storeId, Long attendanceId, Long userId) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        attendanceRepository.delete(getAttendanceInStore(storeId, attendanceId));
    }

    private Attendance getAttendanceInStore(Long storeId, Long attendanceId) {
        return attendanceRepository.findByIdAndStoreId(attendanceId, storeId)
                .orElseThrow(() -> new NotFoundException("근태 기록을 찾을 수 없습니다."));
    }

    private int resolveBreakMinutes(Long storeId, LocalDateTime clockInAt, LocalDateTime clockOutAt,
            Integer requested) {
        boolean statutory = storePolicies.policyOf(storeId).breakPolicy() == BreakPolicy.STATUTORY;
        return LaborStandards.resolveBreakMinutes(statutory, Duration.between(clockInAt, clockOutAt).toMinutes(),
                requested);
    }

    /**
     * 한 멤버에게 출근 중 기록은 하나뿐이어야 한다. 앞의 조회는 흔한 경우를 친절한 문구로 막을 뿐이고, 요청 두 개가
     * 거의 동시에 오면 DB의 유니크 제약이 두 번째 INSERT를 막는다. 그 위반을 같은 문구로 바꾼다. Spring Data JDBC는
     * 저장할 때 바로 INSERT를 보내므로 예외가 이 자리에서 난다.
     */
    private Attendance saveUnlessAlreadyWorking(Attendance attendance) {
        try {
            return attendanceRepository.save(attendance);
        } catch (DataAccessException e) {
            String cause = e.getMostSpecificCause().getMessage();
            if (cause != null && cause.contains(ONE_WORKING_CONSTRAINT)) {
                throw new InvalidRequestException("이미 출근 중인 기록이 있습니다.");
            }
            throw e;
        }
    }

    private AttendanceResponse toResponse(Long storeId, Attendance attendance) {
        return toResponses(storeId, List.of(attendance)).getFirst();
    }

    private List<AttendanceResponse> toResponses(Long storeId, List<Attendance> attendances) {
        Map<Long, UserSummary> people = workers.profilesOf(storeId,
                attendances.stream().map(Attendance::getMemberId).distinct().toList());
        return attendances.stream()
                .map(attendance -> AttendanceResponse.of(attendance, people.get(attendance.getMemberId())))
                .toList();
    }
}
