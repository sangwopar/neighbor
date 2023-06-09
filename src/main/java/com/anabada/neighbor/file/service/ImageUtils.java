package com.anabada.neighbor.file.service;

import com.anabada.neighbor.file.domain.FileRequest;
import com.anabada.neighbor.file.domain.FileResponse;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class ImageUtils {
//    private final String uploadPath = Paths.get("/Users", "upload_anabada").toString();
//    private final String uploadPath = "C:\\upload_anabada\\"

    private final String uploadPath = Paths.get(System.getProperty("user.home"), "upload_anabada").toString();
    /**
     * 다중 이미지 업로드
     * @param multipartFileList - 이미지 객체 List
     * @return db에 저장할 이미지 정보 List
     */
    public List<FileRequest> uploadImages(final List<MultipartFile> multipartFileList) {
        List<FileRequest> images = new ArrayList<>();
        for (MultipartFile multipartFile : multipartFileList) {
            if (multipartFile.isEmpty()) {//리스트안의 이미지가 없을경우 건너뛰기
                continue;
            }
            images.add(uploadImage(multipartFile));
        }
        return images;
    }

    /**
     * 단일 이미지 업로드
     * @param multipartFile - 이미지 객체
     * @return db에 저장할 이미지 정보
     */
    private FileRequest uploadImage(MultipartFile multipartFile) {
        if (multipartFile.isEmpty()) { //업로드된 파일이 비어 있는지, 즉 멀티파트 형식에서 파일이 선택되지 않았는지 또는 선택한 파일에 내용이 없는지 여부를 반환한다.
            return null;
        }
        String saveName = generateSaveFilename(multipartFile.getOriginalFilename());//getOriginalFilename 클라이언트 파일시스템의 원래 파일 이름을 반환한다.
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));//현재 컴퓨터의 날짜형식을 yyMMdd로 String변환해 가져옴
        String uploadPath = getUploadPath(today) + File.separator + saveName;
        File uploadFile = new File(uploadPath);
        try {
            multipartFile.transferTo(uploadFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return  FileRequest.builder()
                .originalName(multipartFile.getOriginalFilename())
                .saveName(saveName)
                .size(multipartFile.getSize())
                .build();
    }

    /**
     *  실제 디스크에 저장되는 파일명 생성
     *  uuid에는32자리의 랜덤문자열, extension에는 업로드 한 파일의 확장자를 담아
     *  랜덤문자열 + "." + 파일 확장자에 해당되는 파일명을 리턴함.
     * @param originalFilename 원본 파일명
     * @return  디스크에 저장할 파일명
     */
    private String generateSaveFilename(final String originalFilename) {
        String uuid = UUID.randomUUID().toString().replaceAll("-", "");//랜덤uuid생성 중간에 있는 "-"들은 replaceAll을 이용해 제거
        String extension = StringUtils.getFilenameExtension(originalFilename);//업로드한 파일의 확장자명을 가져옴
        return uuid + "." + extension;
    }

    /**
     * 업로드 경로 반환
     * @return 업로드 경로
     */
    private String getUploadPath() {
        return makeDirectories(uploadPath);
    }
    /**
     * 업로드 경로 반환
     * @param addPath - 추가 경로
     * @return 업로드 경로
     */
    private String getUploadPath(final String addPath) {
        return makeDirectories(uploadPath + File.separator + addPath);
    }

    /**
     * 업로드 폴더(디렉토리) 생성
     * @param path - 업로드경로
     * @return 업로드 경로
     */
    private String makeDirectories(final String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir.getPath();
    }

    /**
     * 다운로드할 첨부파일(리소스) 조회
     * @param file 첨부파일 상세정보
     * @return 첨부파일(리소스)
     */
    public Resource readFileAsResource(FileResponse file) {
        String uploadedDate = file.getCreatedDate().toLocalDate().format(DateTimeFormatter.ofPattern("yyMMdd"));
        String filename = file.getSaveName();
        Path filePath = Paths.get(uploadPath, uploadedDate, filename);
        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isFile()) {
                throw new RuntimeException("file not found : " + filePath.toString());
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new RuntimeException("file not found : " + filePath.toString());
        }
    }

    public Stream<Path> loadAll(FileResponse file) {
        String uploadedDate = file.getCreatedDate().toLocalDate().format(DateTimeFormatter.ofPattern("yyMMdd"));
        String filename = file.getSaveName();
        Path filePath = Paths.get(uploadPath, uploadedDate, filename);
        System.out.println(filePath);
        try {
            return Files.walk(filePath, 1)
                    .filter(path -> !path.equals(filePath)).map(filePath::relativize);
        } catch (IOException e) {
            throw new RuntimeException(e) ;
       }

    }

}
