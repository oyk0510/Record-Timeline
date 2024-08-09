package com.api.RecordTimeline.domain.career.domain;

import com.api.RecordTimeline.domain.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ForeignLanguage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String languageName;

    @Enumerated(EnumType.STRING)
    private Proficiency proficiency;

    private String userEmail;  // 사용자 이메일 필드 추가

    public ForeignLanguage update(ForeignLanguage newLanguage) {
        return ForeignLanguage.builder()
                .id(this.id)
                .languageName(newLanguage.getLanguageName())
                .proficiency(newLanguage.getProficiency())
                .userEmail(this.userEmail)  // 이메일 필드는 변경되지 않도록 설정
                .build();
    }
}
