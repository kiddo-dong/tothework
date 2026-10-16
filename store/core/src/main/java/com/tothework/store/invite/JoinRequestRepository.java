package com.tothework.store.invite;

import com.tothework.store.config.StoreSchema;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

interface JoinRequestRepository extends ListCrudRepository<JoinRequest, Long> {

    Optional<JoinRequest> findByIdAndStoreId(Long id, Long storeId);

    boolean existsByStoreIdAndUserIdAndStatus(Long storeId, Long userId, JoinRequestStatus status);

    List<JoinRequest> findAllByStoreIdAndStatusOrderByRequestedAtAsc(Long storeId, JoinRequestStatus status);

    List<JoinRequest> findAllByUserIdOrderByRequestedAtDesc(Long userId);

    long countByStoreIdAndStatus(Long storeId, JoinRequestStatus status);

    @Modifying
    @Query("DELETE FROM " + StoreSchema.NAME + ".join_requests WHERE user_id = :userId")
    void deleteAllOfUser(Long userId);
}
