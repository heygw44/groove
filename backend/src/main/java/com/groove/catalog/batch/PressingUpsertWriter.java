package com.groove.catalog.batch;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import com.groove.catalog.dto.CatalogImportItem;
import com.groove.catalog.service.CatalogImportRegistrar;

import lombok.RequiredArgsConstructor;

/** 청크 단위로 넘어온 아이템을 실제 상품/앨범으로 등록한다. */
@Component
@RequiredArgsConstructor
public class PressingUpsertWriter implements ItemWriter<CatalogImportItem> {

	private final CatalogImportRegistrar registrar;

	@Override
	public void write(Chunk<? extends CatalogImportItem> chunk) {
		for (CatalogImportItem item : chunk) {
			registrar.register(item);
		}
	}
}
