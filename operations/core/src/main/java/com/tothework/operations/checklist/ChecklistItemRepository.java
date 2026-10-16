package com.tothework.operations.checklist;

import com.tothework.operations.config.OperationsSchema;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

interface ChecklistItemRepository extends ListCrudRepository<ChecklistItem, Long> {

    String TABLE = OperationsSchema.NAME + ".checklist_items";

    List<ChecklistItem> findAllByStoreIdOrderByTypeAscDisplayOrderAscIdAsc(Long storeId);

    Optional<ChecklistItem> findByIdAndStoreId(Long id, Long storeId);

    /**
     * 그 매장·구분에서 지금까지 쓴 가장 큰 순번. 일괄 등록이 0부터 다시 매기면 이미 있는 항목과 순번이 겹쳐 순서가
     * 섞이므로, 뒤에 이어 붙이려고 조회한다. 항목이 없으면 null.
     */
    @Query("SELECT MAX(display_order) FROM " + TABLE + " WHERE store_id = :storeId AND type = :type")
    Integer findMaxDisplayOrder(Long storeId, String type);

    /** 체크 기록은 같은 스키마의 CASCADE로 함께 지워진다. */
    @Modifying
    @Query("DELETE FROM " + TABLE + " WHERE store_id = :storeId")
    void deleteAllOfStore(Long storeId);
}
