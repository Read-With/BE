package com.kw.readwith.aws.s3;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.kw.readwith.config.AmazonConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AmazonS3Manager{

    private final AmazonS3 amazonS3;

    private final AmazonConfig amazonConfig;

    public String uploadFile(String keyName, MultipartFile file){
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(file.getContentType());
        try {
            amazonS3.putObject(new PutObjectRequest(amazonConfig.getBucket(), keyName, file.getInputStream(), metadata)
                    );
        }catch (IOException e){
            log.error("error at AmazonS3Manager uploadFile : {}", (Object) e.getStackTrace());
        }

        return amazonS3.getUrl(amazonConfig.getBucket(), keyName).toString();
    }

    public String uploadFileFromBase64(String keyName, String base64Data, String contentType){
        // 디코딩 전 모든 공백 문자 제거
        base64Data = base64Data.replaceAll("\\s+", "");
        // Base64 디코딩
        byte[] fileContent = Base64.getDecoder().decode(base64Data);

        // Base64를 MultipartFile 객체로 변환
        // contentType("image/png") 등 실제 타입에 맞춰서 설정
        MultipartFile multipartFile =
                new Base64ToMultipartFile(fileContent, keyName, contentType);

        // uploadFile() 호출
        return uploadFile(keyName, multipartFile);
    }

    public String uploadOriginal(String title, MultipartFile file){
        String slug = slugify(title);
        String ext = getExtension(file.getOriginalFilename());
        String keyName = amazonConfig.getOriginal()+"/"+slug+ext;
        return uploadFile(keyName, file);
    }

    public String uploadMetadata(String title, MultipartFile file){
        String slug = slugify(title);
        String uniqueName = generateUniqueFileName(file.getOriginalFilename());
        String keyName = amazonConfig.getMetadata()+"/"+slug+"/"+uniqueName;
        return uploadFile(keyName, file);
    }

    private String slugify(String input){
        String slug = input.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        return slug.length() > 0 ? slug : UUID.randomUUID().toString();
    }

    private String getExtension(String filename){
        int idx = filename.lastIndexOf('.');
        if(idx > -1){
            return filename.substring(idx);
        }
        return "";
    }

    private String generateUniqueFileName(String originalFilename){
        String ext = "";
        int idx = originalFilename.lastIndexOf('.');
        if(idx > -1){
            ext = originalFilename.substring(idx);
        }
        return UUID.randomUUID().toString()+ext;
    }
}