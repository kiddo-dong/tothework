package com.tothework.store.invite;

import com.tothework.identity.api.UserDirectory;
import com.tothework.identity.api.UserSummary;
import com.tothework.shared.error.ConflictException;
import com.tothework.shared.error.ForbiddenException;
import com.tothework.shared.error.InvalidRequestException;
import com.tothework.shared.error.NotFoundException;
import com.tothework.shared.storage.S3Uploader;
import com.tothework.store.api.JoinRequests;
import com.tothework.store.api.MemberRole;
import com.tothework.store.api.MemberStatus;
import com.tothework.store.api.StoreAccess;
import com.tothework.store.member.MemberAdmission;
import com.tothework.store.member.StoreMemberRepository;
import com.tothework.store.store.Store;
import com.tothework.store.store.StoreRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 초대 코드 가입 신청·취소·승인·거절. 응답의 사람·매장 정보는 목록마다 한 번에 조회해 N+1을 피한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JoinRequestService implements JoinRequests {

    private final JoinRequestRepository joinRequestRepository;
    private final StoreRepository storeRepository;
    private final StoreMemberRepository memberRepository;
    private final MemberAdmission memberAdmission;
    private final StoreAccess storeAccess;
    private final UserDirectory userDirectory;
    private final S3Uploader s3Uploader;

    @Transactional
    public JoinRequestResponse requestJoin(Long userId, String code) {
        Store store = storeRepository.findByInviteCode(code)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new NotFoundException("유효하지 않은 초대코드입니다."));
        boolean alreadyMember = memberRepository.findByStoreIdAndUserId(store.getId(), userId)
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .isPresent();
        if (alreadyMember) {
            throw new ConflictException("이미 해당 매장의 멤버입니다.");
        }
        if (joinRequestRepository.existsByStoreIdAndUserIdAndStatus(store.getId(), userId, JoinRequestStatus.PENDING)) {
            throw new ConflictException("이미 가입 신청 중입니다.");
        }
        return toResponses(List.of(joinRequestRepository.save(JoinRequest.submit(store.getId(), userId)))).getFirst();
    }

    public List<JoinRequestResponse> getMyRequests(Long userId) {
        return toResponses(joinRequestRepository.findAllByUserIdOrderByRequestedAtDesc(userId));
    }

    @Transactional
    public void cancelMyRequest(Long requestId, Long userId) {
        JoinRequest joinRequest = joinRequestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("가입 신청을 찾을 수 없습니다."));
        if (!joinRequest.getUserId().equals(userId)) {
            throw new ForbiddenException("본인의 가입 신청만 취소할 수 있습니다.");
        }
        if (joinRequest.getStatus() != JoinRequestStatus.PENDING) {
            throw new InvalidRequestException("대기 중인 신청만 취소할 수 있습니다.");
        }
        joinRequestRepository.delete(joinRequest);
    }

    public List<JoinRequestResponse> getPendingRequests(Long storeId, Long userId) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        return toResponses(joinRequestRepository.findAllByStoreIdAndStatusOrderByRequestedAtAsc(storeId,
                JoinRequestStatus.PENDING));
    }

    /** 승인: 퇴사했던 사람의 재합류(기존 행 재활성화)는 {@link MemberAdmission}이 판단한다. */
    @Transactional
    public JoinRequestResponse approve(Long storeId, Long requestId, Long userId, ApproveJoinRequestRequest request) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        if (request.role() == MemberRole.OWNER) {
            throw new InvalidRequestException("OWNER 역할로는 승인할 수 없습니다.");
        }
        JoinRequest joinRequest = getJoinRequest(storeId, requestId);
        joinRequest.approve(request.role());
        joinRequest = joinRequestRepository.save(joinRequest);
        memberAdmission.admit(storeId, joinRequest.getUserId(), request.role());
        return toResponses(List.of(joinRequest)).getFirst();
    }

    @Transactional
    public JoinRequestResponse reject(Long storeId, Long requestId, Long userId) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        JoinRequest joinRequest = getJoinRequest(storeId, requestId);
        joinRequest.reject();
        return toResponses(List.of(joinRequestRepository.save(joinRequest))).getFirst();
    }

    @Override
    public long countPending(Long storeId) {
        return joinRequestRepository.countByStoreIdAndStatus(storeId, JoinRequestStatus.PENDING);
    }

    private JoinRequest getJoinRequest(Long storeId, Long requestId) {
        return joinRequestRepository.findByIdAndStoreId(requestId, storeId)
                .orElseThrow(() -> new NotFoundException("가입 신청을 찾을 수 없습니다."));
    }

    private List<JoinRequestResponse> toResponses(List<JoinRequest> requests) {
        Map<Long, UserSummary> users = userDirectory.findAll(requests.stream().map(JoinRequest::getUserId).toList());
        Map<Long, Store> stores = storeRepository.findAllById(requests.stream().map(JoinRequest::getStoreId).toList())
                .stream().collect(Collectors.toMap(Store::getId, Function.identity()));
        return requests.stream().map(request -> {
            UserSummary user = users.get(request.getUserId());
            Store store = stores.get(request.getStoreId());
            return new JoinRequestResponse(request.getId(), request.getStoreId(),
                    store == null ? null : store.getName(),
                    store == null ? null : s3Uploader.toPublicUrl(store.getProfileImageKey()),
                    request.getUserId(), user == null ? null : user.name(), user == null ? null : user.email(),
                    user == null ? null : user.profileImageUrl(), request.getStatus(), request.getRequestedAt(),
                    request.getDecidedAt(), request.getDecidedRole());
        }).toList();
    }
}
