package com.groove.file.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.groove.file.dto.FileUploadResponse;
import com.groove.file.service.FileService;
import com.groove.global.common.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "File", description = "파일 업로드")
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

	private final FileService fileService;

	@Operation(summary = "이미지 업로드")
	@PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ApiResponse<FileUploadResponse> uploadImage(@RequestPart("file") MultipartFile file) {
		return ApiResponse.ok(fileService.uploadImage(file));
	}
}
