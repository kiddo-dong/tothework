package com.tothework.shared.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

/** 컨트롤러 파라미터에 현재 사용자 id를 넣는다. 주체는 {@link AuthenticatedUser}여야 한다. */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@AuthenticationPrincipal(expression = "userId()")
public @interface CurrentUserId {
}
