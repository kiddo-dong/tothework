package com.tothework.store.invite;

import com.tothework.shared.error.InvalidRequestException;
import com.tothework.store.api.MemberRole;
import com.tothework.store.config.StoreSchema;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 초대 코드로 들어온 가입 신청. 매장과 사용자는 id로만 참조한다.
 *
 * <p>낙관적 락 버전을 둔다. 두 관리자가 같은 신청을 거의 동시에 승인과 거절로 처리하면 둘 다 "대기 중"을 보고
 * 통과해, 상태는 거절인데 멤버는 만들어진 식으로 어긋났다. 늦게 저장하는 쪽을 실패시킨다.
 */
@Getter
@Table(schema = StoreSchema.NAME, name = "join_requests")
public class JoinRequest {

    @Id
    private Long id;
    private final Long storeId;
    private final Long userId;
    private JoinRequestStatus status;
    private final LocalDateTime requestedAt;
    private LocalDateTime decidedAt;
    private MemberRole decidedRole;
    @Version
    private Long version;
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;

    @PersistenceCreator
    JoinRequest(Long id, Long storeId, Long userId, JoinRequestStatus status, LocalDateTime requestedAt,
            LocalDateTime decidedAt, MemberRole decidedRole, Long version, LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this.id = id;
        this.storeId = storeId;
        this.userId = userId;
        this.status = status;
        this.requestedAt = requestedAt;
        this.decidedAt = decidedAt;
        this.decidedRole = decidedRole;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    static JoinRequest submit(Long storeId, Long userId) {
        return new JoinRequest(null, storeId, userId, JoinRequestStatus.PENDING, LocalDateTime.now(), null, null, null,
                null, null);
    }

    void approve(MemberRole role) {
        validatePending();
        status = JoinRequestStatus.APPROVED;
        decidedRole = role;
        decidedAt = LocalDateTime.now();
    }

    void reject() {
        validatePending();
        status = JoinRequestStatus.REJECTED;
        decidedAt = LocalDateTime.now();
    }

    private void validatePending() {
        if (status != JoinRequestStatus.PENDING) {
            throw new InvalidRequestException("이미 처리된 가입 신청입니다.");
        }
    }
}
