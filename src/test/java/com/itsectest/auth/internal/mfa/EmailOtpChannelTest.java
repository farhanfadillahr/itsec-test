package com.itsectest.auth.internal.mfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import com.itsectest.shared.error.ApiException;
import com.itsectest.shared.error.ErrorCode;
import com.itsectest.shared.security.Role;
import com.itsectest.user.api.UserAccount;
import com.itsectest.user.domain.UserStatus;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

@ExtendWith(MockitoExtension.class)
class EmailOtpChannelTest {

    @Mock private JavaMailSender mailSender;

    private static UserAccount account(String fullname) {
        return new UserAccount(UUID.randomUUID(), fullname, "farhan", "farhan@example.com",
                Role.VIEWER, UserStatus.ACTIVE, true, null);
    }

    private EmailOtpChannel channel(String from) {
        return new EmailOtpChannel(mailSender, new OtpMailProperties(from, true));
    }

    private MimeMessage deliver(EmailOtpChannel channel, UserAccount account) throws Exception {
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));

        channel.deliver(account, "482913", Duration.ofMinutes(5));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage message = captor.getValue();
        message.saveChanges();
        return message;
    }

    private static Part partOfType(Part part, String mimeType) throws Exception {
        if (part.isMimeType(mimeType)) {
            return part;
        }
        if (part.getContent() instanceof Multipart multipart) {
            for (int index = 0; index < multipart.getCount(); index++) {
                Part found = partOfType(multipart.getBodyPart(index), mimeType);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static String bodyOfType(Part part, String mimeType) throws Exception {
        Part found = partOfType(part, mimeType);
        return found == null ? null : (String) found.getContent();
    }

    @Test
    void sendsTheCodeAsHtmlWithAPlainTextAlternative() throws Exception {
        MimeMessage message = deliver(channel("no-reply@itsec-test.local"), account("Farhan"));

        assertThat(message.getAllRecipients()).extracting(Object::toString).containsExactly("farhan@example.com");
        assertThat(message.getFrom()).extracting(Object::toString).containsExactly("no-reply@itsec-test.local");
        assertThat(message.getSubject()).isEqualTo(EmailOtpChannel.SUBJECT);

        String html = bodyOfType(message, "text/html");
        assertThat(html).contains("<html").contains("482913").contains("5 minutes").contains("Hi Farhan");

        String plain = bodyOfType(message, "text/plain");
        assertThat(plain).contains("482913").contains("5 minutes").doesNotContain("<");
    }

    @Test
    void escapesTheNameBecauseItIsUserInput() throws Exception {
        MimeMessage message = deliver(channel("no-reply@itsec-test.local"),
                account("<img src=x onerror=alert(1)>"));

        String html = bodyOfType(message, "text/html");
        assertThat(html).doesNotContain("<img src=x").contains("&lt;img src=x onerror=alert(1)&gt;");
    }

    @Test
    void keepsTheDisplayNameOfTheSender() throws Exception {
        MimeMessage message = deliver(channel("ITSEC Test <no-reply@farhanf.xyz>"), account("Farhan"));

        InternetAddress from = (InternetAddress) message.getFrom()[0];
        assertThat(from.getPersonal()).isEqualTo("ITSEC Test");
        assertThat(from.getAddress()).isEqualTo("no-reply@farhanf.xyz");
    }

    @Test
    void encodesAsUtf8() throws Exception {
        MimeMessage message = deliver(channel("no-reply@itsec-test.local"), account("Farhān Rafi"));

        Part plain = partOfType(message, "text/plain");
        assertThat(plain.getContentType()).containsIgnoringCase("charset=UTF-8");
        assertThat((String) plain.getContent()).contains("Farhān");
    }

    @Test
    void logsTheCodeInsteadWhenDeliveryIsSwitchedOff() {
        EmailOtpChannel channel = new EmailOtpChannel(mailSender,
                new OtpMailProperties("no-reply@itsec-test.local", false));

        channel.deliver(account("Farhan"), "482913", Duration.ofMinutes(5));

        verify(mailSender, never()).createMimeMessage();
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void saysPlainlyWhenTheMailServerRefuses() {
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));
        doThrow(new MailSendException("relay refused")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> channel("no-reply@itsec-test.local")
                .deliver(account("Farhan"), "482913", Duration.ofMinutes(5)))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getCode())
                .isEqualTo(ErrorCode.MAIL_DELIVERY_FAILED);
    }

    @Test
    void treatsAMalformedSenderAddressAsADeliveryFailure() {
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));

        assertThatThrownBy(() -> channel("ITSEC Test <no-reply@")
                .deliver(account("Farhan"), "482913", Duration.ofMinutes(5)))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getCode())
                .isEqualTo(ErrorCode.MAIL_DELIVERY_FAILED);
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void fallsBackToADefaultSenderAddress() {
        assertThat(new OtpMailProperties(null, true).from()).isEqualTo("no-reply@itsec-test.local");
        assertThat(new OtpMailProperties("  ", true).from()).isEqualTo("no-reply@itsec-test.local");
    }
}
