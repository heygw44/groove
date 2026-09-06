package com.groove.global.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.groove.fixture.AlbumFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.GenreFixture;
import com.groove.fixture.LabelFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.ProductFixture;
import com.groove.inventory.service.StockService;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.product.entity.Album;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Genre;
import com.groove.product.entity.Label;
import com.groove.product.entity.Product;
import com.groove.product.repository.AlbumRepository;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.GenreRepository;
import com.groove.product.repository.LabelRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.review.entity.Review;
import com.groove.review.repository.ReviewRepository;

@ExtendWith(MockitoExtension.class)
class LocalDataInitializerTest {

	@Mock
	MemberRepository memberRepository;

	@Mock
	PasswordEncoder passwordEncoder;

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
	ReviewRepository reviewRepository;

	@Mock
	LocalSignalSeeder localSignalSeeder;

	@Mock
	ObjectProvider<LocalSignalSeeder> localSignalSeederProvider;

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
		@DisplayName("회원 이메일이 이미 있으면 해당 회원 생성을 건너뛴다")
		void skipsMemberWhenEmailExists() {
			// given
			LocalDataInitializer initializer = newInitializer();
			given(memberRepository.existsByEmail(anyString())).willReturn(true);
			given(reviewRepository.count()).willReturn(1L);

			// when
			initializer.run(null);

			// then
			verify(memberRepository, never()).save(any());
		}

		@Test
		@DisplayName("장르/레이블/아티스트/앨범이 모두 이미 있으면 새 프레싱을 만들지 않는다")
		void skipsPressingsWhenCatalogAlreadySeeded() {
			// given
			LocalDataInitializer initializer = newInitializer();
			given(memberRepository.existsByEmail(anyString())).willReturn(true);
			given(reviewRepository.count()).willReturn(1L);
			given(genreRepository.findByName(anyString()))
					.willReturn(Optional.of(GenreFixture.withId(GenreFixture.create("Jazz"), 1L)));
			given(labelRepository.findFirstByNameOrderByIdAsc(anyString()))
					.willReturn(Optional.of(LabelFixture.withId(LabelFixture.create(), 1L)));
			Artist existingArtist = ArtistFixture.withId(1L);
			given(artistRepository.findFirstByNameOrderByIdAsc(anyString())).willReturn(Optional.of(existingArtist));
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
		@DisplayName("장르/레이블/아티스트/앨범이 하나도 없으면 회원 3명과 시드 정의만큼의 앨범·프레싱을 시딩한다")
		void seedsEveryAlbumWhenCatalogEmpty() {
			// given
			LocalDataInitializer initializer = newInitializer();
			given(reviewRepository.count()).willReturn(1L);

			// when
			initializer.run(null);

			// then
			int albumCount = SeedAlbums.ALBUMS.size();
			int pressingCount = IntStream.range(0, albumCount).map(initializer::pressingCountFor).sum();
			verify(albumRepository, times(albumCount)).save(any());
			verify(productRepository, times(pressingCount)).save(any());
			verify(stockService, times(pressingCount)).create(any(), anyInt());
			verify(memberRepository, times(3)).save(any());
		}

		@Test
		@DisplayName("리뷰 데이터가 이미 있으면 리뷰 시딩을 건너뛴다")
		void skipsReviewsWhenReviewsExist() {
			// given
			LocalDataInitializer initializer = newInitializer();
			given(reviewRepository.count()).willReturn(1L);

			// when
			initializer.run(null);

			// then
			verify(reviewRepository, never()).saveAll(any());
		}

		@Test
		@DisplayName("취향/행동 신호 시딩은 user1·user2 를 찾아 넘겨 호출한다")
		void delegatesSignalSeedingWithDemoMembers() {
			// given
			LocalDataInitializer initializer = newInitializer();
			given(reviewRepository.count()).willReturn(1L);
			given(localSignalSeederProvider.getIfAvailable()).willReturn(localSignalSeeder);
			Member user1 = MemberFixture.withId(MemberFixture.create("user1@groove.com"), 1L);
			Member user2 = MemberFixture.withId(MemberFixture.create("user2@groove.com"), 2L);
			given(memberRepository.findByEmail("user1@groove.com")).willReturn(Optional.of(user1));
			given(memberRepository.findByEmail("user2@groove.com")).willReturn(Optional.of(user2));

			// when
			initializer.run(null);

			// then
			verify(localSignalSeeder).seed(List.of(user1, user2));
		}

		@Test
		@DisplayName("LocalSignalSeeder 빈이 없으면 신호 시딩을 건너뛴다")
		void skipsSignalSeedingWhenSeederAbsent() {
			// given
			LocalDataInitializer initializer = newInitializer();
			given(reviewRepository.count()).willReturn(1L);
			given(localSignalSeederProvider.getIfAvailable()).willReturn(null);

			// when
			initializer.run(null);

			// then — 신호 시딩용 회원 조회 자체가 일어나지 않는다
			verify(memberRepository, never()).findByEmail(anyString());
		}

		@Test
		@DisplayName("리뷰 데이터가 비어 있으면 상품마다 회원 2명의 리뷰를 시딩한다")
		void seedsTwoReviewsPerProductWhenEmpty() {
			// given
			LocalDataInitializer initializer = newInitializer();
			given(reviewRepository.count()).willReturn(0L);
			Member user1 = MemberFixture.withId(MemberFixture.create("user1@groove.com"), 1L);
			Member user2 = MemberFixture.withId(MemberFixture.create("user2@groove.com"), 2L);
			given(memberRepository.findByEmail("user1@groove.com")).willReturn(Optional.of(user1));
			given(memberRepository.findByEmail("user2@groove.com")).willReturn(Optional.of(user2));
			int productCount = SeedAlbums.ALBUMS.size();
			List<Product> products = IntStream.range(0, productCount)
					.mapToObj(i -> ProductFixture.withId(ProductFixture.create(null), (long) (i + 1)))
					.toList();
			given(productRepository.findAll(any(Sort.class))).willReturn(products);

			// when
			initializer.run(null);

			// then
			ArgumentCaptor<List<Review>> captor = ArgumentCaptor.forClass(List.class);
			verify(reviewRepository).saveAll(captor.capture());
			assertThat(captor.getValue()).hasSize(productCount * 2);
			verify(productRepository, times(productCount)).refreshReviewStats(anyLong());
		}
	}

	private LocalDataInitializer newInitializer() {
		return new LocalDataInitializer(memberRepository, passwordEncoder, genreRepository, labelRepository,
				artistRepository, albumRepository, productRepository, stockService, reviewRepository,
				localSignalSeederProvider);
	}
}
