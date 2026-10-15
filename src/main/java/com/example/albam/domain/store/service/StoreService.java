package com.example.albam.domain.store.service;

import com.example.albam.domain.store.dto.BusinessHourRequest;
import com.example.albam.domain.store.dto.CreateStoreRequest;
import com.example.albam.domain.store.dto.InviteCodeResponse;
import com.example.albam.domain.store.dto.MyStoreResponse;
import com.example.albam.domain.store.dto.StoreResponse;
import com.example.albam.domain.store.dto.TransferOwnershipRequest;
import com.example.albam.domain.store.dto.UpdateStoreRequest;
import com.example.albam.domain.store.entity.BusinessHour;
import com.example.albam.domain.store.entity.Store;
import com.example.albam.domain.store.repository.StoreRepository;
import com.example.albam.domain.storemember.entity.MemberRole;
import com.example.albam.domain.storemember.entity.MemberStatus;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.repository.StoreMemberRepository;
import com.example.albam.domain.storemember.service.StoreAuthorizationService;
import com.example.albam.domain.user.entity.User;
import com.example.albam.domain.user.repository.UserRepository;
import com.example.albam.global.exception.InvalidRequestException;
import com.example.albam.global.file.S3Uploader;
import com.example.albam.global.exception.NotFoundException;
import java.security.SecureRandom;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreService {

    private static final int OWNER_DEFAULT_WAGE = 0;
    private static final int INVITE_CODE_LENGTH = 6;
    private static final String STORE_IMAGE_DIRECTORY = "store-images";
    private static final String INVITE_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ1234567890"; // 초대 코드 생성시 이 문자열에서 랜덤으로 뽑아 조합

    private final StoreRepository storeRepository;
    private final StoreMemberRepository storeMemberRepository;
    private final UserRepository userRepository;
    private final StoreAuthorizationService storeAuthorizationService;
    private final S3Uploader s3Uploader;
    private final PlatformTransactionManager transactionManager;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public StoreResponse createStore(Long userId, CreateStoreRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));
        Store store = storeRepository.save(
                new Store(request.name(), request.address(), request.businessRegistrationNumber(),
                        request.category(), toBusinessHours(request.businessHours()), generateUniqueInviteCode(),
                        request.breakPolicy(), request.smallBusiness(), request.payday()));
        storeMemberRepository.save(new StoreMember(store, user, MemberRole.OWNER, OWNER_DEFAULT_WAGE));
        return toResponse(store);
    }

    public InviteCodeResponse getInviteCode(Long storeId, Long userId) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        return new InviteCodeResponse(getStoreEntity(storeId).getInviteCode());
    }

    @Transactional
    public InviteCodeResponse regenerateInviteCode(Long storeId, Long userId) {
        storeAuthorizationService.requireOwner(storeId, userId);
        Store store = getStoreEntity(storeId);
        store.changeInviteCode(generateUniqueInviteCode());
        return new InviteCodeResponse(store.getInviteCode());
    }

    public List<MyStoreResponse> getMyStores(Long userId) {
        return storeMemberRepository.findAllByUserIdAndStatus(userId, MemberStatus.ACTIVE).stream()
                .map(member -> MyStoreResponse.from(member,
                        s3Uploader.toPublicUrl(member.getStore().getProfileImageKey())))
                .toList();
    }

    public StoreResponse getStore(Long storeId, Long userId) {
        storeAuthorizationService.requireMember(storeId, userId);
        return toResponse(getStoreEntity(storeId));
    }

    @Transactional
    public StoreResponse updateStore(Long storeId, Long userId, UpdateStoreRequest request) {
        storeAuthorizationService.requireOwner(storeId, userId);
        Store store = getStoreEntity(storeId);
        store.update(request.name(), request.address(), request.businessRegistrationNumber(),
                request.category(), toBusinessHours(request.businessHours()), request.breakPolicy(),
                request.smallBusiness(), request.payday());
        return toResponse(store);
    }

    /**
     * 매장 양도: 대상 멤버를 OWNER로 올리고 기존 오너는 MANAGER로 내린다 (매장당 OWNER 1명 유지).
     * 기존 오너는 이후 원하면 스스로 매장을 나갈 수 있다. 실수 방지를 위해 매장 이름 확인이 필요하다.
     */
    @Transactional
    public void transferOwnership(Long storeId, Long userId, TransferOwnershipRequest request) {
        StoreMember owner = storeAuthorizationService.requireOwner(storeId, userId);
        Store store = getStoreEntity(storeId);
        if (!store.getName().equals(request.confirmName())) {
            throw new InvalidRequestException(
                    "매장 이름이 일치하지 않습니다. 이전하려면 매장 이름(" + store.getName() + ")을 정확히 입력해 주세요.");
        }
        StoreMember target = storeMemberRepository.findById(request.targetMemberId())
                .orElseThrow(() -> new NotFoundException("멤버를 찾을 수 없습니다."));
        if (!target.getStore().getId().equals(storeId)) {
            throw new NotFoundException("멤버를 찾을 수 없습니다.");
        }
        if (target.getId().equals(owner.getId())) {
            throw new InvalidRequestException("자기 자신에게는 소유권을 이전할 수 없습니다.");
        }
        if (target.getStatus() != MemberStatus.ACTIVE) {
            throw new InvalidRequestException("퇴사 처리된 멤버에게는 소유권을 이전할 수 없습니다.");
        }
        target.changeRole(MemberRole.OWNER);
        owner.changeRole(MemberRole.MANAGER);
    }

    /**
     * 매장을 소프트 삭제한다. 실수 방지를 위해 매장 이름을 정확히 입력해야 실행된다.
     *
     * <p>즉시 완전히 지우지는 않는다 — 근태·급여 기록까지 그 자리에서 함께 사라지면 되돌릴 방법이
     * 없기 때문이다. 유예기간(90일) 동안은 화면에서 안 보이지만 DB에는 남아있고, 그 뒤
     * {@code StorePurgeService}가 실제로 지운다.
     */
    @Transactional
    public void deleteStore(Long storeId, Long userId, String confirmName) {
        storeAuthorizationService.requireOwner(storeId, userId);
        Store store = getStoreEntity(storeId);
        if (!store.getName().equals(confirmName)) {
            throw new InvalidRequestException(
                    "매장 이름이 일치하지 않습니다. 삭제하려면 매장 이름(" + store.getName() + ")을 정확히 입력해 주세요.");
        }
        store.softDelete();
    }

    /**
     * 매장 대표 사진 교체. 사용자 프로필 사진과 같은 방식이다 — 새 사진을 먼저 올리고 DB가 그쪽을
     * 가리키게 한 뒤, 커밋이 끝난 다음에 옛 파일을 지운다. 순서를 바꾸면 저장에 실패했을 때 이미
     * 지워진 사진을 가리키게 된다.
     *
     * <p>S3 업로드는 트랜잭션 밖에서 한다. 파일을 주고받는 동안 DB 커넥션을 붙잡지 않기 위함이다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public StoreResponse updateProfileImage(Long storeId, Long userId, MultipartFile image) {
        storeAuthorizationService.requireOwner(storeId, userId);
        String uploadedKey = s3Uploader.upload(image, STORE_IMAGE_DIRECTORY + "/" + storeId);
        return replaceProfileImage(storeId, uploadedKey);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public StoreResponse deleteProfileImage(Long storeId, Long userId) {
        storeAuthorizationService.requireOwner(storeId, userId);
        return replaceProfileImage(storeId, null);
    }

    private StoreResponse replaceProfileImage(Long storeId, String newKey) {
        String[] previousKeyHolder = new String[1];
        StoreResponse response = new TransactionTemplate(transactionManager).execute(status -> {
            Store store = getStoreEntity(storeId);
            previousKeyHolder[0] = store.getProfileImageKey();
            store.changeProfileImageKey(newKey);
            return toResponse(store);
        });
        if (previousKeyHolder[0] != null) {
            s3Uploader.delete(previousKeyHolder[0]);
        }
        return response;
    }

    /** 엔티티에는 S3 key만 있으므로, 응답을 만들 때 공개 URL로 조립한다. */
    private StoreResponse toResponse(Store store) {
        return StoreResponse.from(store, s3Uploader.toPublicUrl(store.getProfileImageKey()));
    }

    private Store getStoreEntity(Long storeId) {
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new NotFoundException("매장을 찾을 수 없습니다."));
    }

    private Map<DayOfWeek, BusinessHour> toBusinessHours(Map<DayOfWeek, BusinessHourRequest> businessHours) {
        if (businessHours == null) {
            return Map.of();
        }
        return businessHours.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> new BusinessHour(entry.getValue().openTime(), entry.getValue().closeTime(),
                                entry.getValue().closed())));
    }

    private String generateUniqueInviteCode() {
        String code;
        do {
            code = generateInviteCode();
        } while (storeRepository.existsByInviteCode(code));
        return code;
    }

    private String generateInviteCode() {
        StringBuilder sb = new StringBuilder(INVITE_CODE_LENGTH);
        for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
            sb.append(INVITE_CODE_CHARS.charAt(secureRandom.nextInt(INVITE_CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
