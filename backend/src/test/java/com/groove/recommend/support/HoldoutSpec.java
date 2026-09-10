package com.groove.recommend.support;

/**
 * 홀드아웃 측정 1회의 구성. {@code kind} 는 어떤 신호에서 뗄지, {@code foldCount} 는 등분 수,
 * {@code randomSeed} 는 회원별 셔플 시드다. 같은 스펙이라도 fold index 마다 다른 부분집합이 홀드아웃된다.
 * 후속 ablation·스윕 이슈가 이 레코드를 인자로 받아 여러 구성을 순회하는 형태로 재사용한다.
 */
public record HoldoutSpec(HoldoutKind kind, int foldCount, long randomSeed) {
}
