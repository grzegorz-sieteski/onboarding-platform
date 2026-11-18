# Onboarding Platform: 3 mikroserwisy, DDD, Event Sourcing, Kafka

Trzy niezależne serwisy Spring Boot (Java 25, Spring Boot 4.1) współpracują przez Kafkę w modelu **choreografii**.
Nie ma tu centralnego orkiestratora: każdy serwis reaguje na fakty opublikowane przez inny.

```
                 ApplicantVerified                 ClientRestricted
 ┌────────────┐ ───────────────────▶ ┌────────────┐ ─────────────────▶ ┌──────────────┐
 │ Onboarding │  onboarding.customer │ Screening  │  screening.client  │ Restriction  │
 │  :8081     │  -events.v1          │  :8082     │  -events.v1        │  :8083       │
 └────────────┘ ◀─────────────────── └────────────┘                    └──────────────┘
       ▲            ClientCleared                                              │
       │            screening.client-events.v1                                 │
       └──────────────────────── ClientBanned ─────────────────────────────────┘
                                 restriction.client-events.v1
```

Cykl życia onboardingu:

```
start ─▶ PENDING_VERIFICATION ─┬─▶ REJECTED                                   (EligibilityPolicy: dane / wiek)
                               └─▶ PENDING_SCREENING ─┬──────────────▶ ONBOARDED  (ClientCleared)
                                                      │
                                       (minął termin) └─▶ MANUAL_REVIEW ─┬─▶ ONBOARDED  (akceptacja oficera / spóźnione ClientCleared)
                                                                         └─▶ REJECTED   (odrzucenie przez oficera)
każdy status poza BANNED ──────────────────────────────────────────────▶ BANNED     (ClientBanned)
```

Klient jest aktywny (`ONBOARDED`) **dopiero po pozytywnym screeningu albo decyzji oficera compliance**.

### Timeout screeningu i ręczna weryfikacja
- **Termin wylicza domena.** `ApplicantVerified` niesie `screeningDueAt` (`ScreeningSla`, `app.screening.timeout`, domyślnie 15 min).
- **Kto szuka przeterminowanych.** `ScreeningTimeoutJob` (co `app.screening.check-interval-ms`, ShedLock, więc działa na jednej replice) czyta projekcję `screening_deadline`.
  Dla każdego klienta wysyła komendę `EscalateOverdueScreening`.
- **Decyzja należy do agregatu.** Metoda `escalateIfScreeningOverdue()` eskaluje tylko klienta, który *nadal* czeka po terminie (zdarzenie `ManualReviewRequested`).
  Jeśli w międzyczasie przyszedł werdykt, nic się nie dzieje. Wyścig z równoległym `ClientCleared` rozstrzyga optimistic locking: przegrany próbuje ponownie i dostaje już nowy stan.
- **Kolejka compliance** to projekcja `manual_review_queue`: `GET /api/manual-reviews`.
- **Decyzja oficera** jest audytowana:
  - `POST .../{id}/approve` → `ManualReviewApproved` + `CustomerOnboarded`,
  - `POST .../{id}/reject` → `ManualReviewRejected` + `OnboardingRejected`.
- **Spóźniony werdykt** screeningu nadal obowiązuje:
  - `ClientCleared` w `MANUAL_REVIEW` aktywuje klienta i zamyka sprawę;
  - `ClientRestricted` → `ClientBanned` banuje klienta nawet po ręcznej akceptacji;
  - werdykt dla sprawy już rozstrzygniętej jest ignorowany.
- **Projekcje są inline.** Aktualizują się w tej samej transakcji co event store, więc nie rozjeżdżają się ze zdarzeniami i można je odbudować z `event_store`.

## Bounded contexts

| Kontekst | Serwis | Odpowiedzialność | Język (ubiquitous language) |
|---|---|---|---|
| **Customer Onboarding** (core) | `onboarding-service` | przyjmuje wniosek, sprawdza dane (pełnoletność), czeka na screening, aktywuje klienta po `ClientCleared`, banuje po `ClientBanned` | Applicant, Onboarding, Eligibility, Pending Screening / Onboarded / Rejected / Banned |
| **Sanctions Screening** (supporting, compliance) | `screening-service` | sprawdza nazwisko klienta z czarną listą i wydaje werdykt | Screening Case, Blacklist, Surname match, Cleared / Restricted |
| **Client Restrictions** (supporting) | `restriction-service` | jedno źródło prawdy o tym, czy klient jest zablokowany, niezależnie od źródła decyzji (screening albo ręcznie) | Restriction, Ban, Restriction Source |

### Context map
- **Onboarding → Screening**: Onboarding jest upstreamem i publikuje *Published Language* (`ApplicantVerified.v1`). Screening jest konsumentem z własnym modelem (`ScreenedPerson`) i czyta tylko potrzebne pola (*tolerant reader*).
- **Screening → Onboarding**: `ClientCleared.v1` aktywuje klienta. Negatywny werdykt celowo nie idzie bezpośrednio do onboardingu: o banie decyduje kontekst Restriction.
- **Screening → Restriction**: Screening publikuje `ClientRestricted.v1`, a Restriction tłumaczy ten fakt na własną komendę `BanClient(source = SANCTIONS_SCREENING)`.
- **Restriction → Onboarding**: Restriction publikuje `ClientBanned.v1`, a Onboarding wykonuje `BanOnboarding`.
- Konteksty **nie współdzielą kodu ani bazy**. Każdy ma własną kopię klas kontraktu (consumer-side), własną bazę Postgres, własny topic i własny DLT.

## Agregaty (wszystkie event-sourced)

| Agregat | Stream | Zdarzenia domenowe (wewnętrzne) | Inwarianty |
|---|---|---|---|
| `CustomerOnboarding` | `customer-onboarding-{customerId}` | `OnboardingStarted`, `ApplicantVerified`, `ManualReviewRequested`, `ManualReviewApproved`, `ManualReviewRejected`, `CustomerOnboarded`, `OnboardingRejected`, `OnboardingBanned` | weryfikacja przez `EligibilityPolicy` raz, przy starcie; `ONBOARDED` tylko po screeningu lub akceptacji oficera; eskalacja tylko po terminie i tylko raz; ban jest ostateczny, idempotentny i wygrywa z późniejszym werdyktem |
| `ScreeningCase` | `screening-case-{customerId}` | `ScreeningRequested`, `ScreeningPassed`, `BlacklistMatchFound` | klient jest screenowany raz; strumień jest audytem decyzji compliance |
| `ClientRestriction` | `client-restriction-{customerId}` | `ClientBanned`, `BanRequestIgnored` | klient jest banowany co najwyżej raz; kolejne żądania trafiają tylko do audytu |

Value objecty: `CustomerId`, `PersonName`, `EmailAddress`, `Applicant`, `Surname` (normalizacja: wielkość liter, diakrytyki, `ł→l`), `BlacklistEntry`.
Domain service / policy: `EligibilityPolicy`. Porty domenowe: `*Repository`, `Blacklist`.

**Czarna lista celowo nie jest event-sourced.** To dane referencyjne utrzymywane przez compliance (zwykły CRUD).
Event-sourced są *decyzje* podjęte na jej podstawie, czyli `ScreeningCase`.

Zdarzenia domenowe (wewnętrzne) **≠** zdarzenia integracyjne (publiczne, wersjonowane `V1`).
Mapowanie robią klasy `*IntegrationEvents`. Zdarzenia czysto wewnętrzne, np. `OnboardingStarted` czy `BanRequestIgnored`, nie wychodzą z serwisu.

## Architektura serwisu (clean / hexagonal)

```
com.gs5.<context>
├── *ServiceApplication   composition root: @Import jawnej listy konfiguracji, bez skanowania classpath
├── domain/            czysta Java: agregaty, VO, zdarzenia i błędy (sealed), polityki, porty
│   └── shared/        EventSourcedAggregate, DomainEvent, Result<T, E>
├── application/       use case'y (command handler + queries) i port UnitOfWork - zero Springa
└── infrastructure/    adaptery to zwykłe klasy; każdy pakiet ma własną *Configuration, która je składa
    ├── eventstore/    JdbcEventStore (optimistic concurrency), EventSerializer (rejestr z sealed interface)
    ├── persistence/   repozytorium event-sourced, TransactionTemplateUnitOfWork
    ├── projection/    (onboarding) projekcje inline: kolejka manual review, terminy screeningu
    ├── outbox/        transactional outbox + OutboxRelay, uruchamiany jako PeriodicJob
    ├── messaging/     koperta IntegrationEvent, kontrakty V1, listenery, inbox, konfiguracja Kafki
    ├── scheduling/    PeriodicJob: harmonogram z JDK + ShedLock wołany z kodu
    ├── web/           functional endpoints (RouterFunction) + ProblemDetail
    └── config/        UseCaseConfiguration: składa use case'y z portów
```

## Bez adnotacji w kodzie: Spring tylko w konfiguracji

Domena i aplikacja to czysta Java. Adaptery to zwykłe klasy bez adnotacji. Spring „wie” o naszych klasach tylko z klas
`*Configuration`, które budują obiekty przez `new` w metodach `@Bean`.

- **Nic nie jest skanowane.** Klasa startowa ma `@SpringBootConfiguration`, `@EnableAutoConfiguration` i `@Import` z jawną listą
  konfiguracji. Konfiguracje mają `proxyBeanMethods = false`, więc nie powstają też proxy CGLIB.
- **Konfiguracja leży w pakiecie adaptera** (`PersistenceConfiguration`, `MessagingConfiguration`, `WebConfiguration`...),
  więc klasy adaptera zostają package-private. Reszta kodu widzi tylko porty.
- **Transakcje bez `@Transactional`.** Warstwa aplikacji ma port `UnitOfWork`, a infrastruktura implementuje go na `TransactionTemplate`.
  Repozytorium działa w `TransactionTemplate` z `PROPAGATION_MANDATORY`, czyli odmawia zapisu poza transakcją wywołującego.
- **HTTP bez kontrolerów.** Functional endpoints: wszystkie trasy są w jednym `RouterFunction` w `WebConfiguration`, a handlery
  to zwykłe klasy. Walidacja jest tylko w domenie (fabryki `of(...)`), bez Bean Validation i bez dublowania reguł.
- **Kafka bez `@KafkaListener`.** Kontenery listenerów buduje kod: `createContainer(topic)` na fabryce Spring Boota i `setupMessageListener(...)`.
  Dostają ten sam error handler, ack mode i współbieżność co adnotowany listener.
- **Harmonogram bez `@Scheduled` i `@SchedulerLock`.** `PeriodicJob` to `ScheduledExecutorService` z JDK i ShedLock wywołany z kodu
  (`DefaultLockingTaskExecutor`), startowany i zatrzymywany jawnie.
- **Konfiguracja bez `@ConfigurationProperties`.** Rekordy `*Properties` są zwykłymi rekordami wiązanymi przez `Binder` w konfiguracji.
  Wszystkie wartości są w `application.yml`.
- **Pilnuje tego test, a nie obietnica.** `ArchitectureTest` (ArchUnit) w każdym serwisie sprawdza, że domena i aplikacja nie zależą
  od Springa ani od infrastruktury, a adnotacje Springa są tylko w klasach `*Configuration` i w klasie startowej.
- **Use case'y da się testować bez kontenera.** `OnboardingCommandHandlerTest` używa repozytorium w pamięci i `UnitOfWork`,
  który tylko zapisuje, czy był rollback.

## Błędy to wartości, nie wyjątki (`Result<T, E>`)

Wynik każdej operacji, która może się nie udać, to `Result<T, E>`. Jest to `sealed interface` z dwoma rekordami (`Success`, `Failure`),
a błąd `E` to osobny `sealed interface` rekordów dla każdego kontekstu: `OnboardingError`, `ScreeningError`, `RestrictionError`.

```java
switch (commands.handle(command)) {
    case Result.Success(var customerId) -> ResponseEntity.ok(customerId);
    case Result.Failure(var error)      -> problemFor(error);   // switch po sealed OnboardingError: exhaustive
}
```

- **Domena i aplikacja nie rzucają wyjątków.** Value objecty walidują w fabrykach `of(...)` (`EmailAddress.of`, `Surname.of`, `CustomerId.of`...),
  komendy agregatu zwracają `Result`, a command handler i repozytorium też. Odrzucona komenda nie tworzy zdarzenia ani nie zmienia agregatu.
- **Silnik walidacji dla dowolnej liczby części.** Reguły ma każdy value object, a `Validation` (`domain/shared`) tylko składa wyniki:
  `check(...)` rejestruje część i zwraca typowany uchwyt, a `build(...)` tworzy całość dopiero wtedy, gdy przeszły wszystkie części.
  Inaczej zwraca wszystkie błędy naraz: pojedynczy bez zmian, kilka jako `InvalidInput`. HTTP odpowiada wtedy 400 z listą `problems`.
- **Dodanie nowego błędu przestaje się kompilować tam, gdzie nie jest obsłużony** (mapowanie na HTTP, na Kafkę). Kompilator pilnuje kompletności.
- **Przegrany wyścig o wersję strumienia** (`ConcurrentModification`) jest wartością. `JdbcEventStore` zamienia `DuplicateKeyException` w `Result.Failure`.
- **Porażka cofa transakcję.** Use case, który zwraca `Failure`, kończy się normalnie, a transakcja zakończona normalnie zostałaby zatwierdzona
  razem z tym, co zdążyło się zapisać (np. pierwszym z dwóch zdarzeń). Dlatego `UnitOfWork` cofa każdy `Failure` (`setRollbackOnly()`).
  Na H2 pilnuje tego `SaveFailureIntegrationTest`: bez tej linii zostaje w bazie połowa operacji.
  PostgreSQL po nieudanym `INSERT` sam unieważnia całą transakcję, więc w prawdziwym wyścigu na Postgresie (30 klientów, 6 równoległych
  zatwierdzeń każdy) wynik był taki sam z tą linią i bez niej. Linia sprawia, że poprawność nie zależy od tego, jak dana baza traktuje błąd w trakcie transakcji.
- **Wyjątki zostają tylko na granicy z frameworkami:**
  - awaria infrastruktury (baza, outbox) leci jako wyjątek i cofa transakcję: zdarzenia, projekcje i outbox schodzą razem,
  - listener Kafki musi rzucić wyjątek, żeby kontener ponowił albo odrzucił wiadomość: `RejectedMessageException` (od razu do DLT)
    i `RetryableMessageException` (retry z back-offem, potem DLT). Rzuca je `orElseThrow` w jednym miejscu, na brzegu listenera,
  - błędna konfiguracja (np. `app.screening.timeout`) przerywa start aplikacji.

## Gwarancje: spójność, idempotencja, kolejność

1. **Brak dual-write.** Zapis zdarzeń do `event_store` i wiadomości do `outbox` odbywa się w jednej lokalnej transakcji.
   Kafka dostaje wiadomość dopiero z outboxa (`OutboxRelay`).
2. **Kolejność w obrębie agregatu (event store).** Unikalny klucz `(stream_id, stream_version)` daje optimistic locking.
   Dwóch równoległych pisarzy nie dopisze tej samej wersji.
3. **Kolejność publikacji.**
   - `OutboxRelay` działa tylko na jednej replice naraz (**ShedLock**).
   - Wysyła wiadomości po kolei i synchronicznie. Przy pierwszym błędzie przerywa batch, więc późniejsza wiadomość nigdy nie wyprzedzi wcześniejszej.
4. **Kolejność w Kafce.**
   - Klucz wiadomości = `customerId`, więc wszystkie zdarzenia klienta trafiają do jednej partycji.
   - Producent ma `enable.idempotence=true` i `acks=all`. Retry nie zamienia więc kolejności, nawet przy 5 requestach in-flight.
5. **Kolejność konsumpcji.**
   - Partycję obsługuje jeden wątek, a commit offsetu następuje po każdym rekordzie (`ack-mode: record`).
   - Retry są **blokujące** (`DefaultErrorHandler`), po nich wiadomość trafia do DLT.
   - Non-blocking retry topics są celowo niewykorzystane, bo pozwoliłyby kolejnym zdarzeniom tego samego klienta wyprzedzić błędne.
   - Błędy nienaprawialne (nieczytelna wiadomość, odmowa domeny: `RejectedMessageException`) idą od razu do DLT.
     Przegrany wyścig o wersję (`RetryableMessageException`) jest ponawiany.
6. **At-least-once + idempotencja.**
   - Każda wiadomość ma `eventId`. Inbox (`processed_message`) i efekt biznesowy commitują się atomowo.
   - Dodatkowo agregaty są idempotentne na poziomie domeny: drugi ban nic nie zmienia, drugi screening się nie otwiera.
7. **Brak kolejności między topicami.** `ClientCleared` (topic screeningu) i `ClientBanned` (topic restriction) mogą dotrzeć do onboardingu w dowolnej kolejności, np. przy ręcznym banie w trakcie screeningu.
   Kafka tego nie uporządkuje, więc rozstrzyga to maszyna stanów agregatu: `BANNED` jest stanem końcowym, a spóźnione `ClientCleared` jest ignorowane.
   Wynik nie zależy od kolejności dostarczenia (test `banWinsWhenClearanceArrivesAfterIt`).
8. Koperta niesie `aggregateVersion`, więc konsument może wykrywać luki lub reordering, gdy kontrakty zaczną mieć wiele zdarzeń na klienta.

## Uruchomienie

```bash
mvn -q package -DskipTests        # 3 niezależne jary (root pom to tylko agregator buildu, nie parent)
docker compose up -d --build      # Kafka (KRaft) + 3x Postgres + 3 serwisy
./scripts/e2e.sh                  # pełny scenariusz: blacklist -> onboarding -> BANNED / ONBOARDED
./scripts/e2e-screening-timeout.sh # awaria screeningu -> timeout -> MANUAL_REVIEW -> akceptacja -> spóźniony werdykt ignorowany
```

Testy (bez Dockera: H2 w trybie PostgreSQL + `@EmbeddedKafka`):

```bash
mvn test
```

### API
| Serwis | Endpoint |
|---|---|
| onboarding :8081 | `POST /api/onboardings` `{firstName,lastName,email,dateOfBirth}` · `GET /api/onboardings/{customerId}` |
| onboarding :8081 | `GET /api/manual-reviews` · `POST /api/manual-reviews/{customerId}/approve` `{reviewer,comment}` · `POST /api/manual-reviews/{customerId}/reject` `{reviewer,reason}` |
| screening :8082 | `POST /api/blacklist` `{lastName,reason}` · `GET /api/blacklist` · `DELETE /api/blacklist/{lastName}` · `GET /api/screenings/{customerId}` |
| restriction :8083 | `GET /api/restrictions/{customerId}` · `POST /api/restrictions/{customerId}/ban` `{reason}` |

## Świadome uproszczenia i dalsze kroki
- **Reviewer to pole w żądaniu.** Produkcyjnie powinien pochodzić z uwierzytelnienia (np. JWT), a endpointy `/api/manual-reviews` powinny wymagać roli compliance.
- **Brak powiadomień o eskalacji.** Można dodać publiczne zdarzenie `OnboardingEscalated.v1` dla serwisu powiadomień albo alertów.
- **Dopasowanie po samym nazwisku**, zgodnie z wymaganiem. Produkcyjnie potrzebne są imię, data urodzenia i fuzzy matching.
- **Read side** buduje się przez replay strumienia, co przy krótkich strumieniach w zupełności wystarcza.
  Następny krok to projekcje i snapshoty.
- **Ponowny screening** po dopisaniu nazwiska do listy (`BlacklistEntryAdded` → rescreen istniejących klientów).
- **Contract testing** (np. Pact lub JSON Schema w registry) zamiast ręcznie zduplikowanych rekordów kontraktu.
