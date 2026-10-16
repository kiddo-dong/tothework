package com.tothework.store.store;

import com.tothework.store.config.StoreSchema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface StoreRepository extends ListCrudRepository<Store, Long> {

    String TABLE = StoreSchema.NAME + ".stores";

    /**
     * 삭제되지 않은 매장. 매장을 찾을 때는 반드시 이 메서드를 쓴다. 기본 {@code findById}는 삭제된 매장도
     * 돌려준다(Spring Data JDBC에는 JPA의 {@code @SQLRestriction}이 없다).
     */
    default Optional<Store> findActive(Long storeId) {
        return findById(storeId).filter(store -> !store.isDeleted());
    }

    default boolean existsActive(Long storeId) {
        return findActive(storeId).isPresent();
    }

    Optional<Store> findByInviteCode(String inviteCode);

    boolean existsByInviteCode(String inviteCode);

    @Query("SELECT id FROM " + TABLE + " WHERE deleted_at IS NOT NULL AND deleted_at < :cutoff")
    List<Long> findIdsSoftDeletedBefore(LocalDateTime cutoff);

    /** 행을 지운다. 영업시간·온보딩·소속·가입 신청은 같은 스키마의 CASCADE로 함께 지워진다. */
    @Modifying
    @Query("DELETE FROM " + TABLE + " WHERE id = :id")
    void purgeById(Long id);
}
