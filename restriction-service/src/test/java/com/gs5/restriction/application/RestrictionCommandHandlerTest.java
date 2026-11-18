package com.gs5.restriction.application;

import com.gs5.restriction.domain.ClientRestriction;
import com.gs5.restriction.domain.ClientRestrictionRepository;
import com.gs5.restriction.domain.CustomerId;
import com.gs5.restriction.domain.RestrictionError;
import com.gs5.restriction.domain.RestrictionError.ConcurrentModification;
import com.gs5.restriction.domain.RestrictionError.InvalidValue;
import com.gs5.restriction.domain.RestrictionEvent;
import com.gs5.restriction.domain.RestrictionSource;
import com.gs5.restriction.domain.shared.Result;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/** The use case runs without Spring, without a database and without Kafka - plain objects and two fakes. */
class RestrictionCommandHandlerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC);

    private final InMemoryRestrictions restrictions = new InMemoryRestrictions();
    private final RecordingUnitOfWork unitOfWork = new RecordingUnitOfWork();
    private final RestrictionCommandHandler commands = new RestrictionCommandHandler(restrictions, unitOfWork, CLOCK);

    @Test
    void bansAClientInOneUnitOfWork() {
        CustomerId id = CustomerId.newId();

        valueOf(commands.handle(new BanClientCommand(id, "Fraud", RestrictionSource.MANUAL)));

        assertThat(valueOf(restrictions.load(id)).isBanned()).isTrue();
        assertThat(unitOfWork.committed).isEqualTo(1);
    }

    @Test
    void banWithoutReasonIsRefusedAndNothingIsSaved() {
        Result<CustomerId, RestrictionError> result =
                commands.handle(new BanClientCommand(CustomerId.newId(), " ", RestrictionSource.MANUAL));

        assertThat(failureOf(result)).isInstanceOf(InvalidValue.class);
        assertThat(restrictions.saves).isZero();
        assertThat(unitOfWork.rolledBack).isEqualTo(1);
    }

    @Test
    void lostRaceOnSaveIsAFailureAndIsRolledBack() {
        CustomerId id = CustomerId.newId();
        restrictions.conflictOnNextSave = true;

        Result<CustomerId, RestrictionError> result =
                commands.handle(new BanClientCommand(id, "Fraud", RestrictionSource.SANCTIONS_SCREENING));

        assertThat(failureOf(result)).isEqualTo(new ConcurrentModification(id, 0));
        assertThat(valueOf(restrictions.load(id)).isBanned()).isFalse();
        assertThat(unitOfWork.rolledBack).isEqualTo(1);
    }

    private static <T> T valueOf(Result<T, RestrictionError> result) {
        return switch (result) {
            case Result.Success<T, RestrictionError>(var value) -> value;
            case Result.Failure<T, RestrictionError>(var error) -> fail("expected a value but got " + error);
        };
    }

    private static <T> RestrictionError failureOf(Result<T, RestrictionError> result) {
        return switch (result) {
            case Result.Failure<T, RestrictionError>(var error) -> error;
            case Result.Success<T, RestrictionError>(var value) -> fail("expected a failure but got " + value);
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

    private static final class InMemoryRestrictions implements ClientRestrictionRepository {

        private final Map<CustomerId, List<RestrictionEvent>> streams = new HashMap<>();
        private boolean conflictOnNextSave;
        private int saves;

        @Override
        public Result<ClientRestriction, RestrictionError> load(CustomerId customerId) {
            return Result.success(ClientRestriction.rehydrate(customerId, streams.getOrDefault(customerId, List.of())));
        }

        @Override
        public Result<ClientRestriction, RestrictionError> save(ClientRestriction restriction) {
            if (conflictOnNextSave) {
                conflictOnNextSave = false;
                return Result.failure(new ConcurrentModification(restriction.customerId(), restriction.version()));
            }
            streams.computeIfAbsent(restriction.customerId(), _ -> new ArrayList<>())
                    .addAll(restriction.uncommittedEvents());
            restriction.markEventsAsCommitted();
            saves++;
            return Result.success(restriction);
        }
    }
}
