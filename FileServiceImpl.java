package com.xperexpo.organizationservice.service.impl;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.xperexpo.organizationservice.client.FileExternalService;
import com.xperexpo.organizationservice.exception.ExpoRuntimeException;
import com.xperexpo.organizationservice.payload.FileNameResponse;
import com.xperexpo.organizationservice.payload.FileRemoveResponse;
import com.xperexpo.organizationservice.service.FileService;

@Service
public class FileServiceImpl implements FileService {

	@Autowired
	private FileExternalService fileExternalService;

	@Override
	public String uploadImage(MultipartFile image, String bucketFolderName) {
		ResponseEntity<FileNameResponse> uploadImage = fileExternalService.uploadImage(image, bucketFolderName);
		if (!uploadImage.getStatusCode().equals(HttpStatus.CREATED)) {
			throw ExpoRuntimeException.internalServerError(" file-service uploadImage error");
		}
		return Objects.requireNonNull(uploadImage.getBody()).getFileName();

	}

	@Override
	public boolean removeImage(String fileName, String bucketFolderName) {
		ResponseEntity<FileRemoveResponse> removedImage = fileExternalService.removeImage(bucketFolderName, fileName);
		if (!Objects.requireNonNull(removedImage.getBody()).isRemoved()) {
			throw ExpoRuntimeException.internalServerError(" file-service removeOrganizationLogo error");
		}
		return true;
	}
}
