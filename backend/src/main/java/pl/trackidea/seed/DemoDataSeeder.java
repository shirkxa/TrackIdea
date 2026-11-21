package pl.trackidea.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.trackidea.domain.*;
import pl.trackidea.repo.*;
import pl.trackidea.service.TextVectorizer;
import pl.trackidea.service.AssessmentService;
import pl.trackidea.service.RadarService;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Dane demonstracyjne (fikcyjne instytucje i innowacje) oraz konta zespołu PCI.
 * Wczytywane z plików JSON w resources/seed, tylko gdy baza jest pusta.
 */
@Component
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    record InstitutionSeed(String key, String name, InstitutionType type, String city, String county) {
    }

    record PersonSeed(String key, String name, Role role, String institution, String headline, String email) {
    }

    record InnovationSeed(String key, String title, String summary, String problem, String solution,
                          String requirements, String results, InnovationType type, Sector sector, Maturity maturity,
                          TransferModel transferModel, String ipStatus, List<String> tags, List<String> links,
                          String institution, String sourceAuthors, String sourceName, InnovationStatus status,
                          List<PathType> paths, int daysAgo, Integer assessAfterDays,
                          Map<Criterion, Integer> expertDelta, Integer trl) {
    }

    record NeedSeed(String key, String title, String description, String institution, Sector sector,
                    List<String> tags, boolean wantsReady) {
    }

    private final JsonMapper json = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();
    private final InstitutionRepository institutions;
    private final PersonRepository people;
    private final InnovationRepository innovations;
    private final InnovationEventRepository events;
    private final NeedRepository needs;
    private final PriorArtRepository priorArt;
    private final AssessmentRepository assessments;
    private final AssessmentService assessmentService;
    private final RadarService radar;
    private final PasswordEncoder passwordEncoder;
    private final String demoPassword;
    private final boolean demoData;

    private final Map<String, Institution> inst = new HashMap<>();

    public DemoDataSeeder(InstitutionRepository institutions, PersonRepository people, InnovationRepository innovations,
                          InnovationEventRepository events, NeedRepository needs, PriorArtRepository priorArt,
                          AssessmentRepository assessments, AssessmentService assessmentService, RadarService radar,
                          PasswordEncoder passwordEncoder,
                          @Value("${trackidea.demo-password:demo123}") String demoPassword,
                          @Value("${trackidea.demo-data:true}") boolean demoData) {
        this.demoData = demoData;
        this.institutions = institutions;
        this.people = people;
        this.innovations = innovations;
        this.events = events;
        this.needs = needs;
        this.priorArt = priorArt;
        this.assessments = assessments;
        this.assessmentService = assessmentService;
        this.radar = radar;
        this.passwordEncoder = passwordEncoder;
        this.demoPassword = demoPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (institutions.count() > 0) {
            syncPasswords();
            return;
        }
        Instant now = Instant.now();

        for (InstitutionSeed s : read("seed/institutions.json", new TypeReference<List<InstitutionSeed>>() { })) {
            inst.put(s.key(), institutions.save(new Institution(s.name(), s.type(), s.city(), s.county(), null)));
        }

        // konta zespołu PCI mają to samo hasło demo (trackidea.demo-password), zapisane jako hash BCrypt
        String passwordHash = passwordEncoder.encode(initialPassword());
        Person analyst = null;
        for (PersonSeed s : read("seed/people.json", new TypeReference<List<PersonSeed>>() { })) {
            Person p = new Person();
            p.setName(s.name());
            p.setRole(s.role());
            p.setInstitution(inst.get(s.institution()));
            p.setHeadline(s.headline());
            p.setEmail(s.email());
            p.setPasswordHash(passwordHash);
            people.save(p);
            if (s.role() == Role.EKSPERT && analyst == null) {
                analyst = p;
            }
        }
        if (!demoData) {
            // bez fikcyjnych przykładów: zostają konta PCI, katalog wypełnia Radar
            radar.scanInitial();
            log.info("Dane startowe: {} kont PCI, bez danych przykładowych", people.count());
            return;
        }
        priorArt.saveAll(read("seed/prior_art.json", new TypeReference<List<PriorArt>>() { }));

        for (NeedSeed s : read("seed/needs.json", new TypeReference<List<NeedSeed>>() { })) {
            Need n = new Need();
            n.setTitle(s.title());
            n.setDescription(s.description());
            n.setInstitution(inst.get(s.institution()));
            n.setSector(s.sector());
            if (s.tags() != null) n.getTags().addAll(s.tags());
            n.setWantsReadySolution(s.wantsReady());
            n.setCreatedAt(now.minus(Duration.ofDays(5 + Math.abs(s.key().hashCode() % 40))));
            needs.save(n);
        }

        List<InnovationSeed> seeds = read("seed/innovations.json", new TypeReference<List<InnovationSeed>>() { });
        Map<String, Innovation> created = new LinkedHashMap<>();
        for (InnovationSeed s : seeds) {
            created.put(s.key(), createInnovation(s, now));
        }
        // oceny liczymy, gdy wszystkie innowacje i potrzeby już są (podobieństwo, dopasowania)
        for (InnovationSeed s : seeds) {
            buildHistory(s, created.get(s.key()), analyst);
        }
        radar.scanInitial();

        log.info("Dane demo: {} instytucji, {} kont PCI, {} innowacji, {} potrzeb", institutions.count(), people.count(),
                innovations.count(), needs.count());
    }

    /**
     * Hasło kont przy pierwszym starcie: z konfiguracji albo – gdy go nie ustawiono (produkcja bez
     * TRACKIDEA_PASSWORD) – losowe, wypisane raz w logu. Dzięki temu znane hasło demo nie trafia na produkcję.
     */
    private String initialPassword() {
        if (demoPassword != null && !demoPassword.isBlank()) {
            return demoPassword;
        }
        byte[] random = new byte[12];
        new SecureRandom().nextBytes(random);
        String generated = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        log.warn("Nie ustawiono hasła kont PCI (TRACKIDEA_PASSWORD) – wygenerowano jednorazowe: {}", generated);
        return generated;
    }

    /** Baza trwała: zmiana hasła w konfiguracji obowiązuje od następnego startu. */
    private void syncPasswords() {
        if (demoPassword == null || demoPassword.isBlank()) {
            return;
        }
        for (Person p : people.findAll()) {
            if (p.getPasswordHash() == null || !passwordEncoder.matches(demoPassword, p.getPasswordHash())) {
                p.setPasswordHash(passwordEncoder.encode(demoPassword));
            }
        }
    }

    private Innovation createInnovation(InnovationSeed s, Instant now) {
        Innovation inn = new Innovation();
        inn.setTitle(s.title());
        inn.setSummary(s.summary());
        inn.setProblem(s.problem());
        inn.setSolution(s.solution());
        inn.setRequirements(s.requirements());
        inn.setResults(s.results());
        inn.setType(s.type());
        inn.setSector(s.sector());
        inn.setMaturity(s.maturity());
        inn.setTransferModel(s.transferModel());
        inn.setIpStatus(s.ipStatus());
        if (s.tags() != null) inn.getTags().addAll(s.tags());
        if (s.links() != null) inn.getLinks().addAll(s.links());
        inn.setInstitution(inst.get(s.institution()));
        inn.setSourceAuthors(s.sourceAuthors());
        inn.setSource(Source.ANALITYK);
        inn.setSourceName(s.sourceName());
        inn.setStatus(s.status());
        inn.setArea(Area.derive(s.type(), s.sector(),
                inn.getInstitution() == null ? null : inn.getInstitution().getType()));
        // TRL dotyczy rozwiązań technologicznych; w danych demo jest szacowany, zweryfikowane karty mają go potwierdzonego
        if (s.trl() != null || inn.getArea() == Area.TECHNOLOGICZNY) {
            inn.setTrl(s.trl() != null ? s.trl()
                    : Trl.estimate(s.maturity(), TextVectorizer.normalize(s.solution() + " " + s.results())));
            if (s.trl() == null && s.status() != InnovationStatus.OCENIONA) {
                inn.getEstimated().add("trl");
            }
        }
        if (s.paths() != null) inn.getChosenPaths().addAll(s.paths());
        Instant created = now.minus(Duration.ofDays(s.daysAgo()));
        inn.setCreatedAt(created);
        inn.setUpdatedAt(created);
        return innovations.save(inn);
    }

    /** Odtwarza oś czasu i ocenę tak, jakby rekord przeszedł weryfikację w przeszłości. */
    private void buildHistory(InnovationSeed s, Innovation inn, Person analyst) {
        Instant created = inn.getCreatedAt();
        event(inn, EventType.UTWORZONO, "Dodana przez analityka PCI na podstawie źródła: " + s.sourceName() + ".",
                analyst, created, InnovationStatus.WYKRYTA);
        if (s.status() == InnovationStatus.WYKRYTA) {
            assessmentService.autoAssess(inn).ifPresent(auto -> auto.setCreatedAt(created));
            return;
        }
        int assessDay = s.assessAfterDays() == null ? Math.max(1, s.daysAgo() / 3) : s.assessAfterDays();
        Instant proposedAt = created.plus(Duration.ofDays(Math.max(0, Math.min(assessDay - 2, s.daysAgo()))));
        Assessment proposal = assessmentService.propose(inn);
        proposal.setCreatedAt(proposedAt);
        assessments.save(proposal);
        event(inn, EventType.STATUS, "W ocenie: Wygenerowano propozycję oceny: "
                + Math.round(proposal.proposedTotal()) + "/100.", analyst, proposedAt, InnovationStatus.W_OCENIE);
        if (s.status() != InnovationStatus.OCENIONA) {
            return;
        }
        Instant approvedAt = created.plus(Duration.ofDays(assessDay));
        int changed = 0;
        for (CriterionScore cs : proposal.getScores()) {
            int delta = s.expertDelta() == null ? 0 : s.expertDelta().getOrDefault(cs.getCriterion(), 0);
            cs.setFinalScore(Math.max(1, Math.min(5, cs.getProposedScore() + delta)));
            cs.setFinalJustification(delta == 0 ? cs.getProposedJustification()
                    : cs.getProposedJustification() + " Korekta analityka: " + (delta > 0 ? "+" : "") + delta
                    + " pkt po sprawdzeniu źródeł.");
            if (delta != 0) changed++;
        }
        proposal.setApprovedBy(analyst);
        proposal.setApprovedAt(approvedAt);
        event(inn, EventType.STATUS, "Zweryfikowana: Analityk zatwierdził ocenę: " + Math.round(proposal.total()) + "/100"
                + (changed > 0 ? " (poprawiono kryteria: " + changed + ")." : " (bez zmian względem propozycji)."),
                analyst, approvedAt, InnovationStatus.OCENIONA);
        if (!inn.getChosenPaths().isEmpty()) {
            event(inn, EventType.SCIEZKA, "Wskazane zastosowania: "
                    + String.join(", ", inn.getChosenPaths().stream().map(PathType::getLabel).toList()) + ".",
                    analyst, approvedAt.plus(Duration.ofDays(1)), InnovationStatus.OCENIONA);
        }
    }

    private void event(Innovation inn, EventType type, String msg, Person actor, Instant at, InnovationStatus after) {
        InnovationEvent e = new InnovationEvent(inn, type, msg, actor);
        e.setCreatedAt(at);
        e.setStatusAfter(after);
        events.save(e);
    }

    private <T> T read(String path, TypeReference<T> type) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return json.readValue(in, type);
        } catch (IOException e) {
            throw new IllegalStateException("Nie udało się wczytać " + path, e);
        }
    }
}
