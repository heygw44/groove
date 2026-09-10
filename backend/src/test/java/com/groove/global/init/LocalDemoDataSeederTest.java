package com.groove.global.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.groove.fixture.MemberFixture;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;

@ExtendWith(MockitoExtension.class)
class LocalDemoDataSeederTest {

	@Mock
	MemberRepository memberRepository;

	@Mock
	PasswordEncoder passwordEncoder;

	@Nested
	@DisplayName("seed()")
	class Seed {

		@Test
		@DisplayName("회원 이메일이 이미 있으면 해당 회원 생성을 건너뛴다")
		void skipsMemberWhenEmailExists() {
			// given
			LocalDemoDataSeeder seeder = newSeeder();
			given(memberRepository.existsByEmail(anyString())).willReturn(true);

			// when
			seeder.seed();

			// then
			verify(memberRepository, never()).save(any());
		}

		@Test
		@DisplayName("회원이 하나도 없으면 관리자·데모 회원 3명을 생성한다")
		void seedsThreeMembersWhenNoneExist() {
			// given
			LocalDemoDataSeeder seeder = newSeeder();
			given(memberRepository.existsByEmail(anyString())).willReturn(false);

			// when
			seeder.seed();

			// then
			verify(memberRepository, times(3)).save(any());
		}

		@Test
		@DisplayName("user1·user2 를 찾아 신호 시딩용 목록으로 돌려준다")
		void returnsUser1AndUser2AsDemoMembers() {
			// given
			LocalDemoDataSeeder seeder = newSeeder();
			Member user1 = MemberFixture.withId(MemberFixture.create("user1@groove.com"), 1L);
			Member user2 = MemberFixture.withId(MemberFixture.create("user2@groove.com"), 2L);
			given(memberRepository.findByEmail("user1@groove.com")).willReturn(Optional.of(user1));
			given(memberRepository.findByEmail("user2@groove.com")).willReturn(Optional.of(user2));

			// when
			List<Member> demoMembers = seeder.seed();

			// then
			assertThat(demoMembers).containsExactly(user1, user2);
		}
	}

	private LocalDemoDataSeeder newSeeder() {
		return new LocalDemoDataSeeder(memberRepository, passwordEncoder);
	}
}
