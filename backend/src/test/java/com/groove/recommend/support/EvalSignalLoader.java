package com.groove.recommend.support;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.order.entity.OrderStatus;
import com.groove.order.repository.OrderItemRepository;
import com.groove.recommend.entity.Decade;
import com.groove.recommend.entity.MemberTasteDecade;
import com.groove.recommend.entity.MemberTasteProfile;
import com.groove.recommend.repository.MemberTasteArtistRepository;
import com.groove.recommend.repository.MemberTasteDecadeRepository;
import com.groove.recommend.repository.MemberTasteGenreRepository;
import com.groove.recommend.repository.MemberTasteProfileRepository;
import com.groove.recommend.service.RecentViewService;
import com.groove.recommend.service.TasteSignal;
import com.groove.wishlist.repository.WishlistRepository;

/**
 * digger 접두사 시드 회원의 추천 신호를 한 번에 메모리로 적재한다. 스프링 빈이 아니라 평범한 클래스라
 * 테스트가 이미 컨텍스트에서 얻은 리포지토리를 생성자로 넘겨 {@code new} 로 만들어 쓴다.
 * {@link #load} 의 취향 조립은 {@code RecommendService.loadTasteSignal()} 과 같은 리포지토리 3종을
 * 같은 순서로 호출해 같은 결과를 보장한다.
 */
public class EvalSignalLoader {

	private static final String SEED_MEMBER_PREFIX = "digger";

	private final MemberRepository memberRepository;
	private final WishlistRepository wishlistRepository;
	private final OrderItemRepository orderItemRepository;
	private final RecentViewService recentViewService;
	private final MemberTasteProfileRepository memberTasteProfileRepository;
	private final MemberTasteGenreRepository memberTasteGenreRepository;
	private final MemberTasteArtistRepository memberTasteArtistRepository;
	private final MemberTasteDecadeRepository memberTasteDecadeRepository;

	public EvalSignalLoader(MemberRepository memberRepository, WishlistRepository wishlistRepository,
			OrderItemRepository orderItemRepository, RecentViewService recentViewService,
			MemberTasteProfileRepository memberTasteProfileRepository,
			MemberTasteGenreRepository memberTasteGenreRepository,
			MemberTasteArtistRepository memberTasteArtistRepository,
			MemberTasteDecadeRepository memberTasteDecadeRepository) {
		this.memberRepository = memberRepository;
		this.wishlistRepository = wishlistRepository;
		this.orderItemRepository = orderItemRepository;
		this.recentViewService = recentViewService;
		this.memberTasteProfileRepository = memberTasteProfileRepository;
		this.memberTasteGenreRepository = memberTasteGenreRepository;
		this.memberTasteArtistRepository = memberTasteArtistRepository;
		this.memberTasteDecadeRepository = memberTasteDecadeRepository;
	}

	/** email 오름차순으로 시드 회원 전원의 신호를 적재한다. */
	public List<EvalSignals> loadSeedMembers() {
		return memberRepository.findAll().stream()
				.filter(member -> member.getEmail().startsWith(SEED_MEMBER_PREFIX))
				.sorted(Comparator.comparing(Member::getEmail))
				.map(this::load)
				.toList();
	}

	private EvalSignals load(Member member) {
		Long memberId = member.getId();
		TasteSignal taste = loadTasteSignal(memberId);
		List<Long> wishedIds = wishlistRepository.findProductIdsByMemberId(memberId).stream().sorted().toList();
		List<Long> purchasedIds = orderItemRepository.findProductIdsByMemberIdAndOrderStatusIn(memberId,
				OrderStatus.PAID_OR_LATER);
		List<Long> recentIds = recentViewService.findRecentProductIds(memberId);
		return new EvalSignals(memberId, taste, wishedIds, purchasedIds, recentIds);
	}

	private TasteSignal loadTasteSignal(Long memberId) {
		return memberTasteProfileRepository.findByMemberId(memberId)
				.map(this::toTasteSignal)
				.orElseGet(TasteSignal::empty);
	}

	private TasteSignal toTasteSignal(MemberTasteProfile profile) {
		Long profileId = profile.getId();
		Set<Long> artistIds = memberTasteArtistRepository.findAllByProfileId(profileId).stream()
				.map(artist -> artist.getArtist().getId())
				.collect(Collectors.toSet());
		Set<Long> genreIds = memberTasteGenreRepository.findAllByProfileId(profileId).stream()
				.map(genre -> genre.getGenre().getId())
				.collect(Collectors.toSet());
		Set<Decade> decades = memberTasteDecadeRepository.findAllByProfileId(profileId).stream()
				.map(MemberTasteDecade::getDecade)
				.collect(Collectors.toSet());
		return new TasteSignal(artistIds, genreIds, decades);
	}
}
