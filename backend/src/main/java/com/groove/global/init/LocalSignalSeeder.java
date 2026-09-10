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
import com.groove.payment.entity.Payment;
import com.groove.payment.repository.PaymentRepository;
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
import com.groove.review.entity.Review;
import com.groove.review.repository.ReviewRepository;
import com.groove.wishlist.entity.Wishlist;
import com.groove.wishlist.repository.WishlistRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 추천이 실제로 동작하는 모습을 보려면 취향·위시·구매 신호가 있어야 한다. 회원마다 장르 하나를 축으로 한
 * 취향 클러스터를 배정하고, 위시/구매의 {@value #CLUSTER_PICK_PERCENT}% 는 클러스터 안에서, 나머지는
 * 전체 상품에서 뽑는다. 노이즈를 섞지 않으면 추천 품질 측정이 시드 생성 규칙을 되맞히는 자기충족이 된다.
 *
 * <p>리뷰도 여기서 디거 30명을 재사용해 시딩한다. 리뷰어가 데모 회원 2명뿐이면 상품당 리뷰 수가 0~2로
 * 묶여 베이지안 스무딩({@code PopularityIndex#bayes}) 이 평균 평점의 단조 변환에 그친다 — 디거 풀을
 * 써야 롱테일 분산과 0건 상품이 실제로 생긴다.
 */
@Slf4j
@Component
@Profile("local")
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

	/** 매출 사전 집계 백필이 90일 창을 쓰므로 결제 승인일도 같은 범위에 흩뿌린다. */
	static final int PAYMENT_BACKFILL_DAYS = 90;

	private static final String EMAIL_FORMAT = "digger%02d@groove.com";
	private static final String NICKNAME_FORMAT = "디거%02d";
	private static final String ORDER_NUMBER_FORMAT = "SD%06d";
	private static final String PAYMENT_KEY_FORMAT = "seed_payment_%06d";
	private static final String PAYMENT_METHOD = "카드";
	private static final int MAX_TASTE_ARTISTS = 2;
	private static final int MAX_TASTE_DECADES = 2;

	// 리뷰 수 분포: 5%는 히트작(10~20건), 15%는 인기작(3~8건), 30%는 롱테일(1~2건), 나머지 50%는 0건.
	// 상품당 리뷰 수가 균일하면 베이지안 스무딩이 평균 평점의 단조 변환이라 tie-break 이 무의미해진다.
	private static final int REVIEW_HIT_PERCENT = 5;
	private static final int REVIEW_POPULAR_PERCENT = 15;
	private static final int REVIEW_LONGTAIL_PERCENT = 30;
	private static final int REVIEW_HIT_MIN = 10;
	private static final int REVIEW_HIT_MAX = 20;
	private static final int REVIEW_POPULAR_MIN = 3;
	private static final int REVIEW_POPULAR_MAX = 8;
	private static final int REVIEW_LONGTAIL_MIN = 1;
	private static final int REVIEW_LONGTAIL_MAX = 2;
	private static final double RATING_CENTER_MIN = 1.0;
	private static final double RATING_CENTER_RANGE = 4.0;
	private static final double RATING_NOISE_RANGE = 2.0;
	private static final int MIN_RATING = 1;
	private static final int MAX_RATING = 5;

	private static final List<String> REVIEW_TITLES = List.of(
			"자주 듣게 되는 앨범", "믹싱이 훌륭합니다", "소장 가치 있음", "기대 이상이었어요", "재구매 의사 있습니다");

	private static final List<String> REVIEW_CONTENTS = List.of(
			"판 상태도 좋고 배송도 빨랐습니다.",
			"음질이 생각보다 훨씬 좋네요.",
			"자켓 디자인이 마음에 듭니다.",
			"플레이어에 걸어두고 매일 듣고 있어요.",
			"선물용으로 구매했는데 반응이 좋았습니다.");

	// 고정 시드. 시드를 다시 만들어도 같은 신호가 나와야 precision 측정값을 실행 간 비교할 수 있다.
	private static final long RANDOM_SEED = 20260906L;

	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;
	private final ProductRepository productRepository;
	private final WishlistRepository wishlistRepository;
	private final OrderRepository orderRepository;
	private final PaymentRepository paymentRepository;
	private final MemberTasteProfileRepository memberTasteProfileRepository;
	private final MemberTasteGenreRepository memberTasteGenreRepository;
	private final MemberTasteArtistRepository memberTasteArtistRepository;
	private final MemberTasteDecadeRepository memberTasteDecadeRepository;
	private final ProductViewLogRepository productViewLogRepository;
	private final ReviewRepository reviewRepository;

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
		List<Member> diggers = new ArrayList<>();
		int orderSequence = 0;

		for (int index = 0; index < MEMBER_COUNT; index++) {
			Member member = createMember(index);
			diggers.add(member);
			Genre clusterGenre = genres.get(index % genres.size());
			List<Product> clusterProducts = productsOf(products, clusterGenre);

			seedTasteProfile(member, clusterGenre, clusterProducts, random);
			Set<Product> wished = seedWishlist(member, products, clusterProducts, random);
			orderSequence = seedOrders(member, products, clusterProducts, wished, random, now, orderSequence);
		}

		seedViewLogs(demoMembers, products, now);
		seedReviews(diggers, products, random);

		// 로컬은 Flyway 가 꺼져 있어 V12 백필이 안 돈다. 여기서 만든 주문이 없으면 인기순 정렬이 전부 0 이 된다.
		productRepository.refreshSoldQuantities(products.stream().map(Product::getId).toList());

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
			// 매출 통계 백필이 최근 90일을 다시 채우므로, 주문·결제 승인 시각도 이 창 안에 흩어 놓는다.
			LocalDateTime orderedAt = now.minusDays(random.nextInt(PAYMENT_BACKFILL_DAYS))
					.minusHours(random.nextInt(24));
			Order order = Order.create(orderNumber, member, dummyAddress(member), orderedAt);
			// 시드 주문은 재고를 차감하지 않는다. 추천 신호가 목적이고 재고 정합성은 주문 도메인 테스트가 다룬다.
			items.forEach(product -> order.addItem(product, 1));
			order.markPaid();
			orderRepository.save(order);

			Payment payment = Payment.ready(order);
			payment.approve(String.format(Locale.ROOT, PAYMENT_KEY_FORMAT, sequence), PAYMENT_METHOD, orderedAt);
			paymentRepository.save(payment);
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

	/**
	 * 상품별 리뷰 수를 롱테일로 흩뿌린다. 리뷰어는 디거 풀에서 상품마다 다시 뽑아 상품 간 리뷰어 중복이
	 * 실제 구매 이력과 무관하게 섞이게 한다 — 서비스 계층의 "구매자만" 규칙은 이 시더가 원래도 우회한다.
	 */
	private void seedReviews(List<Member> reviewerPool, List<Product> products, Random random) {
		List<Review> reviews = new ArrayList<>();
		for (Product product : products) {
			int reviewCount = Math.min(nextReviewCount(random), reviewerPool.size());
			if (reviewCount == 0) {
				continue;
			}

			double ratingCenter = RATING_CENTER_MIN + random.nextDouble() * RATING_CENTER_RANGE;
			List<Member> reviewers = pick(reviewerPool, reviewCount, random);
			for (Member reviewer : reviewers) {
				int phraseIndex = random.nextInt(REVIEW_TITLES.size());
				reviews.add(Review.create(product, reviewer, ratingAround(ratingCenter, random),
						REVIEW_TITLES.get(phraseIndex), REVIEW_CONTENTS.get(phraseIndex)));
			}
		}
		reviewRepository.saveAll(reviews);
		products.forEach(product -> productRepository.refreshReviewStats(product.getId()));

		log.info("더미 리뷰를 시딩했다: {}건", reviews.size());
	}

	/** 50%는 0건, 30%는 1~2건(롱테일), 15%는 3~8건(인기작), 5%는 10~20건(히트작)으로 나눈다. */
	private int nextReviewCount(Random random) {
		int roll = random.nextInt(100);
		if (roll < REVIEW_HIT_PERCENT) {
			return REVIEW_HIT_MIN + random.nextInt(REVIEW_HIT_MAX - REVIEW_HIT_MIN + 1);
		}
		if (roll < REVIEW_HIT_PERCENT + REVIEW_POPULAR_PERCENT) {
			return REVIEW_POPULAR_MIN + random.nextInt(REVIEW_POPULAR_MAX - REVIEW_POPULAR_MIN + 1);
		}
		if (roll < REVIEW_HIT_PERCENT + REVIEW_POPULAR_PERCENT + REVIEW_LONGTAIL_PERCENT) {
			return REVIEW_LONGTAIL_MIN + random.nextInt(REVIEW_LONGTAIL_MAX - REVIEW_LONGTAIL_MIN + 1);
		}
		return 0;
	}

	/** 상품마다 다른 평점 중심(center)에 노이즈를 더해 상품별 평균 평점 자체에도 편차를 만든다. */
	private int ratingAround(double center, Random random) {
		double noise = (random.nextDouble() - 0.5) * RATING_NOISE_RANGE;
		int rating = (int) Math.round(center + noise);
		return Math.max(MIN_RATING, Math.min(MAX_RATING, rating));
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
