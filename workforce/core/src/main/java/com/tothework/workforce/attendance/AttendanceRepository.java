package com.tothework.workforce.attendance;

import com.tothework.workforce.config.WorkforceSchema;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface AttendanceRepository extends ListCrudRepository<Attendance, Long> {

    Optional<Attendance> findByIdAndStoreId(Long id, Long storeId);

    Optional<Attendance> findFirstByMemberIdAndStatus(Long memberId, AttendanceStatus status);

    List<Attendance> findAllByMemberIdAndWorkDateBetweenOrderByWorkDateDesc(Long memberId, LocalDate from, LocalDate to);

    List<Attendance> findAllByStoreIdAndWorkDateBetweenOrderByWorkDateDesc(Long storeId, LocalDate from, LocalDate to);

    List<Attendance> findAllByMemberIdAndStatusAndWorkDateBetween(Long memberId, AttendanceStatus status,
            LocalDate from, LocalDate to);

    @Modifying
    @Query("DELETE FROM " + WorkforceSchema.NAME + ".attendances WHERE store_id = :storeId")
    void deleteAllOfStore(Long storeId);
}
