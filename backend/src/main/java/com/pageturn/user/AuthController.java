package com.pageturn.user;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pageturn.common.error.ApiException;
import com.pageturn.common.security.AuthenticatedUser;
import com.pageturn.common.security.SessionCookies;
import com.pageturn.user.UserDtos.LoginRequest;
import com.pageturn.user.UserDtos.RegisterRequest;
import com.pageturn.user.UserDtos.UserView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;
    private final SessionCookies sessionCookies;
    private final JwtDecoder jwtDecoder;
    private final UserRepository users;

    public AuthController(AuthService authService, TokenService tokenService, SessionCookies sessionCookies,
            JwtDecoder jwtDecoder, UserRepository users) {
        this.authService = authService;
        this.tokenService = tokenService;
        this.sessionCookies = sessionCookies;
        this.jwtDecoder = jwtDecoder;
        this.users = users;
    }

    /** GET /api/v1/auth/csrf — public. Makes sure the browser has a CSRF cookie before it sends a form. */
    @GetMapping("/csrf")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void csrf(CsrfToken token) {
        token.getToken(); // reading the token makes Spring Security send the XSRF-TOKEN cookie
    }

    /** POST /api/v1/auth/register — public. Creates a MEMBER account and signs them in. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserView register(@Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
        UserAccount user = authService.register(request);
        sessionCookies.write(response, tokenService.issueFor(user));
        return UserView.of(user);
    }

    /** POST /api/v1/auth/login — public. */
    @PostMapping("/login")
    public UserView login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        UserAccount user = authService.login(request);
        sessionCookies.write(response, tokenService.issueFor(user));
        return UserView.of(user);
    }

    /** POST /api/v1/auth/logout — ends the session on every device and deletes the cookie. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String token = SessionCookies.read(request);
        if (token != null) {
            try {
                authService.logout(UUID.fromString(jwtDecoder.decode(token).getSubject()));
            }
            catch (JwtException | IllegalArgumentException | NullPointerException ignored) {
                // Expired or invalid token: nothing to revoke, just clear the cookie.
            }
        }
        sessionCookies.clear(response);
    }

    /** GET /api/v1/auth/me — the signed-in user (401 when not signed in). */
    @GetMapping("/me")
    public UserView me(@AuthenticationPrincipal AuthenticatedUser current) {
        return users.findById(current.id())
                .map(UserView::of)
                .orElseThrow(() -> ApiException.unauthorized("Please sign in again."));
    }
}
