package com.groove.recommend.service;

import java.util.Set;

import com.groove.recommend.entity.Decade;

/** 회원의 취향 프로필에서 뽑은 매칭 신호(아티스트·장르·연대). */
public record TasteSignal(Set<Long> artistIds, Set<Long> genreIds, Set<Decade> decades) {

	public static TasteSignal empty() {
		return new TasteSignal(Set.of(), Set.of(), Set.of());
	}

	public boolean isEmpty() {
		return artistIds.isEmpty() && genreIds.isEmpty() && decades.isEmpty();
	}
}
