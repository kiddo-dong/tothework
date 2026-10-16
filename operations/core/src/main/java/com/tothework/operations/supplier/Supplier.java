package com.tothework.operations.supplier;

import com.tothework.operations.config.OperationsSchema;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 매장 거래처(발주처) 디렉토리. "뭘 어디서 시키는지"를 기록한다. 발주서 작성·재고 추적 같은 발주 관리는 스코프
 * 밖이며, 로그인 계정 정보는 저장하지 않는다.
 *
 * <p>발주 품목은 이 애그리거트의 자식이 아니다. 품목은 itemId로 하나씩 수정되는데, Spring Data JDBC는 애그리거트를
 * 저장할 때 자식 행을 지우고 다시 넣어 id가 바뀌기 때문이다. 품목은 {@link SupplierItem}이 supplierId로 가리킨다.
 */
@Getter
@Table(schema = OperationsSchema.NAME, name = "suppliers")
public class Supplier {

    @Id
    private Long id;
    private final Long storeId;
    private String name;
    /** 자유 카테고리 (식자재/포장재/원두 등 매장이 직접 정의). */
    private String category;
    private String siteUrl;
    private String phone;
    /** 발주 요일, 최소주문금액, 담당자 등 자유 기입. */
    private String memo;
    private int displayOrder;
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;

    @PersistenceCreator
    Supplier(Long id, Long storeId, String name, String category, String siteUrl, String phone, String memo,
            int displayOrder, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.storeId = storeId;
        this.name = name;
        this.category = category;
        this.siteUrl = siteUrl;
        this.phone = phone;
        this.memo = memo;
        this.displayOrder = displayOrder;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    static Supplier register(Long storeId, SupplierRequest request) {
        Supplier supplier = new Supplier(null, storeId, null, null, null, null, null, 0, null, null);
        supplier.update(request);
        return supplier;
    }

    void update(SupplierRequest request) {
        this.name = request.name();
        this.category = request.category();
        this.siteUrl = request.siteUrl();
        this.phone = request.phone();
        this.memo = request.memo();
        this.displayOrder = request.displayOrderOrDefault();
    }
}
