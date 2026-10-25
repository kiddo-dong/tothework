package com.tothework.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tothework.identity.config.IdentityProperties;
import com.tothework.identity.mail.MailService;
import com.tothework.identity.oauth.OAuthProfilePhotoImporter;
import com.tothework.identity.oauth.OAuthUserInfo;
import com.tothework.identity.oauth.OAuthUserInfoFetcher;
import com.tothework.identity.user.AuthProvider;
import com.tothework.identity.user.User;
import com.tothework.identity.user.UserRepository;
import com.tothework.shared.error.BusinessException;
import com.tothework.shared.error.ConflictException;
import com.tothework.shared.error.ErrorCode;
import com.tothework.shared.error.InvalidRequestException;
import com.tothework.shared.security.JwtTokens;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * 가입·로그인·비밀번호 변경. 가입이 중간에 끊긴 계정이 이메일을 붙잡으면 사용자는 재가입도 로그인도 못 하는
 * 막다른 상태에 빠지므로 그 경우를 특히 본다.
 *
 * <p>Spring Data JDBC로 옮기면서 변경 감지가 없어졌다. 값을 바꾸는 경로는 저장까지 했는지 함께 확인한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceSignupTest {

    private static final String EMAIL = "test@albam.dev";
    private static final String PHONE = "010-1234-5678";

    @Mock private UserRepository userRepository;
    @Mock private EmailTokenRepository emailTokenRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokens jwtTokens;
    @Mock private MailService mailService;
    @Mock private OAuthProfilePhotoImporter oAuthProfilePhotoImporter;
    @Mock private OAuthUserInfoFetcher googleFetcher;
    @Mock private PlatformTransactionManager transactionManager;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, emailTokenRepository, refreshTokenRepository,
                passwordEncoder, jwtTokens, List.of(googleFetcher), mailService, oAuthProfilePhotoImporter,
                transactionManager,
                new IdentityProperties("http://localhost:8080", "http://localhost:5173", false, 7));

        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(emailTokenRepository.save(any(EmailToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                ReflectionTestUtils.setField(saved, "id", 1L);
            }
            return saved;
        }).when(userRepository).save(any(User.class));
        when(jwtTokens.issue(any(), anyString(), any())).thenReturn("token");
        when(jwtTokens.expiresAtOf(anyString())).thenReturn(LocalDateTime.now().plusDays(14));
    }

    private SignupRequest signupRequest() {
        return new SignupRequest(EMAIL, "Test1234!", "Test1234!", "테스트", PHONE, LocalDate.of(1990, 1, 1), true);
    }

    private User unverifiedLocalUser() {
        User user = User.signUpLocally(EMAIL, "old-encoded", "예전이름", "010-0000-0000",
                LocalDate.of(1980, 1, 1), LocalDateTime.now());
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    private User verifiedLocalUser() {
        User user = unverifiedLocalUser();
        user.markEmailVerified();
        return user;
    }

    private User socialUser() {
        User social = User.signUpWithProvider(EMAIL, "소셜", AuthProvider.GOOGLE, "google-1");
        ReflectionTestUtils.setField(social, "id", 1L);
        return social;
    }

    private void givenMailWasSentJustNow(EmailTokenType type) {
        when(emailTokenRepository.existsByUserIdAndTypeAndCreatedAtAfter(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(type), any())).thenReturn(true);
    }

    /** 재발송을 연타하거나 남의 이메일로 반복 호출해도 1분에 한 통만 나간다. Gmail 하루 한도를 지키기 위해서다. */
    @Test
    void resendVerificationWaitsForTheCooldown() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(unverifiedLocalUser()));
        givenMailWasSentJustNow(EmailTokenType.VERIFY_EMAIL);

        assertThatThrownBy(() -> authService.resendVerification(EMAIL))
                .isInstanceOf(com.tothework.shared.error.TooManyRequestsException.class);
        verify(mailService, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void resendVerificationSendsWhenTheLastMailIsOldEnough() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(unverifiedLocalUser()));

        authService.resendVerification(EMAIL);

        verify(mailService).send(org.mockito.ArgumentMatchers.eq(EMAIL), anyString(), anyString());
    }

    /** 비밀번호 재설정은 계정 존재 여부를 드러내지 않도록 오류 없이 넘어가되, 메일은 다시 보내지 않는다. */
    @Test
    void passwordResetQuietlySkipsAMailSentJustNow() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(verifiedLocalUser()));
        givenMailWasSentJustNow(EmailTokenType.PASSWORD_RESET);

        assertThatCode(() -> authService.requestPasswordReset(EMAIL)).doesNotThrowAnyException();
        verify(mailService, never()).send(anyString(), anyString(), anyString());
    }

    /** 미인증 계정에 다시 가입하는 경로도 인증 메일을 보내므로 같은 간격을 지킨다. */
    @Test
    void resubmittedSignupWaitsForTheCooldown() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(unverifiedLocalUser()));
        givenMailWasSentJustNow(EmailTokenType.VERIFY_EMAIL);

        assertThatThrownBy(() -> authService.signup(signupRequest()))
                .isInstanceOf(com.tothework.shared.error.TooManyRequestsException.class);
        verify(mailService, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void signupCreatesTheAccountWhenTheEmailIsFree() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThat(authService.signup(signupRequest())).isEqualTo(1L);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().isEmailVerified()).isFalse();
        verify(mailService).send(anyString(), anyString(), anyString());
    }

    /**
     * 인증 전 계정은 소유자가 증명된 적이 없다. 그대로 거절하면 주소를 잘못 적었거나 메일을 놓친 사람은 그
     * 이메일로 영영 가입할 수 없다. 이메일이 unique라 새 행이 아니라 같은 행을 덮어써 저장해야 한다.
     */
    @Test
    void signupOverwritesAnUnverifiedAccountWithTheSameEmail() {
        User existing = unverifiedLocalUser();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existing));

        assertThatCode(() -> authService.signup(signupRequest())).doesNotThrowAnyException();

        verify(userRepository).save(existing);
        assertThat(existing.getName()).isEqualTo("테스트");
        assertThat(existing.getPhone()).isEqualTo(PHONE);
    }

    /** 덮어쓴 뒤에는 앞서 보낸 인증 링크가 살아 있으면 안 된다. */
    @Test
    void signupInvalidatesTheEarlierVerificationLink() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(unverifiedLocalUser()));

        authService.signup(signupRequest());

        verify(emailTokenRepository).deleteAllOfUser(1L);
    }

    /** 인증을 마친 계정에는 실제 소유자가 있으므로 덮어쓰면 안 된다. */
    @Test
    void signupIsRejectedWhenTheAccountIsAlreadyVerified() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(verifiedLocalUser()));

        assertThatThrownBy(() -> authService.signup(signupRequest())).isInstanceOf(ConflictException.class);
        verify(userRepository, never()).save(any());
    }

    /** 소셜 계정도 provider 쪽에서 이미 검증된 것이라 덮어쓰지 않는다. */
    @Test
    void signupIsRejectedWhenTheEmailBelongsToASocialAccount() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(socialUser()));

        assertThatThrownBy(() -> authService.signup(signupRequest())).isInstanceOf(ConflictException.class);
    }

    /** 재가입이라도 남이 쓰는 전화번호는 가져올 수 없다. */
    @Test
    void signupIsRejectedWhenThePhoneBelongsToAnotherAccount() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(unverifiedLocalUser()));
        when(userRepository.existsByPhoneAndIdNot(PHONE, 1L)).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(signupRequest())).isInstanceOf(ConflictException.class);
    }

    @Test
    void signupIsRejectedWhenThePasswordIsTooSimple() {
        SignupRequest weak = new SignupRequest(EMAIL, "abcdefgh", "abcdefgh", "테스트", PHONE,
                LocalDate.of(1990, 1, 1), true);

        assertThatThrownBy(() -> authService.signup(weak)).isInstanceOf(InvalidRequestException.class);
    }

    // ---------- 이메일 인증 ----------

    /** 인증 표시와 토큰 사용 표시가 둘 다 저장돼야 한다. 하나라도 빠지면 다시 로그인이 막히거나 링크를 재사용할 수 있다. */
    @Test
    void verifyEmailSavesBothTheUserAndTheToken() {
        User user = unverifiedLocalUser();
        EmailToken token = EmailToken.issue(1L, "t", EmailTokenType.VERIFY_EMAIL, LocalDateTime.now().plusHours(1));
        when(emailTokenRepository.findByTokenAndType("t", EmailTokenType.VERIFY_EMAIL)).thenReturn(Optional.of(token));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        authService.verifyEmail("t");

        assertThat(user.isEmailVerified()).isTrue();
        verify(userRepository).save(user);
        assertThat(token.isUsed()).isTrue();
        verify(emailTokenRepository).save(token);
    }

    @Test
    void verifyEmailRejectsAnUsedLink() {
        EmailToken token = EmailToken.issue(1L, "t", EmailTokenType.VERIFY_EMAIL, LocalDateTime.now().plusHours(1));
        token.markUsed();
        when(emailTokenRepository.findByTokenAndType("t", EmailTokenType.VERIFY_EMAIL)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.verifyEmail("t")).isInstanceOf(InvalidRequestException.class);
    }

    // ---------- 로그인 ----------

    @Test
    void loginIssuesTokensForAVerifiedAccount() {
        User user = verifiedLocalUser();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Test1234!", "old-encoded")).thenReturn(true);

        IssuedTokens tokens = authService.login(new LoginRequest(EMAIL, "Test1234!"));

        assertThat(tokens.accessToken()).isEqualTo("token");
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    /** 계정이 없을 때와 비밀번호가 틀릴 때 같은 응답을 내 계정 존재 여부를 드러내지 않는다. */
    @Test
    void loginGivesTheSameAnswerForAWrongPasswordAndAMissingAccount() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(verifiedLocalUser()));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
        when(userRepository.findByEmail("nobody@albam.dev")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "Wrong1234!")))
                .isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@albam.dev", "Wrong1234!")))
                .isInstanceOf(BadCredentialsException.class);
    }

    /** 비밀번호는 맞았고 남은 것이 인증뿐이면, 프론트가 재발송 안내를 띄울 수 있게 다른 코드가 나가야 한다. */
    @Test
    void loginReportsAnUnverifiedEmailWithItsOwnCode() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(unverifiedLocalUser()));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "Test1234!")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED));
    }

    // ---------- 비밀번호 변경 ----------

    private ChangePasswordRequest changeRequest(String current, String next) {
        return new ChangePasswordRequest(current, next, next);
    }

    @Test
    void changePasswordReplacesAndSavesThePassword() {
        User user = unverifiedLocalUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Current1!", "old-encoded")).thenReturn(true);
        when(passwordEncoder.encode("NewPass1!")).thenReturn("encoded-new");

        authService.changePassword(1L, changeRequest("Current1!", "NewPass1!"));

        assertThat(user.getPassword()).isEqualTo("encoded-new");
        verify(userRepository).save(user);
    }

    /** 자리를 비운 사이 열린 화면으로 남이 비밀번호를 바꾸는 것을 막는다. */
    @Test
    void changePasswordIsRejectedWhenTheCurrentOneIsWrong() {
        User user = unverifiedLocalUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(1L, changeRequest("Wrong1!", "NewPass1!")))
                .isInstanceOf(InvalidRequestException.class);
        assertThat(user.getPassword()).isEqualTo("old-encoded");
        verify(userRepository, never()).save(any());
    }

    /** 비밀번호를 바꾸는 이유 중 하나가 남이 들어와 있는 것 같아서다. 세션을 두면 쫓아내야 할 쪽이 남는다. */
    @Test
    void changePasswordEndsEverySession() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(unverifiedLocalUser()));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        authService.changePassword(1L, changeRequest("Current1!", "NewPass1!"));

        verify(refreshTokenRepository).deleteAllOfUser(1L);
    }

    @Test
    void changePasswordIsRejectedWhenTheTwoEntriesDiffer() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(unverifiedLocalUser()));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        assertThatThrownBy(() -> authService.changePassword(1L,
                new ChangePasswordRequest("Current1!", "NewPass1!", "Different1!")))
                .isInstanceOf(InvalidRequestException.class);
    }

    /** 소셜 계정은 비밀번호가 없어 바꿀 것도 없다. */
    @Test
    void changePasswordIsRejectedForASocialAccount() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(socialUser()));

        assertThatThrownBy(() -> authService.changePassword(1L, changeRequest("Current1!", "NewPass1!")))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void confirmPasswordResetSavesTheNewPasswordAndEndsSessions() {
        User user = unverifiedLocalUser();
        EmailToken token = EmailToken.issue(1L, "r", EmailTokenType.PASSWORD_RESET, LocalDateTime.now().plusMinutes(5));
        when(emailTokenRepository.findByTokenAndType("r", EmailTokenType.PASSWORD_RESET)).thenReturn(Optional.of(token));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewPass1!")).thenReturn("encoded-new");

        authService.confirmPasswordReset(new PasswordResetConfirmRequest("r", "NewPass1!", "NewPass1!"));

        assertThat(user.getPassword()).isEqualTo("encoded-new");
        verify(userRepository).save(user);
        verify(emailTokenRepository).save(token);
        verify(refreshTokenRepository).deleteAllOfUser(1L);
    }

    // ---------- 소셜 로그인 ----------

    /** 이미 가입한 사람이 다시 로그인할 때 사진을 또 가져오면, 직접 올린 사진이 매번 되돌아간다. */
    @Test
    void oauthLoginDoesNotRefetchThePhotoForAnExistingAccount() {
        when(googleFetcher.getProvider()).thenReturn(AuthProvider.GOOGLE);
        when(googleFetcher.fetch(anyString()))
                .thenReturn(new OAuthUserInfo("google-1", EMAIL, "소셜", "https://provider/photo.png"));
        when(userRepository.existsByProviderAndProviderId(AuthProvider.GOOGLE, "google-1")).thenReturn(true);
        when(userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-1"))
                .thenReturn(Optional.of(socialUser()));

        authService.oauthLogin(AuthProvider.GOOGLE, "provider-access-token");

        verify(oAuthProfilePhotoImporter, never()).importFrom(anyString());
    }

    @Test
    void oauthLoginRegistersANewAccountWithTheImportedPhoto() {
        when(googleFetcher.getProvider()).thenReturn(AuthProvider.GOOGLE);
        when(googleFetcher.fetch(anyString()))
                .thenReturn(new OAuthUserInfo("google-2", "new@albam.dev", "새사람", "https://provider/photo.png"));
        when(userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-2")).thenReturn(Optional.empty());
        when(oAuthProfilePhotoImporter.importFrom("https://provider/photo.png")).thenReturn("profile-images/x.png");

        IssuedTokens tokens = authService.oauthLogin(AuthProvider.GOOGLE, "provider-access-token");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getProfileImageKey()).isEqualTo("profile-images/x.png");
        assertThat(saved.getValue().isEmailVerified()).isTrue();
        assertThat(tokens.profileCompleted()).isFalse();
    }
}
