package com.api.RecordTimeline.domain.mainPage.dto.response;

import com.api.RecordTimeline.domain.mainTimeline.domain.MainTimeline;
import com.api.RecordTimeline.domain.mainTimeline.dto.request.MainTimelineRequestDTO;
import com.api.RecordTimeline.domain.mainTimeline.dto.response.ReadResponseDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;



@NoArgsConstructor
@AllArgsConstructor
@Getter
public class MainPageMemberDto {
    private Long id;
    private String nickname;
    private String profileImageUrl;
    private String introduction;
    private List<MainTimelineDto> mainTimeline;
    private Long followerCount;
}
