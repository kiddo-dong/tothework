package com.tothework.workforce.attendance;

import com.tothework.identity.api.UserSummary;
import com.tothework.workforce.api.Attendances;
import com.tothework.workforce.api.RosterEntry;
import com.tothework.workforce.employment.Workers;
import com.tothework.workforce.shift.Shift;
import com.tothework.workforce.shift.ShiftRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class AttendanceLookup implements Attendances {

    private final AttendanceRepository attendanceRepository;
    private final ShiftRepository shiftRepository;
    private final AttendanceReportService attendanceReportService;
    private final Workers workers;

    @Override
    public Optional<LocalDateTime> workingSince(Long memberId) {
        return attendanceRepository.findFirstByMemberIdAndStatus(memberId, AttendanceStatus.WORKING)
                .map(Attendance::getClockInAt);
    }

    /** 스케줄 목록에 리포트의 판정을 붙인다. 시작 전인 스케줄은 리포트에 없으므로 판정이 비어 있다. */
    @Override
    public List<RosterEntry> rosterOn(Long storeId, LocalDate date) {
        List<Shift> shifts = shiftRepository
                .findAllByStoreIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(storeId, date, date).stream()
                .filter(shift -> !shift.isCanceled())
                .toList();
        if (shifts.isEmpty()) {
            return List.of();
        }
        Map<Long, AttendanceReportEntry> reportByShiftId = attendanceReportService.reportOf(storeId, null, date, date)
                .stream()
                .filter(entry -> entry.shiftId() != null)
                .collect(Collectors.toMap(AttendanceReportEntry::shiftId, Function.identity()));
        Map<Long, UserSummary> people = workers.profilesOf(storeId,
                shifts.stream().map(Shift::getMemberId).distinct().toList());
        return shifts.stream()
                .map(shift -> {
                    AttendanceReportEntry entry = reportByShiftId.get(shift.getId());
                    UserSummary user = people.get(shift.getMemberId());
                    return new RosterEntry(shift.getId(), shift.getMemberId(), user == null ? null : user.name(),
                            user == null ? null : user.profileImageUrl(), shift.getStartTime(), shift.getEndTime(),
                            entry == null ? null : entry.status(), entry == null ? null : entry.clockInAt(),
                            entry == null ? null : entry.clockOutAt());
                })
                .toList();
    }
}
