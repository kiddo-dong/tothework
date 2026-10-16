package com.tothework.identity.mail;

import com.tothework.shared.error.BusinessException;
import com.tothework.shared.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** 가입 인증·비밀번호 재설정 메일. 미설정이어도 기동은 되고 보내는 시점에만 실패한다. */
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender javaMailSender;

    public void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        try {
            javaMailSender.send(message);
        } catch (MailException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "메일 발송에 실패했습니다. SMTP 설정(GMAIL_USERNAME/GMAIL_APP_PASSWORD)을 확인해 주세요.");
        }
    }
}
