<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="frontend/public/logo-white.png">
    <img src="frontend/public/logo-black.png" alt="TrackIdea" height="120">
  </picture>
</p>

# TrackIdea – radar innowacji

**Narzędzie do odkrywania i analizy istniejących innowacji w Polsce.** Radar zbiera rozwiązania technologiczne,
edukacyjne i administracyjne z rozproszonych źródeł, porządkuje je w karty, ocenia poziom wdrożenia i potencjał
oraz pokazuje, gdzie mogą znaleźć zastosowanie.

Prototyp na HackNation – wyzwanie Podkarpackiego Centrum Innowacji **„Radar innowacji”**.
Plan projektu: [`docs/TrackIdea_plan_projektu.pdf`](docs/TrackIdea_plan_projektu.pdf).

> Radar nie jest platformą do zgłaszania pomysłów, portalem społecznościowym ani narzędziem do pozyskiwania
> finansowania – zgodnie z doprecyzowaniem wyzwania.

## Problem

Informacje o innowacjach są rozproszone w wielu źródłach, więc wartościowe rozwiązania pozostają mało znane,
a instytucje niezależnie budują podobne rzeczy.

## Rozwiązanie

| Etap | Co robi TrackIdea |
|---|---|
| **Wyszukaj** | Radar importuje rekordy z otwartych źródeł i przypisuje je do obszaru: technologiczny, edukacyjny lub administracyjny (definicja innowacji wg Podręcznika Oslo 2018). |
| **Uporządkuj** | Każdy rekord to karta: opis, instytucja, źródło, etap wdrożenia. Duplikaty z wielu źródeł są wykrywane. Do 4 innowacji można porównać obok siebie. Gotowe widoki dla grup odbiorców: inwestorzy, samorządy, uczelnie, szkoły, startupy, wynalazcy. |
| **Oceń** | Każda karta od razu dostaje ocenę automatyczną (szacunek) według kryteriów z FAQ: poziom wdrożenia (koncepcja → stosowane w praktyce), TRL 1–9, innowacyjność, potencjał zastosowania, wiarygodność. Szacunki są oznaczone i odróżnione od faktów ze źródła; analityk PCI je weryfikuje. |
| **Wskaż zastosowanie** | Reguły podpowiadają, gdzie rozwiązanie może zostać użyte; zapytania „szukam rozwiązania” dostają dopasowania. |

```
Wykryta → W ocenie → Zweryfikowana        (albo: Odrzucona / Połączona)
```

## Technologie

- **backend/** – Java 21, Spring Boot 4.2, Spring Security, JPA, H2 (domyślnie) lub PostgreSQL
- **frontend/** – React 19, Vite
- Dokumentacja API: Swagger UI pod `/swagger-ui.html`; katalog można pobrać jako CSV

## Uruchomienie

Wymagania: **Java 21+** i **Node.js 20.19+**.

```bash
cd backend
./mvnw spring-boot:run
```

```bash
cd frontend
npm install
npm run dev
```

Otwórz **http://localhost:5173**. Restart backendu przywraca czyste dane demo.

**Źródła Radaru.** Po starcie backend pobiera prawdziwe rekordy i odświeża je co 6 godzin:

- **OpenAlex** – publikacje polskich uczelni i instytutów,
- **CORDIS** – projekty UE z udziałem polskich gmin, szkół, uczelni i firm,
- **Fundusze Europejskie 2021–2027** (dane.gov.pl) – w tym projekty PARP i NCBR oraz programów regionalnych; Podkarpacie na początku,
- **RAD-on** (OPI PIB) – patenty i wzory użytkowe polskich uczelni i instytutów z opisem, twórcami i linkiem do karty
  w e-Wyszukiwarce Urzędu Patentowego; uczelnie z Podkarpacia na początku.

**Źródło potrzeb.** Tablica „Szukam rozwiązania” dostaje też prawdziwe zapotrzebowanie z **Biuletynu Zamówień Publicznych**
(ezamowienia.gov.pl): ogłoszenia, w których gmina, szkoła, szpital lub uczelnia chce coś wdrożyć, zbudować albo zbadać
(bez serwisu, licencji i samego sprzętu). Każda taka potrzeba ma link do ogłoszenia i termin składania ofert, po którym
znika z tablicy; innowacje są do niej dopasowywane tak samo jak do zapytań z formularza.

Analityk PCI może też dodać rekord ze **strony** (gminy, szkoły, uczelni) albo z **treści dokumentu** – Radar wyciąga
pola karty (model Claude, gdy jest klucz; bez niego reguły) i oznacza je jako szacunek. Bez internetu Radar może wczytać próbki z plików:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--trackidea.radar.live=false
```

Opcjonalnie asystent AI z Claude: uruchom backend z ustawioną zmienną `ANTHROPIC_API_KEY`
(bez niej działa tryb offline).

Katalog jest publiczny. **Logowanie** potrzebne jest tylko zespołowi PCI do weryfikacji i oceny:
`k.nowak@pci.example.org`, hasło `demo123` (w trybie demo konto można też przełączyć w prawym górnym rogu).

## Testy

```bash
cd backend
./mvnw test
```

53 testy (JUnit 5): jednostkowe dla wzoru oceny i wag, podobieństwa tekstów po polsku, reguł dopasowań,
analizy tekstu offline, parsowania danych ze źródeł Radaru (w tym pliku XLSX Funduszy Europejskich, patentów RAD-on
i ogłoszeń BZP), zapisu i zamykania potrzeb ze źródeł, ochrony przed pobieraniem adresów wewnętrznych, skali TRL i obszarów oraz testy integracyjne logowania, CSRF, uprawnień zespołu PCI, automatycznej oceny kart i analizy dokumentu.

## Dane, bezpieczeństwo i ograniczenia

- **Dane:** OpenAlex, CORDIS, dane.gov.pl, RAD-on i BZP przez otwarte API (bez kluczy); kolejne źródła dodaje się jako nowy importer
  (`SourceImporter` dla innowacji, `NeedSource` dla potrzeb). OpenAlex bez klucza ma wspólny dzienny limit zapytań na adres IP –
  po jego wyczerpaniu źródło wraca następnego dnia. Strony wskazane przez analityka są pobierane tylko z publicznych adresów http/https.
- **Ocena:** jawne reguły i wagi; AI i reguły tylko proponują, decyzję zatwierdza ekspert PCI.
- **Prywatność:** Radar nie zbiera danych osobowych użytkowników; autorzy innowacji są podawani tak, jak w źródle.
- **Bezpieczeństwo:** logowanie Spring Security (sesja HttpOnly, CSRF, hasła BCrypt), uprawnienia sprawdzane
  w backendzie. Tryb demo służy tylko prezentacji – w produkcji `trackidea.demo-mode=false`.

## Co dalej

Importery API baz danych, SSO instytucji, embeddingi zamiast TF-IDF, kalibracja wag w pilotażu z PCI
i uruchomienie dla kolejnych regionów.
