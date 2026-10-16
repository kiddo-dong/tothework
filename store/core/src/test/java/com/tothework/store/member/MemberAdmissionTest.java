package com.tothework.store.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tothework.shared.error.ConflictException;
import com.tothework.store.api.MemberJoinedEvent;
import com.tothework.store.api.MemberRole;
import com.tothework.store.api.MemberStatus;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 매장 합류 규칙. 퇴사했던 사람이 돌아오면 새 행을 만들지 않고 기존 행을 되살려야 근무 이력이 이어진다. 합류는
 * 이벤트로 알리고, workforce가 그걸 받아 고용 조건을 만든다.
 */
@ExtendWith(MockitoExtension.class)
class MemberAdmissionTest {

    @Mock private StoreMemberRepository memberRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @InjectMocks private MemberAdmission admission;

    @Test
    void aFirstTimeJoinerGetsANewMembershipAndItIsAnnounced() {
        when(memberRepository.findByStoreIdAndUserId(1L, 2L)).thenReturn(Optional.empty());
        when(memberRepository.save(any(StoreMember.class))).thenAnswer(invocation -> {
            StoreMember saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 50L);
            return saved;
        });

        StoreMember admitted = admission.admit(1L, 2L, MemberRole.STAFF);

        assertThat(admitted.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        verify(eventPublisher).publishEvent(new MemberJoinedEvent(50L, 1L, 2L, MemberRole.STAFF, false));
    }

    @Test
    void aReturningMemberIsReactivatedInsteadOfDuplicated() {
        StoreMember former = StoreMember.join(1L, 2L, MemberRole.STAFF);
        ReflectionTestUtils.setField(former, "id", 50L);
        former.resign();
        when(memberRepository.findByStoreIdAndUserId(1L, 2L)).thenReturn(Optional.of(former));
        when(memberRepository.save(former)).thenReturn(former);

        StoreMember admitted = admission.admit(1L, 2L, MemberRole.MANAGER);

        assertThat(admitted).isSameAs(former);
        assertThat(admitted.getRole()).isEqualTo(MemberRole.MANAGER);
        assertThat(admitted.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        verify(eventPublisher).publishEvent(new MemberJoinedEvent(50L, 1L, 2L, MemberRole.MANAGER, true));
    }

    @Test
    void anAlreadyActiveMemberIsRejected() {
        when(memberRepository.findByStoreIdAndUserId(1L, 2L))
                .thenReturn(Optional.of(StoreMember.join(1L, 2L, MemberRole.STAFF)));

        assertThatThrownBy(() -> admission.admit(1L, 2L, MemberRole.MANAGER))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("이미 해당 매장의 멤버");
        verify(memberRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }
}
