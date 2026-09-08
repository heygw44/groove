package com.groove.recommend.service;

/**
 * 추천 점수 계산에 쓰는 상품 특성(아티스트/레이블/장르/연대/HIDDEN 여부)이 바뀐 이벤트.
 * 평점(avg_rating) 변경은 동점 처리용 보조 키일 뿐이라 대상에서 뺐다 — 최대 TTL 만큼 늦게 반영돼도
 * 랭킹 결과가 실질적으로 달라지지 않는다.
 */
public record ProductCatalogChangedEvent() {
}
