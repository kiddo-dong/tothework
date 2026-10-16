package com.tothework.workforce.employment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tothework.shared.error.ForbiddenException;
import com.tothework.shared.error.InvalidRequestException;
import com.tothework.store.api.MemberRole;
import com.tothework.store.api.MemberStatus;
import com.tothework.store.api.Membership;
import com.tothework.store.api.Memberships;
import com.tothework.store.api.StoreAccess;
import com.tothework.workforce.api.EmploymentTerms;
import com.tothework.workforce.api.TaxMode;
import com.tothework.workforce.api.TermsChanges;
import com.tothework.workforce.labor.LaborStandards;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 고용 조건 변경 규칙. 기존 StoreMemberServiceTest의 시급·공제 시나리오를 옮겼다. 누가 바꿀 수 있는지는 store에
 * 묻고(권한·소속), 무엇으로 바꿀 수 있는지는 workforce가 정한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmploymentServiceTest {

    private static final Long STORE_ID = 1L;
    private static final Long MANAGER_USER_ID = 100L;
    private static final Membership MANAGER = membership(10L, MANAGER_USER_ID, MemberRole.MANAGER);
    private static final Membership STAFF = membership(20L, 200L, MemberRole.STAFF);
    private static final Membership OWNER = membership(30L, 300L, MemberRole.OWNER);

    @Mock private MemberEmploymentRepository repository;
    @Mock private StoreAccess storeAccess;
    @Mock private Memberships memberships;

    private EmploymentService service;
    private MemberEmployment staffEmployment;

    private static Membership membership(Long memberId, Long userId, MemberRole role) {
        return new Membership(memberId, STORE_ID, userId, role, MemberStatus.ACTIVE, null, LocalDateTime.now(), null);
    }

    @BeforeEach
    void setUp() {
        service = new EmploymentService(repository, storeAccess, memberships);
        staffEmployment = MemberEmployment.startFor(20L, STORE_ID);
        when(storeAccess.requireOwnerOrManager(STORE_ID, MANAGER_USER_ID)).thenReturn(MANAGER);
        when(memberships.getMemberOfStore(STORE_ID, 10L)).thenReturn(MANAGER);
        when(memberships.getMemberOfStore(STORE_ID, 20L)).thenReturn(STAFF);
        when(memberships.getMemberOfStore(STORE_ID, 30L)).thenReturn(OWNER);
        when(repository.findById(20L)).thenReturn(Optional.of(staffEmployment));
        when(repository.save(any(MemberEmployment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private TermsChanges wage(int hourlyWage) {
        return new TermsChanges(hourlyWage, null, null, null);
    }

    @Test
    void rejectsAnHourlyWageBelowTheMinimumWage() {
        assertThatThrownBy(() -> service.applyChange(STORE_ID, 20L, MANAGER_USER_ID,
                wage(LaborStandards.MINIMUM_HOURLY_WAGE - 1)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("최저임금");
        verify(repository, never()).save(any());
    }

    @Test
    void appliesSeveralFieldsTogetherAndSaves() {
        EmploymentTerms terms = service.applyChange(STORE_ID, 20L, MANAGER_USER_ID, new TermsChanges(
                12_000, TaxMode.WITHHOLDING_3_3, Set.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), DayOfWeek.SUNDAY));

        assertThat(terms.hourlyWage()).isEqualTo(12_000);
        assertThat(terms.taxMode()).isEqualTo(TaxMode.WITHHOLDING_3_3);
        assertThat(terms.availableDays()).containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
        assertThat(terms.weeklyHolidayDay()).isEqualTo(DayOfWeek.SUNDAY);
        verify(repository).save(staffEmployment);
    }

    /** 매니저 자신의 행은 role이 MANAGER라 소유자 검사를 통과한다. 막지 않으면 요청 한 번으로 자기 시급을 올릴 수 있다. */
    @Test
    void aManagerCannotRaiseTheirOwnWage() {
        assertThatThrownBy(() -> service.applyChange(STORE_ID, 10L, MANAGER_USER_ID, wage(50_000)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("본인의 시급");
    }

    /** 시급을 못 올려도 공제를 없애면 실수령액이 오른다. 같은 구멍이라 함께 막는다. */
    @Test
    void aManagerCannotRemoveTheirOwnDeductions() {
        assertThatThrownBy(() -> service.applyChange(STORE_ID, 10L, MANAGER_USER_ID,
                new TermsChanges(null, TaxMode.NONE, null, null)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("공제");
    }

    /** 근무 가능 요일은 금전·권한과 무관해 본인도 바꿀 수 있다. */
    @Test
    void aManagerCanStillChangeTheirOwnAvailableDays() {
        when(repository.findById(10L)).thenReturn(Optional.empty());

        EmploymentTerms terms = service.applyChange(STORE_ID, 10L, MANAGER_USER_ID,
                new TermsChanges(null, null, Set.of(DayOfWeek.TUESDAY), null));

        assertThat(terms.availableDays()).containsExactly(DayOfWeek.TUESDAY);
    }

    @Test
    void theOwnersTermsCannotBeChangedThroughThisApi() {
        assertThatThrownBy(() -> service.applyChange(STORE_ID, 30L, MANAGER_USER_ID, wage(12_000)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("매장 소유자");
    }

    @Test
    void validationAloneChangesNothing() {
        service.validateChange(STORE_ID, 20L, MANAGER_USER_ID, wage(12_000));

        assertThat(staffEmployment.getHourlyWage()).isZero();
        verify(repository, never()).save(any());
    }

    /** 합류 이벤트가 처리되기 전에 조회가 와도 기본값으로 답한다. */
    @Test
    void termsNotYetCreatedReadAsDefaults() {
        when(repository.findById(40L)).thenReturn(Optional.empty());

        EmploymentTerms terms = service.termsOf(STORE_ID, 40L);

        assertThat(terms.hourlyWage()).isZero();
        assertThat(terms.taxMode()).isEqualTo(TaxMode.NONE);
        assertThat(terms.availableDays()).isEmpty();
    }
}
