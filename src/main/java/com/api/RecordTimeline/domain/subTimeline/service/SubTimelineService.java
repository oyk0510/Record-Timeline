package com.api.RecordTimeline.domain.subTimeline.service;

import com.api.RecordTimeline.domain.bookmark.repository.BookmarkRepository;
import com.api.RecordTimeline.domain.like.repository.LikeRepository;
import com.api.RecordTimeline.domain.mainTimeline.domain.MainTimeline;
import com.api.RecordTimeline.domain.mainTimeline.repository.MainTimelineRepository;
import com.api.RecordTimeline.domain.member.domain.Member;
import com.api.RecordTimeline.domain.member.repository.MemberRepository;
import com.api.RecordTimeline.domain.subTimeline.domain.SubTimeline;
import com.api.RecordTimeline.domain.subTimeline.dto.request.SubTimelineCreateRequestDTO;
//import com.api.RecordTimeline.domain.subTimeline.dto.response.AccessDeniedResponseDTO;
import com.api.RecordTimeline.domain.subTimeline.dto.request.UpdateSubTimelineRequestDTO;
import com.api.RecordTimeline.domain.subTimeline.dto.response.SubMyTimelineResponseDTO;
import com.api.RecordTimeline.domain.subTimeline.dto.response.SubPrivacyUpdateResponseDTO;
import com.api.RecordTimeline.domain.subTimeline.dto.response.SubTimelineWithLikeBookmarkDTO;
import com.api.RecordTimeline.domain.subTimeline.dto.response.SubUpdateStatusResponseDTO;
import com.api.RecordTimeline.domain.subTimeline.repository.SubTimelineRepository;
import com.api.RecordTimeline.global.exception.ApiException;
import com.api.RecordTimeline.global.exception.ErrorType;
import com.api.RecordTimeline.global.s3.S3FileUploader;
import com.api.RecordTimeline.global.security.jwt.JwtAuthenticationToken;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubTimelineService {

    private final SubTimelineRepository subTimelineRepository;
    private final MainTimelineRepository mainTimelineRepository;
    private final BookmarkRepository bookmarkRepository;
    private final LikeRepository likeRepository;
    private final MemberRepository memberRepository;
    private final S3FileUploader s3FileUploader;
    private final ImageUploadService imageUploadService;

    private static final Pattern IMAGE_URL_PATTERN = Pattern.compile("<img[^>]+src=\"([^\"]+)\"");

    public SubTimeline createSubTimeline(SubTimelineCreateRequestDTO request) {
        MainTimeline mainTimeline = mainTimelineRepository.findById(request.getMainTimelineId())
                .orElseThrow(() -> new IllegalArgumentException("해당 메인타임라인을 찾을 수 없습니다. : " + request.getMainTimelineId()));

        // Base64 이미지를 URL로 변환
        String contentWithUrls = replaceBase64WithUrls(request.getContent());

        SubTimeline subTimeline = SubTimeline.builder()
                .mainTimeline(mainTimeline)
                .title(request.getTitle())
                .content(contentWithUrls)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .isPrivate(request.isPrivate())
                .isDone(request.isDone())
                .build();
        return subTimelineRepository.save(subTimeline);
    }

    public SubTimeline updateSubTimeline(Long subTimelineId, UpdateSubTimelineRequestDTO request) {
        SubTimeline existingSubTimeline = subTimelineRepository.findById(subTimelineId)
                .orElseThrow(() -> new IllegalArgumentException("해당 서브타임라인을 찾을 수 없습니다. : " + subTimelineId));

        MainTimeline mainTimeline = existingSubTimeline.getMainTimeline();
        if (mainTimeline.isPrivate()) {
            throw new ApiException(ErrorType._ACCESS_DENIED);
        }

        // 소유권 검증
        checkOwnership(existingSubTimeline.getMainTimeline().getMember().getEmail());

        // 기존 이미지 URL 추출
        List<String> existingImageUrls = extractImageUrls(existingSubTimeline.getContent());

        // Base64 이미지를 URL로 변환
        String contentWithUrls = replaceBase64WithUrls(request.getContent());

        // 새 이미지 URL 추출
        List<String> newImageUrls = extractImageUrls(contentWithUrls);

        // 삭제할 URL 찾기
        List<String> urlsToDelete = existingImageUrls.stream()
                .filter(url -> !newImageUrls.contains(url))
                .collect(Collectors.toList());

        // S3에서 제거된 이미지 삭제
        urlsToDelete.forEach(s3FileUploader::deleteFileFromS3);

        // subTimeline 업데이트
        SubTimeline updatedSubTimeline = existingSubTimeline.toBuilder()
                .title(request.getTitle())
                .content(contentWithUrls)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .isPrivate(request.getIsPrivate())
                .isDone(request.getIsDone())
                .build();

        return subTimelineRepository.save(updatedSubTimeline);
    }


    public SubTimelineWithLikeBookmarkDTO getSubTimelineWithLikeAndBookmark(Long subTimelineId) {
        SubTimeline subTimeline = subTimelineRepository.findById(subTimelineId)
                .orElseThrow(() -> new ApiException(ErrorType._SUBTIMELINE_NOT_FOUND));

        MainTimeline mainTimeline = subTimeline.getMainTimeline();
        if (mainTimeline.isPrivate()) {
            throw new ApiException(ErrorType._ACCESS_DENIED);
        }

        Member member = getCurrentAuthenticatedMember();

        boolean liked = likeRepository.findByMemberAndSubTimeline(member, subTimeline).isPresent();
        boolean bookmarked = bookmarkRepository.findByMemberAndSubTimeline(member, subTimeline).isPresent();

        return new SubTimelineWithLikeBookmarkDTO(
                subTimeline.getId(),
                subTimeline.getTitle(),
                subTimeline.getContent(),
                subTimeline.getStartDate(),
                subTimeline.getEndDate(),
                subTimeline.getLikeCount(),
                subTimeline.getBookmarkCount(),
                liked,
                bookmarked
        );
    }

    public Member getCurrentAuthenticatedMember() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        JwtAuthenticationToken jwtToken = (JwtAuthenticationToken) authentication;
        Long memberId = jwtToken.getUserId();

        // Optional을 사용하지 않고 명시적으로 null 체크를 수행
        Member member = memberRepository.findByIdAndIsDeletedFalse(memberId);
        if (member == null) {
            throw new ApiException(ErrorType._USER_NOT_FOUND_DB);
        }

        return member;
    }


    private List<String> extractImageUrls(String content) {
        Matcher matcher = IMAGE_URL_PATTERN.matcher(content);
        List<String> imageUrls = new ArrayList<>();
        while (matcher.find()) {
            imageUrls.add(matcher.group(1));
        }
        return imageUrls;
    }

    private String replaceBase64WithUrls(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        Matcher matcher = IMAGE_URL_PATTERN.matcher(content);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String base64Image = matcher.group(1);

            // Base64 형식인지 확인
            if (base64Image.startsWith("data:image")) {
                String imageUrl = imageUploadService.uploadBase64Image(base64Image);
                matcher.appendReplacement(result, "<img src=\"" + imageUrl + "\"");
            } else {
                matcher.appendReplacement(result, matcher.group(0)); // 기존 URL 유지
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }

    @Transactional
    public void deleteSubTimeline(Long subTimelineId) {
        SubTimeline subTimeline = subTimelineRepository.findById(subTimelineId)
                .orElseThrow(() -> new IllegalArgumentException("SubTimeline not found"));

        MainTimeline mainTimeline = subTimeline.getMainTimeline();
        if (mainTimeline.isPrivate()) {
            throw new ApiException(ErrorType._ACCESS_DENIED);
        }

        checkOwnership(subTimeline.getMainTimeline().getMember().getEmail());

        // 관련된 북마크와 좋아요 데이터를 삭제
        bookmarkRepository.deleteBySubTimeline(subTimeline);
        likeRepository.deleteBySubTimeline(subTimeline);

        subTimelineRepository.delete(subTimeline);
    }

    public List<SubTimeline> getSubTimelinesByMainTimelineId(Long mainTimelineId) {
        MainTimeline mainTimeline = mainTimelineRepository.findById(mainTimelineId)
                .orElseThrow(() -> new ApiException(ErrorType._MAINTIMELINE_NOT_FOUND));

        if (mainTimeline.isPrivate()) {
            throw new ApiException(ErrorType._ACCESS_DENIED);
        }

        return subTimelineRepository.findByMainTimelineId(mainTimelineId);
    }

    // 서브 타임라인 시작 날짜 기준으로 정렬
    public List<SubTimeline> getSubTimelinesByMainTimelineIdOrderByStartDate(Long mainTimelineId) {

        MainTimeline mainTimeline = mainTimelineRepository.findById(mainTimelineId)
                .orElseThrow(() -> new ApiException(ErrorType._MAINTIMELINE_NOT_FOUND));

        if (mainTimeline.isPrivate()) {
            throw new ApiException(ErrorType._ACCESS_DENIED);
        }

        //return subTimelineRepository.findByMainTimelineIdOrderByStartDate(mainTimelineId);
        return subTimelineRepository.findByMainTimelineIdOrderByStartDateAsc(mainTimelineId);
    }

    // 메인타임라인 제목을 가져오는 메서드 추가
    public String getMainTimelineTitle(Long mainTimelineId) {
        return mainTimelineRepository.findById(mainTimelineId)
                .map(MainTimeline::getTitle)
                .orElseThrow(() -> new IllegalArgumentException("해당 메인타임라인을 찾을 수 없습니다. : " + mainTimelineId));
    }

    // 서브타임라인 공개/비공개 설정
    public SubPrivacyUpdateResponseDTO setSubTimelinePrivacy(Long subTimelineId, boolean isPrivate) {
        SubTimeline subTimeline = subTimelineRepository.findById(subTimelineId)
                .orElseThrow(() -> new ApiException(ErrorType._SUBTIMELINE_NOT_FOUND));

        checkOwnership(subTimeline.getMainTimeline().getMember().getEmail());

        subTimeline.setPrivate(isPrivate);
        subTimelineRepository.save(subTimeline);

        String message = isPrivate ? "서브타임라인이 비공개 처리 되었습니다." : "서브타임라인이 공개 처리 되었습니다.";
        return SubPrivacyUpdateResponseDTO.success(message);
    }

    // 사용자 본인의 서브타임라인 조회 (토큰 필요)
    public List<SubMyTimelineResponseDTO> getMySubTimelines() {
        Member member = getCurrentAuthenticatedMember();
        List<SubTimeline> subTimelines = subTimelineRepository.findByMainTimeline_Member_IdOrderByStartDateAsc(member.getId());

        // 서브타임라인 엔티티를 SubMyTimelineResponseDTO로 변환하여 리스트로 반환
        return subTimelines.stream()
                .map(SubMyTimelineResponseDTO::from)
                .collect(Collectors.toList());
    }

    // 모든 서브타임라인 조회 (비공개 제외, 시작 날짜 순서대로 정렬)
    public List<SubTimeline> getAllSubTimelinesByMainTimelineId(Long mainTimelineId) {
        return subTimelineRepository.findByMainTimelineId(mainTimelineId).stream()
                .filter(subTimeline -> !subTimeline.isPrivate())
                .sorted(Comparator.comparing(SubTimeline::getStartDate))  // 시작 날짜 순서대로 정렬
                .collect(Collectors.toList());
    }

    public SubPrivacyUpdateResponseDTO setSubTimelineDoneStatus(Long subTimelineId, boolean isDone) {
        SubTimeline subTimeline = subTimelineRepository.findById(subTimelineId)
                .orElseThrow(() -> new ApiException(ErrorType._SUBTIMELINE_NOT_FOUND));

        checkOwnership(subTimeline.getMainTimeline().getMember().getEmail());

        subTimeline.setDone(isDone);
        subTimelineRepository.save(subTimeline);

        String message = isDone ? "서브타임라인이 완료 상태로 업데이트 되었습니다." : "서브타임라인이 진행중 상태로 업데이트 되었습니다.";
        return SubPrivacyUpdateResponseDTO.success(message);
    }

    public SubUpdateStatusResponseDTO toggleSubTimelineDoneStatus(Long subTimelineId) {
        SubTimeline subTimeline = subTimelineRepository.findById(subTimelineId)
                .orElseThrow(() -> new ApiException(ErrorType._SUBTIMELINE_NOT_FOUND));

        // 현재 상태를 반전시킴
        boolean newDoneStatus = !subTimeline.isDone();
        subTimeline.setDone(newDoneStatus);
        subTimelineRepository.save(subTimeline);

        String message = newDoneStatus ? "서브타임라인이 완료 상태로 업데이트 되었습니다." : "서브타임라인이 진행중 상태로 업데이트 되었습니다.";
        return SubUpdateStatusResponseDTO.success(newDoneStatus);
    }


    private void checkOwnership(String ownerEmail) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = authentication.getName();
        if (!userEmail.equals(ownerEmail)) {
            throw new ApiException(ErrorType._DO_NOT_HAVE_PERMISSION);
        }
    }
}