package com.groove.global.init;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import com.groove.fixture.AlbumFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.GenreFixture;
import com.groove.fixture.LabelFixture;
import com.groove.fixture.MemberFixture;
import com.groove.inventory.service.StockService;
import com.groove.member.entity.Member;
import com.groove.product.entity.Album;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Genre;
import com.groove.product.entity.Label;
import com.groove.product.repository.AlbumRepository;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.GenreRepository;
import com.groove.product.repository.LabelRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.stats.service.SalesAggregationService;

@ExtendWith(MockitoExtension.class)
class LocalDataInitializerTest {

	@Mock
	GenreRepository genreRepository;

	@Mock
	LabelRepository labelRepository;

	@Mock
	ArtistRepository artistRepository;

	@Mock
	AlbumRepository albumRepository;

	@Mock
	ProductRepository productRepository;

	@Mock
	StockService stockService;

	@Mock
	LocalDemoDataSeeder localDemoDataSeeder;

	@Mock
	ObjectProvider<LocalDemoDataSeeder> localDemoDataSeederProvider;

	@Mock
	LocalSignalSeeder localSignalSeeder;

	@Mock
	ObjectProvider<LocalSignalSeeder> localSignalSeederProvider;

	@Mock
	SalesAggregationService salesAggregationService;

	private final Clock clock = Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"), ZoneId.of("Asia/Seoul"));

	/** 이름/키로 찾지 못하면 넘겨받은 엔티티를 그대로 저장한 것처럼 되돌려준다. 미사용 시 실패하지 않게 lenient 로 둔다. */
	@BeforeEach
	void stubCatalogSaves() {
		lenient().when(genreRepository.save(any(Genre.class))).thenAnswer(invocation -> invocation.getArgument(0));
		lenient().when(labelRepository.save(any(Label.class))).thenAnswer(invocation -> invocation.getArgument(0));
		lenient().when(artistRepository.save(any(Artist.class))).thenAnswer(invocation -> invocation.getArgument(0));
		lenient().when(albumRepository.save(any(Album.class))).thenAnswer(invocation -> invocation.getArgument(0));
		lenient().when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Nested
	@DisplayName("run()")
	class Run {

		@Test
		@DisplayName("장르/레이블/아티스트/앨범이 모두 이미 있으면 새 프레싱을 만들지 않는다")
		void skipsPressingsWhenCatalogAlreadySeeded() {
			// given
			LocalDataInitializer initializer = newInitializer();
			given(genreRepository.findByName(anyString()))
					.willReturn(Optional.of(GenreFixture.withId(GenreFixture.create("Jazz"), 1L)));
			given(labelRepository.findFirstByNameOrderByIdAsc(anyString()))
					.willReturn(Optional.of(LabelFixture.withId(LabelFixture.create(), 1L)));
			Artist existingArtist = ArtistFixture.withId(1L);
			given(artistRepository.findFirstByNameOrderByIdAsc(anyString()))
					.willReturn(Optional.of(existingArtist));
			Album existingAlbum = AlbumFixture.withId(AlbumFixture.create(existingArtist), 1L);
			given(albumRepository.findFirstByTitleAndArtistIdOrderByIdAsc(anyString(), any()))
					.willReturn(Optional.of(existingAlbum));
			given(productRepository.existsByAlbumId(any())).willReturn(true);

			// when
			initializer.run(null);

			// then — 두 번째 실행이라고 가정해도 아무것도 새로 만들지 않아 상품 수가 늘지 않는다
			verify(genreRepository, never()).save(any());
			verify(labelRepository, never()).save(any());
			verify(artistRepository, never()).save(any());
			verify(albumRepository, never()).save(any());
			verify(productRepository, never()).save(any());
			verify(stockService, never()).create(any(), anyInt());
		}

		@Test
		@DisplayName("장르/레이블/아티스트/앨범이 하나도 없으면 시드 정의만큼의 앨범·프레싱을 시딩한다")
		void seedsEveryAlbumWhenCatalogEmpty() {
			// given
			LocalDataInitializer initializer = newInitializer();

			// when
			initializer.run(null);

			// then
			int albumCount = SeedAlbums.ALBUMS.size();
			int pressingCount = IntStream.range(0, albumCount).map(initializer::pressingCountFor).sum();
			verify(albumRepository, times(albumCount)).save(any());
			verify(productRepository, times(pressingCount)).save(any());
			verify(stockService, times(pressingCount)).create(any(), anyInt());
		}

		@Test
		@DisplayName("데모 데이터 시더 빈이 있으면 계정·리뷰를 시딩하고 그 결과를 신호 시더에 넘긴다")
		void delegatesSignalSeedingWithDemoMembersFromDemoDataSeeder() {
			// given
			LocalDataInitializer initializer = newInitializer();
			given(localDemoDataSeederProvider.getIfAvailable()).willReturn(localDemoDataSeeder);
			given(localSignalSeederProvider.getIfAvailable()).willReturn(localSignalSeeder);
			Member user1 = MemberFixture.withId(MemberFixture.create("user1@groove.com"), 1L);
			Member user2 = MemberFixture.withId(MemberFixture.create("user2@groove.com"), 2L);
			List<Member> demoMembers = List.of(user1, user2);
			given(localDemoDataSeeder.seed()).willReturn(demoMembers);

			// when
			initializer.run(null);

			// then
			verify(localDemoDataSeeder).seed();
			verify(localSignalSeeder).seed(demoMembers);
		}

		@Test
		@DisplayName("신호 시더 빈이 없으면 신호 시딩을 건너뛴다")
		void skipsSignalSeedingWhenSignalSeederAbsent() {
			// given
			LocalDataInitializer initializer = newInitializer();
			given(localDemoDataSeederProvider.getIfAvailable()).willReturn(localDemoDataSeeder);
			given(localSignalSeederProvider.getIfAvailable()).willReturn(null);
			given(localDemoDataSeeder.seed()).willReturn(List.of());

			// when
			initializer.run(null);

			// then
			verify(localSignalSeeder, never()).seed(any());
		}

		@Test
		@DisplayName("데모 데이터 시더 빈이 없으면 계정·리뷰 시딩과 신호 시딩을 모두 건너뛴다")
		void skipsDemoDataAndSignalSeedingWhenDemoDataSeederAbsent() {
			// given — seed 프로파일처럼 local 전용 빈이 등록되지 않은 상황을 재현한다
			LocalDataInitializer initializer = newInitializer();
			given(localDemoDataSeederProvider.getIfAvailable()).willReturn(null);
			given(localSignalSeederProvider.getIfAvailable()).willReturn(localSignalSeeder);

			// when
			initializer.run(null);

			// then
			verify(localDemoDataSeeder, never()).seed();
			verify(localSignalSeeder).seed(List.of());
		}
	}

	private LocalDataInitializer newInitializer() {
		return new LocalDataInitializer(genreRepository, labelRepository, artistRepository, albumRepository,
				productRepository, stockService, localDemoDataSeederProvider, localSignalSeederProvider,
				salesAggregationService, clock);
	}
}
