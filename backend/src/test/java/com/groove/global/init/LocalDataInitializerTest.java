package com.groove.global.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.groove.fixture.MemberFixture;
import com.groove.fixture.ProductFixture;
import com.groove.inventory.service.StockService;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.product.entity.Genre;
import com.groove.product.entity.Product;
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
	ProductRepository productRepository;

	@Mock
	StockService stockService;

	@Mock
	ReviewRepository reviewRepository;

	@Mock
	LocalSignalSeeder localSignalSeeder;

	@Nested
	@DisplayName("run()")
	class Run {

		@Test
		@DisplayName("상품 데이터가 이미 있으면 카탈로그 시딩을 건너뛴다")
		void skipsCatalogWhenProductsExist() {
			// given
			LocalDataInitializer initializer = newInitializer();
			given(productRepository.count()).willReturn(1L);
			given(reviewRepository.count()).willReturn(1L);

			// when
			initializer.run(null);

			// then
			verify(productRepository, never()).save(any());
			verify(artistRepository, never()).saveAll(any());
		}

		@Test
		@DisplayName("회원 이메일이 이미 있으면 해당 회원 생성을 건너뛴다")
		void skipsMemberWhenEmailExists() {
			// given
			LocalDataInitializer initializer = newInitializer();
			given(memberRepository.existsByEmail(anyString())).willReturn(true);
			given(productRepository.count()).willReturn(1L);
			given(reviewRepository.count()).willReturn(1L);

			// when
			initializer.run(null);

			// then
			verify(memberRepository, never()).save(any());
		}

		@Test
		@DisplayName("데이터가 비어 있으면 회원 3명과 시드 정의만큼의 앨범을 시딩한다")
		void seedsEveryAlbumWhenEmpty() {
			// given
			LocalDataInitializer initializer = newInitializer();
			given(productRepository.count()).willReturn(0L);
			given(reviewRepository.count()).willReturn(1L);
			given(genreRepository.findByName(anyString())).willReturn(Optional.empty());
			given(genreRepository.save(any(Genre.class))).willAnswer(invocation -> invocation.getArgument(0));
			given(labelRepository.saveAll(any())).willAnswer(invocation -> invocation.getArgument(0));
			given(artistRepository.saveAll(any())).willAnswer(invocation -> invocation.getArgument(0));
			given(productRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

			// when
			initializer.run(null);

			// then
			int albumCount = SeedAlbums.ALBUMS.size();
			verify(productRepository, times(albumCount)).save(any());
			verify(stockService, times(albumCount)).create(any(), anyInt());
			verify(memberRepository, times(3)).save(any());
		}

		@Test
		@DisplayName("리뷰 데이터가 이미 있으면 리뷰 시딩을 건너뛴다")
		void skipsReviewsWhenReviewsExist() {
			// given
			LocalDataInitializer initializer = newInitializer();
			given(productRepository.count()).willReturn(1L);
			given(reviewRepository.count()).willReturn(1L);

			// when
			initializer.run(null);

			// then
			verify(reviewRepository, never()).saveAll(any());
		}

		@Test
		@DisplayName("카탈로그·리뷰를 건너뛰어도 취향/행동 신호 시딩은 user1·user2 를 넘겨 호출한다")
		void delegatesSignalSeedingWithDemoMembers() {
			// given
			LocalDataInitializer initializer = newInitializer();
			given(productRepository.count()).willReturn(1L);
			given(reviewRepository.count()).willReturn(1L);
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
		@DisplayName("리뷰 데이터가 비어 있으면 상품마다 회원 2명의 리뷰를 시딩한다")
		void seedsTwoReviewsPerProductWhenEmpty() {
			// given
			LocalDataInitializer initializer = newInitializer();
			given(productRepository.count()).willReturn(1L);
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
				artistRepository, productRepository, stockService, reviewRepository, localSignalSeeder);
	}
}
