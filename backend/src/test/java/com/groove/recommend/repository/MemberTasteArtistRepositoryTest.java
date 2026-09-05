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

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.TasteProfileFixture;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.product.entity.Artist;
import com.groove.product.repository.ArtistRepository;
import com.groove.recommend.entity.MemberTasteArtist;
import com.groove.recommend.entity.MemberTasteArtistId;
import com.groove.recommend.entity.MemberTasteProfile;
import com.groove.support.DataJpaTestSupport;

import jakarta.persistence.EntityManager;

class MemberTasteArtistRepositoryTest extends DataJpaTestSupport {

	@Autowired
	private MemberTasteArtistRepository tasteArtistRepository;

	@Autowired
	private MemberTasteProfileRepository profileRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private EntityManager entityManager;

	@Nested
	@DisplayName("save()")
	class Save {

		@Test
		@DisplayName("복합 키로 저장하면 같은 키로 조회된다")
		void findsByCompositeId() {
			// given
			Member member = memberRepository.save(MemberFixture.create("taste-artist-save@groove.com"));
			MemberTasteProfile profile = profileRepository.save(TasteProfileFixture.create(member));
			Artist artist = artistRepository.save(ArtistFixture.create());

			// when
			tasteArtistRepository.saveAndFlush(MemberTasteArtist.of(profile, artist));
			Optional<MemberTasteArtist> found = tasteArtistRepository.findById(
					MemberTasteArtistId.of(profile.getId(), artist.getId()));

			// then
			assertThat(found).isPresent();
			assertThat(found.get().getId().getProfileId()).isEqualTo(profile.getId());
			assertThat(found.get().getId().getArtistId()).isEqualTo(artist.getId());
		}

		@Test
		@DisplayName("존재하지 않는 프로필을 참조하면 FK 위반이 발생한다")
		void throwsWhenProfileDoesNotExist() {
			// given
			MemberTasteProfile fakeProfile = TasteProfileFixture.withId(
					TasteProfileFixture.create(MemberFixture.create("taste-artist-fk-profile@groove.com")),
					999_999L);
			Artist artist = artistRepository.save(ArtistFixture.create("타지-아티스트-FK프로필"));

			// when & then
			assertThatThrownBy(() -> tasteArtistRepository.saveAndFlush(MemberTasteArtist.of(fakeProfile, artist)))
					.isInstanceOf(DataIntegrityViolationException.class);
		}

		@Test
		@DisplayName("존재하지 않는 아티스트를 참조하면 FK 위반이 발생한다")
		void throwsWhenArtistDoesNotExist() {
			// given
			Member member = memberRepository.save(MemberFixture.create("taste-artist-fk-artist@groove.com"));
			MemberTasteProfile profile = profileRepository.save(TasteProfileFixture.create(member));
			Artist fakeArtist = ArtistFixture.withId(ArtistFixture.create("타지-아티스트-없음"), 999_999L);

			// when & then
			assertThatThrownBy(() -> tasteArtistRepository.saveAndFlush(MemberTasteArtist.of(profile, fakeArtist)))
					.isInstanceOf(DataIntegrityViolationException.class);
		}
	}

	@Nested
	@DisplayName("findAllByProfileId()")
	class FindAllByProfileId {

		@Test
		@DisplayName("다른 프로필의 아티스트는 섞지 않는다")
		void doesNotMixOtherProfilesArtists() {
			// given
			Member memberA = memberRepository.save(MemberFixture.create("taste-artist-find-a@groove.com"));
			Member memberB = memberRepository.save(MemberFixture.create("taste-artist-find-b@groove.com"));
			MemberTasteProfile profileA = profileRepository.save(TasteProfileFixture.create(memberA));
			MemberTasteProfile profileB = profileRepository.save(TasteProfileFixture.create(memberB));
			Artist artistA = artistRepository.save(ArtistFixture.create("타지-아티스트-조회A"));
			Artist artistB = artistRepository.save(ArtistFixture.create("타지-아티스트-조회B"));
			tasteArtistRepository.saveAndFlush(MemberTasteArtist.of(profileA, artistA));
			tasteArtistRepository.saveAndFlush(MemberTasteArtist.of(profileB, artistB));

			// when
			List<MemberTasteArtist> found = tasteArtistRepository.findAllByProfileId(profileA.getId());

			// then
			assertThat(found).extracting(a -> a.getArtist().getId()).containsExactly(artistA.getId());
		}

		@Test
		@DisplayName("저장된 아티스트가 없으면 빈 리스트를 반환한다")
		void returnsEmptyListWhenNoArtistSaved() {
			// given
			Member member = memberRepository.save(MemberFixture.create("taste-artist-find-empty@groove.com"));
			MemberTasteProfile profile = profileRepository.save(TasteProfileFixture.create(member));

			// when
			List<MemberTasteArtist> found = tasteArtistRepository.findAllByProfileId(profile.getId());

			// then
			assertThat(found).isEmpty();
		}
	}

	@Nested
	@DisplayName("replaceAll()")
	class ReplaceAll {

		@Test
		@DisplayName("기존 아티스트를 새 아티스트 집합으로 전체 교체한다")
		void replacesExistingArtistsWithNewSet() {
			// given
			Member member = memberRepository.save(MemberFixture.create("taste-artist-replace@groove.com"));
			MemberTasteProfile profile = profileRepository.save(TasteProfileFixture.create(member));
			Artist artistA = artistRepository.save(ArtistFixture.create("타지-아티스트-A"));
			Artist artistB = artistRepository.save(ArtistFixture.create("타지-아티스트-B"));
			Artist artistC = artistRepository.save(ArtistFixture.create("타지-아티스트-C"));
			tasteArtistRepository.saveAll(List.of(
					MemberTasteArtist.of(profile, artistA), MemberTasteArtist.of(profile, artistB)));
			entityManager.flush();
			entityManager.clear();

			// when
			tasteArtistRepository.replaceAll(profile.getId(),
					List.of(MemberTasteArtist.of(profile, artistB), MemberTasteArtist.of(profile, artistC)));
			entityManager.flush();
			entityManager.clear();

			// then
			List<MemberTasteArtist> found = tasteArtistRepository.findAllByProfileId(profile.getId());
			assertThat(found).extracting(a -> a.getArtist().getId())
					.containsExactlyInAnyOrder(artistB.getId(), artistC.getId());
		}
	}
}
