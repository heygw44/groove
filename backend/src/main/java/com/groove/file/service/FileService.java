package com.groove.file.service;

import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.groove.file.config.FileProperties;
import com.groove.file.dto.FileUploadResponse;
import com.groove.file.storage.FileStorage;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FileService {

	private static final long MAX_SIZE = 5 * 1024 * 1024L;

	private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

	private static final Map<String, String> EXTENSION_CONTENT_TYPES = Map.of(
			"jpg", "image/jpeg",
			"jpeg", "image/jpeg",
			"png", "image/png",
			"webp", "image/webp"
	);

	private final FileStorage fileStorage;
	private final FileProperties fileProperties;

	public FileUploadResponse uploadImage(MultipartFile file) {
		String extension = validate(file);
		String key = fileStorage.store(file, extension);
		String url = buildUrl(key);
		return FileUploadResponse.of(url, file);
	}

	private String validate(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new BusinessException(ErrorCode.FILE_EMPTY);
		}
		if (file.getSize() > MAX_SIZE) {
			throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED);
		}

		String extension = extractExtension(file);
		if (!ALLOWED_EXTENSIONS.contains(extension)) {
			throw new BusinessException(ErrorCode.FILE_INVALID_FORMAT);
		}

		String expectedContentType = EXTENSION_CONTENT_TYPES.get(extension);
		if (!expectedContentType.equals(file.getContentType())) {
			throw new BusinessException(ErrorCode.FILE_INVALID_FORMAT);
		}

		return extension;
	}

	private String extractExtension(MultipartFile file) {
		String originalName = file.getOriginalFilename();
		if (originalName == null || originalName.isBlank()) {
			throw new BusinessException(ErrorCode.FILE_INVALID_FORMAT);
		}

		int dotIndex = originalName.lastIndexOf('.');
		if (dotIndex < 0 || dotIndex == originalName.length() - 1) {
			throw new BusinessException(ErrorCode.FILE_INVALID_FORMAT);
		}

		return originalName.substring(dotIndex + 1).toLowerCase();
	}

	private String buildUrl(String key) {
		String baseUrl = fileProperties.baseUrl();
		if (baseUrl.endsWith("/")) {
			baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
		}
		return baseUrl + "/" + key;
	}
}
