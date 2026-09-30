package com.groove.order.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.groove.order.dto.AdminOrderItemSearchCondition;
import com.groove.order.dto.AdminOrderItemSummaryResponse;
import com.groove.order.dto.AdminOrderSearchCondition;
import com.groove.order.dto.AdminOrderSummaryResponse;
import com.groove.order.dto.OrderListItemRow;
import com.groove.order.dto.OrderSearchCondition;
import com.groove.order.dto.OrderSummaryResponse;
import com.groove.order.entity.OrderStatusGroup;

/** 마이페이지·관리자 주문 목록 조회 전용. 상세는 JPA 엔티티 그래프({@code OrderRepository})를 쓴다. */
@Mapper
public interface OrderQueryMapper {

	List<OrderSummaryResponse> findMyOrders(OrderSearchCondition condition);

	long countMyOrders(OrderSearchCondition condition);

	/** 페이지에 담긴 주문 id 들의 상품 행을 한 번에 조회한다. statusGroup 이 있으면 그 상태의 상품주문만 돌려준다. 정렬은 order_id, order_item.id 오름차순이다. */
	List<OrderListItemRow> findItemsByOrderIds(@Param("orderIds") List<Long> orderIds,
			@Param("statusGroup") OrderStatusGroup statusGroup);

	List<AdminOrderSummaryResponse> findAdminOrders(AdminOrderSearchCondition condition);

	long countAdminOrders(AdminOrderSearchCondition condition);

	List<AdminOrderItemSummaryResponse> findAdminOrderItems(AdminOrderItemSearchCondition condition);

	long countAdminOrderItems(AdminOrderItemSearchCondition condition);
}
