package com.tothework.workforce.employment;

import com.tothework.workforce.config.WorkforceSchema;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.relational.core.sql.LockMode;
import org.springframework.data.relational.repository.Lock;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

interface MemberEmploymentRepository extends ListCrudRepository<MemberEmployment, Long> {

    /** 행에 쓰기 락을 걸고 읽는다 (SELECT ... FOR UPDATE). 같은 멤버의 스케줄 쓰기를 한 줄로 세운다. */
    @Lock(LockMode.PESSIMISTIC_WRITE)
    Optional<MemberEmployment> findLockedByMemberId(Long memberId);

    /**
     * 기본 고용 조건(시급 0원, 공제 없음)을 만든다. 이미 있으면 값은 그대로 둔다.
     *
     * <p>INSERT IGNORE가 아니라 ON DUPLICATE KEY UPDATE인 이유: MySQL은 INSERT IGNORE가 중복 키를 만나면 그 행에
     * 공유 락을 건다. 여러 트랜잭션이 공유 락을 함께 쥔 채 곧이어 쓰기 락을 요청하면 서로를 기다려 교착 상태가
     * 된다(동시성 테스트에서 실제로 났다). ON DUPLICATE KEY UPDATE는 중복 행에 배타 락을 걸어 줄이 바로 선다.
     */
    default void insertDefaultIfAbsent(Long memberId, Long storeId) {
        insertDefaultIfAbsent(memberId, storeId, LocalDateTime.now());
    }

    /** 시각은 앱이 넘긴다. SQL의 NOW()는 DB 세션 시간대를 따라, 앱이 저장하는 다른 시각과 어긋날 수 있다. */
    @Modifying
    @Query("INSERT INTO " + WorkforceSchema.NAME + ".employment_terms"
            + " (member_id, store_id, hourly_wage, tax_mode, version, created_at, updated_at)"
            + " VALUES (:memberId, :storeId, 0, 'NONE', 0, :now, :now)"
            + " ON DUPLICATE KEY UPDATE member_id = member_id")
    void insertDefaultIfAbsent(Long memberId, Long storeId, LocalDateTime now);

    /** 근무 가능 요일은 같은 스키마의 CASCADE로 함께 지워진다. */
    @Modifying
    @Query("DELETE FROM " + WorkforceSchema.NAME + ".employment_terms WHERE store_id = :storeId")
    void deleteAllOfStore(Long storeId);
}
