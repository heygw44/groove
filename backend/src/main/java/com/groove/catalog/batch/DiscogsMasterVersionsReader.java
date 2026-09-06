package com.groove.catalog.batch;

import java.util.List;

import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamReader;

import com.groove.catalog.client.PressingLookupClient;
import com.groove.catalog.client.dto.DiscogsMasterVersionsResponse;
import com.groove.catalog.client.dto.DiscogsMasterVersionsResponse.Version;

/** Discogs 마스터 버전 목록을 페이지 단위로 조회해 스트리밍한다. */
public class DiscogsMasterVersionsReader implements ItemStreamReader<Version> {

	static final String PAGE_KEY = "discogsMasterImport.page";
	static final String INDEX_KEY = "discogsMasterImport.index";

	private final PressingLookupClient client;
	private final long masterId;

	private int page;
	private int index;
	private int totalPages;
	private List<Version> buffer;

	public DiscogsMasterVersionsReader(PressingLookupClient client, long masterId) {
		this.client = client;
		this.masterId = masterId;
	}

	@Override
	public void open(ExecutionContext executionContext) {
		// 재시작 시 이미 처리한 페이지를 다시 조회하지 않도록 위치를 ExecutionContext 에서 복원한다.
		page = executionContext.containsKey(PAGE_KEY) ? executionContext.getInt(PAGE_KEY) : 0;
		index = executionContext.containsKey(INDEX_KEY) ? executionContext.getInt(INDEX_KEY) : 0;
		buffer = null;
		totalPages = Integer.MAX_VALUE;
	}

	@Override
	public Version read() {
		while (buffer == null || index >= buffer.size()) {
			if (buffer != null) {
				page++;
				index = 0;
			}
			if (page >= totalPages) {
				return null;
			}
			fetch(page);
		}
		return buffer.get(index++);
	}

	@Override
	public void update(ExecutionContext executionContext) {
		executionContext.putInt(PAGE_KEY, page);
		executionContext.putInt(INDEX_KEY, index);
	}

	@Override
	public void close() {
		buffer = null;
	}

	private void fetch(int pageToFetch) {
		DiscogsMasterVersionsResponse response = client.getMasterVersions(masterId, pageToFetch);
		buffer = response.versions() == null ? List.of() : response.versions();
		totalPages = response.pagination() == null ? pageToFetch + 1 : response.pagination().pages();
	}
}
