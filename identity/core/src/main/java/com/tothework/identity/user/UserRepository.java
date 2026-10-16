package com.tothework.identity.user;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

public interface UserRepository extends ListCrudRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);

    boolean existsByEmail(String email);

    boolean existsByProviderAndProviderId(AuthProvider provider, String providerId);

    boolean existsByPhone(String phone);

    boolean existsByPhoneAndIdNot(String phone, Long id);

    /** 인증을 끝내지 않은 채 기간이 지난 로컬 계정. 자동 정리 대상이다. */
    List<User> findByProviderAndEmailVerifiedFalseAndCreatedAtBefore(AuthProvider provider, LocalDateTime threshold);
}
