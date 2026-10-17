package com.tothework.store.store;

import com.tothework.store.api.BreakPolicy;
import com.tothework.store.config.StoreSchema;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 매장 (애그리거트 루트). 영업시간과 온보딩 단계는 매장과 함께 저장되는 자식이다.
 *
 * <p>소프트 삭제: {@code deletedAt}이 채워진 매장은 없는 매장으로 다룬다. JPA의 {@code @SQLRestriction}이 하던 일을
 * 이제 {@link StoreRepository#findActive}가 한 곳에서 한다. 상태를 바꾸는 메서드는 값만 바꾸므로 저장은 호출한
 * 쪽이 한다.
 */
@Getter
@Table(schema = StoreSchema.NAME, name = "stores")
public class Store {

    /** 단계 이름은 화면이 정하므로 값 자체는 검사하지 않고 길이만 제한한다. */
    public static final int ONBOARDING_STEP_KEY_MAX_LENGTH = 40;

    @Id
    private Long id;
    private String name;
    private String address;
    private String businessRegistrationNumber;
    private StoreCategory category;
    @MappedCollection(idColumn = "store_id", keyColumn = "day_of_week")
    private Map<DayOfWeek, BusinessHour> businessHours = new HashMap<>();
    private String inviteCode;
    private BreakPolicy breakPolicy;
    /** 상시 근로자 5인 미만 사업장 여부. 5인 미만은 연장·야간·휴일 가산수당과 주 52시간 상한이 적용되지 않는다. */
    private boolean smallBusiness;
    /** 급여 지급일(매월 며칠). 알바생에게 보여 주기 위한 값일 뿐 앱이 지급을 처리하지는 않는다. */
    private Integer payday;
    /** 매장 대표 사진의 S3 key. */
    private String profileImageKey;
    private StorePlan plan = StorePlan.BASIC;
    @MappedCollection(idColumn = "store_id")
    private Set<OnboardingStep> onboardingSteps = new HashSet<>();
    private LocalDateTime onboardingCompletedAt;
    private LocalDateTime deletedAt;
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;

    Store() {
    }

    public static Store open(String name, String address, String businessRegistrationNumber, StoreCategory category,
            Map<DayOfWeek, BusinessHour> businessHours, String inviteCode, BreakPolicy breakPolicy,
            Boolean smallBusiness, Integer payday) {
        Store store = new Store();
        store.inviteCode = inviteCode;
        store.update(name, address, businessRegistrationNumber, category, businessHours, breakPolicy, smallBusiness,
                payday);
        return store;
    }

    void update(String name, String address, String businessRegistrationNumber, StoreCategory category,
            Map<DayOfWeek, BusinessHour> businessHours, BreakPolicy breakPolicy, Boolean smallBusiness,
            Integer payday) {
        this.name = name;
        this.address = address;
        this.businessRegistrationNumber = businessRegistrationNumber;
        this.category = category;
        this.businessHours = businessHours == null ? new HashMap<>() : new HashMap<>(businessHours);
        this.breakPolicy = breakPolicy == null ? BreakPolicy.STATUTORY : breakPolicy;
        this.smallBusiness = Boolean.TRUE.equals(smallBusiness);
        this.payday = payday;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public Set<String> getConfirmedOnboardingSteps() {
        return onboardingSteps.stream().map(OnboardingStep::stepKey).collect(Collectors.toUnmodifiableSet());
    }

    /** 단계 하나를 확인 상태로 표시한다. 이미 표시돼 있으면 아무 일도 하지 않아 같은 요청이 여러 번 와도 결과가 같다. */
    public void confirmOnboardingStep(String stepKey) {
        onboardingSteps.add(new OnboardingStep(stepKey));
    }

    /** 온보딩을 마친 것으로 표시한다. 이미 마쳤다면 처음 끝낸 시각을 유지한다. */
    public void completeOnboarding() {
        if (onboardingCompletedAt == null) {
            onboardingCompletedAt = LocalDateTime.now();
        }
    }

    void changeProfileImageKey(String profileImageKey) {
        this.profileImageKey = profileImageKey;
    }

    void changeInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }

    /** 삭제 표시. 이미 표시돼 있으면 최초 시각을 유지해, 반복 호출로 유예기간이 늘어나지 않게 한다. */
    void softDelete() {
        if (deletedAt == null) {
            deletedAt = LocalDateTime.now();
        }
    }
}
