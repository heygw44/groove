package com.groove.recommend.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 홈 추천 시드 조립. 위시+구매를 소유로 묶고, 최근 본 상품 중 소유하지 않은 것만 따로 남긴다. */
public record HomeSeeds(Set<Long> seedIds, Set<Long> recentOnlySeedIds) {

	public static HomeSeeds of(Collection<Long> wishedIds, Collection<Long> purchasedIds, List<Long> recentIds) {
		Set<Long> ownedIds = new HashSet<>(wishedIds);
		ownedIds.addAll(purchasedIds);
		Set<Long> recentOnlySeedIds = recentIds.stream()
				.filter(id -> !ownedIds.contains(id))
				.collect(Collectors.toCollection(LinkedHashSet::new));
		Set<Long> seedIds = new LinkedHashSet<>(ownedIds);
		seedIds.addAll(recentIds);
		return new HomeSeeds(seedIds, recentOnlySeedIds);
	}
}
