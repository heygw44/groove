package com.groove.auth.resolver;

import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.groove.auth.LoginMember;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;

/** {@code @AuthMember} 파라미터에 SecurityContext 의 인증 principal({@code LoginMember})을 주입한다. */
@Component
public class AuthMemberArgumentResolver implements HandlerMethodArgumentResolver {

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(AuthMember.class)
				&& LoginMember.class.equals(parameter.getParameterType());
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
		NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof LoginMember loginMember) {
			return loginMember;
		}
		if (!parameter.getParameterAnnotation(AuthMember.class).required()) {
			return null;
		}
		throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
	}
}
