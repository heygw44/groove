package com.groove.member.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;

class MemberTest {

	@Nested
	@DisplayName("create()")
	class Create {

		@Test
		@DisplayName("생성하면 role 은 USER, status 는 ACTIVE 로 설정된다")
		void setsRoleUserAndStatusActive() {
			// when
			Member member = Member.create("groover@groove.com", "$2a$10$encodedpassword", "그루버");

			// then
			assertThat(member.getRole()).isEqualTo(MemberRole.USER);
			assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
		}
	}

	@Nested
	@DisplayName("changeNickname()")
	class ChangeNickname {

		@Test
		@DisplayName("닉네임을 변경하면 반영된다")
		void changesNickname() {
			// given
			Member member = Member.create("groover@groove.com", "$2a$10$encodedpassword", "그루버");

			// when
			member.changeNickname("새그루버");

			// then
			assertThat(member.getNickname()).isEqualTo("새그루버");
		}
	}

	@Nested
	@DisplayName("changePassword()")
	class ChangePassword {

		@Test
		@DisplayName("비밀번호를 변경하면 인코딩된 값으로 교체된다")
		void replacesEncodedPassword() {
			// given
			Member member = Member.create("groover@groove.com", "$2a$10$encodedpassword", "그루버");

			// when
			member.changePassword("$2a$10$newencodedpassword");

			// then
			assertThat(member.getPassword()).isEqualTo("$2a$10$newencodedpassword");
		}
	}

	@Nested
	@DisplayName("withdraw()")
	class Withdraw {

		@Test
		@DisplayName("활성 회원이면 상태를 WITHDRAWN 으로 바꾼다")
		void changesStatusToWithdrawn() {
			// given
			Member member = Member.create("groover@groove.com", "$2a$10$encodedpassword", "그루버");

			// when
			member.withdraw();

			// then
			assertThat(member.isWithdrawn()).isTrue();
		}

		@Test
		@DisplayName("이미 탈퇴한 회원이면 MEMBER_WITHDRAWN 예외를 던진다")
		void throwsWhenAlreadyWithdrawn() {
			// given
			Member member = Member.create("groover@groove.com", "$2a$10$encodedpassword", "그루버");
			member.withdraw();

			// when & then
			assertThatThrownBy(member::withdraw)
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.MEMBER_WITHDRAWN);
		}
	}

	@Nested
	@DisplayName("MemberRole.authority()")
	class Authority {

		@Test
		@DisplayName("역할을 조회하면 ROLE_ 접두사가 붙은 이름을 반환한다")
		void returnsRolePrefixedName() {
			// when & then
			assertThat(MemberRole.USER.authority()).isEqualTo("ROLE_USER");
			assertThat(MemberRole.ADMIN.authority()).isEqualTo("ROLE_ADMIN");
		}
	}
}
