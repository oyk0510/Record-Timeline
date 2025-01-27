package com.api.RecordTimeline.domain.findInfo.password.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PasswordResetDto {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String newPassword;
}
