package com.tothework.operations.manual;

import com.tothework.operations.config.OperationsSchema;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 매장 매뉴얼 (레시피, 기기 사용법 등 영구적인 지식). 카테고리는 매장이 자유롭게 정한다.
 *
 * <p>이미지는 늘 통째로 교체되므로 자식 컬렉션이다. 목록 화면은 이미지를 쓰지 않으니 {@link ManualSummaries}가 루트만
 * 읽는다(애그리거트로 읽으면 매뉴얼마다 이미지 조회가 따라붙는다).
 *
 * @param authorId 작성한 멤버의 memberId
 */
@Getter
@Table(schema = OperationsSchema.NAME, name = "manuals")
public class Manual {

    @Id
    private Long id;
    private final Long storeId;
    private final Long authorId;
    private String category;
    private String title;
    private String content;
    private int displayOrder;
    @MappedCollection(idColumn = "manual_id", keyColumn = "sort_order")
    private List<ManualImage> images = new ArrayList<>();
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;

    @PersistenceCreator
    Manual(Long id, Long storeId, Long authorId, String category, String title, String content, int displayOrder,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.storeId = storeId;
        this.authorId = authorId;
        this.category = category;
        this.title = title;
        this.content = content;
        this.displayOrder = displayOrder;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    static Manual write(Long storeId, Long authorId, String category, String title, String content, int displayOrder,
            List<String> imageKeys) {
        Manual manual = new Manual(null, storeId, authorId, category, title, content, displayOrder, null, null);
        manual.replaceImages(imageKeys);
        return manual;
    }

    void update(String category, String title, String content, int displayOrder, List<String> imageKeys) {
        this.category = category;
        this.title = title;
        this.content = content;
        this.displayOrder = displayOrder;
        replaceImages(imageKeys);
    }

    List<String> imageKeys() {
        return images.stream().map(ManualImage::imageKey).toList();
    }

    private void replaceImages(List<String> imageKeys) {
        this.images = new ArrayList<>(imageKeys.stream().map(ManualImage::new).toList());
    }
}
