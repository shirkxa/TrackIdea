package pl.trackidea.web;

import org.springframework.stereotype.Component;
import pl.trackidea.domain.Person;
import pl.trackidea.repo.PersonRepository;

/**
 * Użytkownik demo wybrany przełącznikiem ról we frontendzie (nagłówek X-User-Id).
 * W wersji produkcyjnej zastępuje go Spring Security (logowanie e-mail / SSO instytucji).
 */
@Component
public class CurrentUser {

    public static final String HEADER = "X-User-Id";

    private final PersonRepository people;

    public CurrentUser(PersonRepository people) {
        this.people = people;
    }

    public Person resolve(Long id) {
        return id == null ? null : people.findById(id).orElse(null);
    }
}
