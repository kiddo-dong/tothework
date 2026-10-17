package com.tothework.store.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.tothework.store.api.BreakPolicy;
import com.tothework.store.api.StoreAccess;
import com.tothework.shared.error.InvalidRequestException;
import java.util.HashMap;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 온보딩 진행 상태. 프리셋이 미리 채워 넣은 값 때문에 "데이터가 있다"만으로는 진행도를 알 수 없어,
 * 사장님이 확인했다는 사실을 따로 기록한다.
 */
@ExtendWith(MockitoExtension.class)
class StoreOnboardingServiceTest {

    @Mock
    private StoreRepository storeRepository;
    @Mock
    private StoreAccess storeAccess;

    @InjectMocks
    private StoreOnboardingService storeOnboardingService;

    private static final Long STORE_ID = 1L;
    private static final Long USER_ID = 1L;

    private Store store;

    @BeforeEach
    void setUp() {
        store = Store.open("가게", "주소", null, null, new HashMap<>(), "ABC123", BreakPolicy.STATUTORY, false, null);
        when(storeRepository.findActive(STORE_ID)).thenReturn(Optional.of(store));
        lenient().when(storeRepository.save(any(Store.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void startsWithNothingConfirmedAndNotComplete() {
        OnboardingResponse response = storeOnboardingService.getOnboarding(STORE_ID, USER_ID);

        assertThat(response.confirmedSteps()).isEmpty();
        assertThat(response.completed()).isFalse();
        assertThat(response.completedAt()).isNull();
    }

    @Test
    void recordsAStepTheOwnerConfirmed() {
        OnboardingResponse response = storeOnboardingService.confirmStep(STORE_ID, USER_ID, "checklist");

        assertThat(response.confirmedSteps()).containsExactly("checklist");
    }

    /** 화면을 다시 열어 같은 버튼을 눌러도 상태가 달라지면 안 된다. */
    @Test
    void confirmingTheSameStepTwiceChangesNothing() {
        storeOnboardingService.confirmStep(STORE_ID, USER_ID, "checklist");
        OnboardingResponse response = storeOnboardingService.confirmStep(STORE_ID, USER_ID, "checklist");

        assertThat(response.confirmedSteps()).containsExactly("checklist");
    }

    @Test
    void keepsEveryConfirmedStep() {
        storeOnboardingService.confirmStep(STORE_ID, USER_ID, "info");
        OnboardingResponse response = storeOnboardingService.confirmStep(STORE_ID, USER_ID, "invite");

        assertThat(response.confirmedSteps()).containsExactlyInAnyOrder("info", "invite");
    }

    @Test
    void marksOnboardingComplete() {
        OnboardingResponse response = storeOnboardingService.complete(STORE_ID, USER_ID);

        assertThat(response.completed()).isTrue();
        assertThat(response.completedAt()).isNotNull();
    }

    /** 온보딩 화면은 나중에도 열 수 있다. 다시 눌렀다고 끝낸 시각이 밀리면 기록의 뜻이 달라진다. */
    @Test
    void keepsTheFirstCompletionTimeWhenCompletedAgain() {
        var first = storeOnboardingService.complete(STORE_ID, USER_ID).completedAt();
        var second = storeOnboardingService.complete(STORE_ID, USER_ID).completedAt();

        assertThat(second).isEqualTo(first);
    }

    /** 단계 이름은 경로로 들어오므로 컬럼에 들어갈 수 있는지 확인한다. */
    @Test
    void rejectsAStepNameLongerThanTheColumn() {
        String tooLong = "a".repeat(Store.ONBOARDING_STEP_KEY_MAX_LENGTH + 1);

        assertThatThrownBy(() -> storeOnboardingService.confirmStep(STORE_ID, USER_ID, tooLong))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void rejectsABlankStepName() {
        assertThatThrownBy(() -> storeOnboardingService.confirmStep(STORE_ID, USER_ID, "  "))
                .isInstanceOf(InvalidRequestException.class);
    }

    /** 온보딩은 매장을 세우는 일이라 사장만 다룬다. */
    @Test
    void requiresOwnerRights() {
        storeOnboardingService.confirmStep(STORE_ID, USER_ID, "info");

        verify(storeAccess).requireOwner(STORE_ID, USER_ID);
    }
}
