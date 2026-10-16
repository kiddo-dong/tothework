package com.tothework.operations.handover;

import com.tothework.operations.config.OperationsSchema;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

interface HandoverNoteRepository extends ListCrudRepository<HandoverNote, Long> {

    String TABLE = OperationsSchema.NAME + ".handover_notes";

    List<HandoverNote> findAllByStoreIdAndWorkDateBetweenOrderByCreatedAtDesc(Long storeId, LocalDate from,
            LocalDate to);

    /** 홈 화면의 최근 노트. 앞의 몇 개만 쓰므로 DB에서 자른다. */
    @Query("SELECT * FROM " + TABLE + " WHERE store_id = :storeId AND work_date BETWEEN :from AND :to"
            + " ORDER BY created_at DESC LIMIT :limit")
    List<HandoverNote> findRecent(Long storeId, LocalDate from, LocalDate to, int limit);

    Optional<HandoverNote> findByIdAndStoreId(Long id, Long storeId);

    @Modifying
    @Query("DELETE FROM " + TABLE + " WHERE store_id = :storeId")
    void deleteAllOfStore(Long storeId);
}
