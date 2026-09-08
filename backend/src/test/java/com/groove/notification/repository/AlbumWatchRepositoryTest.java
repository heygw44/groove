package com.groove.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.groove.fixture.AlbumFixture;
import com.groove.fixture.AlbumWatchFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.notification.entity.AlbumWatch;
import com.groove.product.entity.Album;
import com.groove.product.entity.Artist;
import com.groove.product.repository.AlbumRepository;
import com.groove.product.repository.ArtistRepository;
import com.groove.support.DataJpaTestSupport;

class AlbumWatchRepositoryTest extends DataJpaTestSupport {

	@Autowired
	private AlbumWatchRepository albumWatchRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private AlbumRepository albumRepository;

	@Nested
	@DisplayName("save()")
	class Save {

		@Test
		@DisplayName("같은 회원이 같은 앨범을 두 번 구독하면 유니크 제약 위반이 발생한다")
		void throwsWhenMemberAndAlbumDuplicated() {
			// given
			Member member = memberRepository.save(MemberFixture.create("album-watch-repo-uk@groove.com"));
			Artist artist = artistRepository.save(ArtistFixture.create());
			Album album = albumRepository.save(AlbumFixture.create(artist));
			albumWatchRepository.saveAndFlush(AlbumWatchFixture.create(member, album));

			// when & then
			assertThatThrownBy(() -> albumWatchRepository.saveAndFlush(AlbumWatchFixture.create(member, album)))
					.isInstanceOf(DataIntegrityViolationException.class);
		}
	}

	@Nested
	@DisplayName("existsByMemberIdAndAlbumId() / findByMemberIdAndAlbumId()")
	class ExistsAndFind {

		@Test
		@DisplayName("구독한 조합이면 true 와 값을 반환한다")
		void returnsTrueAndValueWhenPresent() {
			// given
			Member member = memberRepository.save(MemberFixture.create("album-watch-repo-present@groove.com"));
			Artist artist = artistRepository.save(ArtistFixture.create());
			Album album = albumRepository.save(AlbumFixture.create(artist));
			AlbumWatch albumWatch = albumWatchRepository.save(AlbumWatchFixture.create(member, album));

			// when
			boolean exists = albumWatchRepository.existsByMemberIdAndAlbumId(member.getId(), album.getId());
			Optional<AlbumWatch> found = albumWatchRepository.findByMemberIdAndAlbumId(member.getId(),
					album.getId());

			// then
			assertThat(exists).isTrue();
			assertThat(found).isPresent();
			assertThat(found.get().getId()).isEqualTo(albumWatch.getId());
		}

		@Test
		@DisplayName("구독하지 않은 조합이면 false 와 empty 를 반환한다")
		void returnsFalseAndEmptyWhenAbsent() {
			// given
			Member member = memberRepository.save(MemberFixture.create("album-watch-repo-absent@groove.com"));
			Artist artist = artistRepository.save(ArtistFixture.create());
			Album album = albumRepository.save(AlbumFixture.create(artist));

			// when
			boolean exists = albumWatchRepository.existsByMemberIdAndAlbumId(member.getId(), album.getId());
			Optional<AlbumWatch> found = albumWatchRepository.findByMemberIdAndAlbumId(member.getId(),
					album.getId());

			// then
			assertThat(exists).isFalse();
			assertThat(found).isEmpty();
		}
	}

	@Nested
	@DisplayName("findAllByMemberId()")
	class FindAllByMemberId {

		private Pageable pageable(int page, int size) {
			return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
		}

		@Test
		@DisplayName("본인 구독만 등록일 내림차순으로 album 을 함께 로딩해 반환한다")
		void returnsOwnWatchesSortedDescWithAlbum() {
			// given
			Member member = memberRepository.save(MemberFixture.create("album-watch-repo-sort@groove.com"));
			Member other = memberRepository.save(MemberFixture.create("album-watch-repo-sort-other@groove.com"));
			Artist artist = artistRepository.save(ArtistFixture.create());
			Album firstAlbum = albumRepository.save(AlbumFixture.create(artist, "앨범1"));
			Album secondAlbum = albumRepository.save(AlbumFixture.create(artist, "앨범2"));

			AlbumWatch firstWatch = albumWatchRepository.save(AlbumWatchFixture.create(member, firstAlbum));
			AlbumWatch secondWatch = albumWatchRepository.save(AlbumWatchFixture.create(member, secondAlbum));
			albumWatchRepository.save(AlbumWatchFixture.create(other, firstAlbum));

			// when
			Page<AlbumWatch> result = albumWatchRepository.findAllByMemberId(member.getId(), pageable(0, 20));

			// then
			assertThat(result.getContent()).extracting(AlbumWatch::getId)
					.containsExactly(secondWatch.getId(), firstWatch.getId());
			assertThat(result.getContent()).extracting(watch -> watch.getAlbum().getTitle())
					.containsExactly("앨범2", "앨범1");
		}

		@Test
		@DisplayName("구독한 앨범이 없으면 빈 페이지를 반환한다")
		void returnsEmptyPageWhenNoWatches() {
			// given
			Member member = memberRepository.save(MemberFixture.create("album-watch-repo-empty@groove.com"));

			// when
			Page<AlbumWatch> result = albumWatchRepository.findAllByMemberId(member.getId(), pageable(0, 20));

			// then
			assertThat(result.getContent()).isEmpty();
		}

		@Test
		@DisplayName("size 를 넘겨도 그 크기만큼만 반환하고 다음 페이지로 이어진다")
		void limitsBySizeAndContinuesToNextPage() {
			// given
			Member member = memberRepository.save(MemberFixture.create("album-watch-repo-paging@groove.com"));
			Artist artist = artistRepository.save(ArtistFixture.create());
			Album firstAlbum = albumRepository.save(AlbumFixture.create(artist, "앨범P1"));
			Album secondAlbum = albumRepository.save(AlbumFixture.create(artist, "앨범P2"));
			Album thirdAlbum = albumRepository.save(AlbumFixture.create(artist, "앨범P3"));

			AlbumWatch firstWatch = albumWatchRepository.save(AlbumWatchFixture.create(member, firstAlbum));
			AlbumWatch secondWatch = albumWatchRepository.save(AlbumWatchFixture.create(member, secondAlbum));
			AlbumWatch thirdWatch = albumWatchRepository.save(AlbumWatchFixture.create(member, thirdAlbum));

			// when
			Page<AlbumWatch> firstPage = albumWatchRepository.findAllByMemberId(member.getId(), pageable(0, 2));
			Page<AlbumWatch> secondPage = albumWatchRepository.findAllByMemberId(member.getId(), pageable(1, 2));

			// then
			assertThat(firstPage.getContent()).extracting(AlbumWatch::getId)
					.containsExactly(thirdWatch.getId(), secondWatch.getId());
			assertThat(secondPage.getContent()).extracting(AlbumWatch::getId)
					.containsExactly(firstWatch.getId());
		}
	}
}
