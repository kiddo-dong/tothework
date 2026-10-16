package com.tothework.workforce.shift;

import com.tothework.identity.api.UserSummary;
import com.tothework.workforce.api.Schedules;
import com.tothework.workforce.api.ShiftResponse;
import com.tothework.workforce.employment.Workers;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class ScheduleLookup implements Schedules {

    private final ShiftRepository shiftRepository;
    private final Workers workers;

    @Override
    public List<ShiftResponse> activeShiftsOf(Long storeId, Long memberId, LocalDate from, LocalDate to) {
        List<Shift> shifts = shiftRepository
                .findAllByMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(memberId, from, to).stream()
                .filter(shift -> !shift.isCanceled())
                .toList();
        if (shifts.isEmpty()) {
            return List.of();
        }
        UserSummary user = workers.profilesOf(storeId, List.of(memberId)).get(memberId);
        return shifts.stream().map(shift -> shift.toResponse(user)).toList();
    }
}
