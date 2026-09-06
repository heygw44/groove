package com.groove.catalog.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.catalog.dto.CatalogImportItem;
import com.groove.catalog.dto.CatalogImportResult;
import com.groove.inventory.service.StockService;
import com.groove.product.entity.Album;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Genre;
import com.groove.product.entity.Label;
import com.groove.product.entity.Product;
import com.groove.product.repository.AlbumRepository;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.GenreRepository;
import com.groove.product.repository.LabelRepository;
import com.groove.product.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

/** Discogs 카탈로그 항목을 상품으로 등록한다. 아티스트/레이블/앨범은 이름·마스터 id 기준으로 재사용한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogImportRegistrar {

	private final ArtistRepository artistRepository;
	private final LabelRepository labelRepository;
	private final AlbumRepository albumRepository;
	private final GenreRepository genreRepository;
	private final ProductRepository productRepository;
	private final StockService stockService;

	@Transactional
	public CatalogImportResult register(CatalogImportItem item) {
		Artist artist = resolveArtist(item.artistName());
		Label label = resolveLabel(item.labelName(), item.country());
		Album album = resolveAlbum(item, artist);

		Product product = Product.createImported(album, item.title(), artist, label, item.country(),
				item.pressingYear(), item.catalogNo(), item.barcode(), item.editionType(), item.price(),
				item.discogsReleaseId());
		if (item.genreNames() != null) {
			item.genreNames().forEach(genreName -> genreRepository.findByName(genreName)
					.ifPresent(product::addGenre));
		}
		Product saved = productRepository.save(product);
		stockService.create(saved, 0);

		return new CatalogImportResult(saved.getId(), album.getId());
	}

	private Artist resolveArtist(String artistName) {
		return artistRepository.findFirstByNameOrderByIdAsc(artistName)
				.orElseGet(() -> artistRepository.save(Artist.create(artistName, null, null)));
	}

	private Label resolveLabel(String labelName, String country) {
		if (labelName == null || labelName.isBlank()) {
			return null;
		}
		return labelRepository.findFirstByNameOrderByIdAsc(labelName)
				.orElseGet(() -> labelRepository.save(Label.create(labelName, country)));
	}

	private Album resolveAlbum(CatalogImportItem item, Artist artist) {
		if (item.discogsMasterId() != null) {
			return albumRepository.findByDiscogsMasterId(item.discogsMasterId())
					.orElseGet(() -> createAlbum(item, artist));
		}
		return albumRepository.findFirstByTitleAndArtistIdOrderByIdAsc(item.title(), artist.getId())
				.orElseGet(() -> createAlbum(item, artist));
	}

	private Album createAlbum(CatalogImportItem item, Artist artist) {
		Album album = Album.create(item.title(), artist, item.pressingYear());
		if (item.discogsMasterId() != null) {
			album.linkDiscogsMaster(item.discogsMasterId());
		}
		return albumRepository.save(album);
	}
}
