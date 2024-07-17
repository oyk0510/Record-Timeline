package com.api.RecordTimeline.domain.bookmark.service;

import com.api.RecordTimeline.domain.bookmark.domain.Bookmark;
import com.api.RecordTimeline.domain.bookmark.repository.BookmarkRepository;
import com.api.RecordTimeline.domain.member.domain.Member;
import com.api.RecordTimeline.domain.member.repository.MemberRepository;
import com.api.RecordTimeline.domain.subTimeline.domain.SubTimeline;
import com.api.RecordTimeline.domain.subTimeline.repository.SubTimelineRepository;
import com.api.RecordTimeline.global.exception.ApiException;
import com.api.RecordTimeline.global.exception.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final MemberRepository memberRepository;
    private final SubTimelineRepository subTimelineRepository;

    public void toggleBookmark(Long subTimelineId) {
        Member member = getCurrentAuthenticatedMember();
        SubTimeline subTimeline = subTimelineRepository.findById(subTimelineId)
                .orElseThrow(() -> new NoSuchElementException("SubTimeline not found"));

        Optional<Bookmark> existingBookmark = bookmarkRepository.findByMemberAndSubTimeline(member, subTimeline);

        if (existingBookmark.isPresent()) {
            bookmarkRepository.delete(existingBookmark.get());
        } else {
            Bookmark bookmark = Bookmark.builder()
                    .member(member)
                    .subTimeline(subTimeline)
                    .build();
            bookmarkRepository.save(bookmark);
        }
    }

    private Member getCurrentAuthenticatedMember() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = authentication.getName();
        return Optional.ofNullable(memberRepository.findByEmailAndIsDeletedFalse(userEmail))
                .orElseThrow(() -> new NoSuchElementException("활성 상태의 해당 이메일로 등록된 사용자를 찾을 수 없습니다: " + userEmail));
    }
}
