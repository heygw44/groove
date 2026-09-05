package com.groove.recommend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.GenreFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.TasteProfileFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Genre;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.GenreRepository;
import com.groove.recommend.dto.TasteProfileResponse;
import com.groove.recommend.dto.TasteProfileUpdateRequest;
import com.groove.recommend.entity.Decade;
import com.groove.recommend.entity.MemberTasteArtist;
import com.groove.recommend.entity.MemberTasteDecade;
import com.groove.recommend.entity.MemberTasteGenre;
import com.groove.recommend.entity.MemberTasteProfile;
import com.groove.recommend.repository.MemberTasteArtistRepository;
import com.groove.recommend.repository.MemberTasteDecadeRepository;
import com.groove.recommend.repository.MemberTasteGenreRepository;
import com.groove.recommend.repository.MemberTasteProfileRepository;

@ExtendWith(MockitoExtension.class)
class TasteProfileServiceTest {

	private static final Long MEMBER_ID = 1L;
	private static final Long PROFILE_ID = 10L;
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 5, 10, 0);

	@Mock
	private MemberTasteProfileRepository memberTasteProfileRepository;

	@Mock
	private MemberTasteGenreRepository memberTasteGenreRepository;

	@Mock
	private MemberTasteArtistRepository memberTasteArtistRepository;

	@Mock
	private MemberTasteDecadeRepository memberTasteDecadeRepository;

	@Mock
	private MemberRepository memberRepository;

	@Mock
	private GenreRepository genreRepository;

	@Mock
	private ArtistRepository artistRepository;

	private TasteProfileService tasteProfileService;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
		tasteProfileService = new TasteProfileService(memberTasteProfileRepository, memberTasteGenreRepository,
				memberTasteArtistRepository, memberTasteDecadeRepository, memberRepository, genreRepository,
				artistRepository, clock);
	}

	private MemberTasteProfile savedProfile() {
		Member member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
		return TasteProfileFixture.withId(TasteProfileFixture.create(member), PROFILE_ID);
	}

	@Nested
	@DisplayName("getMyProfile()")
	class GetMyProfile {

		@Test
		@DisplayName("프로필이 있으면 선호 장르·아티스트·연대를 반환한다")
		void returnsProfile() {
			// given
			MemberTasteProfile profile = savedProfile();
			ReflectionTestUtils.setField(profile, "updatedAt", NOW);
			Genre genre = GenreFixture.withId(GenreFixture.create("Jazz"), 3L);
			Artist artist = ArtistFixture.withId(12L);
			given(memberTasteProfileRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(profile));
			given(memberTasteGenreRepository.findAllByProfileId(PROFILE_ID))
					.willReturn(List.of(MemberTasteGenre.of(profile, genre)));
			given(memberTasteArtistRepository.findAllByProfileId(PROFILE_ID))
					.willReturn(List.of(MemberTasteArtist.of(profile, artist)));
			given(memberTasteDecadeRepository.findAllByProfileId(PROFILE_ID))
					.willReturn(List.of(MemberTasteDecade.of(profile, Decade.D1970),
							MemberTasteDecade.of(profile, Decade.D1960)));

			// when
			TasteProfileResponse response = tasteProfileService.getMyProfile(MEMBER_ID);

			// then
			assertThat(response.genres()).extracting("id").containsExactly(3L);
			assertThat(response.artists()).extracting("id").containsExactly(12L);
			assertThat(response.decades()).containsExactly(Decade.D1960, Decade.D1970);
			assertThat(response.updatedAt()).isEqualTo(NOW);
		}

		@Test
		@DisplayName("프로필이 없으면 RECOMMEND_PROFILE_NOT_FOUND 예외를 던진다")
		void throwsWhenProfileNotFound() {
			// given
			given(memberTasteProfileRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> tasteProfileService.getMyProfile(MEMBER_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.RECOMMEND_PROFILE_NOT_FOUND);
		}
	}

	@Nested
	@DisplayName("update()")
	class Update {

		private TasteProfileUpdateRequest request() {
			return new TasteProfileUpdateRequest(List.of(3L), List.of(12L), List.of(Decade.D1970));
		}

		private void givenGenreAndArtistExist() {
			given(genreRepository.findAllById(any()))
					.willReturn(List.of(GenreFixture.withId(GenreFixture.create("Jazz"), 3L)));
			given(artistRepository.findAllById(any())).willReturn(List.of(ArtistFixture.withId(12L)));
		}

		@Test
		@DisplayName("프로필이 없으면 새로 만들고 선호 항목을 저장한다")
		void createsProfileWhenAbsent() {
			// given
			givenGenreAndArtistExist();
			Member member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
			given(memberTasteProfileRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.empty());
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(memberTasteProfileRepository.saveAndFlush(any(MemberTasteProfile.class)))
					.willAnswer(invocation -> TasteProfileFixture.withId(invocation.getArgument(0), PROFILE_ID));

			// when
			TasteProfileResponse response = tasteProfileService.update(MEMBER_ID, request());

			// then
			verify(memberTasteProfileRepository).saveAndFlush(any(MemberTasteProfile.class));
			assertThat(response.genres()).extracting("id").containsExactly(3L);
			assertThat(response.artists()).extracting("id").containsExactly(12L);
			assertThat(response.decades()).containsExactly(Decade.D1970);
			assertThat(response.updatedAt()).isEqualTo(NOW);
		}

		@Test
		@DisplayName("프로필이 이미 있으면 새로 만들지 않고 선호 항목만 교체한다")
		void replacesItemsWhenProfileExists() {
			// given
			givenGenreAndArtistExist();
			given(memberTasteProfileRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(savedProfile()));

			// when
			tasteProfileService.update(MEMBER_ID, request());

			// then
			verify(memberTasteProfileRepository, never()).saveAndFlush(any(MemberTasteProfile.class));
			verify(memberTasteGenreRepository).replaceAll(eq(PROFILE_ID), anyList());
			verify(memberTasteArtistRepository).replaceAll(eq(PROFILE_ID), anyList());
			verify(memberTasteDecadeRepository).replaceAll(eq(PROFILE_ID), anyList());
			verify(memberTasteProfileRepository).touchUpdatedAt(PROFILE_ID, NOW);
		}

		@Test
		@DisplayName("선호 항목을 빈 배열로 보내면 모두 지운 상태로 교체한다")
		void clearsItemsWhenEmpty() {
			// given
			given(genreRepository.findAllById(any()))
					.willReturn(List.of(GenreFixture.withId(GenreFixture.create("Jazz"), 3L)));
			given(memberTasteProfileRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(savedProfile()));
			TasteProfileUpdateRequest request = new TasteProfileUpdateRequest(List.of(3L), List.of(), List.of());

			// when
			TasteProfileResponse response = tasteProfileService.update(MEMBER_ID, request);

			// then
			ArgumentCaptor<List<MemberTasteArtist>> captor = ArgumentCaptor.captor();
			verify(memberTasteArtistRepository).replaceAll(eq(PROFILE_ID), captor.capture());
			assertThat(captor.getValue()).isEmpty();
			assertThat(response.artists()).isEmpty();
			assertThat(response.decades()).isEmpty();
		}

		@Test
		@DisplayName("존재하지 않는 장르 id 가 있으면 GENRE_NOT_FOUND 예외를 던진다")
		void throwsWhenGenreNotFound() {
			// given
			given(genreRepository.findAllById(any())).willReturn(List.of());

			// when & then
			assertThatThrownBy(() -> tasteProfileService.update(MEMBER_ID, request()))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.GENRE_NOT_FOUND);
		}

		@Test
		@DisplayName("존재하지 않는 아티스트 id 가 있으면 ARTIST_NOT_FOUND 예외를 던진다")
		void throwsWhenArtistNotFound() {
			// given
			given(genreRepository.findAllById(any()))
					.willReturn(List.of(GenreFixture.withId(GenreFixture.create("Jazz"), 3L)));
			given(artistRepository.findAllById(any())).willReturn(List.of());

			// when & then
			assertThatThrownBy(() -> tasteProfileService.update(MEMBER_ID, request()))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ARTIST_NOT_FOUND);
		}
	}
}
