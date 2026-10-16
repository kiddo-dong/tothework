package com.tothework.workforce.employment;

import com.tothework.workforce.api.EmploymentTerms;
import com.tothework.workforce.api.TaxMode;
import com.tothework.workforce.config.WorkforceSchema;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 멤버 한 명의 고용 조건 (애그리거트 루트). id는 store가 정한 memberId를 그대로 쓴다.
 *
 * <p>id를 직접 정하므로 Spring Data JDBC가 새 행인지 판단할 수 없다. 버전이 비어 있으면 새 행으로 보게 하려고
 * {@code @Version}을 둔다. 버전은 동시에 두 관리자가 시급을 고칠 때 늦은 쪽을 실패시키는 역할도 한다.
 */
@Getter
@Table(schema = WorkforceSchema.NAME, name = "employment_terms")
class MemberEmployment {

    @Id
    private Long memberId;
    private Long storeId;
    private int hourlyWage;
    private TaxMode taxMode;
    @MappedCollection(idColumn = "member_id")
    private Set<AvailableDay> availableDays = new HashSet<>();
    private DayOfWeek weeklyHolidayDay;
    @Version
    private Long version;
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;

    MemberEmployment() {
    }

    static MemberEmployment startFor(Long memberId, Long storeId) {
        MemberEmployment employment = new MemberEmployment();
        EmploymentTerms defaults = EmploymentTerms.defaultsFor(memberId, storeId);
        employment.memberId = memberId;
        employment.storeId = storeId;
        employment.hourlyWage = defaults.hourlyWage();
        employment.taxMode = defaults.taxMode();
        return employment;
    }

    EmploymentTerms toTerms() {
        return new EmploymentTerms(memberId, storeId, hourlyWage, taxMode,
                availableDays.stream().map(AvailableDay::dayOfWeek).collect(Collectors.toUnmodifiableSet()),
                weeklyHolidayDay);
    }

    void changeHourlyWage(int hourlyWage) {
        this.hourlyWage = hourlyWage;
    }

    void changeTaxMode(TaxMode taxMode) {
        this.taxMode = taxMode;
    }

    void changeAvailableDays(Set<DayOfWeek> days) {
        this.availableDays = days.stream().map(AvailableDay::new).collect(Collectors.toCollection(HashSet::new));
    }

    void changeWeeklyHolidayDay(DayOfWeek weeklyHolidayDay) {
        this.weeklyHolidayDay = weeklyHolidayDay;
    }
}
