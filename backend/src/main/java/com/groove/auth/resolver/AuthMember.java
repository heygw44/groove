package com.groove.auth.resolver;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 컨트롤러 메서드 파라미터에 붙여 인증된 회원({@code LoginMember})을 주입받는다. */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthMember {

	/** false 면 비로그인 요청일 때 예외 대신 {@code null} 을 주입한다. */
	boolean required() default true;
}
