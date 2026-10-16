package com.tothework.store.member;

import com.tothework.store.config.StoreSchema;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface StoreMemberRepository extends ListCrudRepository<StoreMember, Long> {

    String MEMBERS = StoreSchema.NAME + ".store_members";
    String STORES = StoreSchema.NAME + ".stores";

    /**
     * 삭제되지 않은 매장에서의 소속. 권한 확인이 쓰는 조회라, 여기서 삭제된 매장을 거르면 그 뒤의 모든 기능이 함께
     * 막힌다 (매장을 지웠는데 공지·체크리스트가 계속 등록되던 문제가 이 조회에서 생겼었다).
     */
    @Query("SELECT m.* FROM " + MEMBERS + " m JOIN " + STORES + " s ON s.id = m.store_id"
            + " WHERE m.store_id = :storeId AND m.user_id = :userId AND s.deleted_at IS NULL")
    Optional<StoreMember> findInActiveStore(Long storeId, Long userId);

    /** 매장 상태와 상관없이 그 매장의 그 사람 행. 재합류 판단에 쓴다. */
    Optional<StoreMember> findByStoreIdAndUserId(Long storeId, Long userId);

    List<StoreMember> findAllByStoreIdOrderByIdAsc(Long storeId);

    /** 내 매장 목록: 재직 중이고 매장이 살아 있는 소속. */
    @Query("SELECT m.* FROM " + MEMBERS + " m JOIN " + STORES + " s ON s.id = m.store_id"
            + " WHERE m.user_id = :userId AND m.status = 'ACTIVE' AND s.deleted_at IS NULL ORDER BY m.id")
    List<StoreMember> findActiveOfUser(Long userId);

    @Query("SELECT COUNT(*) > 0 FROM " + MEMBERS + " m JOIN " + STORES + " s ON s.id = m.store_id"
            + " WHERE m.user_id = :userId AND m.status = 'ACTIVE' AND s.deleted_at IS NULL")
    boolean hasActiveMembership(Long userId);
}
