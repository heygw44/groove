package com.groove.global.health;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.groove.global.common.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Health", description = "서버 상태 확인")
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

	@Operation(summary = "헬스체크",
			description = "프로세스 생존 여부(liveness)만 확인한다. DB·Redis 를 포함한 준비 상태는 /actuator/health 를 본다.")
	@SecurityRequirements
	@GetMapping
	public ApiResponse<Map<String, Object>> health() {
		return ApiResponse.ok(Map.of(
				"status", "UP",
				"serverTime", LocalDateTime.now()
		));
	}
}
