package com.groove.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.order.entity.OrderStatus;
import com.groove.order.repository.OrderItemRepository;
import com.groove.product.dto.ProductSummaryResponse;
import com.groove.product.entity.ProductStatus;
import com.groove.recommend.dto.RecommendItemResponse;
import com.groove.recommend.service.BoughtTogetherAggregator;
import com.groove.recommend.service.RecommendService;
import com.groove.support.IntegrationTestSupport;
import com.groove.wishlist.repository.WishlistRepository;

import jakarta.persistence.EntityManager;

/**
 * 규칙 기반 추천의 품질 측정. 시드 회원 위시리스트의 20% 를 홀드아웃으로 떼고, 남은 신호만으로 계산한 추천
 * 상위 10개에 홀드아웃이 몇 개나 들어오는지 센다. 일반 빌드에서는 제외되고 {@code ./gradlew precisionAt10}
 * 으로만 돈다. 합성 시드 기준이라 실사용 지표가 아니라 규칙이 무작위보다 낫다는 것을 보이는 용도다.
 */
@Tag("precision")
@ActiveProfiles({"local", "seed", "test"})
class RecommendPrecisionTest extends IntegrationTestSupport {

	private static final int TOP_K = 10;
	private static final int HOLDOUT_EVERY = 5;
	private static final String SEED_MEMBER_PREFIX = "digger";
	private static final Path REPORT_PATH = Path.of("build", "reports", "precision-at-10.md");

	@Autowired
	RecommendService recommendService;

	@Autowired
	BoughtTogetherAggregator boughtTogetherAggregator;

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	WishlistRepository wishlistRepository;

	@Autowired
	OrderItemRepository orderItemRepository;

	@Autowired
	EntityManager entityManager;

	@Nested
	@DisplayName("recommendHome()")
	class RecommendHome {

		@Test
		@Transactional
		@DisplayName("위시리스트 20% 를 홀드아웃으로 떼면 추천 상위 10개가 무작위 기준선보다 홀드아웃을 잘 맞힌다")
		void recallAtTenBeatsRandomBaseline() throws IOException {
			// given
			boughtTogetherAggregator.refresh();
			List<Member> members = seedMembers();
			assertThat(members).isNotEmpty();

			List<Long> byPopularity = productIdsByPopularity();
			List<MemberResult> results = new ArrayList<>();

			// when
			for (Member member : members) {
				results.add(measure(member, byPopularity));
			}

			// then
			Metrics metrics = Metrics.of(results, byPopularity.size());
			String report = metrics.toMarkdown();
			System.out.println(report);
			writeReport(report);

			assertThat(metrics.recallAtK()).isGreaterThan(metrics.randomBaseline() * 3);
			assertThat(metrics.recallAtK()).isGreaterThan(metrics.popularityRecallAtK());
		}
	}

	@Nested
	@DisplayName("recommendRelated()")
	class RecommendRelated {

		@Test
		@Transactional
		@DisplayName("멀티 프레싱 앨범 상품을 기준으로 추천하면 같은 앨범은 빠지고 결과 앨범이 중복되지 않는다")
		void excludesSameAlbumAndDedupsAlbumsAmongCandidates() {
			// given
			Map<Long, Long> albumIdByProductId = new LinkedHashMap<>();
			Map<Long, List<Long>> productIdsByAlbumId = new LinkedHashMap<>();
			for (Object[] row : productIdsWithAlbumIds()) {
				Long productId = (Long)row[0];
				Long albumId = (Long)row[1];
				albumIdByProductId.put(productId, albumId);
				productIdsByAlbumId.computeIfAbsent(albumId, key -> new ArrayList<>()).add(productId);
			}
			Optional<Map.Entry<Long, List<Long>>> multiPressingAlbum = productIdsByAlbumId.entrySet().stream()
					.filter(entry -> entry.getValue().size() >= 2)
					.findFirst();
			assertThat(multiPressingAlbum).isPresent();
			Long targetAlbumId = multiPressingAlbum.get().getKey();
			Long targetProductId = multiPressingAlbum.get().getValue().get(0);

			// when
			List<RecommendItemResponse> items = recommendService.recommendRelated(targetProductId, null, TOP_K);

			// then
			List<Long> resultAlbumIds = items.stream()
					.map(item -> item.product().id())
					.map(albumIdByProductId::get)
					.toList();
			assertThat(resultAlbumIds).doesNotContain(targetAlbumId);
			assertThat(resultAlbumIds).doesNotHaveDuplicates();
		}

		private List<Object[]> productIdsWithAlbumIds() {
			return entityManager
					.createQuery("select p.id, p.album.id from Product p where p.status <> :hidden", Object[].class)
					.setParameter("hidden", ProductStatus.HIDDEN)
					.getResultList();
		}
	}

	private MemberResult measure(Member member, List<Long> byPopularity) {
		List<Long> wishedIds = wishlistRepository.findProductIdsByMemberId(member.getId()).stream()
				.sorted()
				.toList();
		Set<Long> holdout = holdoutOf(wishedIds);
		if (holdout.isEmpty()) {
			return new MemberResult(0, 0, 0);
		}

		holdout.forEach(productId -> wishlistRepository.findByMemberIdAndProductId(member.getId(), productId)
				.ifPresent(wishlistRepository::delete));
		entityManager.flush();

		List<Long> recommended = recommendService.recommendHome(member.getId(), TOP_K).items().stream()
				.map(RecommendItemResponse::product)
				.map(ProductSummaryResponse::id)
				.toList();

		int hits = (int)recommended.stream().filter(holdout::contains).count();
		int popularityHits = (int)popularityTopK(member, byPopularity).stream().filter(holdout::contains).count();
		return new MemberResult(holdout.size(), hits, popularityHits);
	}

	/**
	 * 대조군. 개인화 없이 평점 높은 순으로 10개를 고른다. 규칙 추천이 균등 무작위보다 낫다는 것만으로는
	 * 부족하고, "그냥 인기순으로 뿌리기"보다 나아야 개인화가 값을 한다고 말할 수 있다.
	 */
	private List<Long> popularityTopK(Member member, List<Long> byPopularity) {
		Set<Long> excluded = new HashSet<>(wishlistRepository.findProductIdsByMemberId(member.getId()));
		excluded.addAll(orderItemRepository.findProductIdsByMemberIdAndOrderStatusIn(member.getId(),
				OrderStatus.PAID_OR_LATER));
		return byPopularity.stream()
				.filter(id -> !excluded.contains(id))
				.limit(TOP_K)
				.toList();
	}

	/** 평점 내림차순 → 최신순 → id 내림차순. RecommendService 의 동점 처리와 같은 순서다. */
	private List<Long> productIdsByPopularity() {
		return entityManager.createQuery(
						"select p.id from Product p where p.status <> :hidden "
								+ "order by p.averageRating desc, p.createdAt desc, p.id desc", Long.class)
				.setParameter("hidden", ProductStatus.HIDDEN)
				.getResultList();
	}

	/** id 오름차순으로 {@value #HOLDOUT_EVERY} 개마다 1개. 난수가 아니라 실행마다 같은 홀드아웃이 나온다. */
	private Set<Long> holdoutOf(List<Long> wishedIds) {
		Set<Long> holdout = new LinkedHashSet<>();
		for (int i = HOLDOUT_EVERY - 1; i < wishedIds.size(); i += HOLDOUT_EVERY) {
			holdout.add(wishedIds.get(i));
		}
		return holdout;
	}

	private List<Member> seedMembers() {
		return memberRepository.findAll().stream()
				.filter(member -> member.getEmail().startsWith(SEED_MEMBER_PREFIX))
				.sorted(Comparator.comparing(Member::getEmail))
				.toList();
	}

	private void writeReport(String report) throws IOException {
		Files.createDirectories(REPORT_PATH.getParent());
		Files.writeString(REPORT_PATH, report);
	}

	private record MemberResult(int holdoutCount, int hitCount, int popularityHitCount) {
	}

	private record Metrics(int memberCount, int holdoutTotal, int hitTotal, int hitMemberCount,
			int popularityHitTotal, long candidateCount) {

		static Metrics of(List<MemberResult> results, long candidateCount) {
			int holdoutTotal = results.stream().mapToInt(MemberResult::holdoutCount).sum();
			int hitTotal = results.stream().mapToInt(MemberResult::hitCount).sum();
			int hitMemberCount = (int)results.stream().filter(result -> result.hitCount() > 0).count();
			int popularityHitTotal = results.stream().mapToInt(MemberResult::popularityHitCount).sum();
			return new Metrics(results.size(), holdoutTotal, hitTotal, hitMemberCount, popularityHitTotal,
					candidateCount);
		}

		double recallAtK() {
			return holdoutTotal == 0 ? 0 : (double)hitTotal / holdoutTotal;
		}

		double precisionAtK() {
			return memberCount == 0 ? 0 : (double)hitTotal / ((long)memberCount * TOP_K);
		}

		double hitRateAtK() {
			return memberCount == 0 ? 0 : (double)hitMemberCount / memberCount;
		}

		double randomBaseline() {
			return candidateCount == 0 ? 0 : (double)TOP_K / candidateCount;
		}

		double popularityRecallAtK() {
			return holdoutTotal == 0 ? 0 : (double)popularityHitTotal / holdoutTotal;
		}

		String toMarkdown() {
			return """
					# 추천 품질 측정 (precision@10)

					| 항목 | 값 |
					|---|---|
					| 측정 회원 | %d명 |
					| 후보 상품 | %d건 |
					| 홀드아웃 | %d건 |
					| 규칙 추천 적중 | %d건 |
					| 인기순 적중 | %d건 |

					| 지표 | 값 |
					|---|---|
					| recall@10 | %.3f |
					| precision@10 | %.3f |
					| hit-rate@10 | %.3f |
					| 인기순 대조군 recall@10 | %.3f |
					| 무작위 기준선 recall@10 | %.3f |
					| 무작위 대비 | %.1f배 |
					| 인기순 대비 | %.1f배 |
					""".formatted(memberCount, candidateCount, holdoutTotal, hitTotal, popularityHitTotal,
					recallAtK(), precisionAtK(), hitRateAtK(), popularityRecallAtK(), randomBaseline(),
					randomBaseline() == 0 ? 0 : recallAtK() / randomBaseline(),
					popularityRecallAtK() == 0 ? 0 : recallAtK() / popularityRecallAtK());
		}
	}
}
