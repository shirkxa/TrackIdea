<p align="center">
  <img src="frontend/public/logo-black.png" alt="TrackIdea" height="120">
</p>

# TrackIdea – radar ukrytych innowacji

**GitHub dla innowacji z sektora publicznego.** Każdy dobry pomysł ze szkoły, uczelni, samorządu czy innej
instytucji publicznej dostaje swoje „repozytorium” (kartę innowacji), przejrzystą ocenę i rekomendowaną ścieżkę
rozwoju, a jego wdrożenie w kolejnej instytucji to po prostu **fork**.

Prototyp na HackNation – wyzwanie Podkarpackiego Centrum Innowacji **„Radar innowacji”**.
Pełny plan projektu: [`docs/TrackIdea_plan_projektu.pdf`](docs/TrackIdea_plan_projektu.pdf).

> Wszystkie instytucje, osoby i dane w wersji demo są fikcyjne.

## Problem

Wartościowe rozwiązania powstają lokalnie, ale zostają znane tylko tam, gdzie powstały. Brakuje mechanizmu,
który pozwala je systematycznie **zauważać**, **porządkować**, **oceniać** i **kierować** do osób i organizacji,
które mogą je wdrożyć, rozwinąć albo sfinansować. W efekcie różne instytucje niezależnie wymyślają to samo.

## Rozwiązanie

| Etap | Co robi TrackIdea |
|---|---|
| **Znajdź** | Zgłoszenia od twórców i „zwiadowców” (można zgłosić cudzy pomysł) + **Radar**: automatyczny import z otwartych baz (PBN, NCBiR, Interreg, patenty UPRP/EPO). |
| **Uporządkuj** | Karta innowacji jak README: problem, rozwiązanie, wymagania, efekty, typ, sektor, etap, model transferu. **Asystent AI** wypełnia ją z opisu własnymi słowami. |
| **Sprawdź duplikaty** | Wyszukiwanie semantyczne pokazuje podobne innowacje i patenty już w trakcie pisania. Rekordy Radaru podobne w ≥ 75% są oznaczane jako duplikaty. |
| **Oceń** | 6 kryteriów z **jawnymi wagami** (skala problemu 20%, nowość 15%, dojrzałość 15%, przenaszalność 20%, wpływ 20%, zespół 10%). System proponuje oceny z uzasadnieniem, ekspert PCI je zatwierdza lub poprawia. |
| **Wskaż ścieżkę** | Jawne reguły rekomendują: otwartą replikację, rozwój w PCI, ochronę IP i komercjalizację, grant, pilotaż B+R albo połączenie sił z podobną innowacją. |
| **Połącz** | Polecajki z wyjaśnieniem „dlaczego”: innowacja ↔ potrzeby z tablicy ↔ partnerzy i inwestorzy. Prośba o kontakt – dane widoczne po akceptacji. |
| **Śledź efekt** | Jawny status (jak w Banku Pomysłów), fork = wdrożenie w innej instytucji, panel PCI z miernikami skuteczności. |

### Cykl życia innowacji

```
Wykryta / Zgłoszona → Potwierdzona → Uzupełniona → W ocenie → Oceniona
    → Ścieżka wskazana → W transferze → Wdrożona (×N)
```

Każde przejście ma właściciela (autor, opiekun instytucji, ekspert PCI) i zapisuje się na osi czasu innowacji.

## Architektura

```
TrackIdea/
├── backend/    Java 21 · Spring Boot 4.2 · Spring Data JPA · H2 (domyślnie) / PostgreSQL
├── frontend/   React 19 · Vite · React Router
└── docs/       plan projektu (PDF)
```

- **Backend** (`backend/src/main/java/pl/trackidea`)
  - `domain/` – encje: innowacja, ocena, potrzeba, osoba, instytucja, prośba o kontakt, rekordy Radaru, patenty
  - `service/` – logika: statusy (`InnovationService`), ocena (`AssessmentService`), rekomendacje ścieżek
    (`RecommendationService`), dopasowania (`MatchingService`), podobieństwo (`SimilarityService`,
    `TextVectorizer`), Radar (`RadarService`), asystent (`AssistantService`), mierniki (`StatsService`)
  - `web/` – REST API pod `/api`
  - `seed/` + `resources/seed`, `resources/radar` – dane demonstracyjne i próbki źródeł Radaru
- **Frontend** (`frontend/src`) – katalog, karta innowacji, zgłaszanie z asystentem, tablica potrzeb,
  panel PCI z Radarem, „Moje”, profile i strony instytucji. W trybie dev Vite przekazuje `/api` do backendu.

## Uruchomienie

Wymagania: **Java 21+** i **Node.js 20.19+ lub 22+**. Maven nie jest potrzebny – repo ma wrapper `mvnw`.

**Terminal 1 – backend** (port 8080):

```bash
cd backend
./mvnw spring-boot:run
```

Gotowe, gdy w logach pojawi się `Dane demo: 20 instytucji, 27 osób…`.

**Terminal 2 – frontend** (port 5173):

```bash
cd frontend
npm install
npm run dev
```

Otwórz **http://localhost:5173**.

Każdy restart backendu przywraca czyste dane demo (baza H2 w pamięci).

### Opcjonalnie: asystent AI (Claude)

Bez klucza asystent działa offline na regułach słownikowych. Z kluczem pola karty wyodrębnia model Claude
(structured output):

```bash
ANTHROPIC_API_KEY=sk-ant-... ./mvnw spring-boot:run
```

Model można zmienić właściwością `trackidea.ai.model` w `application.properties`.

### Opcjonalnie: PostgreSQL

```bash
SPRING_PROFILES_ACTIVE=postgres DATABASE_URL=jdbc:postgresql://localhost:5432/trackidea ./mvnw spring-boot:run
```

## Użytkownicy demo

Logowanie zastępuje **przełącznik ról** w prawym górnym rogu. Najważniejsze osoby do scenariuszy:

| Osoba | Rola | Do czego |
|---|---|---|
| Tomasz Wrona | Opiekun – LO „Horyzont” w Krośnie | zgłasza cudzy pomysł (zwiadowca) |
| Anna Kowalczyk | Twórca – LO „Horyzont” | potwierdza autorstwo, prosi o ocenę |
| Katarzyna Nowak | Ekspert PCI | zatwierdza oceny, wybiera ścieżki, uruchamia Radar |
| Dorota Wilk | Opiekun – LO „Alfa” w Przemyślu | forkuje i potwierdza wdrożenie |
| dr inż. Marek Zając | Twórca – Akademia Techniczno-Przyrodnicza | potwierdza innowację wykrytą przez Radar |
| Jan Dudek | Partner – SkyAgro Systems | dostaje polecajkę drona, prosi o kontakt |
| Piotr Mazur | Opiekun – Gmina Wiśniowa Dolina | szuka rozwiązania problemu z usterkami |

## Scenariusze demo (punkt 6 wyzwania)

### A. Szkoła – program mentoringu rówieśniczego (innowacja edukacyjna)

1. **Tomasz Wrona** → *Zgłoś innowację* → „Scenariusz A – mentoring w liceum” → *Wypełnij kartę asystentem*.
   Panel z boku pokazuje podobne rozwiązania (tutoring z matematyki, publikacja o mentoringu) – to nie duplikat.
2. Model transferu: *Otwarta replikacja*, zaznacz *Zgłaszam cudzy pomysł*, autor: Anna Kowalczyk → *Zgłoś*.
   Status: **Zgłoszona** – niepubliczna do potwierdzenia autorki.
3. **Anna Kowalczyk** → *Moje* → karta → *Potwierdzam autorstwo* → status **Uzupełniona** → *Poproś o ocenę*.
4. **Katarzyna Nowak** → *Panel PCI* → *Oceny do zatwierdzenia* → karta: propozycja ok. 75/100 z uzasadnieniem
   każdego kryterium (3 szkoły na tablicy potrzeb, spadek nieobecności o 30%) → *Zatwierdź ocenę*.
5. Ścieżki: *Otwarta replikacja* z 3 dopasowanymi szkołami → zaznacz → *Zatwierdź*.
6. **Dorota Wilk** → karta → *Wdrażamy u siebie (fork)* → *Potwierdź wdrożenie*. Oryginał: **Wdrożona**, 1 fork.

### B. Uczelnia – dron do wykrywania chorób upraw (innowacja techniczna, Radar)

1. **Katarzyna Nowak** → *Panel PCI* → *Uruchom skan Radaru*: 3 nowe innowacje i **1 duplikat** (rekord NCBiR
   o mapie usterek rozpoznany jako istniejąca innowacja gminy Sośnica Górna, 82%).
2. **dr inż. Marek Zając** → *Moje* → *Czekają na potwierdzenie* → dron → *Potwierdzam autorstwo*.
3. *Edytuj kartę*: model transferu *Współpraca B+R*, sektor *Rolnictwo* → zapisz → *Poproś o ocenę*.
4. Ocena pokazuje podobne patenty EPO (kryterium „Nowość”). Rekomendacja: **pilotaż / współpraca B+R** z firmą
   SkyAgro (potrzeba z tablicy), a w polecanych partnerach **Jan Dudek** (obserwuje tag „drony”).
5. **Jan Dudek** → *Moje* → *Polecane dla Ciebie* → dron → *Poproś o kontakt*; autor akceptuje w *Moje* –
   obie strony widzą swoje e-maile.

### C. Gmina – zgłaszanie usterek (ograniczanie dublowania prac)

1. **Piotr Mazur** → *Zgłoś innowację* → „Scenariusz C – usterki w gminie”: już w trakcie pisania pojawia się
   ostrzeżenie, że podobne rozwiązanie istnieje w Gminie Sośnica Górna.
2. Zamiast budować od zera → karta „Mapa zgłoszeń usterek” → *Wdrażamy u siebie (fork)*.
   Na tablicy potrzeb ta sama mapa jest dopasowana do potrzeby gminy (94%).

### Mierniki skuteczności

*Panel PCI* pokazuje mierniki z planu: odnalezione innowacje (Radar vs zgłoszenia), wykryte potwierdzone przez
autorów, czas zgłoszenie → ocena, zgodność propozycji z ekspertem, konwersję kontaktów, forki i wdrożenia,
zatrzymane duplikaty.

## API (wybrane endpointy)

Pełna, interaktywna dokumentacja: **http://localhost:8080/swagger-ui.html** (specyfikacja OpenAPI: `/v3/api-docs`).
Katalog można też pobrać jako **CSV** (przycisk „CSV” w katalogu albo `GET /api/innovations/export` z tymi samymi
filtrami) – plik otwiera się bezpośrednio w Excelu.

| Metoda | Ścieżka | Opis |
|---|---|---|
| GET | `/api/innovations?q=&type=&sector=&sort=` | katalog: wyszukiwanie semantyczne, filtry, sortowanie |
| GET | `/api/innovations/{id}` | karta innowacji z oceną, osią czasu i uprawnieniami oglądającego |
| GET | `/api/innovations/{id}/recommendations` | rekomendowane ścieżki z regułą i uzasadnieniem |
| GET | `/api/innovations/{id}/matches` | dopasowane potrzeby i partnerzy z powodami |
| POST | `/api/innovations` / PUT `/api/innovations/{id}` | zgłoszenie / edycja karty |
| POST | `/api/innovations/{id}/confirm`, `/request-assessment`, `/assessment/approve`, `/paths`, `/fork`, `/confirm-deployment` | przejścia statusów |
| POST | `/api/assistant/draft`, `/api/similar` | asystent karty, podobne rozwiązania |
| GET/POST | `/api/needs` | tablica potrzeb z dopasowaniami |
| GET | `/api/innovations/export` | eksport katalogu do CSV (UTF-8, separator `;`) |
| POST | `/api/radar/scan` | skan źródeł Radaru |
| GET | `/api/stats`, `/api/pci/queue` | mierniki i kolejka pracy PCI |

Użytkownika demo wskazuje nagłówek `X-User-Id`.

## Dane, AI i ograniczenia

- **Źródła danych:** otwarte bazy wskazane przez PCI (PBN, NCBiR, Interreg, UPRP, EPO). W prototypie Radar czyta
  **próbki eksportów** z plików JSON (`backend/src/main/resources/radar`) – docelowo każde źródło to osobny
  importer korzystający z API danej bazy.
- **Podobieństwo:** TF-IDF + podobieństwo cosinusowe z prostym stemmingiem dla polskiego. Działa offline i jest
  wytłumaczalne; można je podmienić na embeddingi modelu językowego (np. PostgreSQL + pgvector).
- **Ocena i rekomendacje:** jawne reguły opisane w uzasadnieniu każdej oceny. **AI i reguły tylko proponują –
  decyzję zawsze zatwierdza ekspert PCI.** Wynik merytoryczny jest liczony osobno od głosów społeczności, żeby
  popularność nie zagłuszyła jakości.
- **Asystent AI:** opcjonalny (Claude); bez klucza działa na regułach. Może się mylić przy krótkich opisach,
  dlatego każde pole jest edytowalne.
- **Prywatność:** zgłoszenia zwiadowców i rekordy Radaru są niepubliczne do potwierdzenia autora, a dane
  kontaktowe są widoczne dopiero po zaakceptowaniu prośby o kontakt.
- **Logowanie:** w demo zastępuje je przełącznik ról; w wersji produkcyjnej Spring Security i SSO instytucji.

## Co dalej po hackathonie

- importery API dla PBN, NCBiR, UPRP i EPO oraz RSS-y stron szkół i gmin (Radar ma już kontrakt importera i REST API),
- logowanie i role (Spring Security, SSO instytucji), powiadomienia e-mail,
- embeddingi i PostgreSQL + pgvector,
- kalibracja wag kryteriów na danych z pilotażu z PCI (10–20 instytucji z Podkarpacia),
- uruchomienie dla kolejnych regionów (wiele instytucji od początku, PCI jako operator).
