package com.tothework.operations.supplier;

import com.tothework.operations.config.OperationsSchema;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

interface SupplierItemRepository extends ListCrudRepository<SupplierItem, Long> {

    /** 이 매장 거래처의 품목. 매장은 거래처가 갖고 있어 같은 스키마 안에서 JOIN한다. */
    @Query("SELECT i.* FROM " + OperationsSchema.NAME + ".supplier_items i JOIN " + OperationsSchema.NAME
            + ".suppliers s ON s.id = i.supplier_id WHERE i.id = :id AND s.store_id = :storeId")
    Optional<SupplierItem> findInStore(Long id, Long storeId);
}
