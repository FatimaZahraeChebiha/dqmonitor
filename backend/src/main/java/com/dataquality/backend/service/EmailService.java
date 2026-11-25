package com.dataquality.backend.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper around JavaMailSender. Failures are logged, not thrown - a flaky
 * SMTP provider should never break the underlying action (password reset request,
 * analysis completion) that triggered the email.
 */
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        String resetLink = frontendUrl + "/reset-password?token=" + resetToken;
        String body = """
                You requested a password reset for your DQMonitor account.

                Reset your password using the link below (valid for 1 hour):
                %s

                If you did not request this, you can safely ignore this email.
                """.formatted(resetLink);
        send(toEmail, "DQMonitor - Password reset request", body);
    }

    public void sendAnalysisCompleteEmail(String toEmail, String fileName, double qualityScore, int invalidRows) {
        String body = """
                The quality analysis for "%s" has finished.

                Quality score : %.1f%%
                Anomalies     : %d row(s)

                View the full report in DQMonitor under Reports.
                """.formatted(fileName, qualityScore, invalidRows);
        send(toEmail, "DQMonitor - Analysis complete: " + fileName, body);
    }

    public void sendLowQualityAlertEmail(String toEmail, String fileName, double qualityScore, double threshold) {
        String body = """
                ALERT: the quality score for "%s" is below your organization's threshold.

                Quality score : %.1f%%
                Threshold     : %.1f%%

                Review the anomalies in DQMonitor under Reports.
                """.formatted(fileName, qualityScore, threshold);
        send(toEmail, "DQMonitor - Low quality score alert: " + fileName, body);
    }

    private void send(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send email to {} (subject: {}): {}", to, subject, e.getMessage());
        }
    }
}
