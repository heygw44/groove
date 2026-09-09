package com.groove.global.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.GenreFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.ProductFixture;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderItem;
import com.groove.order.entity.OrderStatus;
import com.groove.order.repository.OrderRepository;
import com.groove.payment.repository.PaymentRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Genre;
import com.groove.product.entity.Product;
import com.groove.product.repository.ProductRepository;
import com.groove.recommend.entity.MemberTasteProfile;
import com.groove.recommend.entity.ProductViewLog;
import com.groove.recommend.repository.MemberTasteArtistRepository;
import com.groove.recommend.repository.MemberTasteDecadeRepository;
import com.groove.recommend.repository.MemberTasteGenreRepository;
import com.groove.recommend.repository.MemberTasteProfileRepository;
import com.groove.recommend.repository.ProductViewLogRepository;
import com.groove.wishlist.entity.Wishlist;
import com.groove.wishlist.repository.WishlistRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LocalSignalSeederTest {

	private static final int GENRE_COUNT = 10;
	private static final int PRODUCTS_PER_GENRE = 12;
	private static final int ARTIST_COUNT = 20;

	@Mock
	MemberRepository memberRepository;

	@Mock
	PasswordEncoder passwordEncoder;

	@Mock
	ProductRepository productRepository;

	@Mock
	WishlistRepository wishlistRepository;

	@Mock
	OrderRepository orderRepository;

	@Mock
	PaymentRepository paymentRepository;

	@Mock
	MemberTasteProfileRepository memberTasteProfileRepository;

	@Mock
	MemberTasteGenreRepository memberTasteGenreRepository;

	@Mock
	MemberTasteArtistRepository memberTasteArtistRepository;

	@Mock
	MemberTasteDecadeRepository memberTasteDecadeRepository;

	@Mock
	ProductViewLogRepository productViewLogRepository;

	@Nested
	@DisplayName("seed()")
	class Seed {

		@Test
		@DisplayName("시드 회원이 이미 있으면 아무것도 시딩하지 않는다")
		void skipsWhenSeedMembersExist() {
			// given
			given(memberRepository.existsByEmail("digger01@groove.com")).willReturn(true);

			// when
			newSeeder().seed(List.of());

			// then
			verify(memberRepository, never()).save(any());
			verify(productRepository, never()).findAll(any(Sort.class));
		}

		@Test
		@DisplayName("상품이 하나도 없으면 시딩을 건너뛴다")
		void skipsWhenNoProducts() {
			// given
			given(memberRepository.existsByEmail(anyString())).willReturn(false);
			given(productRepository.findAll(any(Sort.class))).willReturn(List.of());

			// when
			newSeeder().seed(List.of());

			// then
			verify(memberRepository, never()).save(any());
		}

		@Test
		@DisplayName("회원마다 취향 프로필과 위시리스트를 하나씩 만든다")
		void seedsProfileAndWishlistPerMember() {
			// given
			stubCatalog();

			// when
			newSeeder().seed(List.of());

			// then
			verify(memberRepository, times(LocalSignalSeeder.MEMBER_COUNT)).save(any());
			verify(memberTasteProfileRepository, times(LocalSignalSeeder.MEMBER_COUNT)).save(any());
			verify(memberTasteGenreRepository, times(LocalSignalSeeder.MEMBER_COUNT)).save(any());

			assertThat(capturedWishlists()).allSatisfy(wishes ->
					assertThat(wishes).hasSizeBetween(LocalSignalSeeder.WISHLIST_MIN, LocalSignalSeeder.WISHLIST_MAX));
		}

		@Test
		@DisplayName("시드 주문은 PAID 상태이고 주문번호가 겹치지 않는다")
		void seedsPaidOrdersWithUniqueNumbers() {
			// given
			stubCatalog();

			// when
			newSeeder().seed(List.of());

			// then
			List<Order> orders = capturedOrders();
			assertThat(orders).hasSizeGreaterThanOrEqualTo(
					LocalSignalSeeder.MEMBER_COUNT * LocalSignalSeeder.ORDER_MIN);
			assertThat(orders).allSatisfy(order -> {
				assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
				assertThat(order.getItems()).isNotEmpty().hasSizeLessThanOrEqualTo(
						LocalSignalSeeder.ORDER_ITEM_MAX);
			});
			assertThat(orders).extracting(Order::getOrderNumber).doesNotHaveDuplicates();
		}

		@Test
		@DisplayName("주문 상품은 같은 회원의 위시리스트와 겹치지 않는다")
		void keepsOrderItemsOutOfWishlist() {
			// given
			stubCatalog();

			// when
			newSeeder().seed(List.of());

			// then
			List<Order> orders = capturedOrders();
			for (List<Wishlist> wishes : capturedWishlists()) {
				Member member = wishes.get(0).getMember();
				Set<Product> wished = new HashSet<>(wishes.stream().map(Wishlist::getProduct).toList());
				List<Product> ordered = orders.stream()
						.filter(order -> order.getMember() == member)
						.flatMap(order -> order.getItems().stream())
						.map(OrderItem::getProduct)
						.toList();
				assertThat(ordered).doesNotContainAnyElementsOf(wished);
			}
		}

		@Test
		@DisplayName("위시리스트의 70% 이상은 배정된 취향 클러스터 장르에서 나온다")
		void picksMostOfWishlistFromTasteCluster() {
			// given
			stubCatalog();

			// when
			newSeeder().seed(List.of());

			// then
			List<List<Wishlist>> wishlists = capturedWishlists();
			long clusterHits = 0;
			long total = 0;
			for (int index = 0; index < wishlists.size(); index++) {
				Long clusterGenreId = (long) (index % GENRE_COUNT + 1);
				for (Wishlist wish : wishlists.get(index)) {
					total++;
					if (genreIdsOf(wish.getProduct()).contains(clusterGenreId)) {
						clusterHits++;
					}
				}
			}
			// 노이즈 30% 를 섞으므로 정확히 70% 는 아니다. 무작위(1/10)와 확연히 다르다는 것만 확인한다.
			assertThat((double) clusterHits / total).isGreaterThan(0.5);
		}

		@Test
		@DisplayName("고정 시드를 쓰므로 두 번 실행해도 같은 위시리스트가 나온다")
		void producesSameSignalsOnEveryRun() {
			// given
			stubCatalog();

			// when
			List<List<Long>> first = runAndCaptureWishlistProductIds();
			List<List<Long>> second = runAndCaptureWishlistProductIds();

			// then
			assertThat(first).isEqualTo(second);
		}

		@Test
		@DisplayName("데모 회원을 넘기면 그 회원에게만 조회 로그를 심는다")
		void seedsViewLogsForDemoMembersOnly() {
			// given
			stubCatalog();
			Member demo = MemberFixture.create("user1@groove.com");

			// when
			newSeeder().seed(List.of(demo));

			// then
			ArgumentCaptor<List<ProductViewLog>> captor = ArgumentCaptor.forClass(List.class);
			verify(productViewLogRepository).saveAll(captor.capture());
			assertThat(captor.getValue()).hasSize(LocalSignalSeeder.VIEW_LOG_COUNT);
			assertThat(captor.getValue()).allSatisfy(log -> assertThat(log.getMember()).isSameAs(demo));
		}
	}

	private List<List<Wishlist>> capturedWishlists() {
		ArgumentCaptor<List<Wishlist>> captor = ArgumentCaptor.forClass(List.class);
		verify(wishlistRepository, times(LocalSignalSeeder.MEMBER_COUNT)).saveAll(captor.capture());
		return captor.getAllValues();
	}

	private List<Order> capturedOrders() {
		ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
		verify(orderRepository, atLeast(1)).save(captor.capture());
		return captor.getAllValues();
	}

	private List<List<Long>> runAndCaptureWishlistProductIds() {
		newSeeder().seed(List.of());
		List<List<Long>> ids = capturedWishlists().stream()
				.map(wishes -> wishes.stream().map(wish -> wish.getProduct().getId()).toList())
				.toList();
		Mockito.reset(wishlistRepository);
		return ids;
	}

	private Set<Long> genreIdsOf(Product product) {
		return product.getProductGenres().stream()
				.map(link -> link.getGenre().getId())
				.collect(Collectors.toSet());
	}

	private void stubCatalog() {
		given(memberRepository.existsByEmail(anyString())).willReturn(false);
		given(productRepository.findAll(any(Sort.class))).willReturn(catalog());
		given(memberRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
		given(memberTasteProfileRepository.save(any())).willAnswer(invocation -> {
			MemberTasteProfile profile = invocation.getArgument(0);
			ReflectionTestUtils.setField(profile, "id", 1L);
			return profile;
		});
	}

	/** 장르 10개 × 상품 12개. 각 상품은 장르 하나만 갖고 아티스트/연대는 골고루 돌아간다. */
	private List<Product> catalog() {
		List<Genre> genres = IntStream.range(0, GENRE_COUNT)
				.mapToObj(index -> GenreFixture.withId(GenreFixture.create("Genre" + index), (long) (index + 1)))
				.toList();
		List<Product> products = new ArrayList<>();
		long id = 0;
		for (int genreIndex = 0; genreIndex < GENRE_COUNT; genreIndex++) {
			for (int i = 0; i < PRODUCTS_PER_GENRE; i++) {
				id++;
				Artist artist = ArtistFixture.withId(ArtistFixture.create("Artist" + id % ARTIST_COUNT),
						id % ARTIST_COUNT + 1);
				Product product = ProductFixture.withId(ProductFixture.create(artist, "Album" + id), id);
				product.addGenre(genres.get(genreIndex));
				ReflectionTestUtils.setField(product, "releaseDate", LocalDate.of(1960 + (int) (id % 60), 1, 1));
				products.add(product);
			}
		}
		return products;
	}

	private LocalSignalSeeder newSeeder() {
		return new LocalSignalSeeder(memberRepository, passwordEncoder, productRepository, wishlistRepository,
				orderRepository, paymentRepository, memberTasteProfileRepository, memberTasteGenreRepository,
				memberTasteArtistRepository, memberTasteDecadeRepository, productViewLogRepository);
	}
}
