package com.groove.order.api;

import com.groove.security.AuthPrincipal;
import com.groove.common.api.CursorCodec;
import com.groove.common.api.KeysetSort;
import com.groove.common.api.PageResponse;
import com.groove.common.api.ScrollResponse;
import com.groove.common.api.SortValidator;
import com.groove.order.api.dto.OrderSummaryResponse;
import com.groove.order.application.OrderService;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Window;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/** 회원 본인 주문 목록. 정렬 화이트리스트는 createdAt 만 허용. */
@Tag(name = "내 주문", description = "로그인한 회원 본인의 주문 목록 조회 (인증 필요)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/members/me/orders")
public class MemberOrderController {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of("createdAt");

    /** 커서 페이징 윈도우 상한. */
    private static final int MAX_SCROLL_SIZE = 100;

    private final OrderService orderService;
    private final CursorCodec cursorCodec;

    public MemberOrderController(OrderService orderService, CursorCodec cursorCodec) {
        this.orderService = orderService;
        this.cursorCodec = cursorCodec;
    }

    @Operation(summary = "내 주문 목록 조회",
            description = "로그인한 회원 본인의 주문을 페이징 조회한다. status 로 주문 상태를 필터링할 수 있으며, 정렬은 createdAt 만 허용한다(기본 최신순).")
    @ApiResponse(responseCode = "200", description = "주문 목록 조회 성공")
    @ApiResponse(responseCode = "400", description = "허용되지 않은 정렬 키 등 입력 검증 실패")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @GetMapping
    public ResponseEntity<PageResponse<OrderSummaryResponse>> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Parameter(description = "주문 상태 필터 (미지정 시 전체)", example = "PAID")
            @RequestParam(required = false) OrderStatus status,
            @ParameterObject
            @PageableDefault(size = 20)
            @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        SortValidator.requireAllowed(pageable.getSort(), ALLOWED_SORT_PROPERTIES);

        Page<Order> page = orderService.listForMember(principal.memberId(), status, pageable);
        return ResponseEntity.ok(PageResponse.from(page, OrderSummaryResponse::from));
    }

    @Operation(summary = "내 주문 목록 커서(keyset) 조회",
            description = "내 주문을 keyset 커서 페이징으로 조회한다. 정렬은 createdAt 최신순으로 고정되며, "
                    + "깊은 페이지에서 offset 스캔 비용 없이 (member_id, created_at) 인덱스를 타고 전진한다. 첫 페이지는 "
                    + "cursor 없이 호출하고, 응답의 nextCursor 를 다음 요청 cursor 로 넘긴다.")
    @ApiResponse(responseCode = "200", description = "주문 목록 조회 성공 (다음 페이지 커서 포함)")
    @ApiResponse(responseCode = "400", description = "잘못된 커서 등 입력 검증 실패")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @GetMapping("/scroll")
    public ResponseEntity<ScrollResponse<OrderSummaryResponse>> listScroll(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Parameter(description = "주문 상태 필터 (미지정 시 전체)", example = "PAID")
            @RequestParam(required = false) OrderStatus status,
            @Parameter(description = "다음 페이지 커서 (첫 페이지는 생략)")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 (1~100, 기본 20)", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        // 정렬은 서버 고정(createdAt DESC) + id tiebreaker. 클라이언트 sort 입력을 받지 않는다.
        Sort sort = KeysetSort.withIdTiebreaker(Sort.by(Sort.Direction.DESC, "createdAt"));
        ScrollPosition position = cursorCodec.resolve(cursor, sort);
        int limit = Math.clamp(size, 1, MAX_SCROLL_SIZE);

        Window<Order> window = orderService.listForMemberKeyset(principal.memberId(), status, limit, sort, position);
        return ResponseEntity.ok(ScrollResponse.from(window, OrderSummaryResponse::from, cursorCodec, sort));
    }
}
