package com.tothework.identity.user;

import com.tothework.identity.config.IdentitySchema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 사용자 (애그리거트 루트). 인증 토큰은 사용자 id를 참조하는 별도 애그리거트다.
 *
 * <p>상태를 바꾸는 메서드는 값만 바꾼다. Spring Data JDBC에는 변경 감지가 없으므로 호출한 쪽이 반드시
 * {@code UserRepository.save}로 저장해야 한다.
 */
@Getter
@Table(schema = IdentitySchema.NAME, name = "users")
public class User {

    @Id
    private Long id;
    private String email;
    private String password;
    private String name;
    private String phone;
    private LocalDate birthDate;
    private LocalDateTime termsAgreedAt;
    /** 프로필 이미지의 S3 key (전체 URL이 아님). 공개 URL은 응답을 만들 때 조립한다. */
    private String profileImageKey;
    private final AuthProvider provider;
    private String providerId;
    /** 이메일 인증 여부. 소셜 가입은 provider가 이미 검증했으므로 true로 시작한다. */
    private boolean emailVerified;
    /** 탈퇴 시각. 다른 서비스의 기록(근무 이력 등)이 이 id를 참조하므로 행을 지우지 않고 개인정보만 익명화한다. */
    private LocalDateTime deletedAt;
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;

    @PersistenceCreator
    User(Long id, String email, String password, String name, String phone, LocalDate birthDate,
            LocalDateTime termsAgreedAt, String profileImageKey, AuthProvider provider, String providerId,
            boolean emailVerified, LocalDateTime deletedAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.name = name;
        this.phone = phone;
        this.birthDate = birthDate;
        this.termsAgreedAt = termsAgreedAt;
        this.profileImageKey = profileImageKey;
        this.provider = provider;
        this.providerId = providerId;
        this.emailVerified = emailVerified;
        this.deletedAt = deletedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** 이메일·비밀번호 가입. 인증 메일을 확인해야 로그인할 수 있다. */
    public static User signUpLocally(String email, String encodedPassword, String name, String phone,
            LocalDate birthDate, LocalDateTime termsAgreedAt) {
        return new User(null, email, encodedPassword, name, phone, birthDate, termsAgreedAt, null,
                AuthProvider.LOCAL, null, false, null, null, null);
    }

    /** 소셜 가입. 전화번호·생년월일·약관 동의는 추가 정보 입력에서 받는다. */
    public static User signUpWithProvider(String email, String name, AuthProvider provider, String providerId) {
        return new User(null, email, null, name, null, null, null, null, provider, providerId, true, null,
                null, null);
    }

    /**
     * 인증을 마치지 않은 로컬 계정에 같은 이메일로 다시 가입할 때, 기존 행을 새 입력으로 덮어쓴다.
     *
     * <p>새 행을 만드는 대신 덮어쓰는 이유는 이메일이 unique이기 때문이다. 인증 전 계정은 소유자가
     * 증명된 적이 없어(메일함 접근을 보인 적이 없다) 이렇게 덮어써도 남의 계정을 건드리는 것이
     * 아니다. 덮어써도 여전히 인증을 통과해야 로그인할 수 있으므로 가로채기에도 쓰이지 않는다.
     */
    public void resubmitSignup(String password, String name, String phone, LocalDate birthDate) {
        this.password = password;
        this.name = name;
        this.phone = phone;
        this.birthDate = birthDate;
        this.termsAgreedAt = LocalDateTime.now();
    }

    public void markEmailVerified() {
        this.emailVerified = true;
    }

    /**
     * 프로필 입력 완료 여부. 소셜 가입 직후에는 전화번호·생년월일·약관동의가 비어 있으며,
     * 입력 도중 창이 닫혀도 재로그인 후 이 값으로 미완성 상태를 감지해 이어서 입력받는다.
     */
    public boolean isProfileCompleted() {
        return phone != null && birthDate != null && termsAgreedAt != null;
    }

    /** 소셜 가입 등으로 비어 있는 추가 정보를 채우고 약관 동의 시각을 기록한다. */
    public void completeProfile(String name, String phone, LocalDate birthDate) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
        this.phone = phone;
        this.birthDate = birthDate;
        this.termsAgreedAt = LocalDateTime.now();
    }

    /**
     * 탈퇴 처리: 개인정보를 익명화하고 로그인 불가능한 상태로 만든다.
     * 이메일은 unique 제약을 유지하면서 원래 주소를 해제하기 위해 대체 값으로 바꾼다.
     */
    public void anonymizeForWithdrawal() {
        this.email = "deleted-" + id + "@withdrawn.albam";
        this.name = "탈퇴회원";
        this.password = null;
        this.phone = null;
        this.birthDate = null;
        this.profileImageKey = null;
        this.providerId = null;
        this.emailVerified = false;
        this.deletedAt = LocalDateTime.now();
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    /**
     * 프로필 수정.
     *
     * <p>생년월일은 값이 왔을 때만 바꾼다. 안 보냈다고 비워버리면 {@link #isProfileCompleted()}가
     * false가 되어 그때부터 서비스 이용이 막히는데, 이름만 고치려던 사람에게 일어날 일은 아니다.
     * (이름과 전화번호는 요청에서 필수라 항상 값이 있다.)
     */
    public void updateProfile(String name, String phone, LocalDate birthDate) {
        this.name = name;
        this.phone = phone;
        if (birthDate != null) {
            this.birthDate = birthDate;
        }
    }

    public void changeProfileImageKey(String profileImageKey) {
        this.profileImageKey = profileImageKey;
    }
}
