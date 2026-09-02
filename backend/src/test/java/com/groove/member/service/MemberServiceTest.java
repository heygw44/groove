package com.groove.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.groove.auth.repository.RefreshTokenRepository;
import com.groove.fixture.MemberFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.member.dto.MemberResponse;
import com.groove.member.dto.MemberUpdateRequest;
import com.groove.member.dto.PasswordChangeRequest;
import com.groove.member.entity.Member;
import com.groove.member.entity.MemberRole;
import com.groove.member.entity.MemberStatus;
import com.groove.member.repository.MemberRepository;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

	private static final Long MEMBER_ID = 1L;

	@Mock
	MemberRepository memberRepository;

	@Mock
	RefreshTokenRepository refreshTokenRepository;

	@Mock
	PasswordEncoder passwordEncoder;

	MemberService memberService;

	@BeforeEach
	void setUp() {
		memberService = new MemberService(memberRepository, refreshTokenRepository, passwordEncoder);
	}

	@Nested
	@DisplayName("getMyInfo()")
	class GetMyInfo {

		@Test
		@DisplayName("존재하는 활성 회원이면 회원 정보를 반환한다")
		void returnsMemberInfo() {
			// given
			Member member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

			// when
			MemberResponse response = memberService.getMyInfo(MEMBER_ID);

			// then
			assertThat(response.id()).isEqualTo(MEMBER_ID);
			assertThat(response.email()).isEqualTo(member.getEmail());
			assertThat(response.nickname()).isEqualTo(member.getNickname());
			assertThat(response.role()).isEqualTo(MemberRole.USER);
			assertThat(response.status()).isEqualTo(MemberStatus.ACTIVE);
		}

		@Test
		@DisplayName("회원이 존재하지 않으면 MEMBER_NOT_FOUND 예외를 던진다")
		void throwsWhenMemberNotFound() {
			// given
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> memberService.getMyInfo(MEMBER_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
		}

		@Test
		@DisplayName("탈퇴한 회원이면 MEMBER_WITHDRAWN 예외를 던진다")
		void throwsWhenMemberWithdrawn() {
			// given
			Member member = MemberFixture.withId(MemberFixture.createWithdrawn(), MEMBER_ID);
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

			// when & then
			assertThatThrownBy(() -> memberService.getMyInfo(MEMBER_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.MEMBER_WITHDRAWN);
		}
	}

	@Nested
	@DisplayName("updateNickname()")
	class UpdateNickname {

		@Test
		@DisplayName("닉네임을 변경하고 변경된 정보를 반환한다")
		void changesNicknameAndReturnsResponse() {
			// given
			Member member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			MemberUpdateRequest request = new MemberUpdateRequest("새그루버");

			// when
			MemberResponse response = memberService.updateNickname(MEMBER_ID, request);

			// then
			assertThat(member.getNickname()).isEqualTo("새그루버");
			assertThat(response.nickname()).isEqualTo("새그루버");
		}
	}

	@Nested
	@DisplayName("changePassword()")
	class ChangePassword {

		@Test
		@DisplayName("현재 비밀번호가 일치하지 않으면 MEMBER_PASSWORD_MISMATCH 예외를 던진다")
		void throwsWhenCurrentPasswordMismatch() {
			// given
			Member member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			PasswordChangeRequest request = new PasswordChangeRequest("wrong-password", "new-password1");
			given(passwordEncoder.matches(request.currentPassword(), member.getPassword())).willReturn(false);

			// when & then
			assertThatThrownBy(() -> memberService.changePassword(MEMBER_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.MEMBER_PASSWORD_MISMATCH);
			verify(passwordEncoder, never()).encode(any());
		}

		@Test
		@DisplayName("현재 비밀번호가 일치하면 인코딩된 새 비밀번호로 교체한다")
		void replacesPasswordWithEncodedNewPassword() {
			// given
			Member member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			PasswordChangeRequest request = new PasswordChangeRequest(MemberFixture.encodedPassword(), "new-password1");
			given(passwordEncoder.matches(request.currentPassword(), member.getPassword())).willReturn(true);
			given(passwordEncoder.encode(request.newPassword())).willReturn("new-encoded");

			// when
			memberService.changePassword(MEMBER_ID, request);

			// then
			assertThat(member.getPassword()).isEqualTo("new-encoded");
		}
	}

	@Nested
	@DisplayName("withdraw()")
	class Withdraw {

		@Test
		@DisplayName("탈퇴 처리하고 refresh token 을 삭제한다")
		void marksWithdrawnAndDeletesRefreshToken() {
			// given
			Member member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

			// when
			memberService.withdraw(MEMBER_ID);

			// then
			assertThat(member.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
			verify(refreshTokenRepository).deleteByMemberId(MEMBER_ID);
		}

		@Test
		@DisplayName("이미 탈퇴한 회원이면 MEMBER_WITHDRAWN 예외를 던진다")
		void throwsWhenAlreadyWithdrawn() {
			// given
			Member member = MemberFixture.withId(MemberFixture.createWithdrawn(), MEMBER_ID);
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

			// when & then
			assertThatThrownBy(() -> memberService.withdraw(MEMBER_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.MEMBER_WITHDRAWN);
			verify(refreshTokenRepository, never()).deleteByMemberId(any());
		}
	}
}
