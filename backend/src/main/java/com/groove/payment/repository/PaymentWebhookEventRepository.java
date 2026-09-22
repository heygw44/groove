package com.groove.payment.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.groove.payment.entity.PaymentWebhookEvent;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, Long> {

	Optional<PaymentWebhookEvent> findByPaymentKeyAndTossStatusAndEventCreatedAt(String paymentKey,
			String tossStatus, LocalDateTime eventCreatedAt);
}
