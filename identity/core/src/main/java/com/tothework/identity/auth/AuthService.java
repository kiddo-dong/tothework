package com.tothework.identity.auth;

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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 가입·인증 메일·로그인·토큰 재발급·비밀번호. 규칙은 기존 백엔드와 같다.
 *
 * <p>Spring Data JDBC에는 변경 감지가 없다. 사용자나 토큰의 값을 바꾼 곳마다 저장을 직접 부른다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final int VERIFY_EMAIL_EXPIRATION_HOURS = 24;
    private static final int PASSWORD_RESET_EXPIRATION_MINUTES = 30;

    private final UserRepository userRepository;
    private final EmailTokenRepository emailTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokens jwtTokens;
    private final List<OAuthUserInfoFetcher> oAuthUserInfoFetchers;
    private final MailService mailService;
    private final OAuthProfilePhotoImporter oAuthProfilePhotoImporter;
    private final PlatformTransactionManager transactionManager;
    private final IdentityProperties properties;

    /**
     * 메일 발송(SMTP)은 트랜잭션 밖에서 한다 — DB 커밋까지 커넥션을 붙잡지 않기 위함이자, 메일 서버 장애로 이미
     * 커밋된 가입을 롤백시키지 않기 위함. 인증 메일은 재발송 API로 복구할 수 있다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Long signup(SignupRequest request) {
        if (!request.password().equals(request.passwordConfirm())) {
            throw new InvalidRequestException("비밀번호가 일치하지 않습니다.");
        }
        validatePasswordComplexity(request.password());
        VerificationMail mail = new TransactionTemplate(transactionManager).execute(status -> {
            User existing = userRepository.findByEmail(request.email()).orElse(null);
            if (existing != null) {
                return resubmitSignup(existing, request);
            }
            if (userRepository.existsByPhone(request.phone())) {
                throw new ConflictException("이미 가입된 전화번호입니다.");
            }
            User user = userRepository.save(User.signUpLocally(request.email(),
                    passwordEncoder.encode(request.password()), request.name(), request.phone(),
                    request.birthDate(), LocalDateTime.now()));
            return prepareVerificationMail(user);
        });
        sendVerificationMail(mail);
        return mail.userId();
    }

    /**
     * 이미 그 이메일로 행이 있을 때. 인증을 마치지 않은 로컬 계정이면 가입이 끝나지 않은 것이므로 새 입력으로
     * 덮어쓰고 인증 메일을 다시 보낸다. 인증을 마쳤거나 소셜로 가입한 계정은 실제 소유자가 있으므로 거절한다.
     */
    private VerificationMail resubmitSignup(User existing, SignupRequest request) {
        if (existing.isEmailVerified() || existing.getProvider() != AuthProvider.LOCAL) {
            throw new ConflictException("이미 가입된 이메일입니다.");
        }
        if (userRepository.existsByPhoneAndIdNot(request.phone(), existing.getId())) {
            throw new ConflictException("이미 가입된 전화번호입니다.");
        }
        existing.resubmitSignup(passwordEncoder.encode(request.password()), request.name(),
                request.phone(), request.birthDate());
        userRepository.save(existing);
        // 앞서 보낸 인증 링크는 더 이상 유효하면 안 된다. 옛 메일이 남아 있어도 새 메일만 통한다.
        emailTokenRepository.deleteAllOfUser(existing.getId());
        return prepareVerificationMail(existing);
    }

    private VerificationMail prepareVerificationMail(User user) {
        EmailToken token = emailTokenRepository.save(EmailToken.issue(user.getId(), UUID.randomUUID().toString(),
                EmailTokenType.VERIFY_EMAIL, LocalDateTime.now().plusHours(VERIFY_EMAIL_EXPIRATION_HOURS)));
        return new VerificationMail(user.getId(), user.getEmail(), user.getName(), token.getToken());
    }

    /** 인증 링크는 API를 가리킨다. 토큰 처리는 서버가 하고, 끝나면 사용자를 프론트 화면으로 넘긴다. */
    private void sendVerificationMail(VerificationMail mail) {
        String link = properties.baseUrl() + "/api/v1/auth/verify-email?token=" + mail.token();
        mailService.send(mail.email(), "[ToTheWork] 이메일 인증을 완료해 주세요",
                mail.name() + "님, ToTheWork 가입을 환영합니다!\n\n"
                        + "아래 링크를 클릭해 이메일 인증을 완료해 주세요. (24시간 이내)\n" + link);
    }

    private record VerificationMail(Long userId, String email, String name, String token) {
    }

    private record PasswordResetMail(String email, String name, String token) {
    }

    @Transactional
    public void verifyEmail(String token) {
        EmailToken emailToken = usableToken(token, EmailTokenType.VERIFY_EMAIL,
                "유효하지 않은 인증 링크입니다.", "만료되었거나 이미 사용된 인증 링크입니다.");
        User user = userOf(emailToken);
        user.markEmailVerified();
        userRepository.save(user);
        emailToken.markUsed();
        emailTokenRepository.save(emailToken);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void resendVerification(String email) {
        VerificationMail mail = new TransactionTemplate(transactionManager).execute(status -> {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new InvalidRequestException("가입되지 않은 이메일입니다."));
            if (user.getProvider() != AuthProvider.LOCAL || user.isEmailVerified()) {
                throw new InvalidRequestException("이메일 인증이 필요한 계정이 아닙니다.");
            }
            return prepareVerificationMail(user);
        });
        sendVerificationMail(mail);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void requestPasswordReset(String email) {
        // 계정 존재 여부가 노출되지 않도록, 없는 이메일이거나 소셜 계정이어도 조용히 성공 처리한다
        PasswordResetMail mail = new TransactionTemplate(transactionManager).execute(status ->
                userRepository.findByEmail(email)
                        .filter(user -> user.getProvider() == AuthProvider.LOCAL)
                        .map(user -> {
                            EmailToken token = emailTokenRepository.save(EmailToken.issue(user.getId(),
                                    UUID.randomUUID().toString(), EmailTokenType.PASSWORD_RESET,
                                    LocalDateTime.now().plusMinutes(PASSWORD_RESET_EXPIRATION_MINUTES)));
                            return new PasswordResetMail(user.getEmail(), user.getName(), token.getToken());
                        })
                        .orElse(null));
        if (mail == null) {
            return;
        }
        String link = properties.frontendUrl() + "/password-reset/confirm?token=" + mail.token();
        mailService.send(mail.email(), "[ToTheWork] 비밀번호 재설정 안내",
                mail.name() + "님, 비밀번호 재설정 요청이 접수되었습니다.\n\n"
                        + "아래 링크에서 30분 이내에 새 비밀번호를 설정해 주세요.\n" + link + "\n\n"
                        + "본인이 요청하지 않았다면 이 메일을 무시하세요.");
    }

    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        if (!request.newPassword().equals(request.newPasswordConfirm())) {
            throw new InvalidRequestException("비밀번호가 일치하지 않습니다.");
        }
        validatePasswordComplexity(request.newPassword());
        EmailToken emailToken = usableToken(request.token(), EmailTokenType.PASSWORD_RESET,
                "유효하지 않은 재설정 토큰입니다.", "만료되었거나 이미 사용된 재설정 토큰입니다.");
        User user = userOf(emailToken);
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        emailToken.markUsed();
        emailTokenRepository.save(emailToken);
        // 비밀번호를 바꾸는 이유 중 하나가 계정 탈취이므로, 기존 로그인 세션을 전부 끊는다.
        refreshTokenRepository.deleteAllOfUser(user.getId());
    }

    /**
     * 로그인한 사용자가 스스로 비밀번호를 바꾼다. 지금 비밀번호를 확인하고, 바꾼 뒤에는 모든 세션을 끊는다.
     * 비밀번호를 바꾸는 이유 중 하나가 남이 내 계정에 들어와 있는 것 같아서이기 때문이다.
     */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidRequestException("사용자를 찾을 수 없습니다."));
        if (user.getProvider() != AuthProvider.LOCAL || user.getPassword() == null) {
            throw new InvalidRequestException("소셜 로그인으로 가입한 계정은 비밀번호가 없습니다.");
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new InvalidRequestException("현재 비밀번호가 올바르지 않습니다.");
        }
        if (!request.newPassword().equals(request.newPasswordConfirm())) {
            throw new InvalidRequestException("비밀번호가 일치하지 않습니다.");
        }
        validatePasswordComplexity(request.newPassword());

        user.changePassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        refreshTokenRepository.deleteAllOfUser(userId);
    }

    private void validatePasswordComplexity(String password) {
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = password.chars().anyMatch(c -> !Character.isLetterOrDigit(c));
        int satisfied = (hasLetter ? 1 : 0) + (hasDigit ? 1 : 0) + (hasSpecial ? 1 : 0);
        if (satisfied < 2) {
            throw new InvalidRequestException("비밀번호는 영문/숫자/특수문자 중 2가지 이상을 조합해야 합니다.");
        }
    }

    /**
     * 비밀번호는 이 서비스가 직접 확인한다. 계정이 없을 때와 비밀번호가 틀릴 때 같은 401을 내려 계정 존재 여부를
     * 드러내지 않는다.
     */
    @Transactional
    public IssuedTokens login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다."));
        if (user.getProvider() != AuthProvider.LOCAL) {
            throw new InvalidRequestException(
                    user.getProvider() + " 소셜 로그인으로 가입된 계정입니다. 소셜 로그인을 이용해 주세요.");
        }
        if (user.getPassword() == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BadCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        if (!user.isEmailVerified()) {
            // 비밀번호는 맞았고 남은 것은 인증뿐이라, 프론트가 재발송 안내를 띄울 수 있게 다른 코드로 보낸다.
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED,
                    "이메일 인증이 완료되지 않았습니다. 메일함을 확인해 주세요.");
        }
        return issueTokens(user);
    }

    /**
     * 소셜 로그인. 처음 보는 계정이면 가입까지 함께 처리한다. 제공자 API 호출과 프로필 사진 내려받기 동안 DB
     * 커넥션을 붙잡지 않도록, DB를 건드리는 부분만 따로 트랜잭션으로 묶는다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public IssuedTokens oauthLogin(AuthProvider provider, String accessToken) {
        OAuthUserInfo userInfo = resolveFetcher(provider).fetch(accessToken);

        // 사진은 처음 가입할 때만 가져온다. 로그인할 때마다 덮어쓰면 사용자가 직접 올린 사진이 되돌아간다.
        String profileImageKey = userRepository.existsByProviderAndProviderId(provider, userInfo.providerId())
                ? null
                : oAuthProfilePhotoImporter.importFrom(userInfo.profileImageUrl());

        return new TransactionTemplate(transactionManager).execute(status -> {
            User user = userRepository.findByProviderAndProviderId(provider, userInfo.providerId())
                    .orElseGet(() -> registerOAuthUser(provider, userInfo, profileImageKey));
            return issueTokens(user);
        });
    }

    private User registerOAuthUser(AuthProvider provider, OAuthUserInfo userInfo, String profileImageKey) {
        if (userRepository.existsByEmail(userInfo.email())) {
            throw new ConflictException("이미 다른 방식으로 가입된 이메일입니다.");
        }
        User user = User.signUpWithProvider(userInfo.email(), userInfo.name(), provider, userInfo.providerId());
        if (profileImageKey != null) {
            user.changeProfileImageKey(profileImageKey);
        }
        return userRepository.save(user);
    }

    private OAuthUserInfoFetcher resolveFetcher(AuthProvider provider) {
        return oAuthUserInfoFetchers.stream()
                .filter(fetcher -> fetcher.getProvider() == provider)
                .findFirst()
                .orElseThrow(() -> new InvalidRequestException("지원하지 않는 로그인 방식입니다."));
    }

    /**
     * 리프레시 토큰을 새 토큰 쌍으로 교환한다. 서명·만료뿐 아니라 저장된 기록이 있는지도 확인하므로, 로그아웃이나
     * 비밀번호 변경으로 지워진 토큰은 아직 만료 전이어도 거부된다. 쓴 토큰은 지우고 새로 발급한다(회전).
     *
     * <p>거부할 때도 지운 것(재사용 탐지의 세션 폐기, 소비한 토큰)은 남아야 한다. 같은 트랜잭션에서 예외를 던지면
     * 그 삭제가 함께 롤백돼 폐기가 일어나지 않았으므로, 트랜잭션은 거부 여부만 돌려주고 커밋된 뒤에 예외를 던진다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public IssuedTokens refresh(String refreshToken) {
        if (!jwtTokens.isValid(refreshToken, JwtTokens.Type.REFRESH)) {
            throw new InvalidRequestException("유효하지 않은 리프레시 토큰입니다.");
        }
        IssuedTokens issued = new TransactionTemplate(transactionManager).execute(status -> {
            Optional<RefreshToken> stored = refreshTokenRepository.findByTokenHash(hashToken(refreshToken));
            if (stored.isEmpty()) {
                revokeAllSessionsAfterReuse(refreshToken);
                return null;
            }

            // 같은 토큰을 든 두 요청은 둘 다 위 조회에서 행을 찾는다. 삭제는 한쪽만 성공하므로 지운 행 수로 누가
            // 이겼는지 가린다. 진 쪽은 거의 항상 같은 사람의 다른 탭이라 탈취로 보지 않고 세션을 끊지 않는다.
            if (refreshTokenRepository.deleteClaimed(stored.get().getId()) == 0) {
                throw new BusinessException(ErrorCode.TOKEN_ALREADY_ROTATED,
                        "다른 창에서 로그인이 먼저 갱신됐어요. 다시 시도해 주세요.");
            }
            // 탈퇴한 계정은 이메일이 익명화돼 있어, 여기서 발급하면 그 익명 이메일로 인증이 통과한다.
            return userRepository.findById(stored.get().getUserId())
                    .filter(user -> user.getDeletedAt() == null)
                    .map(this::issueTokens)
                    .orElse(null);
        });
        if (issued == null) {
            throw new InvalidRequestException("만료되었거나 이미 사용된 로그인 정보입니다. 다시 로그인해 주세요.");
        }
        return issued;
    }

    /**
     * 서명은 멀쩡한데 기록이 없는 토큰은 이미 회전으로 소비됐거나 폐기된 토큰이 다시 쓰이는 것이다. 유출됐을
     * 가능성이 있다고 보고 그 사용자의 세션을 전부 끊어, 탈취자가 다른 토큰으로 갈아타지 못하게 한다.
     */
    private void revokeAllSessionsAfterReuse(String refreshToken) {
        Long userId;
        try {
            userId = jwtTokens.userIdOf(refreshToken);
        } catch (RuntimeException e) {
            return;
        }
        int revoked = refreshTokenRepository.deleteAllOfUser(userId);
        if (revoked > 0) {
            log.warn("이미 사용된 리프레시 토큰이 다시 들어와 userId={}의 세션 {}건을 폐기했다. "
                    + "토큰 유출이거나, 여러 탭이 동시에 갱신을 시도한 경우다.", userId, revoked);
        }
    }

    /** 이 토큰 하나만 폐기한다. 이미 없는 토큰이어도 조용히 넘어간다 — 로그아웃은 몇 번을 눌러도 성공해야 한다. */
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hashToken(refreshToken)).ifPresent(refreshTokenRepository::delete);
    }

    private IssuedTokens issueTokens(User user) {
        String accessToken = jwtTokens.issue(user.getId(), user.getEmail(), JwtTokens.Type.ACCESS);
        String refreshToken = jwtTokens.issue(user.getId(), user.getEmail(), JwtTokens.Type.REFRESH);
        refreshTokenRepository.save(RefreshToken.record(user.getId(), hashToken(refreshToken),
                jwtTokens.expiresAtOf(refreshToken)));
        return new IssuedTokens(accessToken, refreshToken, user.isProfileCompleted());
    }

    private EmailToken usableToken(String token, EmailTokenType type, String unknownMessage, String unusableMessage) {
        EmailToken emailToken = emailTokenRepository.findByTokenAndType(token, type)
                .orElseThrow(() -> new InvalidRequestException(unknownMessage));
        if (!emailToken.isUsable()) {
            throw new InvalidRequestException(unusableMessage);
        }
        return emailToken;
    }

    private User userOf(EmailToken emailToken) {
        return userRepository.findById(emailToken.getUserId())
                .orElseThrow(() -> new InvalidRequestException("유효하지 않은 링크입니다."));
    }

    /** 토큰 원문 대신 해시를 저장·조회한다. DB가 유출돼도 그대로 쓸 수 있는 토큰이 함께 넘어가지 않는다. */
    static String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }
}
