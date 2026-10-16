package com.tothework.identity.user;

import com.tothework.identity.api.UserWithdrawnEvent;
import com.tothework.identity.api.WithdrawalGuard;
import com.tothework.identity.auth.EmailTokenRepository;
import com.tothework.shared.error.ConflictException;
import com.tothework.shared.error.NotFoundException;
import com.tothework.shared.storage.S3Uploader;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/** 내 정보 조회·수정·추가 정보 입력·탈퇴·프로필 사진. 사용자를 바꾼 곳마다 저장을 직접 부른다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private static final String PROFILE_IMAGE_DIRECTORY = "profile-images";

    private final UserRepository userRepository;
    private final EmailTokenRepository emailTokenRepository;
    private final List<WithdrawalGuard> withdrawalGuards;
    private final ApplicationEventPublisher eventPublisher;
    private final S3Uploader s3Uploader;
    private final PlatformTransactionManager transactionManager;

    public UserResponse getMe(Long userId) {
        return toResponse(getUser(userId));
    }

    /** 엔티티에는 S3 key만 있으므로, 응답을 만들 때 공개 URL로 조립한다. */
    private UserResponse toResponse(User user) {
        return UserResponse.from(user, s3Uploader.toPublicUrl(user.getProfileImageKey()));
    }

    /** 소셜 가입 후 추가 정보 입력. 입력 도중 창이 닫혀도 재로그인 후 profileCompleted=false를 보고 이어서 한다. */
    @Transactional
    public UserResponse completeProfile(Long userId, CompleteProfileRequest request) {
        User user = getUser(userId);
        if (user.isProfileCompleted()) {
            throw new ConflictException("이미 프로필 입력이 완료된 계정입니다. 수정은 프로필 수정 API를 이용해 주세요.");
        }
        if (userRepository.existsByPhoneAndIdNot(request.phone(), userId)) {
            throw new ConflictException("이미 사용 중인 전화번호입니다.");
        }
        user.completeProfile(request.name(), request.phone(), request.birthDate());
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateMe(Long userId, UpdateUserRequest request) {
        if (userRepository.existsByPhoneAndIdNot(request.phone(), userId)) {
            throw new ConflictException("이미 사용 중인 전화번호입니다.");
        }
        User user = getUser(userId);
        user.updateProfile(request.name(), request.phone(), request.birthDate());
        return toResponse(userRepository.save(user));
    }

    /**
     * 탈퇴. 다른 서비스의 기록이 이 id를 참조하므로 행을 지우지 않고 개인정보를 익명화한다.
     *
     * <p>막을 조건은 {@link WithdrawalGuard}에 먼저 묻는다 (예: 활동 중인 매장). 다른 서비스가 가진 개인 데이터는
     * {@link UserWithdrawnEvent}를 받은 각 서비스가 커밋 이후 스스로 지운다. S3 삭제는 커밋 뒤 트랜잭션 밖에서 한다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void withdraw(Long userId) {
        withdrawalGuards.forEach(guard -> guard.checkCanWithdraw(userId));
        String profileImageKey = new TransactionTemplate(transactionManager).execute(status -> {
            User user = getUser(userId);
            String key = user.getProfileImageKey();
            emailTokenRepository.deleteAllOfUser(userId);
            user.anonymizeForWithdrawal();
            userRepository.save(user);
            eventPublisher.publishEvent(new UserWithdrawnEvent(userId));
            return key;
        });
        s3Uploader.delete(profileImageKey);
    }

    /** S3 업로드·삭제는 네트워크 호출이라 DB 트랜잭션 밖에서 한다. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UserResponse updateProfileImage(Long userId, MultipartFile image) {
        String uploadedKey = s3Uploader.upload(image, PROFILE_IMAGE_DIRECTORY);
        return replaceProfileImage(userId, user -> user.changeProfileImageKey(uploadedKey));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UserResponse deleteProfileImage(Long userId) {
        return replaceProfileImage(userId, user -> user.changeProfileImageKey(null));
    }

    private UserResponse replaceProfileImage(Long userId, Consumer<User> change) {
        String[] previousKey = new String[1];
        UserResponse response = new TransactionTemplate(transactionManager).execute(status -> {
            User user = getUser(userId);
            previousKey[0] = user.getProfileImageKey();
            change.accept(user);
            return toResponse(userRepository.save(user));
        });
        s3Uploader.delete(previousKey[0]);
        return response;
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));
    }
}
