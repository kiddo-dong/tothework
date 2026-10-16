package com.tothework.store.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tothework.shared.error.ForbiddenException;
import com.tothework.shared.error.NotFoundException;
import com.tothework.store.api.MemberRole;
import com.tothework.store.api.MemberStatus;
import com.tothework.store.api.Membership;
import com.tothework.store.api.MembershipChanges;
import com.tothework.store.store.StoreRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 소속(역할·직함·재직 상태) 변경 규칙. 시급·공제 같은 고용 조건 규칙은 workforce의 EmploymentServiceTest가 본다.
 * 기존 StoreMemberServiceTest의 시나리오를 소속 쪽만 옮겼다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MembershipServiceTest {

    private static final Long STORE_ID = 1L;
    private static final Long MANAGER_USER_ID = 100L;

    @Mock private StoreMemberRepository memberRepository;
    @Mock private StoreRepository storeRepository;

    private MembershipService service;
    private StoreMember manager;
    private StoreMember staff;

    @BeforeEach
    void setUp() {
        service = new MembershipService(memberRepository, storeRepository);
        manager = member(10L, MANAGER_USER_ID, MemberRole.MANAGER);
        staff = member(20L, 200L, MemberRole.STAFF);
        when(memberRepository.findInActiveStore(STORE_ID, MANAGER_USER_ID)).thenReturn(Optional.of(manager));
        when(memberRepository.findById(10L)).thenReturn(Optional.of(manager));
        when(memberRepository.findById(20L)).thenReturn(Optional.of(staff));
        when(memberRepository.save(any(StoreMember.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private StoreMember member(Long id, Long userId, MemberRole role) {
        StoreMember member = StoreMember.join(STORE_ID, userId, role);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private Membership apply(Long memberId, MembershipChanges changes) {
        return service.applyChange(STORE_ID, memberId, MANAGER_USER_ID, changes);
    }

    @Test
    void managerCannotPromoteSomeoneToOwner() {
        assertThatThrownBy(() -> apply(20L, new MembershipChanges(MemberRole.OWNER, null, null)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("소유권 이전");
        assertThat(staff.getRole()).isEqualTo(MemberRole.STAFF);
    }

    @Test
    void theOwnerCannotBeModifiedThroughThisApi() {
        StoreMember owner = member(30L, 300L, MemberRole.OWNER);
        when(memberRepository.findById(30L)).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> apply(30L, new MembershipChanges(null, "사장님", null)))
                .isInstanceOf(ForbiddenException.class);
        verify(memberRepository, never()).save(any());
    }

    @Test
    void setsATitleWithoutTouchingTheRole() {
        Membership result = apply(20L, new MembershipChanges(null, " 홀팀장 ", null));

        assertThat(result.title()).isEqualTo("홀팀장");
        assertThat(result.role()).isEqualTo(MemberRole.STAFF);
        verify(memberRepository).save(staff);
    }

    @Test
    void clearsTheTitleWhenGivenBlank() {
        staff.changeTitle("홀팀장");

        assertThat(apply(20L, new MembershipChanges(null, "", null)).title()).isNull();
    }

    @Test
    void keepsTheTitleWhenTheFieldIsAbsent() {
        staff.changeTitle("홀팀장");

        assertThat(apply(20L, new MembershipChanges(MemberRole.MANAGER, null, null)).title()).isEqualTo("홀팀장");
    }

    @Test
    void allowsPromotionToManager() {
        assertThat(apply(20L, new MembershipChanges(MemberRole.MANAGER, null, null)).role()).isEqualTo(MemberRole.MANAGER);
    }

    /** 관리자 권한은 다른 사람을 관리하라고 준 것이지 자기 대우를 정하라고 준 것이 아니다. */
    @Test
    void managerCannotChangeTheirOwnRole() {
        assertThatThrownBy(() -> apply(10L, new MembershipChanges(MemberRole.STAFF, null, null)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("본인의 역할");
        assertThat(manager.getRole()).isEqualTo(MemberRole.MANAGER);
    }

    @Test
    void managerCannotChangeTheirOwnStatus() {
        assertThatThrownBy(() -> apply(10L, new MembershipChanges(null, null, MemberStatus.INACTIVE)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("본인의 재직 상태");
    }

    /** 직함은 금전·권한에 영향이 없다. 본인 것을 고치는 길까지 막으면 과하다. */
    @Test
    void managerCanStillRenameThemselves() {
        assertThat(apply(10L, new MembershipChanges(null, "홀팀장", null)).title()).isEqualTo("홀팀장");
    }

    /** 검증만 하는 단계에서는 아무것도 바뀌거나 저장되면 안 된다. 다른 서비스의 검증이 이어서 실패할 수 있다. */
    @Test
    void validationAloneChangesNothing() {
        service.validateChange(STORE_ID, 20L, MANAGER_USER_ID, new MembershipChanges(MemberRole.MANAGER, "팀장", null));

        assertThat(staff.getRole()).isEqualTo(MemberRole.STAFF);
        assertThat(staff.getTitle()).isNull();
        verify(memberRepository, never()).save(any());
    }

    @Test
    void aMemberOfAnotherStoreLooksLikeItDoesNotExist() {
        StoreMember elsewhere = StoreMember.join(99L, 400L, MemberRole.STAFF);
        ReflectionTestUtils.setField(elsewhere, "id", 40L);
        when(memberRepository.findById(40L)).thenReturn(Optional.of(elsewhere));

        assertThatThrownBy(() -> service.getMemberOfStore(STORE_ID, 40L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void aStaffMemberCannotEditAnyone() {
        when(memberRepository.findInActiveStore(STORE_ID, 200L)).thenReturn(Optional.of(staff));

        assertThatThrownBy(() -> service.applyChange(STORE_ID, 10L, 200L, new MembershipChanges(null, "x", null)))
                .isInstanceOf(ForbiddenException.class);
    }

    /** 지운 매장을 열어 둔 탭에서 다시 눌렀을 때 "멤버가 아니다" 대신 매장이 없다고 알려 준다. */
    @Test
    void aDeletedStoreIsReportedAsMissingRatherThanForbidden() {
        when(memberRepository.findInActiveStore(STORE_ID, 500L)).thenReturn(Optional.empty());
        when(storeRepository.existsActive(STORE_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.requireMember(STORE_ID, 500L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void anOutsiderOfALiveStoreIsForbidden() {
        when(memberRepository.findInActiveStore(STORE_ID, 500L)).thenReturn(Optional.empty());
        when(storeRepository.existsActive(STORE_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.requireMember(STORE_ID, 500L)).isInstanceOf(ForbiddenException.class);
    }
}
