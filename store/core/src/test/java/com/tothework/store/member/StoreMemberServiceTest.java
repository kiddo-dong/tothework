package com.tothework.store.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tothework.identity.api.UserDirectory;
import com.tothework.shared.error.ForbiddenException;
import com.tothework.store.api.MemberRole;
import com.tothework.store.api.MemberStatus;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreMemberServiceTest {

    private static final Long STORE_ID = 1L;

    @Mock private StoreMemberRepository memberRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private UserDirectory userDirectory;

    private StoreMemberService service;
    private StoreMember owner;
    private StoreMember staff;

    @BeforeEach
    void setUp() {
        service = new StoreMemberService(memberRepository, new MembershipService(memberRepository, storeRepository),
                userDirectory);
        owner = member(10L, 100L, MemberRole.OWNER);
        staff = member(20L, 200L, MemberRole.STAFF);
        when(memberRepository.findInActiveStore(STORE_ID, 100L)).thenReturn(Optional.of(owner));
        when(memberRepository.findInActiveStore(STORE_ID, 200L)).thenReturn(Optional.of(staff));
        when(memberRepository.findById(10L)).thenReturn(Optional.of(owner));
        when(memberRepository.findById(20L)).thenReturn(Optional.of(staff));
    }

    private StoreMember member(Long id, Long userId, MemberRole role) {
        StoreMember member = StoreMember.join(STORE_ID, userId, role);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    @Test
    void theOwnerCannotBeRemoved() {
        assertThatThrownBy(() -> service.removeMember(STORE_ID, 10L, 100L)).isInstanceOf(ForbiddenException.class);
        verify(memberRepository, never()).save(any());
    }

    /** 근무 이력이 이 행을 참조하므로 지우지 않고 비활성화한 뒤 저장한다. */
    @Test
    void removingAMemberMarksThemInactiveAndSaves() {
        service.removeMember(STORE_ID, 20L, 100L);

        assertThat(staff.getStatus()).isEqualTo(MemberStatus.INACTIVE);
        assertThat(staff.getResignedAt()).isNotNull();
        verify(memberRepository).save(staff);
    }

    @Test
    void theOwnerCannotLeave() {
        assertThatThrownBy(() -> service.leaveStore(STORE_ID, 100L)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void leavingMarksTheMemberInactiveAndSaves() {
        service.leaveStore(STORE_ID, 200L);

        assertThat(staff.getStatus()).isEqualTo(MemberStatus.INACTIVE);
        verify(memberRepository).save(staff);
    }
}
