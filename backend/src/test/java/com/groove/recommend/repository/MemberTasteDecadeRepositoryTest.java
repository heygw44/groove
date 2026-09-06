package com.groove.recommend.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
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

		@Test
		@DisplayName("존재하지 않는 프로필을 참조하면 FK 위반이 발생한다")
		void throwsWhenProfileDoesNotExist() {
			// given
			MemberTasteProfile fakeProfile = TasteProfileFixture.withId(
					TasteProfileFixture.create(MemberFixture.create("taste-decade-fk-profile@groove.com")),
					999_999L);

			// when & then
			assertThatThrownBy(() -> tasteDecadeRepository.saveAndFlush(
					MemberTasteDecade.of(fakeProfile, Decade.D1970)))
					.isInstanceOf(DataIntegrityViolationException.class);
		}

		@Test
		@DisplayName("연대 컬럼은 문자열로 저장된다")
		void storesDecadeColumnAsString() {
			// given
			Member member = memberRepository.save(MemberFixture.create("taste-decade-varchar@groove.com"));
			MemberTasteProfile profile = profileRepository.save(TasteProfileFixture.create(member));
			tasteDecadeRepository.saveAndFlush(MemberTasteDecade.of(profile, Decade.D1970));

			// when
			Object decadeColumnValue = entityManager
					.createNativeQuery("select decade from member_taste_decade where profile_id = ?1")
					.setParameter(1, profile.getId())
					.getSingleResult();

			// then
			assertThat(decadeColumnValue).isEqualTo("D1970");
		}
	}

	@Nested
	@DisplayName("findAllByProfileId()")
	class FindAllByProfileId {

		@Test
		@DisplayName("다른 프로필의 연대는 섞지 않는다")
		void doesNotMixOtherProfilesDecades() {
			// given
			Member memberA = memberRepository.save(MemberFixture.create("taste-decade-find-a@groove.com"));
			Member memberB = memberRepository.save(MemberFixture.create("taste-decade-find-b@groove.com"));
			MemberTasteProfile profileA = profileRepository.save(TasteProfileFixture.create(memberA));
			MemberTasteProfile profileB = profileRepository.save(TasteProfileFixture.create(memberB));
			tasteDecadeRepository.saveAndFlush(MemberTasteDecade.of(profileA, Decade.D1980));
			tasteDecadeRepository.saveAndFlush(MemberTasteDecade.of(profileB, Decade.D1990));

			// when
			List<MemberTasteDecade> found = tasteDecadeRepository.findAllByProfileId(profileA.getId());

			// then
			assertThat(found).extracting(MemberTasteDecade::getDecade).containsExactly(Decade.D1980);
		}

		@Test
		@DisplayName("저장된 연대가 없으면 빈 리스트를 반환한다")
		void returnsEmptyListWhenNoDecadeSaved() {
			// given
			Member member = memberRepository.save(MemberFixture.create("taste-decade-find-empty@groove.com"));
			MemberTasteProfile profile = profileRepository.save(TasteProfileFixture.create(member));

			// when
			List<MemberTasteDecade> found = tasteDecadeRepository.findAllByProfileId(profile.getId());

			// then
			assertThat(found).isEmpty();
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
