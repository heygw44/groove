package com.groove.recommend.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import com.groove.fixture.MemberFixture;
import com.groove.fixture.TasteProfileFixture;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.recommend.entity.MemberTasteProfile;
import com.groove.support.DataJpaTestSupport;

class MemberTasteProfileRepositoryTest extends DataJpaTestSupport {

	@Autowired
	private MemberTasteProfileRepository profileRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Nested
	@DisplayName("save()")
	class Save {

		@Test
		@DisplayName("같은 회원으로 프로필을 두 번 저장하면 유니크 제약 위반이 발생한다")
		void throwsWhenMemberDuplicated() {
			// given
			Member member = memberRepository.save(MemberFixture.create("taste-profile-uk@groove.com"));
			profileRepository.saveAndFlush(TasteProfileFixture.create(member));

			// when & then
			assertThatThrownBy(() -> profileRepository.saveAndFlush(TasteProfileFixture.create(member)))
					.isInstanceOf(DataIntegrityViolationException.class);
		}
	}

	@Nested
	@DisplayName("findByMemberId()")
	class FindByMemberId {

		@Test
		@DisplayName("프로필이 있으면 값을 반환한다")
		void returnsProfileWhenPresent() {
			// given
			Member member = memberRepository.save(MemberFixture.create("taste-profile-present@groove.com"));
			MemberTasteProfile saved = profileRepository.save(TasteProfileFixture.create(member));

			// when
			Optional<MemberTasteProfile> found = profileRepository.findByMemberId(member.getId());

			// then
			assertThat(found).isPresent();
			assertThat(found.get().getId()).isEqualTo(saved.getId());
		}

		@Test
		@DisplayName("프로필이 없으면 empty 를 반환한다")
		void returnsEmptyWhenAbsent() {
			// given
			Member member = memberRepository.save(MemberFixture.create("taste-profile-absent@groove.com"));

			// when
			Optional<MemberTasteProfile> found = profileRepository.findByMemberId(member.getId());

			// then
			assertThat(found).isEmpty();
		}
	}
}
