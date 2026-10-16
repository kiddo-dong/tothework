package com.tothework.workforce.shift;

import com.tothework.workforce.config.WorkforceSchema;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

interface ShiftTemplateRepository extends ListCrudRepository<ShiftTemplate, Long> {

    String TABLE = WorkforceSchema.NAME + ".shift_templates";

    List<ShiftTemplate> findAllByStoreIdOrderByDisplayOrderAscIdAsc(Long storeId);

    Optional<ShiftTemplate> findByIdAndStoreId(Long id, Long storeId);

    boolean existsByStoreIdAndName(Long storeId, String name);

    boolean existsByStoreIdAndNameAndIdNot(Long storeId, String name, Long id);

    /** 일괄 등록을 기존 목록 뒤에 이어 붙이기 위한 현재 최대 순번. 템플릿이 없으면 null. */
    @Query("SELECT MAX(display_order) FROM " + TABLE + " WHERE store_id = :storeId")
    Integer findMaxDisplayOrder(Long storeId);

    @Modifying
    @Query("DELETE FROM " + TABLE + " WHERE store_id = :storeId")
    void deleteAllOfStore(Long storeId);
}
