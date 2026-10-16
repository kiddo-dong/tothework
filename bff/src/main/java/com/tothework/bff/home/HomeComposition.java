package com.tothework.bff.home;

import com.tothework.bff.home.HomeResponse.ChecklistProgress;
import com.tothework.bff.home.HomeResponse.ManagerSection;
import com.tothework.bff.home.HomeResponse.MyDaySection;
import com.tothework.bff.home.HomeResponse.TodayOrderItem;
import com.tothework.bff.home.HomeResponse.TodayRosterEntry;
import com.tothework.operations.api.Checklists;
import com.tothework.operations.api.DailyChecklistProgress;
import com.tothework.operations.api.HandoverNotes;
import com.tothework.operations.api.Notices;
import com.tothework.operations.api.Suppliers;
import com.tothework.store.api.JoinRequests;
import com.tothework.store.api.Membership;
import com.tothework.store.api.StoreAccess;
import com.tothework.workforce.api.Attendances;
import com.tothework.workforce.api.Payrolls;
import com.tothework.workforce.api.Payrolls.LaborCost;
import com.tothework.workforce.api.Schedules;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 매장 홈 화면 조합. 역할에 따라 공통(myDay) / 관리자 섹션을 채운다.
 *
 * <p>store·workforce·operations의 공개 계약만 부른다. 권한은 여기서 한 번 확인하고, 각 서비스의 조회 계약은 확인 없이
 * 값만 돌려준다. 서비스마다 다시 확인하면 홈 한 번에 같은 권한 조회가 여러 번 나간다. 트랜잭션은 없다 — 서비스가
 * 나뉘면 어차피 한 트랜잭션으로 묶을 수 없고, 화면용 조회라 서비스마다 조금 다른 시점의 값이어도 괜찮다.
 */
@Component
@RequiredArgsConstructor
class HomeComposition {

    private static final int RECENT_HANDOVER_LIMIT = 5;

    private final StoreAccess storeAccess;
    private final JoinRequests joinRequests;
    private final Schedules schedules;
    private final Attendances attendances;
    private final Payrolls payrolls;
    private final Notices notices;
    private final Checklists checklists;
    private final HandoverNotes handoverNotes;
    private final Suppliers suppliers;

    HomeResponse getHome(Long storeId, Long userId) {
        Membership me = storeAccess.requireMember(storeId, userId);
        LocalDate today = LocalDate.now();
        ManagerSection managerSection = me.isOwnerOrManager() ? managerSection(storeId, today) : null;
        return new HomeResponse(me.role(), me.memberId(), myDay(storeId, me, today), managerSection);
    }

    private MyDaySection myDay(Long storeId, Membership me, LocalDate today) {
        LocalDateTime clockInAt = attendances.workingSince(me.memberId()).orElse(null);
        DailyChecklistProgress checklist = checklists.progressOn(storeId, today);
        return new MyDaySection(
                schedules.activeShiftsOf(storeId, me.memberId(), today, today),
                clockInAt != null,
                clockInAt,
                payrolls.estimateNetPay(storeId, me.memberId(), YearMonth.from(today)),
                notices.countUnread(storeId, me.memberId()),
                new ChecklistProgress(checklist.openDone(), checklist.openTotal()),
                new ChecklistProgress(checklist.closeDone(), checklist.closeTotal()),
                handoverNotes.findRecent(storeId, today.minusDays(1), today, RECENT_HANDOVER_LIMIT));
    }

    private ManagerSection managerSection(Long storeId, LocalDate today) {
        List<TodayRosterEntry> roster = attendances.rosterOn(storeId, today).stream()
                .map(entry -> new TodayRosterEntry(entry.shiftId(), entry.storeMemberId(), entry.userName(),
                        entry.userProfileImageUrl(), entry.startTime(), entry.endTime(), entry.compliance(),
                        entry.clockInAt(), entry.clockOutAt()))
                .toList();
        List<TodayOrderItem> orderItems = suppliers.findOrderLinesFor(storeId, today.getDayOfWeek()).stream()
                .map(line -> new TodayOrderItem(line.supplierName(), line.itemName(), line.spec(), line.quantity()))
                .toList();
        LaborCost laborCost = payrolls.laborCostOf(storeId, YearMonth.from(today));
        return new ManagerSection(roster, orderItems, laborCost.totalLaborCost(), laborCost.totalNetPay(),
                joinRequests.countPending(storeId));
    }
}
