package com.groove.payment.entity;

import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.groove.global.common.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 토스 웹훅(PAYMENT_STATUS_CHANGED) 수신 이력. 서명 헤더가 없어 본문을 그대로 믿지 않고 재조회로
 * 검증하므로, 여기 담는 toss_status 는 판단이 아니라 멱등 키(중복 전송 흡수)와 감사 용도다.
 */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "payment_webhook_event",
		uniqueConstraints = @UniqueConstraint(name = "uk_payment_webhook_event_dedup",
				columnNames = {"payment_key", "toss_status", "event_created_at"}))
public class PaymentWebhookEvent extends BaseTimeEntity {

	private static final int MAX_DETAIL_LENGTH = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "event_type", length = 50)
	private String eventType;

	@Column(name = "payment_key", length = 200)
	private String paymentKey;

	@Column(name = "toss_order_id", length = 64)
	private String tossOrderId;

	@Column(name = "toss_status", length = 30)
	private String tossStatus;

	@Column(name = "event_created_at")
	private LocalDateTime eventCreatedAt;

	/**
	 * BaseTimeEntity 의 createdAt 은 JPA 기본 감사 시계를 쓴다. 나머지 결제 도메인은 전부 주입된
	 * Asia/Seoul Clock 빈으로 시각을 만들어(ClockConfig) 테스트에서도 고정할 수 있어, 이 필드는 그 Clock 으로
	 * 따로 채운다.
	 */
	@Column(name = "received_at", nullable = false)
	private LocalDateTime receivedAt;

	@Column(name = "processed_at")
	private LocalDateTime processedAt;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(length = 30)
	private PaymentWebhookResult result;

	@Column(length = MAX_DETAIL_LENGTH)
	private String detail;

	private PaymentWebhookEvent(String eventType, String paymentKey, String tossOrderId, String tossStatus,
			LocalDateTime eventCreatedAt, LocalDateTime receivedAt) {
		this.eventType = eventType;
		this.paymentKey = paymentKey;
		this.tossOrderId = tossOrderId;
		this.tossStatus = tossStatus;
		this.eventCreatedAt = eventCreatedAt;
		this.receivedAt = receivedAt;
	}

	public static PaymentWebhookEvent receive(String eventType, String paymentKey, String tossOrderId,
			String tossStatus, LocalDateTime eventCreatedAt, LocalDateTime receivedAt) {
		return new PaymentWebhookEvent(eventType, paymentKey, tossOrderId, tossStatus, eventCreatedAt, receivedAt);
	}

	public void markProcessed(PaymentWebhookResult result, String detail, LocalDateTime processedAt) {
		this.result = result;
		this.detail = truncate(detail);
		this.processedAt = processedAt;
	}

	private static String truncate(String value) {
		if (value == null || value.length() <= MAX_DETAIL_LENGTH) {
			return value;
		}
		return value.substring(0, MAX_DETAIL_LENGTH);
	}
}
