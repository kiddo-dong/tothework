package com.example.albam.domain.manual.service;

import com.example.albam.domain.manual.dto.ManualImageResponse;
import com.example.albam.domain.manual.dto.ManualRequest;
import com.example.albam.domain.manual.dto.ManualResponse;
import com.example.albam.domain.manual.dto.ManualSummaryResponse;
import com.example.albam.domain.manual.entity.Manual;
import com.example.albam.domain.manual.repository.ManualRepository;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.service.StoreAuthorizationService;
import com.example.albam.global.exception.ForbiddenException;
import com.example.albam.global.exception.NotFoundException;
import com.example.albam.global.file.S3Uploader;
import java.util.ArrayList;
import java.util.List;
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
public class ManualService {

    private static final String MANUAL_IMAGE_DIRECTORY = "manual-images";

    private final ManualRepository manualRepository;
    private final StoreAuthorizationService storeAuthorizationService;
    private final S3Uploader s3Uploader;
    private final PlatformTransactionManager transactionManager;

    @Transactional
    public ManualResponse createManual(Long storeId, Long userId, ManualRequest request) {
        StoreMember author = storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        Manual manual = manualRepository.save(new Manual(author.getStore(), author, request.category(),
                request.title(), request.content(), request.displayOrderOrDefault(),
                verifyImageKeysBelongToStore(storeId, request.imageKeys())));
        return toResponse(manual);
    }

    /** 이 매장의 이미지 업로드가 쓰는 key 접두사. */
    private static String imageKeyPrefix(Long storeId) {
        return MANUAL_IMAGE_DIRECTORY + "/" + storeId + "/";
    }

    /**
     * 요청으로 들어온 이미지 key가 이 매장 소유인지 확인한다.
     *
     * <p>없으면 다른 매장의 key를 자기 매뉴얼에 넣었다가 빼는 것만으로 그 파일을 S3에서 지울 수 있다
     * (수정 시 "기존 목록에서 빠진 key"를 삭제하기 때문). 업로드가 매장별 경로에 넣으므로 접두사로 판별한다.
     */
    private List<String> verifyImageKeysBelongToStore(Long storeId, List<String> imageKeys) {
        if (imageKeys == null) {
            return List.of();
        }
        String prefix = imageKeyPrefix(storeId);
        for (String key : imageKeys) {
            if (key == null || !key.startsWith(prefix)) {
                throw new ForbiddenException("이 매장에 업로드된 이미지가 아닙니다.");
            }
        }
        return imageKeys;
    }

    public List<ManualSummaryResponse> getManuals(Long storeId, Long userId) {
        storeAuthorizationService.requireMember(storeId, userId);
        return manualRepository.findAllByStoreIdOrderByCategoryAscDisplayOrderAscIdAsc(storeId).stream()
                .map(ManualSummaryResponse::from)
                .toList();
    }

    public ManualResponse getManual(Long storeId, Long manualId, Long userId) {
        storeAuthorizationService.requireMember(storeId, userId);
        return toResponse(getManualInStore(storeId, manualId));
    }

    /**
     * S3 삭제는 네트워크 호출이라 DB 트랜잭션 안에서 하지 않는다 — 커밋까지 커넥션을 붙잡지 않도록
     * DB 갱신을 먼저 커밋한 뒤, 지워진 이미지는 트랜잭션 밖에서 정리한다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ManualResponse updateManual(Long storeId, Long manualId, Long userId, ManualRequest request) {
        List<String> keptImageKeys = verifyImageKeysBelongToStore(storeId, request.imageKeys());
        List<String> removedImageKeys = new ArrayList<>();
        ManualResponse response = new TransactionTemplate(transactionManager).execute(status -> {
            storeAuthorizationService.requireOwnerOrManager(storeId, userId);
            Manual manual = getManualInStore(storeId, manualId);
            removedImageKeys.addAll(manual.getImageKeys());
            removedImageKeys.removeAll(keptImageKeys);
            manual.update(request.category(), request.title(), request.content(),
                    request.displayOrderOrDefault(), keptImageKeys);
            return toResponse(manual);
        });
        removedImageKeys.forEach(s3Uploader::delete);
        return response;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void deleteManual(Long storeId, Long manualId, Long userId) {
        List<String> images = new TransactionTemplate(transactionManager).execute(status -> {
            storeAuthorizationService.requireOwnerOrManager(storeId, userId);
            Manual manual = getManualInStore(storeId, manualId);
            List<String> removed = new ArrayList<>(manual.getImageKeys());
            manualRepository.delete(manual);
            return removed;
        });
        images.forEach(s3Uploader::delete);
    }

    /**
     * 매뉴얼 본문에 넣을 이미지를 먼저 업로드하고 S3 key를 돌려받는다. 이 key를 그대로 매뉴얼 저장 요청에 넣는다.
     * 매장별 경로에 넣어야 저장 시 소유권을 접두사로 판별할 수 있다.
     */
    public ManualImageResponse uploadImage(Long storeId, Long userId, MultipartFile image) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        return toImageResponse(s3Uploader.upload(image, MANUAL_IMAGE_DIRECTORY + "/" + storeId));
    }

    /** 엔티티에는 S3 key만 있으므로, 응답을 만들 때 공개 URL을 조립해 key와 함께 내려준다. */
    private ManualResponse toResponse(Manual manual) {
        return ManualResponse.from(manual, manual.getImageKeys().stream()
                .map(this::toImageResponse)
                .toList());
    }

    private ManualImageResponse toImageResponse(String key) {
        return new ManualImageResponse(key, s3Uploader.toPublicUrl(key));
    }

    private Manual getManualInStore(Long storeId, Long manualId) {
        return manualRepository.findByIdAndStoreId(manualId, storeId)
                .orElseThrow(() -> new NotFoundException("매뉴얼을 찾을 수 없습니다."));
    }
}
