package com.tothework.store.member;

import com.tothework.shared.error.InvalidRequestException;
import com.tothework.store.api.MemberRole;
import com.tothework.store.api.MemberStatus;
import com.tothework.store.api.Membership;
import com.tothework.store.config.StoreSchema;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 매장 소속 (애그리거트 루트). 매장과 사용자는 id로만 참조한다.
 *
 * <p>기존에는 여기에 시급·공제 방식·주휴일·근무 가능 요일까지 있었다. 그 값들은 workforce만 읽는 고용 조건이라
 * workforce의 {@code EmploymentTerms}로 옮겼고, 같은 id로 연결된다.
 */
@Getter
@Table(schema = StoreSchema.NAME, name = "store_members")
public class StoreMember {

    public static final int TITLE_MAX_LENGTH = 20;

    @Id
    private Long id;
    private final Long storeId;
    private final Long userId;
    private MemberRole role;
    /** 매장이 부르는 직함. 권한과는 무관하다. */
    private String title;
    private MemberStatus status;
    private LocalDateTime joinedAt;
    private LocalDateTime resignedAt;
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;

    @PersistenceCreator
    StoreMember(Long id, Long storeId, Long userId, MemberRole role, String title, MemberStatus status,
            LocalDateTime joinedAt, LocalDateTime resignedAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.storeId = storeId;
        this.userId = userId;
        this.role = role;
        this.title = title;
        this.status = status;
        this.joinedAt = joinedAt;
        this.resignedAt = resignedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static StoreMember join(Long storeId, Long userId, MemberRole role) {
        return new StoreMember(null, storeId, userId, role, null, MemberStatus.ACTIVE, LocalDateTime.now(), null,
                null, null);
    }

    public Membership toMembership() {
        return new Membership(id, storeId, userId, role, status, title, joinedAt, resignedAt);
    }

    public boolean isOwnerOrManager() {
        return role == MemberRole.OWNER || role == MemberRole.MANAGER;
    }

    public void changeRole(MemberRole role) {
        this.role = role;
    }

    /** 빈 문자열은 "직함 없음"으로 본다 — 화면에서 지웠을 때 공백이 남지 않게 한다. */
    public void changeTitle(String title) {
        this.title = title == null || title.isBlank() ? null : title.strip();
    }

    public void changeStatus(MemberStatus status) {
        this.status = status;
        this.resignedAt = status == MemberStatus.INACTIVE ? LocalDateTime.now() : null;
    }

    /** 퇴사 처리: 근무 이력을 보존하기 위해 행을 지우지 않고 비활성화한다. */
    public void resign() {
        if (status == MemberStatus.INACTIVE) {
            throw new InvalidRequestException("이미 퇴사 처리된 멤버입니다.");
        }
        changeStatus(MemberStatus.INACTIVE);
    }

    /** 재입사: 퇴사했던 멤버를 새 역할로 다시 활성화한다. 같은 행이라 근무 이력이 이어진다. */
    public void rejoin(MemberRole role) {
        this.role = role;
        this.joinedAt = LocalDateTime.now();
        changeStatus(MemberStatus.ACTIVE);
    }
}
