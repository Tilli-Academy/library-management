package com.pageturn.user;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pageturn.common.error.ApiException;
import com.pageturn.common.web.TextRules;
import com.pageturn.user.UserDtos.LoginRequest;
import com.pageturn.user.UserDtos.RegisterRequest;

@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid email or password.";
    /** BCrypt only uses the first 72 bytes of a password, so longer ones are refused rather than cut off. */
    static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptLimiter loginLimiter;
    /** Checked when the email is unknown, so "wrong email" and "wrong password" take the same time. */
    private final String dummyPasswordHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, LoginAttemptLimiter loginLimiter) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.loginLimiter = loginLimiter;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /** Everyone who signs up is a MEMBER. Only a librarian can promote someone. */
    @Transactional
    public UserAccount register(RegisterRequest request) {
        String email = UserAccount.normalizeEmail(request.email());
        checkPasswordLength(request.password());
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("An account with this email already exists.");
        }
        UserAccount user = new UserAccount(email, passwordEncoder.encode(request.password()),
                TextRules.clean(request.displayName()), Role.MEMBER);
        user.setPhone(TextRules.clean(request.phone()));
        return users.saveAndFlush(user);
    }

    @Transactional(readOnly = true)
    public UserAccount login(LoginRequest request) {
        String email = UserAccount.normalizeEmail(request.email());
        loginLimiter.reserveAttempt(email);
        Optional<UserAccount> user = users.findByEmail(email);
        String hash = user.map(UserAccount::getPasswordHash).orElse(dummyPasswordHash);
        boolean tooLong = request.password().getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES;
        boolean passwordMatches = !tooLong && passwordEncoder.matches(request.password(), hash);
        // Same message for every failure, so attackers can't find out which emails have accounts.
        if (user.isEmpty() || !passwordMatches || !user.get().isEnabled()) {
            throw ApiException.unauthorized(INVALID_CREDENTIALS);
        }
        loginLimiter.recordSuccess(email);
        return user.get();
    }

    /** Users can update their own phone number (only their own: the id comes from the session). */
    @Transactional
    public UserAccount updateProfile(UUID userId, UserDtos.UpdateProfileRequest request) {
        UserAccount user = users.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Please sign in again."));
        user.setPhone(TextRules.clean(request.phone()));
        return users.saveAndFlush(user);
    }

    /** Signs the user out everywhere: every token issued so far stops working. */
    @Transactional
    public void logout(UUID userId) {
        users.findById(userId).ifPresent(UserAccount::revokeSessions);
    }

    static void checkPasswordLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw ApiException.badRequest(
                    "Password is too long: at most 72 bytes (accented letters and emoji count as 2–4).");
        }
    }
}
