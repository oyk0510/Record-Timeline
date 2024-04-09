package com.api.RecordTimeline.domain.signup.email.service;

import com.api.RecordTimeline.domain.common.CertificationNumber;
import com.api.RecordTimeline.domain.member.repository.MemberRepository;
import com.api.RecordTimeline.domain.signup.email.domain.EmailCertification;
import com.api.RecordTimeline.domain.signup.email.dto.request.CheckCertificationRequestDto;
import com.api.RecordTimeline.domain.signup.email.dto.request.EmailCertificationRequestDto;
import com.api.RecordTimeline.domain.signup.email.dto.response.CheckCertificationResponseDto;
import com.api.RecordTimeline.domain.signup.email.dto.response.EmailCertificationResponseDto;
import com.api.RecordTimeline.domain.signup.email.provider.EmailProvider;
import com.api.RecordTimeline.domain.signup.email.repository.EmailCertificationRepository;
import com.api.RecordTimeline.domain.signup.signup.dto.response.ResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {
    private final MemberRepository memberRepository;
    private final EmailProvider emailProvider;
    private final EmailCertificationRepository emailCertificationRepository;

    @Override
    public ResponseEntity<? super EmailCertificationResponseDto> emailCertification(EmailCertificationRequestDto dto) {
        try {
            String memberId = dto.getMemberId();
            String email = dto.getEmail();

            boolean isExistId = memberRepository.existsByMemberId(memberId);
            if (isExistId)
                return EmailCertificationResponseDto.duplicateId();

            String certificationNumber = CertificationNumber.getCertificationNumber();
            boolean isSuccessed = emailProvider.sendCertificationMail(email, certificationNumber);
            if(!isSuccessed) return EmailCertificationResponseDto.mailSendFail();

            EmailCertification emailCertification = new EmailCertification(memberId, email,certificationNumber);
            emailCertificationRepository.save(emailCertification);


        } catch (Exception exception) {
            exception.printStackTrace();
            return ResponseDto.databaseError();
        }
        return EmailCertificationResponseDto.success();
    }

    @Override
    public ResponseEntity<? super CheckCertificationResponseDto> checkCertification(CheckCertificationRequestDto dto) {
        try{

            String memberId = dto.getMemberId();
            String email = dto.getEmail();
            String certificationNumber = dto.getCertificationNumber();

            EmailCertification emailCertification = emailCertificationRepository.findByMemberId(memberId);
            if(emailCertification == null)
                return CheckCertificationResponseDto.certificationFail();

            boolean isMatched = emailCertification.getEmail().equals(email) && emailCertification.getCertificationNumber().equals(certificationNumber);
            if(!isMatched)
                return CheckCertificationResponseDto.certificationFail();


        } catch (Exception exception){
            exception.printStackTrace();
            return ResponseDto.databaseError();
        }
        return CheckCertificationResponseDto.success();
    }
}
