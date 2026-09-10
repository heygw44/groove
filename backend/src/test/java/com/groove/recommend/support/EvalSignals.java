package com.groove.recommend.support;

import java.util.List;
import java.util.Set;

import com.groove.recommend.service.TasteSignal;

/**
 * 회원 한 명의 추천 신호 스냅샷. {@link EvalSignalLoader} 가 DB 에서 한 번만 읽어 만들고, 그 뒤로는
 * DB 를 다시 건드리지 않는다. 홀드아웃은 행을 지우는 대신 {@link #without(Set)} 으로 뺀 사본을 만들어
 * 흉내 낸다.
 */
public record EvalSignals(Long memberId, TasteSignal taste, List<Long> wishedIds, List<Long> purchasedIds,
		List<Long> recentIds) {

	/** DB 를 건드리지 않고 holdout 에 속한 id 를 뺀 사본을 만든다. */
	public EvalSignals without(Set<Long> holdout) {
		return new EvalSignals(memberId, taste, exclude(wishedIds, holdout), exclude(purchasedIds, holdout),
				exclude(recentIds, holdout));
	}

	private static List<Long> exclude(List<Long> ids, Set<Long> holdout) {
		return ids.stream().filter(id -> !holdout.contains(id)).toList();
	}
}
