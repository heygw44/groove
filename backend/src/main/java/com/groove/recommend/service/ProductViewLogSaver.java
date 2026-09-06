package com.groove.recommend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.product.entity.Product;
import com.groove.product.repository.ProductRepository;
import com.groove.recommend.entity.ProductViewLog;
import com.groove.recommend.repository.ProductViewLogRepository;

import lombok.RequiredArgsConstructor;

/** 상품 조회 로그 DB 저장. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProductViewLogSaver {

	private final ProductViewLogRepository productViewLogRepository;
	private final MemberRepository memberRepository;
	private final ProductRepository productRepository;

	// getReferenceById 는 프록시라 SELECT 없이 FK 값만으로 연관관계를 건다. 조회 시점과 저장 시점 사이에
	// 회원·상품이 삭제됐다면 INSERT 에서 FK 위반 예외가 나는데, 그 처리는 호출자(ProductViewLogWriter)가 삼킨다.
	@Transactional
	public void save(ProductViewedEvent event) {
		Member member = event.memberId() == null ? null : memberRepository.getReferenceById(event.memberId());
		Product product = productRepository.getReferenceById(event.productId());
		productViewLogRepository.save(ProductViewLog.create(member, product, event.viewedAt()));
	}
}
