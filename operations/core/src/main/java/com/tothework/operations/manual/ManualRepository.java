package com.tothework.operations.manual;

import com.tothework.operations.config.OperationsSchema;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

interface ManualRepository extends ListCrudRepository<Manual, Long> {

    String TABLE = OperationsSchema.NAME + ".manuals";

    Optional<Manual> findByIdAndStoreId(Long id, Long storeId);

    /**
     * 이미지 행은 같은 스키마의 CASCADE로 함께 지워진다. S3의 파일은 지우지 않는다(기존 백엔드도 매장 완전 삭제 때
     * 파일을 남겼다).
     */
    @Modifying
    @Query("DELETE FROM " + TABLE + " WHERE store_id = :storeId")
    void deleteAllOfStore(Long storeId);
}
