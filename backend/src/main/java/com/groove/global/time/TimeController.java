package com.groove.global.time;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.groove.global.common.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 클라이언트 카운트다운의 기준 시각. 캐시되면 시각이 굳어버리므로 no-store 로 응답한다. */
@Tag(name = "Time", description = "서버 시각")
@RestController
@RequestMapping("/api/v1/time")
@RequiredArgsConstructor
public class TimeController {

	private final Clock clock;

	@Operation(summary = "서버 시각 조회")
	@SecurityRequirements
	@GetMapping
	public ResponseEntity<ApiResponse<ServerTimeResponse>> getServerTime() {
		OffsetDateTime serverTime = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(ApiResponse.ok(new ServerTimeResponse(serverTime)));
	}
}
