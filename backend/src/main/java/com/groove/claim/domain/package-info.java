/**
 * 반품 도메인 모델 (M16 #239, ERD 반품 §).
 *
 * <p>{@link com.groove.claim.domain.Claim} aggregate root + 항목 {@link com.groove.claim.domain.ClaimItem}
 * + 역물류 상태머신 {@link com.groove.claim.domain.ClaimStatus} + {@link com.groove.claim.domain.ClaimRepository}.
 * 주문/품목을 참조하되 {@code OrderStatus} 와 분리된 별도 상태머신을 가져 상태 폭발을 피한다. 접수 자격·기한·잔여
 * 수량·환불 오케스트레이션은 {@code ClaimService} 가 담당하고, 도메인은 상태 전이 합법성만 재검증한다.
 */
package com.groove.claim.domain;
