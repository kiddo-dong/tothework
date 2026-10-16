package com.tothework.workforce.attendance;

import com.tothework.identity.api.UserSummary;
import com.tothework.shared.error.ForbiddenException;
import com.tothework.store.api.Membership;
import com.tothework.store.api.StoreAccess;
import com.tothework.workforce.api.WorkComplianceStatus;
import com.tothework.workforce.employment.Workers;
import com.tothework.workforce.leave.LeaveUsage;
import com.tothework.workforce.leave.LeaveUsageRepository;
import com.tothework.workforce.shift.Shift;
import com.tothework.workforce.shift.ShiftRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스케줄(Shift)과 실제 근태(Attendance)를 날짜별로 대조해 지각·조퇴·무단결근·스케줄 외 근무를 판정한다.
 *
 * <p>매칭 규칙: 같은 멤버·같은 날짜에서 스케줄 시간대와 겹치는 출근 기록 중 겹침이 가장 큰 것을 매칭한다.
 * 아직 시작 전이거나 진행 중인 스케줄(종료 시각이 지나지 않음)은 출근 기록이 없어도 결근으로 판정하지 않는다.
 * 해당 날짜에 연차 사용 기록이 있으면 결근 대신 연차로 표시한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceReportService {

    private final ShiftRepository shiftRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveUsageRepository leaveUsageRepository;
    private final StoreAccess storeAccess;
    private final Workers workers;

    public List<AttendanceReportEntry> getReport(Long storeId, Long userId, Long storeMemberId,
            LocalDate from, LocalDate to) {
        Membership requester = storeAccess.requireMember(storeId, userId);
        boolean selfOnly = storeMemberId != null && requester.memberId().equals(storeMemberId);
        if (!selfOnly && !requester.isOwnerOrManager()) {
            throw new ForbiddenException("매장 관리자만 다른 멤버의 근태 리포트를 조회할 수 있습니다.");
        }
        return reportOf(storeId, storeMemberId, from, to);
    }

    /** 권한 확인 없는 리포트. 호출한 쪽이 이미 확인했을 때(홈 화면 등) 쓴다. storeMemberId가 null이면 매장 전체. */
    public List<AttendanceReportEntry> reportOf(Long storeId, Long storeMemberId, LocalDate from, LocalDate to) {
        // 같은 workforce 안의 데이터라 저장소를 직접 읽는다. 취소된 스케줄은 근무 예정이 아니므로 뺀다.
        List<Shift> shifts;
        List<Attendance> attendances;
        List<LeaveUsage> leaves;
        if (storeMemberId != null) {
            shifts = shiftRepository.findAllByMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(storeMemberId,
                    from, to);
            attendances = attendanceRepository.findAllByMemberIdAndWorkDateBetweenOrderByWorkDateDesc(storeMemberId,
                    from, to);
            leaves = leaveUsageRepository.findAllByMemberIdAndLeaveDateBetween(storeMemberId, from, to);
        } else {
            shifts = shiftRepository.findAllByStoreIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(storeId, from, to);
            attendances = attendanceRepository.findAllByStoreIdAndWorkDateBetweenOrderByWorkDateDesc(storeId, from, to);
            leaves = leaveUsageRepository.findAllByStoreIdAndLeaveDateBetween(storeId, from, to);
        }
        shifts = shifts.stream().filter(shift -> !shift.isCanceled()).toList();
        Map<Long, UserSummary> people = workers.profilesOf(storeId, Stream.concat(
                shifts.stream().map(Shift::getMemberId), attendances.stream().map(Attendance::getMemberId))
                .distinct().toList());
        Set<String> leaveKeys = leaves.stream()
                .map(leave -> dayKey(leave.getMemberId(), leave.getLeaveDate()))
                .collect(Collectors.toSet());

        LocalDateTime now = LocalDateTime.now();
        Set<Long> matchedAttendanceIds = new HashSet<>();
        List<AttendanceReportEntry> entries = new ArrayList<>();
        Map<String, List<Attendance>> attendancesByMemberAndDate = attendances.stream()
                .collect(Collectors.groupingBy(
                        attendance -> dayKey(attendance.getMemberId(), attendance.getWorkDate())));

        for (Shift shift : shifts) {
            if (shift.startDateTime().isAfter(now)) {
                continue;
            }
            List<Attendance> sameDayAttendances = attendancesByMemberAndDate.getOrDefault(
                    dayKey(shift.getMemberId(), shift.getWorkDate()), List.of());
            Attendance matched = findBestMatch(shift, sameDayAttendances, matchedAttendanceIds, now);
            if (matched == null) {
                boolean onLeave = leaveKeys.contains(
                        dayKey(shift.getMemberId(), shift.getWorkDate()));
                if (onLeave) {
                    entries.add(entryOf(people, shift, null, WorkComplianceStatus.ON_LEAVE, 0, 0));
                } else if (shift.endDateTime().isBefore(now)) {
                    entries.add(entryOf(people, shift, null, WorkComplianceStatus.ABSENT, 0, 0));
                }
                // 아직 진행 중인 스케줄은 판단 유보
                continue;
            }
            matchedAttendanceIds.add(matched.getId());
            long lateMinutes = Math.max(0,
                    Duration.between(shift.startDateTime(), matched.getClockInAt()).toMinutes());
            if (matched.getClockOutAt() == null) {
                entries.add(entryOf(people, shift, matched, WorkComplianceStatus.WORKING, lateMinutes, 0));
                continue;
            }
            long earlyLeaveMinutes = Math.max(0,
                    Duration.between(matched.getClockOutAt(), shift.endDateTime()).toMinutes());
            entries.add(entryOf(people, shift, matched, judge(lateMinutes, earlyLeaveMinutes), lateMinutes,
                    earlyLeaveMinutes));
        }

        for (Attendance attendance : attendances) {
            if (!matchedAttendanceIds.contains(attendance.getId())) {
                entries.add(new AttendanceReportEntry(null, attendance.getId(),
                        attendance.getMemberId(), nameOf(people, attendance.getMemberId()),
                        photoOf(people, attendance.getMemberId()),
                        attendance.getWorkDate(), null, null, attendance.getClockInAt(),
                        attendance.getClockOutAt(), WorkComplianceStatus.EXTRA, 0, 0));
            }
        }

        entries.sort(Comparator.comparing(AttendanceReportEntry::workDate)
                .thenComparing(AttendanceReportEntry::storeMemberId));
        return entries;
    }

    private Attendance findBestMatch(Shift shift, List<Attendance> attendances,
            Set<Long> matchedAttendanceIds, LocalDateTime now) {
        Attendance best = null;
        long bestOverlap = 0;
        for (Attendance attendance : attendances) {
            if (matchedAttendanceIds.contains(attendance.getId())
                    || !attendance.getMemberId().equals(shift.getMemberId())
                    || !attendance.getWorkDate().equals(shift.getWorkDate())) {
                continue;
            }
            LocalDateTime attendanceEnd = attendance.getClockOutAt() == null ? now : attendance.getClockOutAt();
            LocalDateTime overlapStart = max(shift.startDateTime(), attendance.getClockInAt());
            LocalDateTime overlapEnd = min(shift.endDateTime(), attendanceEnd);
            if (overlapStart.isBefore(overlapEnd)) {
                long overlap = Duration.between(overlapStart, overlapEnd).toMinutes();
                if (overlap > bestOverlap) {
                    bestOverlap = overlap;
                    best = attendance;
                }
            }
        }
        return best;
    }

    private WorkComplianceStatus judge(long lateMinutes, long earlyLeaveMinutes) {
        if (lateMinutes > 0 && earlyLeaveMinutes > 0) {
            return WorkComplianceStatus.LATE_AND_EARLY_LEAVE;
        }
        if (lateMinutes > 0) {
            return WorkComplianceStatus.LATE;
        }
        if (earlyLeaveMinutes > 0) {
            return WorkComplianceStatus.EARLY_LEAVE;
        }
        return WorkComplianceStatus.NORMAL;
    }

    private AttendanceReportEntry entryOf(Map<Long, UserSummary> people, Shift shift, Attendance attendance, WorkComplianceStatus status,
            long lateMinutes, long earlyLeaveMinutes) {
        return new AttendanceReportEntry(
                shift.getId(),
                attendance == null ? null : attendance.getId(),
                shift.getMemberId(),
                nameOf(people, shift.getMemberId()),
                photoOf(people, shift.getMemberId()),
                shift.getWorkDate(),
                shift.getStartTime(),
                shift.getEndTime(),
                attendance == null ? null : attendance.getClockInAt(),
                attendance == null ? null : attendance.getClockOutAt(),
                status,
                lateMinutes,
                earlyLeaveMinutes);
    }

    private String dayKey(Long storeMemberId, LocalDate date) {
        return storeMemberId + ":" + date;
    }

    private LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    private LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private static String nameOf(Map<Long, UserSummary> people, Long memberId) {
        UserSummary user = people.get(memberId);
        return user == null ? null : user.name();
    }

    private static String photoOf(Map<Long, UserSummary> people, Long memberId) {
        UserSummary user = people.get(memberId);
        return user == null ? null : user.profileImageUrl();
    }
}
