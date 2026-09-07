package com.groove.limited.entity;

import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;
import java.util.Map;

import org.hibernate.annotations.ColumnDefault;

import com.groove.global.common.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 마감된 드롭의 실패 집계 스냅샷. 진행 중인 드롭은 Redis 가 들고 있고, 마감 시점에 이 테이블로 옮겨진다. */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "limited_drop_stat",
		uniqueConstraints = @UniqueConstraint(name = "uk_limited_drop_stat_drop", columnNames = "drop_id"))
public class LimitedDropStat extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = LAZY)
	@JoinColumn(name = "drop_id", nullable = false, foreignKey = @ForeignKey(name = "fk_limited_drop_stat_drop"))
	private LimitedDrop drop;

	@Column(nullable = false)
	@ColumnDefault("0")
	private int soldOutCount;

	@Column(nullable = false)
	@ColumnDefault("0")
	private int alreadyPurchasedCount;

	@Column(nullable = false)
	@ColumnDefault("0")
	private int notOpenCount;

	@Column(nullable = false)
	@ColumnDefault("0")
	private int closedCount;

	@Column(nullable = false)
	private LocalDateTime flushedAt;

	private LimitedDropStat(LimitedDrop drop) {
		this.drop = drop;
	}

	public static LimitedDropStat of(LimitedDrop drop, Map<LimitedAttemptResult, Long> counts,
			LocalDateTime flushedAt) {
		LimitedDropStat stat = new LimitedDropStat(drop);
		stat.apply(counts, flushedAt);
		return stat;
	}

	/** Redis Hash 는 드롭 시작부터의 누적 총합이라, 가산하지 않고 덮어써야 재flush 가 멱등해진다. */
	public void apply(Map<LimitedAttemptResult, Long> counts, LocalDateTime flushedAt) {
		this.soldOutCount = countOf(counts, LimitedAttemptResult.SOLD_OUT);
		this.alreadyPurchasedCount = countOf(counts, LimitedAttemptResult.ALREADY_PURCHASED);
		this.notOpenCount = countOf(counts, LimitedAttemptResult.NOT_OPEN);
		this.closedCount = countOf(counts, LimitedAttemptResult.CLOSED);
		this.flushedAt = flushedAt;
	}

	private static int countOf(Map<LimitedAttemptResult, Long> counts, LimitedAttemptResult result) {
		return counts.getOrDefault(result, 0L).intValue();
	}
}
