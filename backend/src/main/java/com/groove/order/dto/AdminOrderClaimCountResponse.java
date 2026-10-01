package com.groove.order.dto;

import java.util.List;

import com.groove.order.entity.OrderClaimStatus;
import com.groove.order.entity.OrderClaimType;
import com.groove.order.repository.OrderClaimCountRow;

/** 관리자 클레임 큐의 유형별 상태 건수. {@code return} 은 예약어라 {@code returns} 로 둔다. */
public record AdminOrderClaimCountResponse(ClaimCounts cancel, ClaimCounts returns) {

	public static AdminOrderClaimCountResponse from(List<OrderClaimCountRow> rows) {
		return new AdminOrderClaimCountResponse(
				ClaimCounts.of(rows, OrderClaimType.CANCEL),
				ClaimCounts.of(rows, OrderClaimType.RETURN));
	}

	public record ClaimCounts(long requested, long collecting, long done, long rejected, long withdrawn,
			long total) {

		static ClaimCounts of(List<OrderClaimCountRow> rows, OrderClaimType type) {
			long requested = 0L;
			long collecting = 0L;
			long done = 0L;
			long rejected = 0L;
			long withdrawn = 0L;
			for (OrderClaimCountRow row : rows) {
				if (row.getType() != type) {
					continue;
				}
				// enum 전체를 switch 로 다뤄 상태가 추가되면 컴파일 단계에서 드러나게 한다
				switch (row.getStatus()) {
					case REQUESTED -> requested += row.getCount();
					case COLLECTING -> collecting += row.getCount();
					case DONE -> done += row.getCount();
					case REJECTED -> rejected += row.getCount();
					case WITHDRAWN -> withdrawn += row.getCount();
				}
			}
			return new ClaimCounts(requested, collecting, done, rejected, withdrawn,
					requested + collecting + done + rejected + withdrawn);
		}
	}
}
