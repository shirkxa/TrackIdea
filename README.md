<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="frontend/public/logo-white.png">
    <img src="frontend/public/logo-black.png" alt="TrackIdea" height="120">
  </picture>
</p>

# TrackIdea – radar ukrytych innowacji

**GitHub dla innowacji z sektora publicznego.** Każdy dobry pomysł ze szkoły, uczelni czy samorządu dostaje
kartę innowacji, przejrzystą ocenę i ścieżkę rozwoju, a jego wdrożenie w kolejnej instytucji to po prostu **fork**.

Prototyp na HackNation – wyzwanie Podkarpackiego Centrum Innowacji **„Radar innowacji”**.
Plan projektu: [`docs/TrackIdea_plan_projektu.pdf`](docs/TrackIdea_plan_projektu.pdf).
Wszystkie instytucje, osoby i dane w demo są fikcyjne.

## Problem

Wartościowe rozwiązania powstają lokalnie i zostają znane tylko tam, gdzie powstały. Brakuje sposobu, żeby je
zauważać, oceniać i kierować do tych, którzy mogą je wdrożyć lub sfinansować – instytucje wymyślają więc to samo
niezależnie od siebie.

## Rozwiązanie

| Etap | Co robi TrackIdea |
|---|---|
| **Znajdź** | Zgłoszenia twórców i „zwiadowców” (cudzy pomysł) + **Radar**: import z otwartych baz (PBN, NCBiR, Interreg, patenty). |
| **Uporządkuj** | Karta innowacji jak README. **Asystent AI** wypełnia ją z opisu własnymi słowami. |
| **Sprawdź duplikaty** | Podobne innowacje i patenty widać już w trakcie pisania. |
| **Oceń** | 6 kryteriów z jawnymi wagami i uzasadnieniem; propozycję zatwierdza ekspert PCI. |
| **Wskaż ścieżkę** | Jawne reguły: replikacja, rozwój w PCI, komercjalizacja, grant, pilotaż B+R. |
| **Połącz** | Polecajki z wyjaśnieniem „dlaczego”: potrzeby instytucji, partnerzy, inwestorzy. |
| **Śledź efekt** | Status jak w Banku Pomysłów, fork = wdrożenie gdzie indziej, panel PCI z miernikami. |

```
Wykryta / Zgłoszona → Potwierdzona → Uzupełniona → W ocenie → Oceniona
    → Ścieżka wskazana → W transferze → Wdrożona (×N)
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

Opcjonalnie asystent AI z Claude: uruchom backend z ustawioną zmienną `ANTHROPIC_API_KEY`
(bez niej działa tryb offline).

**Konta demo:** hasło `demo123` (np. `k.nowak@pci.example.org` – ekspert PCI); konto można też przełączyć
w prawym górnym rogu.

## Testy

```bash
cd backend
./mvnw test
```

21 testów (JUnit 5): jednostkowe dla wzoru oceny i wag, podobieństwa tekstów po polsku, reguł dopasowań
i asystenta offline oraz test integracyjny logowania, CSRF i uprawnień ról.

## Dane, bezpieczeństwo i ograniczenia

- **Dane:** Radar czyta w prototypie próbki eksportów z baz wskazanych przez PCI; docelowo każde źródło to importer API.
- **Ocena:** jawne reguły i wagi; AI i reguły tylko proponują, decyzję zatwierdza ekspert PCI.
- **Prywatność:** zgłoszenia zwiadowców i rekordy Radaru są niepubliczne do potwierdzenia autora; dane kontaktowe
  widać dopiero po akceptacji prośby o kontakt.
- **Bezpieczeństwo:** logowanie Spring Security (sesja HttpOnly, CSRF, hasła BCrypt), uprawnienia sprawdzane
  w backendzie. Tryb demo służy tylko prezentacji – w produkcji `trackidea.demo-mode=false`.

## Co dalej

Importery API baz danych, SSO instytucji, embeddingi zamiast TF-IDF, kalibracja wag w pilotażu z PCI
i uruchomienie dla kolejnych regionów.
