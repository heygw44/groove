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

import com.groove.fixture.GenreFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.TasteProfileFixture;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.product.entity.Genre;
import com.groove.product.repository.GenreRepository;
import com.groove.recommend.entity.MemberTasteGenre;
import com.groove.recommend.entity.MemberTasteGenreId;
import com.groove.recommend.entity.MemberTasteProfile;
import com.groove.support.DataJpaTestSupport;

import jakarta.persistence.EntityManager;

class MemberTasteGenreRepositoryTest extends DataJpaTestSupport {

	@Autowired
	private MemberTasteGenreRepository tasteGenreRepository;

	@Autowired
	private MemberTasteProfileRepository profileRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private GenreRepository genreRepository;

	@Autowired
	private EntityManager entityManager;

	@Nested
	@DisplayName("save()")
	class Save {

		@Test
		@DisplayName("복합 키로 저장하면 같은 키로 조회된다")
		void findsByCompositeId() {
			// given
			Member member = memberRepository.save(MemberFixture.create("taste-genre-save@groove.com"));
			MemberTasteProfile profile = profileRepository.save(TasteProfileFixture.create(member));
			Genre genre = genreRepository.save(GenreFixture.create("타지-장르-저장"));

			// when
			tasteGenreRepository.saveAndFlush(MemberTasteGenre.of(profile, genre));
			Optional<MemberTasteGenre> found = tasteGenreRepository.findById(
					MemberTasteGenreId.of(profile.getId(), genre.getId()));

			// then
			assertThat(found).isPresent();
			assertThat(found.get().getId().getProfileId()).isEqualTo(profile.getId());
			assertThat(found.get().getId().getGenreId()).isEqualTo(genre.getId());
		}

		@Test
		@DisplayName("존재하지 않는 프로필을 참조하면 FK 위반이 발생한다")
		void throwsWhenProfileDoesNotExist() {
			// given
			MemberTasteProfile fakeProfile = TasteProfileFixture.withId(
					TasteProfileFixture.create(MemberFixture.create("taste-genre-fk-profile@groove.com")),
					999_999L);
			Genre genre = genreRepository.save(GenreFixture.create("타지-장르-FK프로필"));

			// when & then
			assertThatThrownBy(() -> tasteGenreRepository.saveAndFlush(MemberTasteGenre.of(fakeProfile, genre)))
					.isInstanceOf(DataIntegrityViolationException.class);
		}

		@Test
		@DisplayName("존재하지 않는 장르를 참조하면 FK 위반이 발생한다")
		void throwsWhenGenreDoesNotExist() {
			// given
			Member member = memberRepository.save(MemberFixture.create("taste-genre-fk-genre@groove.com"));
			MemberTasteProfile profile = profileRepository.save(TasteProfileFixture.create(member));
			Genre fakeGenre = GenreFixture.withId(GenreFixture.create("타지-장르-없음"), 999_999L);

			// when & then
			assertThatThrownBy(() -> tasteGenreRepository.saveAndFlush(MemberTasteGenre.of(profile, fakeGenre)))
					.isInstanceOf(DataIntegrityViolationException.class);
		}
	}

	@Nested
	@DisplayName("findAllByProfileId()")
	class FindAllByProfileId {

		@Test
		@DisplayName("다른 프로필의 장르는 섞지 않는다")
		void doesNotMixOtherProfilesGenres() {
			// given
			Member memberA = memberRepository.save(MemberFixture.create("taste-genre-find-a@groove.com"));
			Member memberB = memberRepository.save(MemberFixture.create("taste-genre-find-b@groove.com"));
			MemberTasteProfile profileA = profileRepository.save(TasteProfileFixture.create(memberA));
			MemberTasteProfile profileB = profileRepository.save(TasteProfileFixture.create(memberB));
			Genre genreA = genreRepository.save(GenreFixture.create("타지-장르-조회A"));
			Genre genreB = genreRepository.save(GenreFixture.create("타지-장르-조회B"));
			tasteGenreRepository.saveAndFlush(MemberTasteGenre.of(profileA, genreA));
			tasteGenreRepository.saveAndFlush(MemberTasteGenre.of(profileB, genreB));

			// when
			List<MemberTasteGenre> found = tasteGenreRepository.findAllByProfileId(profileA.getId());

			// then
			assertThat(found).extracting(g -> g.getGenre().getId()).containsExactly(genreA.getId());
		}

		@Test
		@DisplayName("저장된 장르가 없으면 빈 리스트를 반환한다")
		void returnsEmptyListWhenNoGenreSaved() {
			// given
			Member member = memberRepository.save(MemberFixture.create("taste-genre-find-empty@groove.com"));
			MemberTasteProfile profile = profileRepository.save(TasteProfileFixture.create(member));

			// when
			List<MemberTasteGenre> found = tasteGenreRepository.findAllByProfileId(profile.getId());

			// then
			assertThat(found).isEmpty();
		}
	}

	@Nested
	@DisplayName("replaceAll()")
	class ReplaceAll {

		@Test
		@DisplayName("기존 장르를 새 장르 집합으로 전체 교체한다")
		void replacesExistingGenresWithNewSet() {
			// given
			Member member = memberRepository.save(MemberFixture.create("taste-genre-replace@groove.com"));
			MemberTasteProfile profile = profileRepository.save(TasteProfileFixture.create(member));
			Genre genreA = genreRepository.save(GenreFixture.create("타지-장르-A"));
			Genre genreB = genreRepository.save(GenreFixture.create("타지-장르-B"));
			Genre genreC = genreRepository.save(GenreFixture.create("타지-장르-C"));
			tasteGenreRepository.saveAll(List.of(
					MemberTasteGenre.of(profile, genreA), MemberTasteGenre.of(profile, genreB)));
			entityManager.flush();
			entityManager.clear();

			// when
			tasteGenreRepository.replaceAll(profile.getId(),
					List.of(MemberTasteGenre.of(profile, genreB), MemberTasteGenre.of(profile, genreC)));
			entityManager.flush();
			entityManager.clear();

			// then
			List<MemberTasteGenre> found = tasteGenreRepository.findAllByProfileId(profile.getId());
			assertThat(found).extracting(g -> g.getGenre().getId())
					.containsExactlyInAnyOrder(genreB.getId(), genreC.getId());
		}
	}

	@Nested
	@DisplayName("deleteAllByProfileId()")
	class DeleteAllByProfileId {

		@Test
		@DisplayName("해당 프로필의 행만 삭제하고 다른 프로필은 남긴다")
		void deletesOnlyTargetProfileRows() {
			// given
			Member memberA = memberRepository.save(MemberFixture.create("taste-genre-delete-a@groove.com"));
			Member memberB = memberRepository.save(MemberFixture.create("taste-genre-delete-b@groove.com"));
			MemberTasteProfile profileA = profileRepository.save(TasteProfileFixture.create(memberA));
			MemberTasteProfile profileB = profileRepository.save(TasteProfileFixture.create(memberB));
			Genre genre = genreRepository.save(GenreFixture.create("타지-장르-삭제"));
			tasteGenreRepository.saveAndFlush(MemberTasteGenre.of(profileA, genre));
			tasteGenreRepository.saveAndFlush(MemberTasteGenre.of(profileB, genre));

			// when
			tasteGenreRepository.deleteAllByProfileId(profileA.getId());

			// then
			assertThat(tasteGenreRepository.findAllByProfileId(profileA.getId())).isEmpty();
			assertThat(tasteGenreRepository.findAllByProfileId(profileB.getId())).hasSize(1);
		}
	}
}
