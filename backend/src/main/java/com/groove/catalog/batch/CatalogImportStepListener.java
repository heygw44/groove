package com.groove.catalog.batch;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.SkipListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.stereotype.Component;

import com.groove.catalog.client.dto.DiscogsMasterVersionsResponse.Version;
import com.groove.catalog.dto.CatalogImportItem;

import lombok.extern.slf4j.Slf4j;

/**
 * 스킵된 릴리즈 id 를 모아 ExecutionContext 에 남긴다.
 * Step 실행은 단일 스레드라 인스턴스를 싱글턴 빈으로 공유해도 안전하다.
 */
@Slf4j
@Component
public class CatalogImportStepListener implements StepExecutionListener, SkipListener<Version, CatalogImportItem> {

	static final String SKIPPED_RELEASE_IDS_KEY = "skippedReleaseIds";

	private final List<Long> skippedReleaseIds = new ArrayList<>();

	@Override
	public void beforeStep(StepExecution stepExecution) {
		skippedReleaseIds.clear();
	}

	@Override
	public void onSkipInRead(Throwable throwable) {
		log.warn("릴리즈 목록 조회 중 스킵 발생", throwable);
	}

	@Override
	public void onSkipInProcess(Version item, Throwable throwable) {
		log.warn("릴리즈 스킵 id={} 사유={}", item.id(), throwable.getMessage());
		skippedReleaseIds.add(item.id());
	}

	@Override
	public void onSkipInWrite(CatalogImportItem item, Throwable throwable) {
		log.warn("릴리즈 등록 스킵 id={} 사유={}", item.discogsReleaseId(), throwable.getMessage());
		skippedReleaseIds.add(item.discogsReleaseId());
	}

	@Override
	public ExitStatus afterStep(StepExecution stepExecution) {
		String joined = skippedReleaseIds.stream().map(String::valueOf).collect(Collectors.joining(","));
		stepExecution.getExecutionContext().putString(SKIPPED_RELEASE_IDS_KEY, joined);
		return stepExecution.getExitStatus();
	}
}
