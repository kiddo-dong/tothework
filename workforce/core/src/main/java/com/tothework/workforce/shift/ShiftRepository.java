package com.tothework.workforce.shift;

import com.tothework.workforce.config.WorkforceSchema;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface ShiftRepository extends ListCrudRepository<Shift, Long> {

    List<Shift> findAllByMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(Long memberId, LocalDate from,
            LocalDate to);

    List<Shift> findAllByStoreIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(Long storeId, LocalDate from,
            LocalDate to);

    Optional<Shift> findByIdAndStoreId(Long id, Long storeId);

    @Modifying
    @Query("DELETE FROM " + WorkforceSchema.NAME + ".shifts WHERE store_id = :storeId")
    void deleteAllOfStore(Long storeId);
}
