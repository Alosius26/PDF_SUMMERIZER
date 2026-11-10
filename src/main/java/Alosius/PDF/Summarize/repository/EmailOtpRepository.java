package Alosius.PDF.Summarize.repository;

import Alosius.PDF.Summarize.entity.EmailOtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailOtpRepository extends JpaRepository<EmailOtpVerification, Long> {
    Optional<EmailOtpVerification> findTopByEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc(String email, String purpose);
}
