package com.groove.limited.entity;

import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.groove.global.common.BaseTimeEntity;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.product.entity.Product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 한정반 드롭. 상품당 활성 드롭 1개(uk_limited_drop_product)를 DB 로 보장하고, 상태 전이는 엔티티가 직접 검증한다. */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "limited_drop",
		uniqueConstraints = @UniqueConstraint(name = "uk_limited_drop_product", columnNames = "product_id"),
		indexes = @Index(name = "idx_limited_drop_status_open", columnList = "status, open_at"))
public class LimitedDrop extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = LAZY)
	@JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_limited_drop_product"))
	private Product product;

	@Column(name = "total_quantity", nullable = false)
	private int totalQuantity;

	@Column(name = "per_member_limit", nullable = false)
	@ColumnDefault("1")
	private int perMemberLimit;

	@Column(name = "open_at", nullable = false)
	private LocalDateTime openAt;

	@Column(name = "close_at", nullable = false)
	private LocalDateTime closeAt;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	@ColumnDefault("'SCHEDULED'")
	private LimitedDropStatus status;

	@Column(name = "sold_count", nullable = false)
	@ColumnDefault("0")
	private int soldCount;

	@Builder(access = PRIVATE)
	private LimitedDrop(Product product, int totalQuantity, int perMemberLimit, LocalDateTime openAt,
			LocalDateTime closeAt) {
		this.product = product;
		this.totalQuantity = totalQuantity;
		this.perMemberLimit = perMemberLimit;
		this.openAt = openAt;
		this.closeAt = closeAt;
		this.status = LimitedDropStatus.SCHEDULED;
		this.soldCount = 0;
	}

	public static LimitedDrop schedule(Product product, int totalQuantity, Integer perMemberLimit,
			LocalDateTime openAt, LocalDateTime closeAt) {
		int resolvedPerMemberLimit = perMemberLimit == null ? 1 : perMemberLimit;
		validateSchedule(totalQuantity, resolvedPerMemberLimit, openAt, closeAt);

		return LimitedDrop.builder()
				.product(product)
				.totalQuantity(totalQuantity)
				.perMemberLimit(resolvedPerMemberLimit)
				.openAt(openAt)
				.closeAt(closeAt)
				.build();
	}

	/** SCHEDULED 상태에서만 수량/일정을 바꿀 수 있다. */
	public void reschedule(int totalQuantity, int perMemberLimit, LocalDateTime openAt, LocalDateTime closeAt) {
		if (this.status != LimitedDropStatus.SCHEDULED) {
			throw new BusinessException(ErrorCode.LIMITED_INVALID_STATUS);
		}
		validateSchedule(totalQuantity, perMemberLimit, openAt, closeAt);
		this.totalQuantity = totalQuantity;
		this.perMemberLimit = perMemberLimit;
		this.openAt = openAt;
		this.closeAt = closeAt;
	}

	public void open() {
		if (this.status != LimitedDropStatus.SCHEDULED) {
			throw new BusinessException(ErrorCode.LIMITED_INVALID_STATUS);
		}
		this.status = LimitedDropStatus.OPEN;
	}

	public void markSoldOut() {
		if (this.status != LimitedDropStatus.OPEN) {
			throw new BusinessException(ErrorCode.LIMITED_INVALID_STATUS);
		}
		this.status = LimitedDropStatus.SOLD_OUT;
	}

	public void close() {
		if (this.status != LimitedDropStatus.OPEN && this.status != LimitedDropStatus.SOLD_OUT) {
			throw new BusinessException(ErrorCode.LIMITED_INVALID_STATUS);
		}
		this.status = LimitedDropStatus.CLOSED;
	}

	/** 스케줄러 지연으로 open_at 은 지났지만 실제 오픈 처리(Redis 카운터 세팅 등)가 안 된 상태를 NOT_OPEN 으로 거른다. */
	public void validatePurchasable(LocalDateTime now) {
		if (this.status == LimitedDropStatus.CLOSED || !now.isBefore(this.closeAt)) {
			throw new BusinessException(ErrorCode.LIMITED_CLOSED);
		}
		if (this.status == LimitedDropStatus.SCHEDULED || now.isBefore(this.openAt)) {
			throw new BusinessException(ErrorCode.LIMITED_NOT_OPEN);
		}
		if (this.status == LimitedDropStatus.SOLD_OUT) {
			throw new BusinessException(ErrorCode.LIMITED_SOLD_OUT);
		}
	}

	public void recordSale(int quantity) {
		if (quantity <= 0) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
		}
		if (this.soldCount + quantity > this.totalQuantity) {
			throw new BusinessException(ErrorCode.LIMITED_SOLD_OUT);
		}
		this.soldCount += quantity;
		if (this.soldCount == this.totalQuantity && this.status == LimitedDropStatus.OPEN) {
			markSoldOut();
		}
	}

	/**
	 * PENDING 주문 만료 등으로 선점을 되돌린다. 마감 시각이 지났으면 SOLD_OUT 을 OPEN 으로 되돌리지 않는다.
	 */
	public void restoreSale(int quantity, LocalDateTime now) {
		if (quantity <= 0 || this.soldCount - quantity < 0) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
		}
		this.soldCount -= quantity;
		if (this.status == LimitedDropStatus.SOLD_OUT && now.isBefore(this.closeAt)) {
			this.status = LimitedDropStatus.OPEN;
		}
	}

	public int remainingQuantity() {
		return this.totalQuantity - this.soldCount;
	}

	public boolean isActive() {
		return this.status != LimitedDropStatus.CLOSED;
	}

	private static void validateSchedule(int totalQuantity, int perMemberLimit, LocalDateTime openAt,
			LocalDateTime closeAt) {
		if (totalQuantity <= 0) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
		}
		if (perMemberLimit <= 0 || perMemberLimit > totalQuantity) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
		}
		if (openAt == null || closeAt == null || !openAt.isBefore(closeAt)) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
		}
		if (!openAt.isAfter(LocalDateTime.now())) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
		}
	}
}
