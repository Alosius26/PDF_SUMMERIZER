package Alosius.PDF.Summarize.service;

import Alosius.PDF.Summarize.entity.EmailOtpVerification;
import Alosius.PDF.Summarize.entity.User;
import Alosius.PDF.Summarize.repository.EmailOtpRepository;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;

/**
 * Generates, hashes, delivers (via SMTP or dev fallback), and verifies 6-digit email OTPs.
 */
@Service
public class EmailOtpService {

    private static final Logger log = LoggerFactory.getLogger(EmailOtpService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration OTP_TTL = Duration.ofMinutes(10);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(30);

    private final EmailOtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.mail.host:}")
    private String mailHost;

    @Value("${app.mail.port:587}")
    private int mailPort;

    @Value("${app.mail.username:}")
    private String mailUsername;

    @Value("${app.mail.password:}")
    private String mailPassword;

    @Value("${app.mail.from:noreply@pdfsummarizer.local}")
    private String mailFrom;

    public record OtpDispatchResult(boolean mailSent, String devOtp, int expiresInSeconds) {}

    public EmailOtpService(EmailOtpRepository otpRepository, PasswordEncoder passwordEncoder) {
        this.otpRepository = otpRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public OtpDispatchResult issueRegistrationOtp(User user, boolean enforceCooldown) {
        String email = user.getEmail();
        if (enforceCooldown) {
            otpRepository.findTopByEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc(email, "REGISTRATION")
                    .ifPresent(latest -> {
                        long secondsSince = Duration.between(latest.getCreatedAt(), Instant.now()).getSeconds();
                        if (secondsSince < RESEND_COOLDOWN.getSeconds()) {
                            long wait = RESEND_COOLDOWN.getSeconds() - secondsSince;
                            throw new IllegalArgumentException("Please wait " + wait + " seconds before requesting a new OTP.");
                        }
                    });
        }

        String rawOtp = String.format("%06d", RANDOM.nextInt(1_000_000));
        EmailOtpVerification record = new EmailOtpVerification();
        record.setUser(user);
        record.setEmail(email);
        record.setOtpHash(passwordEncoder.encode(rawOtp));
        record.setPurpose("REGISTRATION");
        record.setExpiresAt(Instant.now().plus(OTP_TTL));
        otpRepository.save(record);

        boolean sent = trySendEmail(email, rawOtp);
        if (!sent) {
            log.info("==================================================");
            log.info("DEV EMAIL OTP for {} : {}", email, rawOtp);
            log.info("==================================================");
        }
        return new OtpDispatchResult(sent, sent ? null : rawOtp, (int) OTP_TTL.getSeconds());
    }

    public void verifyRegistrationOtp(String email, String rawOtp) {
        if (rawOtp == null || !rawOtp.trim().matches("^\\d{6}$")) {
            throw new IllegalArgumentException("Please enter a valid 6-digit OTP.");
        }

        EmailOtpVerification record = otpRepository
                .findTopByEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc(email.trim(), "REGISTRATION")
                .orElseThrow(() -> new IllegalArgumentException("No OTP found for this email. Please request a new OTP."));

        if (record.isUsed()) {
            throw new IllegalArgumentException("This OTP has already been used. Please request a new OTP.");
        }

        if (Instant.now().isAfter(record.getExpiresAt())) {
            throw new IllegalArgumentException("This OTP has expired. Please request a new OTP.");
        }

        if (record.getAttempts() >= record.getMaxAttempts()) {
            throw new IllegalArgumentException("Too many incorrect OTP attempts. Please request a new OTP.");
        }

        if (!passwordEncoder.matches(rawOtp.trim(), record.getOtpHash())) {
            record.setAttempts(record.getAttempts() + 1);
            otpRepository.save(record);
            int remaining = Math.max(0, record.getMaxAttempts() - record.getAttempts());
            throw new IllegalArgumentException("Invalid OTP. " + remaining + " attempt(s) remaining.");
        }

        record.setUsed(true);
        record.setVerifiedAt(Instant.now());
        otpRepository.save(record);
    }

    private boolean trySendEmail(String toEmail, String otp) {
        if (mailHost == null || mailHost.isBlank()
                || mailUsername == null || mailUsername.isBlank()
                || mailPassword == null || mailPassword.isBlank()) {
            return false;
        }
        try {
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
            props.put("mail.smtp.ssl.protocols", "TLSv1.2");
            props.put("mail.smtp.ssl.trust", mailHost);
            props.put("mail.smtp.host", mailHost);
            props.put("mail.smtp.port", String.valueOf(mailPort));
            props.put("mail.smtp.connectiontimeout", "10000");
            props.put("mail.smtp.timeout", "10000");
            props.put("mail.smtp.writetimeout", "10000");

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(mailUsername, mailPassword);
                }
            });

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(mailFrom));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            message.setSubject("AI PDF Summarizer - Verify Your Email (OTP: " + otp + ")");
            message.setText(
                    "Your verification code is: " + otp + "\n\n" +
                    "This code expires in 10 minutes. Do not share it with anyone.\n\n" +
                    "— AI PDF Summarizer"
            );
            Transport.send(message);
            log.info("Sent registration OTP email to {}", toEmail);
            return true;
        } catch (Exception e) {
            log.warn("Failed to send SMTP email to {} ({}). Falling back to dev OTP logging.", toEmail, e.getMessage());
            return false;
        }
    }
}
