package com.groove.recommend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.groove.fixture.MemberFixture;
import com.groove.fixture.TasteProfileFixture;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.recommend.entity.Decade;
import com.groove.recommend.entity.MemberTasteDecade;
import com.groove.recommend.entity.MemberTasteDecadeId;
import com.groove.recommend.entity.MemberTasteProfile;
import com.groove.support.DataJpaTestSupport;

import jakarta.persistence.EntityManager;

class MemberTasteDecadeRepositoryTest extends DataJpaTestSupport {

	@Autowired
	private MemberTasteDecadeRepository tasteDecadeRepository;

	@Autowired
	private MemberTasteProfileRepository profileRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private EntityManager entityManager;

	@Nested
	@DisplayName("save()")
	class Save {

		@Test
		@DisplayName("enum 이 포함된 복합 키로 저장하면 같은 키로 조회된다")
		void findsByCompositeIdWithEnum() {
			// given
			Member member = memberRepository.save(MemberFixture.create("taste-decade-save@groove.com"));
			MemberTasteProfile profile = profileRepository.save(TasteProfileFixture.create(member));

			// when
			tasteDecadeRepository.saveAndFlush(MemberTasteDecade.of(profile, Decade.D1970));
			Optional<MemberTasteDecade> found = tasteDecadeRepository.findById(
					MemberTasteDecadeId.of(profile.getId(), Decade.D1970));

			// then
			assertThat(found).isPresent();
			assertThat(found.get().getId().getProfileId()).isEqualTo(profile.getId());
			assertThat(found.get().getId().getDecade()).isEqualTo(Decade.D1970);
		}
	}

	@Nested
	@DisplayName("replaceAll()")
	class ReplaceAll {

		@Test
		@DisplayName("기존 연대를 새 연대 집합으로 전체 교체한다")
		void replacesExistingDecadesWithNewSet() {
			// given
			Member member = memberRepository.save(MemberFixture.create("taste-decade-replace@groove.com"));
			MemberTasteProfile profile = profileRepository.save(TasteProfileFixture.create(member));
			tasteDecadeRepository.saveAll(List.of(
					MemberTasteDecade.of(profile, Decade.D1960), MemberTasteDecade.of(profile, Decade.D1970)));
			entityManager.flush();
			entityManager.clear();

			// when
			tasteDecadeRepository.replaceAll(profile.getId(),
					List.of(MemberTasteDecade.of(profile, Decade.D1980)));
			entityManager.flush();
			entityManager.clear();

			// then
			List<MemberTasteDecade> found = tasteDecadeRepository.findAllByProfileId(profile.getId());
			assertThat(found).extracting(MemberTasteDecade::getDecade).containsExactly(Decade.D1980);
		}
	}
}
