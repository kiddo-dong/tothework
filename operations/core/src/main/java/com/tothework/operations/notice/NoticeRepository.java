package com.tothework.operations.notice;

import com.tothework.operations.config.OperationsSchema;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

interface NoticeRepository extends ListCrudRepository<Notice, Long> {

    String TABLE = OperationsSchema.NAME + ".notices";

    List<Notice> findAllByStoreIdOrderByCreatedAtDesc(Long storeId);

    Optional<Notice> findByIdAndStoreId(Long id, Long storeId);

    /** 확인 기록은 같은 스키마의 CASCADE로 함께 지워진다. */
    @Modifying
    @Query("DELETE FROM " + TABLE + " WHERE store_id = :storeId")
    void deleteAllOfStore(Long storeId);
}
