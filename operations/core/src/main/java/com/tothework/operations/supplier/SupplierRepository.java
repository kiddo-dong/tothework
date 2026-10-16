package com.tothework.operations.supplier;

import com.tothework.operations.config.OperationsSchema;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

interface SupplierRepository extends ListCrudRepository<Supplier, Long> {

    String TABLE = OperationsSchema.NAME + ".suppliers";

    List<Supplier> findAllByStoreIdOrderByCategoryAscDisplayOrderAscIdAsc(Long storeId);

    Optional<Supplier> findByIdAndStoreId(Long id, Long storeId);

    /** 품목과 요일별 발주량은 같은 스키마의 CASCADE로 함께 지워진다. */
    @Modifying
    @Query("DELETE FROM " + TABLE + " WHERE store_id = :storeId")
    void deleteAllOfStore(Long storeId);
}
