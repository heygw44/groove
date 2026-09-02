package com.groove.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.groove.auth.dto.SignupRequest;
import com.groove.auth.dto.SignupResponse;
import com.groove.fixture.MemberFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.member.entity.Member;
import com.groove.member.entity.MemberRole;
import com.groove.member.entity.MemberStatus;
import com.groove.member.repository.MemberRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	MemberRepository memberRepository;

	@Mock
	PasswordEncoder passwordEncoder;

	@InjectMocks
	AuthService authService;

	@Nested
	@DisplayName("signup()")
	class Signup {

		@Test
		@DisplayName("이메일이 중복되지 않으면 비밀번호를 인코딩해 저장하고 응답을 반환한다")
		void savesEncodedPasswordAndReturnsResponse() {
			// given
			SignupRequest request = new SignupRequest("groover@groove.com", "password1", "그루버");
			given(memberRepository.existsByEmail(request.email())).willReturn(false);
			given(passwordEncoder.encode(request.password())).willReturn("encoded");
			willAnswer(invocation -> MemberFixture.withId(invocation.getArgument(0), 1L))
					.given(memberRepository).save(any(Member.class));

			// when
			SignupResponse response = authService.signup(request);

			// then
			ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
			verify(memberRepository).save(captor.capture());
			Member savedMember = captor.getValue();
			assertThat(savedMember.getPassword()).isEqualTo("encoded");
			assertThat(savedMember.getStatus()).isEqualTo(MemberStatus.ACTIVE);
			assertThat(savedMember.getRole()).isEqualTo(MemberRole.USER);

			assertThat(response.id()).isEqualTo(1L);
			assertThat(response.email()).isEqualTo(request.email());
			assertThat(response.nickname()).isEqualTo(request.nickname());
		}

		@Test
		@DisplayName("이메일이 중복되면 MEMBER_EMAIL_DUPLICATE 예외를 던진다")
		void throwsWhenEmailDuplicated() {
			// given
			SignupRequest request = new SignupRequest("groover@groove.com", "password1", "그루버");
			given(memberRepository.existsByEmail(request.email())).willReturn(true);

			// when & then
			assertThatThrownBy(() -> authService.signup(request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.MEMBER_EMAIL_DUPLICATE);
			verify(memberRepository, never()).save(any());
		}
	}
}
