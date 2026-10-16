package com.tothework.workforce.employment;

import com.tothework.workforce.config.WorkforceSchema;
import java.util.List;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

interface MemberEmploymentRepository extends ListCrudRepository<MemberEmployment, Long> {

    List<MemberEmployment> findAllByStoreId(Long storeId);

    /** 근무 가능 요일은 같은 스키마의 CASCADE로 함께 지워진다. */
    @Modifying
    @Query("DELETE FROM " + WorkforceSchema.NAME + ".employment_terms WHERE store_id = :storeId")
    void deleteAllOfStore(Long storeId);
}
