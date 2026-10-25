package com.tothework.workforce.leave;

import com.tothework.workforce.config.WorkforceSchema;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface LeaveUsageRepository extends ListCrudRepository<LeaveUsage, Long> {

    List<LeaveUsage> findAllByMemberIdOrderByLeaveDateDesc(Long memberId);

    long countByMemberId(Long memberId);

    boolean existsByMemberIdAndLeaveDate(Long memberId, LocalDate leaveDate);

    Optional<LeaveUsage> findByIdAndStoreId(Long id, Long storeId);

    List<LeaveUsage> findAllByMemberIdAndLeaveDateBetween(Long memberId, LocalDate from, LocalDate to);

    List<LeaveUsage> findAllByStoreIdAndLeaveDateBetween(Long storeId, LocalDate from, LocalDate to);

    @Modifying
    @Query("DELETE FROM " + WorkforceSchema.NAME + ".leave_usages WHERE store_id = :storeId")
    void deleteAllOfStore(Long storeId);
}
