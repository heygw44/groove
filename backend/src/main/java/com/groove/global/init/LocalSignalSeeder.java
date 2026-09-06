package com.groove.global.init;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.order.entity.Order;
import com.groove.order.entity.ShippingAddress;
import com.groove.order.repository.OrderRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Genre;
import com.groove.product.entity.Product;
import com.groove.product.entity.ProductGenre;
import com.groove.product.repository.ProductRepository;
import com.groove.recommend.entity.Decade;
import com.groove.recommend.entity.MemberTasteArtist;
import com.groove.recommend.entity.MemberTasteDecade;
import com.groove.recommend.entity.MemberTasteGenre;
import com.groove.recommend.entity.MemberTasteProfile;
import com.groove.recommend.entity.ProductViewLog;
import com.groove.recommend.repository.MemberTasteArtistRepository;
import com.groove.recommend.repository.MemberTasteDecadeRepository;
import com.groove.recommend.repository.MemberTasteGenreRepository;
import com.groove.recommend.repository.MemberTasteProfileRepository;
import com.groove.recommend.repository.ProductViewLogRepository;
import com.groove.wishlist.entity.Wishlist;
import com.groove.wishlist.repository.WishlistRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 추천이 실제로 동작하는 모습을 보려면 취향·위시·구매 신호가 있어야 한다. 회원마다 장르 하나를 축으로 한
 * 취향 클러스터를 배정하고, 위시/구매의 {@value #CLUSTER_PICK_PERCENT}% 는 클러스터 안에서, 나머지는
 * 전체 상품에서 뽑는다. 노이즈를 섞지 않으면 추천 품질 측정이 시드 생성 규칙을 되맞히는 자기충족이 된다.
 */
@Slf4j
@Component
@Profile({"local", "seed"})
@RequiredArgsConstructor
public class LocalSignalSeeder {

	static final int MEMBER_COUNT = 30;
	static final String MEMBER_PASSWORD = "user1234!";
	static final int CLUSTER_PICK_PERCENT = 70;
	static final int WISHLIST_MIN = 10;
	static final int WISHLIST_MAX = 15;
	static final int ORDER_MIN = 2;
	static final int ORDER_MAX = 4;
	static final int ORDER_ITEM_MAX = 3;
	static final int VIEW_LOG_COUNT = 12;

	private static final String EMAIL_FORMAT = "digger%02d@groove.com";
	private static final String NICKNAME_FORMAT = "디거%02d";
	private static final String ORDER_NUMBER_FORMAT = "SD%06d";
	private static final int MAX_TASTE_ARTISTS = 2;
	private static final int MAX_TASTE_DECADES = 2;

	// 고정 시드. 시드를 다시 만들어도 같은 신호가 나와야 precision 측정값을 실행 간 비교할 수 있다.
	private static final long RANDOM_SEED = 20260906L;

	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;
	private final ProductRepository productRepository;
	private final WishlistRepository wishlistRepository;
	private final OrderRepository orderRepository;
	private final MemberTasteProfileRepository memberTasteProfileRepository;
	private final MemberTasteGenreRepository memberTasteGenreRepository;
	private final MemberTasteArtistRepository memberTasteArtistRepository;
	private final MemberTasteDecadeRepository memberTasteDecadeRepository;
	private final ProductViewLogRepository productViewLogRepository;

	public void seed(List<Member> demoMembers) {
		// 첫 쓰기가 유니크 email 을 가진 회원이라 회원 존재 여부로 막는다. 취향 테이블 개수로 막으면
		// 회원만 남고 취향이 비워진 상태에서 email 중복으로 기동이 실패한다.
		if (memberRepository.existsByEmail(emailOf(0))) {
			log.info("취향/행동 신호가 이미 있어 시딩을 건너뛴다");
			return;
		}

		// 정렬을 명시해야 클러스터 배정과 무작위 추출이 실행마다 같은 결과를 낸다.
		List<Product> products = productRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
		if (products.isEmpty()) {
			log.warn("상품이 없어 취향/행동 신호 시딩을 건너뛴다");
			return;
		}

		Random random = new Random(RANDOM_SEED);
		LocalDateTime now = LocalDateTime.now();
		List<Genre> genres = distinctGenres(products);
		int orderSequence = 0;

		for (int index = 0; index < MEMBER_COUNT; index++) {
			Member member = createMember(index);
			Genre clusterGenre = genres.get(index % genres.size());
			List<Product> clusterProducts = productsOf(products, clusterGenre);

			seedTasteProfile(member, clusterGenre, clusterProducts, random);
			Set<Product> wished = seedWishlist(member, products, clusterProducts, random);
			orderSequence = seedOrders(member, products, clusterProducts, wished, random, now, orderSequence);
		}

		seedViewLogs(demoMembers, products, now);

		log.info("취향/행동 신호를 시딩했다: 회원 {}명, 주문 {}건", MEMBER_COUNT, orderSequence);
	}

	private Member createMember(int index) {
		String nickname = String.format(Locale.ROOT, NICKNAME_FORMAT, index + 1);
		return memberRepository.save(Member.create(emailOf(index), passwordEncoder.encode(MEMBER_PASSWORD), nickname));
	}

	private String emailOf(int index) {
		return String.format(Locale.ROOT, EMAIL_FORMAT, index + 1);
	}

	private void seedTasteProfile(Member member, Genre clusterGenre, List<Product> clusterProducts, Random random) {
		MemberTasteProfile profile = memberTasteProfileRepository.save(MemberTasteProfile.create(member));

		memberTasteGenreRepository.save(MemberTasteGenre.of(profile, clusterGenre));

		List<Artist> artists = pick(clusterProducts.stream().map(Product::getArtist).distinct().toList(),
				MAX_TASTE_ARTISTS, random);
		memberTasteArtistRepository.saveAll(artists.stream()
				.map(artist -> MemberTasteArtist.of(profile, artist))
				.toList());

		List<Decade> decades = pick(clusterProducts.stream()
				.map(this::decadeOf)
				.filter(Objects::nonNull)
				.distinct()
				.toList(), MAX_TASTE_DECADES, random);
		memberTasteDecadeRepository.saveAll(decades.stream()
				.map(decade -> MemberTasteDecade.of(profile, decade))
				.toList());
	}

	private Set<Product> seedWishlist(Member member, List<Product> products, List<Product> clusterProducts,
			Random random) {
		int size = WISHLIST_MIN + random.nextInt(WISHLIST_MAX - WISHLIST_MIN + 1);
		Set<Product> picked = pickWithNoise(products, clusterProducts, size, Set.of(), random);
		wishlistRepository.saveAll(picked.stream()
				.map(product -> Wishlist.create(member, product))
				.toList());
		return picked;
	}

	/**
	 * 위시 상품은 주문에서 뺀다. 홈 추천은 구매 상품도 후보에서 제외하므로, 홀드아웃으로 뗄 위시 상품이
	 * 구매 이력에도 있으면 추천 후보에 아예 오르지 못해 품질 측정이 왜곡된다.
	 */
	private int seedOrders(Member member, List<Product> products, List<Product> clusterProducts, Set<Product> wished,
			Random random, LocalDateTime now, int orderSequence) {
		int orderCount = ORDER_MIN + random.nextInt(ORDER_MAX - ORDER_MIN + 1);
		int sequence = orderSequence;

		for (int i = 0; i < orderCount; i++) {
			int itemCount = 1 + random.nextInt(ORDER_ITEM_MAX);
			Set<Product> items = pickWithNoise(products, clusterProducts, itemCount, wished, random);
			if (items.isEmpty()) {
				continue;
			}

			sequence++;
			String orderNumber = String.format(Locale.ROOT, ORDER_NUMBER_FORMAT, sequence);
			Order order = Order.create(orderNumber, member, dummyAddress(member), now);
			// 시드 주문은 재고를 차감하지 않는다. 추천 신호가 목적이고 재고 정합성은 주문 도메인 테스트가 다룬다.
			items.forEach(product -> order.addItem(product, 1));
			order.markPaid();
			orderRepository.save(order);
		}
		return sequence;
	}

	private void seedViewLogs(List<Member> demoMembers, List<Product> products, LocalDateTime now) {
		if (demoMembers.isEmpty()) {
			return;
		}

		List<ProductViewLog> logs = new ArrayList<>();
		for (int memberIndex = 0; memberIndex < demoMembers.size(); memberIndex++) {
			Member member = demoMembers.get(memberIndex);
			for (int i = 0; i < VIEW_LOG_COUNT; i++) {
				Product product = products.get((memberIndex * VIEW_LOG_COUNT + i * 7) % products.size());
				logs.add(ProductViewLog.create(member, product, now.minusHours(i + 1L)));
			}
		}
		productViewLogRepository.saveAll(logs);
	}

	/** 클러스터에서 {@value #CLUSTER_PICK_PERCENT}% 를, 나머지는 전체에서 뽑는다. */
	private Set<Product> pickWithNoise(List<Product> products, List<Product> clusterProducts, int size,
			Set<Product> excluded, Random random) {
		Set<Product> picked = new LinkedHashSet<>();
		int attempts = 0;
		int maxAttempts = size * 20;

		while (picked.size() < size && attempts < maxAttempts) {
			attempts++;
			List<Product> pool = random.nextInt(100) < CLUSTER_PICK_PERCENT && !clusterProducts.isEmpty()
					? clusterProducts
					: products;
			Product candidate = pool.get(random.nextInt(pool.size()));
			if (!excluded.contains(candidate)) {
				picked.add(candidate);
			}
		}
		return picked;
	}

	private <T> List<T> pick(List<T> candidates, int max, Random random) {
		if (candidates.isEmpty()) {
			return List.of();
		}
		List<T> shuffled = new ArrayList<>(candidates);
		Collections.shuffle(shuffled, random);
		return shuffled.subList(0, Math.min(max, shuffled.size()));
	}

	private List<Genre> distinctGenres(List<Product> products) {
		return products.stream()
				.flatMap(product -> product.getProductGenres().stream())
				.map(ProductGenre::getGenre)
				.distinct()
				.toList();
	}

	private List<Product> productsOf(List<Product> products, Genre genre) {
		return products.stream()
				.filter(product -> product.getProductGenres().stream()
						.anyMatch(link -> link.getGenre().getId().equals(genre.getId())))
				.toList();
	}

	private Decade decadeOf(Product product) {
		return product.getReleaseDate() == null ? null : Decade.fromYear(product.getReleaseDate().getYear());
	}

	private ShippingAddress dummyAddress(Member member) {
		return ShippingAddress.of(member.getNickname(), "010-0000-0000", "06236", "서울특별시 강남구 테헤란로 1", "101호");
	}
}
