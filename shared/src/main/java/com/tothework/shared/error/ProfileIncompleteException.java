package com.tothework.shared.error;

/** 소셜 가입 후 추가 정보 입력을 마치지 않았다. 프론트가 입력 화면으로 안내할 수 있게 code를 따로 둔다. */
public class ProfileIncompleteException extends BusinessException {

    public ProfileIncompleteException(String message) {
        super(ErrorCode.PROFILE_INCOMPLETE, message);
    }
}
