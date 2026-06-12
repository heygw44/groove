package com.groove.shipping.application;

import com.groove.order.domain.Order;
import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderStatus;
import com.groove.shipping.domain.Shipping;
import com.groove.shipping.domain.ShippingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 완료 주문에 배송을 생성(프로비저닝)하는 단일 진입점 — 정상 경로인 ShippingCreationListener(AFTER_COMMIT)와 안전망인
 * ShippingReconciliationScheduler(주기 보충, 이슈 #169)가 같은 로직을 공유한다.
 *
 * 독립 트랜잭션(REQUIRES_NEW): 리스너는 AFTER_COMMIT 시점이라 활성 트랜잭션이 없고, 스케줄러는 주문마다 격리된 커밋 경계가
 * 필요해 둘 다 이 메서드의 REQUIRES_NEW 로 충족한다.
 *
 * 한 주문당 배송 1건: existsByOrderId 로 흔한 재시도/중복을 미리 거르고, 최종 방어선은 uk_shipping_order UNIQUE 다 —
 * saveAndFlush 로 동기 flush 해 충돌을 즉시 드러낸다.
 *
 * 충돌(DataIntegrityViolationException)·일시 장애 예외는 호출자(리스너/스케줄러)로 전파한다 — REQUIRES_NEW 트랜잭션이 깨끗이
 * 롤백되고, "이미 존재(흡수)" vs "실패(재시도)" 분기와 로깅은 호출자가 정책에 맞게 처리한다.
 */
@Component
public class ShippingProvisioner {

    private static final Logger log = LoggerFactory.getLogger(ShippingProvisioner.class);

    private final ShippingRepository shippingRepository;
    private final OrderRepository orderRepository;
    private final TrackingNumberGenerator trackingNumberGenerator;

    public ShippingProvisioner(ShippingRepository shippingRepository,
                               OrderRepository orderRepository,
                               TrackingNumberGenerator trackingNumberGenerator) {
        this.shippingRepository = shippingRepository;
        this.orderRepository = orderRepository;
        this.trackingNumberGenerator = trackingNumberGenerator;
    }

    /**
     * 주문에 배송이 없으면 PREPARING 배송을 만들고 운송장을 발급한 뒤, 주문을 PAID→PREPARING 으로 락스텝 전진(이슈 #161)시키고
     * 운송장 번호를 비정규화(이슈 #116)한다. 이미 있거나 주문이 없으면 무해하게 건너뛴다.
     *
     * @return 새로 생성하면 true, 이미 있거나 주문이 없어 건너뛰면 false
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean provisionForOrder(Long orderId, String orderNumber) {
        if (shippingRepository.existsByOrderId(orderId)) {
            return false;
        }
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("배송 생성 건너뜀: 주문 없음 orderId={}", orderId);
            return false;
        }
        // 심층 방어(#188): 장기 방치된 PENDING 주문은 PII 익명화 배치가 배송지를 "익명" 으로 마스킹한다. 그런 주문이
        // 뒤늦은 결제로 PAID 가 돼 여기로 오면 "익명" 배송지로 출고될 수 있으므로, 익명화된 주문엔 배송을 만들지 않는다.
        if (order.isAnonymized()) {
            log.warn("배송 생성 건너뜀: 이미 PII 익명화된 주문 order={} — 배송지 마스킹됨", orderNumber);
            return false;
        }
        Shipping shipping = Shipping.prepare(order, order.getShippingInfo(), trackingNumberGenerator.generate());
        shippingRepository.saveAndFlush(shipping);
        // 운송장 번호를 주문에 비정규화한다(이슈 #116) — order 는 이 REQUIRES_NEW 트랜잭션에서 로드된 관리 상태라
        // 더티체킹으로 커밋 시 반영된다. 주문 상세 응답이 운송장 번호를 노출해 프론트가 배송 추적을 연결한다.
        order.recordTrackingNumber(shipping.getTrackingNumber());
        // 배송 생성(PREPARING)에 맞춰 주문도 PAID→PREPARING 으로 락스텝 전진시킨다(이슈 #161). advanceTo 는 합법
        // 전이일 때만 바꾸므로(환불/관리자 선전이로 PAID 가 아니면 무해 무시) 예외로 새지 않는다.
        order.advanceTo(OrderStatus.PREPARING);
        log.info("배송 생성: order={}, tracking={}", orderNumber, shipping.getTrackingNumber());
        return true;
    }
}
