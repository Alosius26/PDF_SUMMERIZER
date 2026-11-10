package Alosius.PDF.Summarize.service;

import Alosius.PDF.Summarize.entity.User;
import Alosius.PDF.Summarize.entity.UserProfile;
import Alosius.PDF.Summarize.repository.UserProfileRepository;
import Alosius.PDF.Summarize.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handles user registration, email OTP verification, login, and profile updates.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailOtpService emailOtpService;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       UserProfileRepository profileRepository,
                       PasswordEncoder passwordEncoder,
                       EmailOtpService emailOtpService,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailOtpService = emailOtpService;
        this.jwtService = jwtService;
    }

    @Transactional
    public Map<String, Object> register(String fullName, String rawEmail, String password) {
        String cleanName = validateName(fullName);
        String email = normalizeAndValidateEmail(rawEmail);
        validatePassword(password);

        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user != null) {
            if (user.isEmailVerified()) {
                throw new IllegalArgumentException("An account with this email already exists. Please sign in.");
            }
            // Allow re-registering an unverified account cleanly
            user.setPasswordHash(passwordEncoder.encode(password));
            UserProfile profile = user.getProfile();
            if (profile == null) {
                profile = createProfile(cleanName);
                user.setProfile(profile);
            } else {
                profile.setFullName(cleanName);
                profile.setAvatarInitials(computeInitials(cleanName));
            }
            user = userRepository.save(user);
        } else {
            user = new User();
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setEmailVerified(false);
            user.setProfile(createProfile(cleanName));
            user = userRepository.save(user);
        }

        EmailOtpService.OtpDispatchResult dispatch = emailOtpService.issueRegistrationOtp(user, false);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", dispatch.mailSent()
                ? "Verification OTP sent to " + email
                : "OTP generated (SMTP not configured on server — use the dev OTP shown below).");
        result.put("email", email);
        result.put("mailSent", dispatch.mailSent());
        result.put("expiresInSeconds", dispatch.expiresInSeconds());
        if (dispatch.devOtp() != null) {
            result.put("devOtp", dispatch.devOtp());
        }
        return result;
    }

    @Transactional
    public Map<String, Object> verifyRegistrationOtp(String rawEmail, String otp) {
        String email = normalizeAndValidateEmail(rawEmail);
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalArgumentException("No account found for this email."));

        emailOtpService.verifyRegistrationOtp(email, otp);

        user.setEmailVerified(true);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole());
        return Map.of(
                "message", "Email verified successfully.",
                "token", token,
                "user", toUserDto(user)
        );
    }

    @Transactional
    public Map<String, Object> resendOtp(String rawEmail) {
        String email = normalizeAndValidateEmail(rawEmail);
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalArgumentException("No account found for this email."));
        if (user.isEmailVerified()) {
            throw new IllegalArgumentException("This email is already verified. Please sign in.");
        }

        EmailOtpService.OtpDispatchResult dispatch = emailOtpService.issueRegistrationOtp(user, true);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", dispatch.mailSent()
                ? "New OTP sent to " + email
                : "New OTP generated (SMTP not configured — use the dev OTP shown below).");
        result.put("email", email);
        result.put("mailSent", dispatch.mailSent());
        result.put("expiresInSeconds", dispatch.expiresInSeconds());
        if (dispatch.devOtp() != null) {
            result.put("devOtp", dispatch.devOtp());
        }
        return result;
    }

    @Transactional
    public Map<String, Object> login(String rawEmail, String password) {
        String email = normalizeAndValidateEmail(rawEmail);
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password is required.");
        }

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password."));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password.");
        }

        if (!user.isActive()) {
            throw new IllegalArgumentException("This account is disabled.");
        }

        if (!user.isEmailVerified()) {
            throw new UnverifiedEmailException("Please verify your email with OTP before signing in.", email);
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole());
        return Map.of(
                "token", token,
                "user", toUserDto(user)
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
        return toUserDto(user);
    }

    @Transactional
    public Map<String, Object> updateProfile(Long userId, String fullName, String defaultSummaryFormat, String preferredModel) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
        UserProfile profile = profileRepository.findById(userId)
                .orElseGet(() -> createProfile("User"));

        if (fullName != null && !fullName.isBlank()) {
            String cleanName = validateName(fullName);
            profile.setFullName(cleanName);
            profile.setAvatarInitials(computeInitials(cleanName));
        }
        if (defaultSummaryFormat != null && !defaultSummaryFormat.isBlank()) {
            String fmt = defaultSummaryFormat.trim().toLowerCase();
            if (fmt.equals("paragraph") || fmt.equals("bullets") || fmt.equals("both")) {
                profile.setDefaultSummaryFormat(fmt);
            }
        }
        if (preferredModel != null && !preferredModel.isBlank()) {
            profile.setPreferredModel(preferredModel.trim());
        }

        profile.setUser(user);
        profileRepository.save(profile);
        return toUserDto(user);
    }

    private UserProfile createProfile(String fullName) {
        UserProfile profile = new UserProfile();
        profile.setFullName(fullName);
        profile.setAvatarInitials(computeInitials(fullName));
        profile.setDefaultSummaryFormat("paragraph");
        return profile;
    }

    private Map<String, Object> toUserDto(User user) {
        UserProfile profile = user.getProfile();
        String fullName = profile != null ? profile.getFullName() : user.getEmail();
        String initials = profile != null ? profile.getAvatarInitials() : computeInitials(fullName);
        String defaultFormat = profile != null ? profile.getDefaultSummaryFormat() : "paragraph";
        String preferredModel = profile != null ? profile.getPreferredModel() : "qwen/qwen3.8-27b";

        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", user.getId());
        dto.put("email", user.getEmail());
        dto.put("role", user.getRole());
        dto.put("fullName", fullName);
        dto.put("avatarInitials", initials);
        dto.put("defaultSummaryFormat", defaultFormat);
        dto.put("preferredModel", preferredModel);
        return dto;
    }

    private String validateName(String fullName) {
        if (fullName == null || fullName.trim().length() < 2) {
            throw new IllegalArgumentException("Full name must be at least 2 characters.");
        }
        return fullName.trim();
    }

    private String normalizeAndValidateEmail(String rawEmail) {
        if (rawEmail == null || !rawEmail.trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("Please enter a valid email address.");
        }
        return rawEmail.trim().toLowerCase();
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters.");
        }
    }

    private String computeInitials(String name) {
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) return "U";
        if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }

    public static class UnverifiedEmailException extends RuntimeException {
        private final String email;
        public UnverifiedEmailException(String message, String email) {
            super(message);
            this.email = email;
        }
        public String getEmail() { return email; }
    }
}
