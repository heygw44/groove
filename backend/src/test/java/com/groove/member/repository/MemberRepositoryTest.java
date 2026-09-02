package com.groove.member.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import com.groove.fixture.MemberFixture;
import com.groove.member.entity.Member;
import com.groove.support.DataJpaTestSupport;

class MemberRepositoryTest extends DataJpaTestSupport {

	@Autowired
	private MemberRepository memberRepository;

	@Nested
	@DisplayName("save()")
	class Save {

		@Test
		@DisplayName("저장하면 id 와 createdAt 이 채워진다")
		void fillsIdAndCreatedAt() {
			// given
			Member member = MemberFixture.create("save-ok@groove.com");

			// when
			Member saved = memberRepository.save(member);

			// then
			assertThat(saved.getId()).isNotNull();
			assertThat(saved.getCreatedAt()).isNotNull();
		}

		@Test
		@DisplayName("같은 이메일을 다시 저장하면 DataIntegrityViolationException 이 발생한다")
		void throwsOnDuplicateEmail() {
			// given
			memberRepository.saveAndFlush(MemberFixture.create("dup@groove.com"));
			Member duplicated = MemberFixture.create("dup@groove.com");

			// when & then
			assertThatThrownBy(() -> memberRepository.saveAndFlush(duplicated))
					.isInstanceOf(DataIntegrityViolationException.class);
		}
	}

	@Nested
	@DisplayName("findByEmail()")
	class FindByEmail {

		@Test
		@DisplayName("존재하는 이메일이면 회원을 반환한다")
		void returnsMemberWhenExists() {
			// given
			Member member = memberRepository.save(MemberFixture.create("find-ok@groove.com"));

			// when
			Optional<Member> found = memberRepository.findByEmail(member.getEmail());

			// then
			assertThat(found).isPresent();
			assertThat(found.get().getId()).isEqualTo(member.getId());
		}

		@Test
		@DisplayName("존재하지 않는 이메일이면 empty 를 반환한다")
		void returnsEmptyWhenNotExists() {
			// when
			Optional<Member> found = memberRepository.findByEmail("no-such@groove.com");

			// then
			assertThat(found).isEmpty();
		}
	}

	@Nested
	@DisplayName("existsByEmail()")
	class ExistsByEmail {

		@Test
		@DisplayName("존재하는 이메일이면 true 를 반환한다")
		void returnsTrueWhenExists() {
			// given
			Member member = memberRepository.save(MemberFixture.create("exists-ok@groove.com"));

			// when
			boolean exists = memberRepository.existsByEmail(member.getEmail());

			// then
			assertThat(exists).isTrue();
		}
	}
}
