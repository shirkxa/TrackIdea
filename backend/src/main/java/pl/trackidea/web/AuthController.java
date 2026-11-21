package pl.trackidea.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import pl.trackidea.domain.Person;
import pl.trackidea.repo.PersonRepository;
import pl.trackidea.service.DomainException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Logowanie e-mailem i hasłem (sesja + ciasteczko HttpOnly).
 * Tryb demo (trackidea.demo-mode=true) pozwala dodatkowo przełączać konta jednym kliknięciem
 * na potrzeby prezentacji – w wersji produkcyjnej jest wyłączony.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Logowanie", description = "Sesja użytkownika, logowanie, wylogowanie, tryb demo")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository;
    private final PersonRepository people;
    private final CurrentUser currentUser;
    private final boolean demoMode;

    public AuthController(AuthenticationManager authenticationManager, SecurityContextRepository contextRepository,
                          PersonRepository people, CurrentUser currentUser,
                          @Value("${trackidea.demo-mode:false}") boolean demoMode) {
        this.authenticationManager = authenticationManager;
        this.contextRepository = contextRepository;
        this.people = people;
        this.currentUser = currentUser;
        this.demoMode = demoMode;
    }

    @GetMapping("/me")
    @Operation(summary = "Zalogowany użytkownik (null dla gościa) i informacja o trybie demo")
    public Map<String, Object> me(CsrfToken csrf) {
        csrf.getToken(); // wymusza ustawienie ciasteczka XSRF-TOKEN dla frontendu
        Person p = currentUser.get();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("user", p == null ? null : Dto.PersonView.of(p));
        out.put("email", p == null ? null : p.getEmail());
        out.put("demoMode", demoMode);
        return out;
    }

    public record LoginRequest(String email, String password) {
    }

    @PostMapping("/login")
    @Operation(summary = "Logowanie e-mailem i hasłem")
    public Map<String, Object> login(@RequestBody LoginRequest body, HttpServletRequest request,
                                     HttpServletResponse response, CsrfToken csrf) {
        if (body == null || body.email() == null || body.password() == null) {
            throw DomainException.badRequest("Podaj e-mail i hasło.");
        }
        Authentication auth;
        try {
            auth = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(body.email().trim(), body.password()));
        } catch (AuthenticationException e) {
            throw new DomainException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Nieprawidłowy e-mail lub hasło.");
        }
        startSession(auth, request, response);
        return me(csrf);
    }

    @PostMapping("/demo/{personId}")
    @Operation(summary = "Tryb demo: zaloguj jako wybrana osoba (tylko gdy trackidea.demo-mode=true)")
    public Map<String, Object> demoLogin(@PathVariable Long personId, HttpServletRequest request,
                                         HttpServletResponse response, CsrfToken csrf) {
        if (!demoMode) {
            throw DomainException.forbidden("Tryb demo jest wyłączony.");
        }
        Person p = people.findById(personId).orElseThrow(() -> DomainException.notFound("Brak konta."));
        Authentication auth = UsernamePasswordAuthenticationToken.authenticated(p.getEmail(), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + p.getRole().name())));
        startSession(auth, request, response);
        return me(csrf);
    }

    @PostMapping("/logout")
    @Operation(summary = "Wylogowanie (koniec sesji)")
    public Map<String, Object> logout(HttpServletRequest request, CsrfToken csrf) {
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("user", null);
        out.put("email", null);
        out.put("demoMode", demoMode);
        return out;
    }

    /** Nowa sesja z nowym identyfikatorem (ochrona przed przejęciem sesji). */
    private void startSession(Authentication auth, HttpServletRequest request, HttpServletResponse response) {
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
    }
}
