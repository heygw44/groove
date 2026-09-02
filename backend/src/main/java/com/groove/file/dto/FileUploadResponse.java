package com.groove.file.dto;

import org.springframework.web.multipart.MultipartFile;

public record FileUploadResponse(
		String url,
		String originalName,
		long size
) {

	public static FileUploadResponse of(String url, MultipartFile file) {
		return new FileUploadResponse(url, file.getOriginalFilename(), file.getSize());
	}
}
