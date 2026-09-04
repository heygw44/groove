package com.groove.limited.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.inventory.entity.Stock;
import com.groove.inventory.entity.StockChangeType;
import com.groove.inventory.entity.StockHistory;
import com.groove.inventory.repository.StockHistoryRepository;
import com.groove.inventory.repository.StockRepository;
import com.groove.limited.dto.LimitedPurchaseResponse;
import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.entity.LimitedPurchase;
import com.groove.limited.repository.LimitedDropRepository;
import com.groove.limited.repository.LimitedPurchaseRepository;
import com.groove.member.entity.Address;
import com.groove.member.entity.Member;
import com.groove.member.repository.AddressRepository;
import com.groove.member.repository.MemberRepository;
import com.groove.order.entity.Order;
import com.groove.order.entity.ShippingAddress;
import com.groove.order.repository.OrderRepository;
import com.groove.order.service.OrderNumberGenerator;

import lombok.RequiredArgsConstructor;

/** 한정반 구매의 DB 반영. Redis 선점 이후 호출되며, DB 단에서도 uk_limited_purchase·재고 조건부 UPDATE 로 다시 한 번 방어한다. */
@Service
@RequiredArgsConstructor
public class LimitedPurchaseWriter {

	private static final int PURCHASE_QUANTITY = 1;
	private static final String STOCK_OUT_REASON_PREFIX = "한정반 구매 ";

	private final LimitedDropRepository limitedDropRepository;
	private final LimitedPurchaseRepository limitedPurchaseRepository;
	private final MemberRepository memberRepository;
	private final AddressRepository addressRepository;
	private final StockRepository stockRepository;
	private final StockHistoryRepository stockHistoryRepository;
	private final OrderRepository orderRepository;
	private final OrderNumberGenerator orderNumberGenerator;
	private final Clock clock;

	@Transactional
	public LimitedPurchaseResponse write(Long dropId, Long memberId, Long addressId) {
		LimitedDrop drop = limitedDropRepository.findByIdForUpdate(dropId)
				.orElseThrow(() -> new BusinessException(ErrorCode.LIMITED_DROP_NOT_FOUND));
		drop.validatePurchasable(LocalDateTime.now(clock));

		Member member = findActiveMember(memberId);
		Address address = addressRepository.findByIdAndMemberId(addressId, memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_ADDRESS_NOT_FOUND));

		LimitedPurchase purchase = LimitedPurchase.create(drop, member, null, PURCHASE_QUANTITY);
		try {
			limitedPurchaseRepository.saveAndFlush(purchase);
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.LIMITED_ALREADY_PURCHASED);
		}

		int updatedRows = stockRepository.decreaseIfAvailable(drop.getProduct().getId(), PURCHASE_QUANTITY);
		if (updatedRows == 0) {
			throw new BusinessException(ErrorCode.LIMITED_SOLD_OUT);
		}

		String orderNumber = orderNumberGenerator.generate();
		Order order = Order.create(orderNumber, member, ShippingAddress.from(address), LocalDateTime.now(clock));
		order.addItem(drop.getProduct(), PURCHASE_QUANTITY);
		orderRepository.save(order);

		Stock stock = stockRepository.findByProductId(drop.getProduct().getId())
				.orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));
		stockHistoryRepository.save(StockHistory.of(stock, StockChangeType.OUT, -PURCHASE_QUANTITY,
				STOCK_OUT_REASON_PREFIX + orderNumber));

		purchase.attachOrder(order);
		drop.recordSale(PURCHASE_QUANTITY);

		return new LimitedPurchaseResponse(order.getId(), order.getOrderNumber(), order.getFinalAmount(),
				order.getExpiresAt());
	}

	/** 한정반 주문이면 구매 이력을 지우고 판매 수량을 되돌린다. 아니면 empty. 호출자 트랜잭션에 참여한다. */
	@Transactional
	public Optional<LimitedRelease> revertByOrder(Long orderId, LocalDateTime now) {
		Optional<LimitedPurchase> found = limitedPurchaseRepository.findByOrderId(orderId);
		if (found.isEmpty()) {
			return Optional.empty();
		}
		LimitedPurchase purchase = found.get();
		Long dropId = purchase.getDrop().getId();
		Long memberId = purchase.getMember().getId();

		LimitedDrop drop = limitedDropRepository.findByIdForUpdate(dropId)
				.orElseThrow(() -> new BusinessException(ErrorCode.LIMITED_DROP_NOT_FOUND));
		drop.restoreSale(purchase.getQuantity(), now);
		limitedPurchaseRepository.delete(purchase);

		return Optional.of(new LimitedRelease(dropId, memberId));
	}

	private Member findActiveMember(Long memberId) {
		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		if (member.isWithdrawn()) {
			throw new BusinessException(ErrorCode.MEMBER_WITHDRAWN);
		}
		return member;
	}
}
