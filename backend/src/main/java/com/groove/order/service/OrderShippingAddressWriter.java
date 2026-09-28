package com.groove.order.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.member.entity.Address;
import com.groove.member.repository.AddressRepository;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderStatus;
import com.groove.order.entity.ShippingAddress;
import com.groove.order.repository.OrderRepository;
import com.groove.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderShippingAddressWriter {

	private final OrderRepository orderRepository;
	private final AddressRepository addressRepository;
	private final PaymentRepository paymentRepository;

	@Transactional
	public void changeAddress(Long memberId, Long orderId, Long addressId) {
		Order order = orderRepository.findByIdForUpdate(orderId)
				.filter(found -> found.getMember().getId().equals(memberId))
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		// 확정 전 판정은 order.changeShippingAddress() 가 다시 하지만, 에러 우선순위(상태 → 주소 소유)를
		// 지키려면 주소 조회보다 먼저 걸러야 한다.
		if (order.getStatus() != OrderStatus.PENDING || order.isPlaced()) {
			throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
		}
		paymentRepository.findByOrderId(orderId)
				.filter(payment -> payment.getStatus().isUnresolved())
				.ifPresent(payment -> {
					throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
				});
		Address address = addressRepository.findByIdAndMemberId(addressId, memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_ADDRESS_NOT_FOUND));
		order.changeShippingAddress(ShippingAddress.from(address));
	}
}
