package com.example.aiplantdisease.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class MinioStorageService {

  private final MinioClient minioClient;

  @Value("${minio.bucket}")
  private String bucket;

  @Value("${minio.presignedUrlSeconds:600}")
  private int presignedUrlSeconds;

  public MinioStorageService(
    @Value("${minio.endpoint}") String endpoint,
    @Value("${minio.accessKey}") String accessKey,
    @Value("${minio.secretKey}") String secretKey
  ) throws Exception {
    this.minioClient = MinioClient.builder()
      .endpoint(endpoint)
      .credentials(accessKey, secretKey)
      .build();
  }

  public String buildObjectKey() {
    // 按需求：`crop-pest/{年月日}/{uuid}.jpg`
    // 这里用 yyyyMMdd 作为 {年月日}，避免特殊字符与路径问题。
    String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE); // yyyyMMdd
    String uuid = UUID.randomUUID().toString();
    return "crop-pest/" + date + "/" + uuid + ".jpg";
  }

  public void ensureBucketExists() throws Exception {
    boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
    if (!exists) {
      minioClient.makeBucket(
        io.minio.MakeBucketArgs.builder().bucket(bucket).build()
      );
    }
  }

  public void uploadJpg(String objectKey, byte[] bytes, String contentType) throws Exception {
    ensureBucketExists();

    minioClient.putObject(
      PutObjectArgs.builder()
        .bucket(bucket)
        .object(objectKey)
        .contentType(contentType)
        .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
        .build()
    );
  }

  public String generatePresignedUrl(String objectKey) throws Exception {
    // 生成临时签名URL，避免文件泄露。
    return minioClient.getPresignedObjectUrl(
      GetPresignedObjectUrlArgs.builder()
        .method(Method.GET)
        .bucket(bucket)
        .object(objectKey)
        .expiry(presignedUrlSeconds)
        .build()
    );
  }
}

