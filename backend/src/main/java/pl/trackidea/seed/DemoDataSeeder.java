package pl.trackidea.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.trackidea.domain.*;
import pl.trackidea.repo.*;
import pl.trackidea.service.AssessmentService;
import pl.trackidea.service.InnovationService;
import pl.trackidea.service.RadarService;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Dane demonstracyjne (fikcyjne instytucje i osoby) potrzebne do scenariuszy z planu:
 * A – szkoła (mentoring rówieśniczy), B – uczelnia (dron z Radaru), C – gmina (mapa usterek).
 * Wczytywane z plików JSON w resources/seed, tylko gdy baza jest pusta.
 */
@Component
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final List<InnovationStatus> PIPELINE = List.of(
            InnovationStatus.POTWIERDZONA, InnovationStatus.UZUPELNIONA, InnovationStatus.W_OCENIE,
            InnovationStatus.OCENIONA, InnovationStatus.SCIEZKA_WSKAZANA, InnovationStatus.W_TRANSFERZE,
            InnovationStatus.WDROZONA);

    record InstitutionSeed(String key, String name, InstitutionType type, String city, String county) {
    }

    record PersonSeed(String key, String name, Role role, String institution, String headline, String bio,
                      String email, String github, String youtube, String linkedin, List<Sector> sectors,
                      List<String> tags, List<Maturity> stages) {
    }

    record ForkSeed(String institution, String author, boolean deployed, int daysAgo) {
    }

    record InnovationSeed(String key, String title, String summary, String problem, String solution,
                          String requirements, String results, InnovationType type, Sector sector, Maturity maturity,
                          TransferModel transferModel, String ipStatus, List<String> tags, List<String> links,
                          String institution, List<String> authors, String reportedBy, String sourceAuthors,
                          InnovationStatus status, List<PathType> paths, int daysAgo, Integer assessAfterDays,
                          int votes, Map<Criterion, Integer> expertDelta, List<ForkSeed> forks) {
    }

    record NeedSeed(String key, String title, String description, String institution, String author, Sector sector,
                    List<String> tags, boolean wantsReady) {
    }

    private final JsonMapper json = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();
    private final InstitutionRepository institutions;
    private final PersonRepository people;
    private final InnovationRepository innovations;
    private final InnovationEventRepository events;
    private final NeedRepository needs;
    private final PriorArtRepository priorArt;
    private final VoteRepository votes;
    private final AssessmentRepository assessments;
    private final ContactRequestRepository contacts;
    private final AssessmentService assessmentService;
    private final InnovationService innovationService;
    private final RadarService radar;
    private final PasswordEncoder passwordEncoder;
    private final String demoPassword;

    private final Map<String, Institution> inst = new HashMap<>();
    private final Map<String, Person> person = new HashMap<>();
    private final Map<String, Innovation> innov = new HashMap<>();

    public DemoDataSeeder(InstitutionRepository institutions, PersonRepository people, InnovationRepository innovations,
                          InnovationEventRepository events, NeedRepository needs, PriorArtRepository priorArt,
                          VoteRepository votes, AssessmentRepository assessments, ContactRequestRepository contacts,
                          AssessmentService assessmentService, InnovationService innovationService, RadarService radar,
                          PasswordEncoder passwordEncoder,
                          @Value("${trackidea.demo-password:demo123}") String demoPassword) {
        this.passwordEncoder = passwordEncoder;
        this.demoPassword = demoPassword;
        this.institutions = institutions;
        this.people = people;
        this.innovations = innovations;
        this.events = events;
        this.needs = needs;
        this.priorArt = priorArt;
        this.votes = votes;
        this.assessments = assessments;
        this.contacts = contacts;
        this.assessmentService = assessmentService;
        this.innovationService = innovationService;
        this.radar = radar;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (institutions.count() > 0) {
            return;
        }
        Instant now = Instant.now();

        for (InstitutionSeed s : read("seed/institutions.json", new TypeReference<List<InstitutionSeed>>() { })) {
            inst.put(s.key(), institutions.save(new Institution(s.name(), s.type(), s.city(), s.county(), null)));
        }
        // wszystkie konta demo mają to samo hasło (trackidea.demo-password), zapisane jako hash BCrypt
        String passwordHash = passwordEncoder.encode(demoPassword);
        for (PersonSeed s : read("seed/people.json", new TypeReference<List<PersonSeed>>() { })) {
            Person p = new Person();
            p.setPasswordHash(passwordHash);
            p.setName(s.name());
            p.setRole(s.role());
            p.setInstitution(inst.get(s.institution()));
            p.setHeadline(s.headline());
            p.setBio(s.bio());
            p.setEmail(s.email());
            p.setGithub(s.github());
            p.setYoutube(s.youtube());
            p.setLinkedin(s.linkedin());
            if (s.sectors() != null) p.getInterestSectors().addAll(s.sectors());
            if (s.tags() != null) p.getInterestTags().addAll(s.tags());
            if (s.stages() != null) p.getSeekingStages().addAll(s.stages());
            person.put(s.key(), people.save(p));
        }
        priorArt.saveAll(read("seed/prior_art.json", new TypeReference<List<PriorArt>>() { }));

        for (NeedSeed s : read("seed/needs.json", new TypeReference<List<NeedSeed>>() { })) {
            Need n = new Need();
            n.setTitle(s.title());
            n.setDescription(s.description());
            n.setInstitution(inst.get(s.institution()));
            n.setAuthor(person.get(s.author()));
            n.setSector(s.sector());
            if (s.tags() != null) n.getTags().addAll(s.tags());
            n.setWantsReadySolution(s.wantsReady());
            n.setCreatedAt(now.minus(Duration.ofDays(5 + Math.abs(s.key().hashCode() % 40))));
            needs.save(n);
        }

        List<InnovationSeed> seeds = read("seed/innovations.json", new TypeReference<List<InnovationSeed>>() { });
        for (InnovationSeed s : seeds) {
            innov.put(s.key(), createInnovation(s, now));
        }
        // głosy przed oceną – kryterium „Skala problemu” bierze je pod uwagę
        List<Person> voters = people.findAll();
        for (InnovationSeed s : seeds) {
            Innovation inn = innov.get(s.key());
            voters.stream().filter(p -> !inn.isAuthor(p)).limit(s.votes()).forEach(p -> votes.save(new Vote(inn, p)));
        }
        for (InnovationSeed s : seeds) {
            buildHistory(s, innov.get(s.key()));
        }

        radar.scanInitial();
        innovations.findAll().stream()
                .filter(i -> i.getSource() == Source.RADAR && "PBN".equals(i.getSourceName()))
                .findFirst()
                .ifPresent(i -> innovationService.confirm(i.getId(), person.get("kamil")));

        contact("krzysztof", "kompozyty", ContactStatus.ZAAKCEPTOWANA,
                "Mamy kilkanaście ton odpadów rocznie – chętnie przetestujemy Państwa metodę na naszej linii.", now, 20);
        contact("joanna", "seniorzy", ContactStatus.ZAAKCEPTOWANA,
                "Fundacja może sfinansować ściągawki i szkolenie wolontariuszy w kolejnych gminach.", now, 30);
        contact("robert", "eseje", ContactStatus.WYSLANA,
                "Rozwijamy platformę dla szkół – czy możemy porozmawiać o integracji Waszego asystenta?", now, 3);
        contact("magda", "energia", ContactStatus.ODRZUCONA,
                "Interesuje nas model licencyjny dla innych gmin.", now, 15);

        log.info("Dane demo: {} instytucji, {} osób, {} innowacji, {} potrzeb", institutions.count(), people.count(),
                innovations.count(), needs.count());
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
        if (s.authors() != null) s.authors().forEach(a -> inn.getAuthors().add(person.get(a)));
        if (s.reportedBy() != null) {
            inn.setSource(Source.ZWIADOWCA);
            inn.setReportedBy(person.get(s.reportedBy()));
            inn.setSourceAuthors(s.sourceAuthors());
        }
        inn.setStatus(s.status());
        if (s.paths() != null) inn.getChosenPaths().addAll(s.paths());
        Instant created = now.minus(Duration.ofDays(s.daysAgo()));
        inn.setCreatedAt(created);
        inn.setUpdatedAt(created);
        return innovations.save(inn);
    }

    /** Odtwarza oś czasu i ocenę tak, jakby innowacja przeszła przez proces w przeszłości. */
    private void buildHistory(InnovationSeed s, Innovation inn) {
        Instant created = inn.getCreatedAt();
        Person author = inn.getAuthors().stream().findFirst().orElse(inn.getReportedBy());
        Person expert = person.get("kasia");
        event(inn, EventType.UTWORZONO, inn.getReportedBy() != null
                ? "Zgłoszone przez zwiadowcę (" + inn.getReportedBy().getName() + "). Czeka na potwierdzenie autora."
                : "Zgłoszone przez autora.", author, created, InnovationStatus.ZGLOSZONA);

        int assessDay = s.assessAfterDays() == null ? Math.max(1, s.daysAgo() / 3) : s.assessAfterDays();
        int targetIdx = PIPELINE.indexOf(s.status());
        for (int k = 0; k <= targetIdx; k++) {
            InnovationStatus st = PIPELINE.get(k);
            if (st == InnovationStatus.W_TRANSFERZE || st == InnovationStatus.WDROZONA) {
                continue; // te statusy wynikają z forków poniżej
            }
            int day = switch (st) {
                case POTWIERDZONA -> 0;
                case UZUPELNIONA -> Math.min(1, s.daysAgo());
                case W_OCENIE -> Math.max(0, Math.min(assessDay - 2, s.daysAgo()));
                case OCENIONA -> assessDay;
                default -> assessDay + 3;
            };
            Instant at = created.plus(Duration.ofDays(day)).plusSeconds(60L * (k + 1));
            Assessment proposal = null;
            String msg = switch (st) {
                case POTWIERDZONA -> "Autor potwierdził zgłoszenie.";
                case UZUPELNIONA -> "Karta kompletna – można poprosić o ocenę.";
                case W_OCENIE -> {
                    proposal = assessmentService.propose(inn);
                    proposal.setCreatedAt(at);
                    assessments.save(proposal);
                    yield "Wygenerowano propozycję oceny: " + Math.round(proposal.proposedTotal()) + "/100. Czeka na eksperta PCI.";
                }
                case OCENIONA -> {
                    Assessment a = assessmentService.latest(inn).orElseThrow();
                    int changed = 0;
                    for (CriterionScore cs : a.getScores()) {
                        int delta = s.expertDelta() == null ? 0 : s.expertDelta().getOrDefault(cs.getCriterion(), 0);
                        cs.setFinalScore(Math.max(1, Math.min(5, cs.getProposedScore() + delta)));
                        cs.setFinalJustification(delta == 0 ? cs.getProposedJustification()
                                : cs.getProposedJustification() + " Korekta eksperta: " + (delta > 0 ? "+" : "") + delta
                                + " pkt po weryfikacji w terenie.");
                        if (delta != 0) changed++;
                    }
                    a.setApprovedBy(expert);
                    a.setApprovedAt(at);
                    yield "Ekspert zatwierdził ocenę: " + Math.round(a.total()) + "/100"
                            + (changed > 0 ? " (poprawiono kryteria: " + changed + ")." : " (bez zmian względem propozycji).");
                }
                case SCIEZKA_WSKAZANA -> "Zatwierdzona ścieżka: "
                        + String.join(", ", inn.getChosenPaths().stream().map(PathType::getLabel).toList()) + ".";
                default -> "";
            };
            Person actor = (st == InnovationStatus.OCENIONA || st == InnovationStatus.SCIEZKA_WSKAZANA) ? expert : author;
            event(inn, EventType.STATUS, st.getLabel() + ": " + msg, actor, at, st);
        }

        if (s.forks() != null) {
            for (ForkSeed f : s.forks()) {
                Institution where = inst.get(f.institution());
                Person who = person.get(f.author());
                Instant at = Instant.now().minus(Duration.ofDays(f.daysAgo()));
                Innovation fork = new Innovation();
                fork.setTitle(inn.getTitle());
                fork.setSummary("Wdrożenie w: " + where.getName() + ". " + inn.getSummary());
                fork.setProblem(inn.getProblem());
                fork.setSolution(inn.getSolution());
                fork.setRequirements(inn.getRequirements());
                fork.setType(inn.getType());
                fork.setSector(inn.getSector());
                fork.setMaturity(inn.getMaturity());
                fork.setTransferModel(inn.getTransferModel());
                fork.getTags().addAll(inn.getTags());
                fork.setInstitution(where);
                fork.getAuthors().add(who);
                fork.setSource(Source.FORK);
                fork.setForkedFrom(inn);
                fork.setCreatedAt(at);
                fork.setStatus(f.deployed() ? InnovationStatus.WDROZONA : InnovationStatus.W_TRANSFERZE);
                innovations.save(fork);
                event(fork, EventType.FORK, "Rozpoczęto wdrożenie na podstawie „" + inn.getTitle() + "” ("
                        + inn.getInstitution().getName() + ").", who, at, InnovationStatus.W_TRANSFERZE);
                event(inn, EventType.STATUS, "W transferze: " + where.getName() + " rozpoczyna wdrożenie u siebie (fork).",
                        who, at, InnovationStatus.W_TRANSFERZE);
                if (f.deployed()) {
                    Instant done = at.plus(Duration.ofDays(Math.max(1, f.daysAgo() / 2)));
                    event(fork, EventType.STATUS, "Wdrożona: potwierdzono wdrożenie w " + where.getName() + ".", who, done,
                            InnovationStatus.WDROZONA);
                    event(inn, EventType.STATUS, "Wdrożona: potwierdzone wdrożenie w " + where.getName()
                            + ". Działa już w 2 instytucjach.", who, done, InnovationStatus.WDROZONA);
                }
            }
        }
    }

    private void contact(String from, String innovationKey, ContactStatus status, String message, Instant now, int daysAgo) {
        Innovation inn = innov.get(innovationKey);
        ContactRequest cr = new ContactRequest();
        cr.setInnovation(inn);
        cr.setFromPerson(person.get(from));
        cr.setToPerson(inn.getAuthors().iterator().next());
        cr.setMessage(message);
        cr.setStatus(status);
        cr.setCreatedAt(now.minus(Duration.ofDays(daysAgo)));
        if (status != ContactStatus.WYSLANA) {
            cr.setAnsweredAt(now.minus(Duration.ofDays(Math.max(0, daysAgo - 2))));
        }
        contacts.save(cr);
        event(inn, EventType.KONTAKT, person.get(from).getName() + " (" + person.get(from).getRole().getLabel()
                + ") prosi o kontakt.", person.get(from), cr.getCreatedAt(), inn.getStatus());
        if (status == ContactStatus.ZAAKCEPTOWANA) {
            event(inn, EventType.KONTAKT, "Nawiązano kontakt: " + person.get(from).getName() + ".",
                    cr.getToPerson(), cr.getAnsweredAt(), inn.getStatus());
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
