package com.itsectest.auth.internal.mfa;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import com.itsectest.shared.error.ApiException;
import com.itsectest.shared.error.ErrorCode;
import com.itsectest.user.api.UserAccount;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailOtpChannel implements OtpChannel {

    static final String SUBJECT = "Your ITSEC Test verification code";

    private static final String FONT =
            "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif";
    private static final String MONO =
            "'SFMono-Regular',Menlo,Consolas,'Liberation Mono',monospace";

    private final JavaMailSender mailSender;
    private final OtpMailProperties properties;

    @Override
    public void deliver(UserAccount account, String code, Duration validFor) {
        if (!properties.deliveryEnabled()) {
            log.info("MFA delivery disabled, OTP for {} is {} (valid {} minutes)",
                    account.username(), code, validFor.toMinutes());
            return;
        }

        long minutes = validFor.toMinutes();
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.from());
            helper.setTo(account.email());
            helper.setSubject(SUBJECT);
            helper.setText(plainText(account.fullname(), code, minutes), html(account.fullname(), code, minutes));

            mailSender.send(message);
            log.debug("OTP delivered to user {}", account.id());
        } catch (MessagingException | MailException ex) {
            log.error("Could not send OTP email to user {}", account.id(), ex);
            throw new ApiException(ErrorCode.MAIL_DELIVERY_FAILED,
                    "Could not send the verification email. Please try again shortly.", ex);
        }
    }

    @Override
    public String name() {
        return "EMAIL";
    }

    static String plainText(String fullname, String code, long minutes) {
        return """
                Hi %s,

                Your verification code is %s

                This code expires in %d minutes. If you did not try to sign in, please change your password.

                ITSEC Test
                """.formatted(fullname, code, minutes);
    }

    static String html(String fullname, String code, long minutes) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta name="color-scheme" content="light only">
                <title>%1$s</title>
                </head>
                <body style="margin:0;padding:0;background-color:#f4f5f7;">
                <div style="display:none;max-height:0;overflow:hidden;opacity:0;">Your code expires in %4$d minutes.</div>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background-color:#f4f5f7;">
                  <tr>
                    <td align="center" style="padding:32px 16px;">
                      <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="max-width:480px;background-color:#ffffff;border:1px solid #e5e7eb;border-radius:8px;">
                        <tr>
                          <td style="padding:28px 32px 0 32px;font-family:%5$s;font-size:12px;font-weight:600;letter-spacing:0.08em;text-transform:uppercase;color:#6b7280;">ITSEC Test</td>
                        </tr>
                        <tr>
                          <td style="padding:10px 32px 0 32px;font-family:%5$s;font-size:20px;font-weight:600;line-height:28px;color:#111827;">Your verification code</td>
                        </tr>
                        <tr>
                          <td style="padding:12px 32px 0 32px;font-family:%5$s;font-size:15px;line-height:24px;color:#374151;">Hi %2$s, use this code to finish signing in.</td>
                        </tr>
                        <tr>
                          <td style="padding:24px 32px;">
                            <div style="background-color:#f3f4f6;border-radius:6px;padding:18px 0 18px 0.3em;text-align:center;font-family:%6$s;font-size:32px;font-weight:700;letter-spacing:0.3em;color:#111827;">%3$s</div>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:0 32px;font-family:%5$s;font-size:14px;line-height:22px;color:#374151;">This code expires in <strong>%4$d minutes</strong>.</td>
                        </tr>
                        <tr>
                          <td style="padding:16px 32px 28px 32px;font-family:%5$s;font-size:13px;line-height:20px;color:#6b7280;">If you did not try to sign in, please change your password.</td>
                        </tr>
                      </table>
                      <p style="margin:16px 0 0 0;font-family:%5$s;font-size:12px;line-height:18px;color:#9ca3af;">This is an automated message from ITSEC Test.</p>
                    </td>
                  </tr>
                </table>
                </body>
                </html>
                """.formatted(SUBJECT, HtmlUtils.htmlEscape(fullname), code, minutes, FONT, MONO);
    }
}
