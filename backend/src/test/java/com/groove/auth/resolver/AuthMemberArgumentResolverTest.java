package com.groove.auth.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.groove.auth.LoginMember;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.member.entity.MemberRole;

class AuthMemberArgumentResolverTest {

	private final AuthMemberArgumentResolver resolver = new AuthMemberArgumentResolver();

	void required(@AuthMember LoginMember member) {
	}

	void optional(@AuthMember(required = false) LoginMember member) {
	}

	void notAnnotated(LoginMember member) {
	}

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Nested
	@DisplayName("supportsParameter()")
	class SupportsParameter {

		@Test
		@DisplayName("AuthMember 가 붙은 LoginMember 파라미터면 true 를 반환한다")
		void returnsTrueForAnnotatedLoginMember() throws NoSuchMethodException {
			// given
			MethodParameter parameter = requiredParameter();

			// when & then
			assertThat(resolver.supportsParameter(parameter)).isTrue();
		}

		@Test
		@DisplayName("AuthMember 가 붙지 않은 파라미터면 false 를 반환한다")
		void returnsFalseForNotAnnotated() throws NoSuchMethodException {
			// given
			Method method = AuthMemberArgumentResolverTest.class.getDeclaredMethod("notAnnotated", LoginMember.class);
			MethodParameter parameter = new MethodParameter(method, 0);

			// when & then
			assertThat(resolver.supportsParameter(parameter)).isFalse();
		}
	}

	@Nested
	@DisplayName("resolveArgument()")
	class ResolveArgument {

		@Test
		@DisplayName("LoginMember principal 이면 필수 여부와 관계없이 반환한다")
		void returnsLoginMemberWhenPrincipalPresent() throws NoSuchMethodException {
			// given
			LoginMember loginMember = new LoginMember(1L, MemberRole.USER);
			setAuthentication(loginMember, "ROLE_USER");

			// when
			Object requiredResult = resolver.resolveArgument(requiredParameter(), null, null, null);
			Object optionalResult = resolver.resolveArgument(optionalParameter(), null, null, null);

			// then
			assertThat(requiredResult).isEqualTo(loginMember);
			assertThat(optionalResult).isEqualTo(loginMember);
		}

		@Test
		@DisplayName("익명 인증이고 필수면 AUTH_UNAUTHORIZED 예외를 던진다")
		void throwsWhenAnonymousAndRequired() throws NoSuchMethodException {
			// given
			setAnonymousAuthentication();

			// when & then
			assertThatThrownBy(() -> resolver.resolveArgument(requiredParameter(), null, null, null))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.AUTH_UNAUTHORIZED);
		}

		@Test
		@DisplayName("익명 인증이고 선택이면 null 을 반환한다")
		void returnsNullWhenAnonymousAndOptional() throws NoSuchMethodException {
			// given
			setAnonymousAuthentication();

			// when
			Object result = resolver.resolveArgument(optionalParameter(), null, null, null);

			// then
			assertThat(result).isNull();
		}

		@Test
		@DisplayName("인증 정보가 없고 선택이면 null 을 반환한다")
		void returnsNullWhenNoAuthenticationAndOptional() throws NoSuchMethodException {
			// when
			Object result = resolver.resolveArgument(optionalParameter(), null, null, null);

			// then
			assertThat(result).isNull();
		}
	}

	private void setAuthentication(LoginMember loginMember, String authority) {
		SecurityContextHolder.getContext().setAuthentication(
				new TestingAuthenticationToken(loginMember, null, authority));
	}

	private void setAnonymousAuthentication() {
		SecurityContextHolder.getContext().setAuthentication(
				new AnonymousAuthenticationToken("key", "anonymousUser",
						List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
	}

	private MethodParameter requiredParameter() throws NoSuchMethodException {
		Method method = AuthMemberArgumentResolverTest.class.getDeclaredMethod("required", LoginMember.class);
		return new MethodParameter(method, 0);
	}

	private MethodParameter optionalParameter() throws NoSuchMethodException {
		Method method = AuthMemberArgumentResolverTest.class.getDeclaredMethod("optional", LoginMember.class);
		return new MethodParameter(method, 0);
	}
}
