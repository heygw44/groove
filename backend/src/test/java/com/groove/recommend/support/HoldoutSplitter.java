package com.groove.recommend.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 회원 한 명의 홀드아웃 후보를 {@link HoldoutSpec#kind()} 에 맞는 신호에서 뽑아
 * {@link HoldoutSpec#foldCount()} 등분한다. 폴드 경계는 {@code new Random(randomSeed * 31 + memberId)}
 * 로 섞은 뒤 순서대로 자른다 — 전역 난수 하나를 쓰면 회원 순서에 따라 홀드아웃이 상관관계를 갖게 되므로
 * 회원마다 다른 시드를 준다.
 */
public final class HoldoutSplitter {

	/** kind 별로 뗄 후보를 셔플해 foldCount 등분한다. 반환 리스트의 인덱스가 fold index 다. */
	public List<Set<Long>> foldsOf(EvalSignals signals, HoldoutSpec spec) {
		List<Long> candidateIds = candidateIdsOf(signals, spec.kind());
		Collections.shuffle(candidateIds, new Random(spec.randomSeed() * 31 + signals.memberId()));
		return partition(candidateIds, spec.foldCount());
	}

	private List<Long> candidateIdsOf(EvalSignals signals, HoldoutKind kind) {
		return switch (kind) {
			case WISH -> distinct(signals.wishedIds());
			case PURCHASE -> distinct(signals.purchasedIds());
			case BOTH -> merge(distinct(signals.wishedIds()), distinct(signals.purchasedIds()));
		};
	}

	private List<Long> distinct(List<Long> ids) {
		return new ArrayList<>(new LinkedHashSet<>(ids));
	}

	private List<Long> merge(List<Long> left, List<Long> right) {
		List<Long> merged = new ArrayList<>(left);
		merged.addAll(right);
		return merged;
	}

	/** 앞쪽 폴드부터 1개씩 더 받는 방식으로 나머지를 분배해 가능한 한 균등하게 자른다. */
	private List<Set<Long>> partition(List<Long> shuffled, int foldCount) {
		int size = shuffled.size();
		int baseSize = size / foldCount;
		int remainder = size % foldCount;
		List<Set<Long>> folds = new ArrayList<>(foldCount);
		int cursor = 0;
		for (int fold = 0; fold < foldCount; fold++) {
			int foldSize = baseSize + (fold < remainder ? 1 : 0);
			folds.add(new LinkedHashSet<>(shuffled.subList(cursor, cursor + foldSize)));
			cursor += foldSize;
		}
		return folds;
	}
}
