package com.tothework.identity.user;

import com.tothework.identity.api.UserDirectory;
import com.tothework.identity.api.UserSummary;
import com.tothework.shared.security.AuthenticatedUser;
import com.tothework.shared.security.AuthenticatedUserLoader;
import com.tothework.shared.storage.S3Uploader;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * identity가 다른 서비스에 사용자 정보를 내주는 두 입구의 구현.
 *
 * <ul>
 *   <li>{@link UserDirectory}: 화면에 사람을 보여 줄 때 쓰는 요약 정보
 *   <li>{@link AuthenticatedUserLoader}: 요청마다 인증된 주체를 채운다
 * </ul>
 *
 * <p>어느 쪽도 {@link User} 엔티티를 밖으로 내보내지 않는다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
class UserLookups implements UserDirectory, AuthenticatedUserLoader {

    private final UserRepository userRepository;
    private final S3Uploader s3Uploader;

    @Override
    public Optional<UserSummary> find(Long userId) {
        return userRepository.findById(userId).map(this::toSummary);
    }

    @Override
    public Map<Long, UserSummary> findAll(Collection<Long> userIds) {
        return userRepository.findAllById(userIds).stream()
                .map(this::toSummary)
                .collect(Collectors.toMap(UserSummary::id, Function.identity()));
    }

    @Override
    public Optional<AuthenticatedUser> loadByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(user -> new IdentityPrincipal(user.getId(), user.isProfileCompleted()));
    }

    private UserSummary toSummary(User user) {
        return new UserSummary(user.getId(), user.getName(), user.getEmail(),
                s3Uploader.toPublicUrl(user.getProfileImageKey()), user.getBirthDate());
    }
}
