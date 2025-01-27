package com.api.RecordTimeline.domain.mainTimeline.dto.request;

import com.api.RecordTimeline.domain.mainTimeline.domain.MainTimeline;
import lombok.*;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class UpdateMainTimelineRequestDTO {

    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean isPrivate;
    private Boolean isDone;

    public MainTimeline toEntity() {
        validate();
        return MainTimeline.builder()
                .title(this.title)
                .startDate(this.startDate)
                .endDate(this.endDate)
                .isPrivate(this.isPrivate != null ? this.isPrivate : false)
                .isDone(this.isDone != null ? this.isDone : false)
                .build();
    }

    private void validate() {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Title cannot be null or empty");
        }
        if (startDate == null) {
            throw new IllegalArgumentException("Start date cannot be null");
        }
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }
    }
}

