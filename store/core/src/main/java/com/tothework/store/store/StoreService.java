package com.tothework.store.store;

import com.tothework.shared.error.InvalidRequestException;
import com.tothework.shared.error.NotFoundException;
import com.tothework.shared.storage.S3Uploader;
import com.tothework.store.api.MemberRole;
import com.tothework.store.api.MemberStatus;
import com.tothework.store.api.Membership;
import com.tothework.store.api.StoreAccess;
import com.tothework.store.member.MemberAdmission;
import com.tothework.store.member.StoreMember;
import com.tothework.store.member.StoreMemberRepository;
import java.security.SecureRandom;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/** 매장 개설·조회·수정·삭제·초대 코드·소유권 이전·대표 사진. 매장을 바꾼 곳마다 저장을 직접 부른다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreService {

    private static final int INVITE_CODE_LENGTH = 6;
    private static final String INVITE_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ1234567890";
    private static final String STORE_IMAGE_DIRECTORY = "store-images";

    private final StoreRepository storeRepository;
    private final StoreMemberRepository memberRepository;
    private final MemberAdmission memberAdmission;
    private final StoreAccess storeAccess;
    private final S3Uploader s3Uploader;
    private final PlatformTransactionManager transactionManager;
    private final SecureRandom secureRandom = new SecureRandom();

    /** 개설자는 OWNER로 합류한다. 고용 조건(시급 0 등)은 합류 이벤트를 받은 workforce가 만든다. */
    @Transactional
    public StoreResponse createStore(Long userId, CreateStoreRequest request) {
        Store store = storeRepository.save(Store.open(request.name(), request.address(),
                request.businessRegistrationNumber(), request.category(), toBusinessHours(request.businessHours()),
                generateUniqueInviteCode(), request.breakPolicy(), request.smallBusiness(), request.payday()));
        memberAdmission.admit(store.getId(), userId, MemberRole.OWNER);
        return toResponse(store);
    }

    public InviteCodeResponse getInviteCode(Long storeId, Long userId) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        return new InviteCodeResponse(activeStore(storeId).getInviteCode());
    }

    @Transactional
    public InviteCodeResponse regenerateInviteCode(Long storeId, Long userId) {
        storeAccess.requireOwner(storeId, userId);
        Store store = activeStore(storeId);
        store.changeInviteCode(generateUniqueInviteCode());
        return new InviteCodeResponse(storeRepository.save(store).getInviteCode());
    }

    public List<MyStoreResponse> getMyStores(Long userId) {
        List<StoreMember> memberships = memberRepository.findActiveOfUser(userId);
        Map<Long, Store> stores = storeRepository.findAllById(memberships.stream().map(StoreMember::getStoreId).toList())
                .stream().collect(Collectors.toMap(Store::getId, store -> store));
        return memberships.stream()
                .map(member -> new MyStoreResponse(toResponse(stores.get(member.getStoreId())), member.getId(),
                        member.getRole()))
                .toList();
    }

    public StoreResponse getStore(Long storeId, Long userId) {
        storeAccess.requireMember(storeId, userId);
        return toResponse(activeStore(storeId));
    }

    @Transactional
    public StoreResponse updateStore(Long storeId, Long userId, UpdateStoreRequest request) {
        storeAccess.requireOwner(storeId, userId);
        Store store = activeStore(storeId);
        store.update(request.name(), request.address(), request.businessRegistrationNumber(), request.category(),
                toBusinessHours(request.businessHours()), request.breakPolicy(), request.smallBusiness(),
                request.payday());
        return toResponse(storeRepository.save(store));
    }

    /**
     * 매장 양도: 대상 멤버를 OWNER로 올리고 기존 오너는 MANAGER로 내린다 (매장당 OWNER 1명 유지). 실수 방지를 위해
     * 매장 이름 확인이 필요하다.
     */
    @Transactional
    public void transferOwnership(Long storeId, Long userId, TransferOwnershipRequest request) {
        Membership ownerMembership = storeAccess.requireOwner(storeId, userId);
        Store store = activeStore(storeId);
        if (!store.getName().equals(request.confirmName())) {
            throw new InvalidRequestException(
                    "매장 이름이 일치하지 않습니다. 이전하려면 매장 이름(" + store.getName() + ")을 정확히 입력해 주세요.");
        }
        StoreMember target = memberRepository.findById(request.targetMemberId())
                .filter(member -> member.getStoreId().equals(storeId))
                .orElseThrow(() -> new NotFoundException("멤버를 찾을 수 없습니다."));
        if (target.getId().equals(ownerMembership.memberId())) {
            throw new InvalidRequestException("자기 자신에게는 소유권을 이전할 수 없습니다.");
        }
        if (target.getStatus() != MemberStatus.ACTIVE) {
            throw new InvalidRequestException("퇴사 처리된 멤버에게는 소유권을 이전할 수 없습니다.");
        }
        StoreMember owner = memberRepository.findById(ownerMembership.memberId()).orElseThrow();
        target.changeRole(MemberRole.OWNER);
        owner.changeRole(MemberRole.MANAGER);
        memberRepository.saveAll(List.of(target, owner));
    }

    /**
     * 매장을 소프트 삭제한다. 즉시 지우면 근태·급여 기록까지 그 자리에서 사라져 되돌릴 방법이 없다. 유예기간(90일)
     * 동안은 모든 API가 404를 내고, 그 뒤 {@code StorePurgeJob}이 지우며 다른 서비스에 알린다.
     */
    @Transactional
    public void deleteStore(Long storeId, Long userId, String confirmName) {
        storeAccess.requireOwner(storeId, userId);
        Store store = activeStore(storeId);
        if (!store.getName().equals(confirmName)) {
            throw new InvalidRequestException(
                    "매장 이름이 일치하지 않습니다. 삭제하려면 매장 이름(" + store.getName() + ")을 정확히 입력해 주세요.");
        }
        store.softDelete();
        storeRepository.save(store);
    }

    /**
     * 대표 사진 교체. 새 사진을 먼저 올리고 DB가 그쪽을 가리키게 한 뒤, 커밋이 끝나면 옛 파일을 지운다. S3 호출은
     * 트랜잭션 밖에서 한다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public StoreResponse updateProfileImage(Long storeId, Long userId, MultipartFile image) {
        storeAccess.requireOwner(storeId, userId);
        String uploadedKey = s3Uploader.upload(image, STORE_IMAGE_DIRECTORY + "/" + storeId);
        return replaceProfileImage(storeId, store -> store.changeProfileImageKey(uploadedKey));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public StoreResponse deleteProfileImage(Long storeId, Long userId) {
        storeAccess.requireOwner(storeId, userId);
        return replaceProfileImage(storeId, store -> store.changeProfileImageKey(null));
    }

    private StoreResponse replaceProfileImage(Long storeId, Consumer<Store> change) {
        String[] previousKey = new String[1];
        StoreResponse response = new TransactionTemplate(transactionManager).execute(status -> {
            Store store = activeStore(storeId);
            previousKey[0] = store.getProfileImageKey();
            change.accept(store);
            return toResponse(storeRepository.save(store));
        });
        s3Uploader.delete(previousKey[0]);
        return response;
    }

    private StoreResponse toResponse(Store store) {
        return StoreResponse.from(store, s3Uploader.toPublicUrl(store.getProfileImageKey()));
    }

    private Store activeStore(Long storeId) {
        return storeRepository.findActive(storeId).orElseThrow(() -> new NotFoundException("매장을 찾을 수 없습니다."));
    }

    private Map<DayOfWeek, BusinessHour> toBusinessHours(Map<DayOfWeek, BusinessHourRequest> businessHours) {
        if (businessHours == null) {
            return Map.of();
        }
        return businessHours.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                entry -> new BusinessHour(entry.getValue().openTime(), entry.getValue().closeTime(),
                        entry.getValue().closed())));
    }

    private String generateUniqueInviteCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(INVITE_CODE_LENGTH);
            for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
                sb.append(INVITE_CODE_CHARS.charAt(secureRandom.nextInt(INVITE_CODE_CHARS.length())));
            }
            code = sb.toString();
        } while (storeRepository.existsByInviteCode(code));
        return code;
    }
}
