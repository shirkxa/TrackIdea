package pl.trackidea.web;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import pl.trackidea.domain.Person;
import pl.trackidea.repo.PersonRepository;

/** Zalogowany użytkownik z sesji Spring Security (null dla gościa). */
@Component
public class CurrentUser {

    private final PersonRepository people;

    public CurrentUser(PersonRepository people) {
        this.people = people;
    }

    public Person get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return people.findByEmailIgnoreCase(auth.getName()).orElse(null);
    }
}
