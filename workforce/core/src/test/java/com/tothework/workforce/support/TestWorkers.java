package com.tothework.workforce.support;

import com.tothework.identity.api.UserSummary;
import com.tothework.store.api.BreakPolicy;
import com.tothework.store.api.MemberRole;
import com.tothework.store.api.MemberStatus;
import com.tothework.store.api.Membership;
import com.tothework.store.api.StorePolicy;
import com.tothework.workforce.api.EmploymentTerms;
import com.tothework.workforce.api.TaxMode;
import com.tothework.workforce.employment.Worker;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;

/**
 * 단위 테스트용 {@link Worker}. 예전에는 Store·User·StoreMember 엔티티를 조립했지만, 이제 규칙이 보는 값은 모두 레코드라
 * 필요한 것만 바꿔 가며 만든다.
 */
public final class TestWorkers {

    public static final Long STORE_ID = 1L;
    public static final Long MEMBER_ID = 10L;
    public static final LocalDate ADULT_BIRTH = LocalDate.of(1990, 1, 1);
    /** 2026년 기준 16세. */
    public static final LocalDate MINOR_BIRTH = LocalDate.of(2010, 1, 1);

    private LocalDate birthDate = ADULT_BIRTH;
    private Set<DayOfWeek> availableDays = Set.of();
    private DayOfWeek weeklyHolidayDay;
    private Map<DayOfWeek, StorePolicy.Hours> businessHours = Map.of();
    private boolean smallBusiness;
    private BreakPolicy breakPolicy = BreakPolicy.STATUTORY;
    private MemberStatus status = MemberStatus.ACTIVE;
    private Long memberId = MEMBER_ID;

    public static TestWorkers aWorker() {
        return new TestWorkers();
    }

    public static StorePolicy.Hours open(int from, int to) {
        return new StorePolicy.Hours(LocalTime.of(from, 0), LocalTime.of(to, 0), false);
    }

    public static StorePolicy.Hours closed() {
        return new StorePolicy.Hours(null, null, true);
    }

    public TestWorkers minor() {
        this.birthDate = MINOR_BIRTH;
        return this;
    }

    public TestWorkers availableOn(DayOfWeek... days) {
        this.availableDays = Set.of(days);
        return this;
    }

    public TestWorkers weeklyHolidayOn(DayOfWeek day) {
        this.weeklyHolidayDay = day;
        return this;
    }

    public TestWorkers storeHours(Map<DayOfWeek, StorePolicy.Hours> hours) {
        this.businessHours = hours;
        return this;
    }

    public TestWorkers smallBusiness() {
        this.smallBusiness = true;
        return this;
    }

    public TestWorkers flexibleBreaks() {
        this.breakPolicy = BreakPolicy.FLEXIBLE;
        return this;
    }

    public TestWorkers id(Long memberId) {
        this.memberId = memberId;
        return this;
    }

    public Worker build() {
        Membership membership = new Membership(memberId, STORE_ID, 100L + memberId, MemberRole.STAFF, status, null,
                LocalDateTime.of(2025, 1, 1, 9, 0), null);
        EmploymentTerms terms = new EmploymentTerms(memberId, STORE_ID, 10_030, TaxMode.NONE, availableDays,
                weeklyHolidayDay);
        UserSummary user = new UserSummary(100L + memberId, "직원" + memberId, "m" + memberId + "@albam.dev", null,
                birthDate);
        StorePolicy policy = new StorePolicy(STORE_ID, smallBusiness, breakPolicy, businessHours, null);
        return new Worker(membership, terms, user, policy);
    }
}
