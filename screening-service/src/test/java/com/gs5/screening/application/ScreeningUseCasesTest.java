package com.gs5.screening.application;

import com.gs5.screening.domain.Blacklist;
import com.gs5.screening.domain.BlacklistEntry;
import com.gs5.screening.domain.CustomerId;
import com.gs5.screening.domain.ScreeningCase;
import com.gs5.screening.domain.ScreeningCaseRepository;
import com.gs5.screening.domain.ScreeningError;
import com.gs5.screening.domain.ScreeningError.ConcurrentModification;
import com.gs5.screening.domain.ScreeningError.InvalidValue;
import com.gs5.screening.domain.ScreeningError.ScreeningNotFound;
import com.gs5.screening.domain.ScreeningEvent;
import com.gs5.screening.domain.ScreeningStatus;
import com.gs5.screening.domain.Surname;
import com.gs5.screening.domain.shared.Result;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/** The use cases run without Spring, without a database and without Kafka - plain objects and fakes. */
class ScreeningUseCasesTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC);

    private final InMemoryScreenings screenings = new InMemoryScreenings();
    private final InMemoryBlacklist blacklist = new InMemoryBlacklist();
    private final RecordingUnitOfWork unitOfWork = new RecordingUnitOfWork();
    private final ScreeningCommandHandler commands = new ScreeningCommandHandler(screenings, blacklist, unitOfWork, CLOCK);
    private final BlacklistManagement blacklistManagement = new BlacklistManagement(blacklist, unitOfWork);

    @Test
    void screensACustomerOnceInOneUnitOfWork() {
        CustomerId id = CustomerId.newId();

        valueOf(commands.handle(new ScreenCustomerCommand(id, "Jan", "Nowak")));
        valueOf(commands.handle(new ScreenCustomerCommand(id, "Jan", "Nowak")));

        assertThat(statusOf(id)).isEqualTo(ScreeningStatus.CLEARED);
        assertThat(screenings.saves).as("a redelivered request does not open a second case").isEqualTo(1);
        assertThat(unitOfWork.committed).isEqualTo(2);
    }

    @Test
    void restrictsACustomerWhoseSurnameIsBlacklisted() {
        valueOf(blacklistManagement.add("Michałowski", "Sanctions list EU-2026/17"));
        CustomerId id = CustomerId.newId();

        valueOf(commands.handle(new ScreenCustomerCommand(id, "Adam", "MICHALOWSKI")));

        assertThat(statusOf(id)).isEqualTo(ScreeningStatus.RESTRICTED);
    }

    @Test
    void invalidPersonIsRefusedAndNothingIsSaved() {
        Result<CustomerId, ScreeningError> result =
                commands.handle(new ScreenCustomerCommand(CustomerId.newId(), " ", "Nowak"));

        assertThat(failureOf(result)).isInstanceOf(InvalidValue.class);
        assertThat(screenings.saves).isZero();
        assertThat(unitOfWork.rolledBack).isEqualTo(1);
    }

    @Test
    void lostRaceOnSaveIsAFailureAndIsRolledBack() {
        CustomerId id = CustomerId.newId();
        screenings.conflictOnNextSave = true;

        Result<CustomerId, ScreeningError> result = commands.handle(new ScreenCustomerCommand(id, "Jan", "Nowak"));

        assertThat(failureOf(result)).isEqualTo(new ConcurrentModification(id, 0));
        assertThat(unitOfWork.rolledBack).isEqualTo(1);
    }

    @Test
    void invalidBlacklistEntryIsRefusedAndRolledBack() {
        assertThat(failureOf(blacklistManagement.add("Kowalski", " "))).isInstanceOf(InvalidValue.class);

        assertThat(blacklistManagement.entries()).isEmpty();
        assertThat(unitOfWork.rolledBack).isEqualTo(1);
    }

    private ScreeningStatus statusOf(CustomerId id) {
        return valueOf(screenings.load(id)).status();
    }

    private static <T> T valueOf(Result<T, ScreeningError> result) {
        return switch (result) {
            case Result.Success<T, ScreeningError>(var value) -> value;
            case Result.Failure<T, ScreeningError>(var error) -> fail("expected a value but got " + error);
        };
    }

    private static <T> ScreeningError failureOf(Result<T, ScreeningError> result) {
        return switch (result) {
            case Result.Failure<T, ScreeningError>(var error) -> error;
            case Result.Success<T, ScreeningError>(var value) -> fail("expected a failure but got " + value);
        };
    }

    /** The contract of the real unit of work without a database: a failure is rolled back, a success committed. */
    private static final class RecordingUnitOfWork implements UnitOfWork {

        private int committed;
        private int rolledBack;

        @Override
        public <T, E> Result<T, E> inTransaction(Supplier<Result<T, E>> work) {
            Result<T, E> result = work.get();
            if (result instanceof Result.Failure<T, E>) {
                rolledBack++;
            } else {
                committed++;
            }
            return result;
        }

        @Override
        public <T> T readOnly(Supplier<T> query) {
            return query.get();
        }
    }

    private static final class InMemoryScreenings implements ScreeningCaseRepository {

        private final Map<CustomerId, List<ScreeningEvent>> streams = new HashMap<>();
        private boolean conflictOnNextSave;
        private int saves;

        @Override
        public Result<ScreeningCase, ScreeningError> load(CustomerId customerId) {
            return Result.of(ScreeningCase.rehydrate(streams.getOrDefault(customerId, List.of())),
                    () -> new ScreeningNotFound(customerId));
        }

        @Override
        public boolean exists(CustomerId customerId) {
            return streams.containsKey(customerId);
        }

        @Override
        public Result<ScreeningCase, ScreeningError> save(ScreeningCase screeningCase) {
            if (conflictOnNextSave) {
                conflictOnNextSave = false;
                return Result.failure(new ConcurrentModification(screeningCase.customerId(), screeningCase.version()));
            }
            streams.computeIfAbsent(screeningCase.customerId(), _ -> new ArrayList<>())
                    .addAll(screeningCase.uncommittedEvents());
            screeningCase.markEventsAsCommitted();
            saves++;
            return Result.success(screeningCase);
        }
    }

    private static final class InMemoryBlacklist implements Blacklist {

        private final List<BlacklistEntry> entries = new ArrayList<>();

        @Override
        public Optional<BlacklistEntry> findEntryMatching(Surname surname) {
            return entries.stream().filter(entry -> entry.surname().matches(surname)).findFirst();
        }

        @Override
        public void add(BlacklistEntry entry) {
            entries.add(entry);
        }

        @Override
        public void remove(Surname surname) {
            entries.removeIf(entry -> entry.surname().matches(surname));
        }

        @Override
        public List<BlacklistEntry> entries() {
            return List.copyOf(entries);
        }
    }
}
