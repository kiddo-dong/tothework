package com.example.albam.domain.storemember.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.example.albam.domain.storemember.dto.StoreMemberResponse;
import com.example.albam.domain.storemember.dto.UpdateStoreMemberRequest;
import com.example.albam.domain.storemember.entity.MemberRole;
import com.example.albam.domain.storemember.entity.MemberStatus;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.entity.TaxMode;
import com.example.albam.domain.storemember.repository.StoreMemberRepository;
import com.example.albam.domain.store.entity.BreakPolicy;
import com.example.albam.domain.store.entity.Store;
import com.example.albam.domain.user.entity.User;
import com.example.albam.global.exception.ForbiddenException;
import com.example.albam.global.exception.InvalidRequestException;
import com.example.albam.global.file.ProfileImageUrls;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StoreMemberServiceTest {

    @Mock
    private StoreMemberRepository storeMemberRepository;
    @Mock
    private StoreAuthorizationService storeAuthorizationService;
    @Mock
    private ProfileImageUrls profileImageUrls;

    @InjectMocks
    private StoreMemberService storeMemberService;

    private static final Long STORE_ID = 1L;
    private static final Long USER_ID = 1L;
    private static final Long TARGET_MEMBER_ID = 20L;
    /** 요청자(매니저) 자신의 멤버 id. setUp에서 manager에 넣는 값과 같아야 한다. */
    private static final Long SELF_MEMBER_ID = 1L;

    private StoreMember manager;
    private StoreMember staffTarget;

    @BeforeEach
    void setUp() {
        Store store = new Store("테스트 매장", "서울", null, null, new HashMap<>(), "ABC123",
                BreakPolicy.STATUTORY, false, null);
        ReflectionTestUtils.setField(store, "id", STORE_ID);

        User managerUser = new User("manager@albam.dev", "pw", "매니저", "010-0000-0000",
                LocalDate.of(1990, 1, 1), null);
        manager = new StoreMember(store, managerUser, MemberRole.MANAGER, 12_000);
        ReflectionTestUtils.setField(manager, "id", 1L);

        User staffUser = new User("staff@albam.dev", "pw", "직원", "010-1111-1111",
                LocalDate.of(1995, 1, 1), null);
        staffTarget = new StoreMember(store, staffUser, MemberRole.STAFF, 11_000);
        ReflectionTestUtils.setField(staffTarget, "id", TARGET_MEMBER_ID);

        lenient().when(storeAuthorizationService.requireOwnerOrManager(anyLong(), anyLong())).thenReturn(manager);
        lenient().when(storeMemberRepository.findById(TARGET_MEMBER_ID)).thenReturn(Optional.of(staffTarget));
    }

    @Test
    void updateMember_managerCannotPromoteTargetToOwner() {
        UpdateStoreMemberRequest request = new UpdateStoreMemberRequest(
                MemberRole.OWNER, null, null, null, null, null, null);

        assertThatThrownBy(() -> storeMemberService.updateMember(STORE_ID, TARGET_MEMBER_ID, USER_ID, request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("소유권 이전");

        assertThat(staffTarget.getRole()).isEqualTo(MemberRole.STAFF); // 역할이 실제로 바뀌지 않았어야 한다
    }

    @Test
    void updateMember_cannotModifyExistingOwnerThroughThisApi() {
        StoreMember ownerTarget = new StoreMember(staffTarget.getStore(), staffTarget.getUser(), MemberRole.OWNER,
                15_000);
        ReflectionTestUtils.setField(ownerTarget, "id", TARGET_MEMBER_ID);
        when(storeMemberRepository.findById(TARGET_MEMBER_ID)).thenReturn(Optional.of(ownerTarget));

        UpdateStoreMemberRequest request = new UpdateStoreMemberRequest(
                null, null, 12_000, null, null, null, null);

        assertThatThrownBy(() -> storeMemberService.updateMember(STORE_ID, TARGET_MEMBER_ID, USER_ID, request))
                .isInstanceOf(ForbiddenException.class);
    }

    /** 직함은 권한과 무관한 표시용 값이다. 붙였다고 할 수 있는 일이 달라지면 안 된다. */
    @Test
    void updateMember_setsATitleWithoutTouchingTheRole() {
        UpdateStoreMemberRequest request = new UpdateStoreMemberRequest(null, "주방장", null, null,
                null, null, null);

        StoreMemberResponse response = storeMemberService.updateMember(STORE_ID, TARGET_MEMBER_ID,
                USER_ID, request);

        assertThat(response.title()).isEqualTo("주방장");
        assertThat(response.role()).isEqualTo(MemberRole.STAFF);
    }

    /** 화면에서 직함을 지웠을 때 공백만 남지 않도록 한다. */
    @Test
    void updateMember_clearsTheTitleWhenGivenBlank() {
        storeMemberService.updateMember(STORE_ID, TARGET_MEMBER_ID, USER_ID,
                new UpdateStoreMemberRequest(null, "홀팀장", null, null, null, null, null));

        StoreMemberResponse response = storeMemberService.updateMember(STORE_ID, TARGET_MEMBER_ID,
                USER_ID, new UpdateStoreMemberRequest(null, "  ", null, null, null, null, null));

        assertThat(response.title()).isNull();
    }

    /** 직함을 안 보냈다고 지워지면, 시급만 고치려던 사람이 직함을 잃는다. */
    @Test
    void updateMember_keepsTheTitleWhenTheFieldIsAbsent() {
        storeMemberService.updateMember(STORE_ID, TARGET_MEMBER_ID, USER_ID,
                new UpdateStoreMemberRequest(null, "주방장", null, null, null, null, null));

        StoreMemberResponse response = storeMemberService.updateMember(STORE_ID, TARGET_MEMBER_ID,
                USER_ID, new UpdateStoreMemberRequest(null, null, 12000, null, null, null, null));

        assertThat(response.title()).isEqualTo("주방장");
    }

    @Test
    void updateMember_allowsPromotionToManager() {
        UpdateStoreMemberRequest request = new UpdateStoreMemberRequest(
                MemberRole.MANAGER, null, null, null, null, null, null);

        storeMemberService.updateMember(STORE_ID, TARGET_MEMBER_ID, USER_ID, request);

        assertThat(staffTarget.getRole()).isEqualTo(MemberRole.MANAGER);
    }

    @Test
    void updateMember_rejectsHourlyWageBelowMinimumWage() {
        UpdateStoreMemberRequest request = new UpdateStoreMemberRequest(
                null, null, 5_000, null, null, null, null);

        assertThatThrownBy(() -> storeMemberService.updateMember(STORE_ID, TARGET_MEMBER_ID, USER_ID, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("최저임금");
    }

    @Test
    void updateMember_appliesMultipleFieldChangesTogether() {
        UpdateStoreMemberRequest request = new UpdateStoreMemberRequest(
                null, null, 12_000, MemberStatus.INACTIVE, null, null, TaxMode.WITHHOLDING_3_3);

        storeMemberService.updateMember(STORE_ID, TARGET_MEMBER_ID, USER_ID, request);

        assertThat(staffTarget.getHourlyWage()).isEqualTo(12_000);
        assertThat(staffTarget.getStatus()).isEqualTo(MemberStatus.INACTIVE);
        assertThat(staffTarget.getTaxMode()).isEqualTo(TaxMode.WITHHOLDING_3_3);
    }

    /**
     * 관리자 권한은 다른 사람을 관리하라고 준 것이지 자기 대우를 정하라고 준 것이 아니다. 매니저 자신의
     * 행은 role이 MANAGER라 OWNER 검사를 통과하므로, 이 검사가 없으면 요청 한 번으로 자기 시급을 올릴 수 있다.
     */
    @Test
    void updateMember_managerCannotRaiseTheirOwnWage() {
        when(storeMemberRepository.findById(SELF_MEMBER_ID)).thenReturn(Optional.of(manager));
        UpdateStoreMemberRequest request = new UpdateStoreMemberRequest(
                null, null, 500_000, null, null, null, null);

        assertThatThrownBy(() -> storeMemberService.updateMember(STORE_ID, SELF_MEMBER_ID, USER_ID, request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("본인의 시급");

        assertThat(manager.getHourlyWage()).isEqualTo(12_000); // 실제로 오르지 않았어야 한다
    }

    /** 시급을 못 올리더라도 공제를 없애면 실수령액이 오른다. 같은 구멍이라 함께 막는다. */
    @Test
    void updateMember_managerCannotRemoveTheirOwnDeductions() {
        when(storeMemberRepository.findById(SELF_MEMBER_ID)).thenReturn(Optional.of(manager));
        UpdateStoreMemberRequest request = new UpdateStoreMemberRequest(
                null, null, null, null, null, null, TaxMode.NONE);

        assertThatThrownBy(() -> storeMemberService.updateMember(STORE_ID, SELF_MEMBER_ID, USER_ID, request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("공제");
    }

    @Test
    void updateMember_managerCannotChangeTheirOwnRole() {
        when(storeMemberRepository.findById(SELF_MEMBER_ID)).thenReturn(Optional.of(manager));
        UpdateStoreMemberRequest request = new UpdateStoreMemberRequest(
                MemberRole.STAFF, null, null, null, null, null, null);

        assertThatThrownBy(() -> storeMemberService.updateMember(STORE_ID, SELF_MEMBER_ID, USER_ID, request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("본인의 역할");

        assertThat(manager.getRole()).isEqualTo(MemberRole.MANAGER);
    }

    /** 직함은 금전·권한에 영향이 없다. 본인 것을 고치는 길을 함께 막아버리면 과하다. */
    @Test
    void updateMember_managerCanStillRenameThemselves() {
        when(storeMemberRepository.findById(SELF_MEMBER_ID)).thenReturn(Optional.of(manager));
        UpdateStoreMemberRequest request = new UpdateStoreMemberRequest(
                null, "홀팀장", null, null, null, null, null);

        StoreMemberResponse response = storeMemberService.updateMember(STORE_ID, SELF_MEMBER_ID, USER_ID, request);

        assertThat(response.title()).isEqualTo("홀팀장");
    }

    @Test
    void removeMember_ownerCannotBeRemoved() {
        StoreMember ownerTarget = new StoreMember(staffTarget.getStore(), staffTarget.getUser(), MemberRole.OWNER,
                15_000);
        ReflectionTestUtils.setField(ownerTarget, "id", TARGET_MEMBER_ID);
        when(storeMemberRepository.findById(TARGET_MEMBER_ID)).thenReturn(Optional.of(ownerTarget));

        assertThatThrownBy(() -> storeMemberService.removeMember(STORE_ID, TARGET_MEMBER_ID, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void removeMember_marksTargetInactive() {
        storeMemberService.removeMember(STORE_ID, TARGET_MEMBER_ID, USER_ID);

        assertThat(staffTarget.getStatus()).isEqualTo(MemberStatus.INACTIVE);
    }

    @Test
    void leaveStore_ownerCannotLeave() {
        StoreMember ownerSelf = new StoreMember(staffTarget.getStore(), staffTarget.getUser(), MemberRole.OWNER,
                15_000);
        when(storeAuthorizationService.requireMember(STORE_ID, USER_ID)).thenReturn(ownerSelf);

        assertThatThrownBy(() -> storeMemberService.leaveStore(STORE_ID, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getStoreMember_throwsWhenMemberBelongsToDifferentStore() {
        Store otherStore = new Store("다른 매장", "부산", null, null, new HashMap<>(), "XYZ999",
                BreakPolicy.STATUTORY, false, null);
        ReflectionTestUtils.setField(otherStore, "id", 999L);
        StoreMember memberOfOtherStore = new StoreMember(otherStore, staffTarget.getUser(), MemberRole.STAFF, 10_000);
        ReflectionTestUtils.setField(memberOfOtherStore, "id", TARGET_MEMBER_ID);
        when(storeMemberRepository.findById(TARGET_MEMBER_ID)).thenReturn(Optional.of(memberOfOtherStore));

        UpdateStoreMemberRequest request = new UpdateStoreMemberRequest(
                null, null, 12_000, null, null, null, null);

        assertThatThrownBy(() -> storeMemberService.updateMember(STORE_ID, TARGET_MEMBER_ID, USER_ID, request))
                .isInstanceOf(com.example.albam.global.exception.NotFoundException.class);
    }
}
