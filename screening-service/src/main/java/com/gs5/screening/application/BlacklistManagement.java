package com.gs5.screening.application;

import com.gs5.screening.domain.Blacklist;
import com.gs5.screening.domain.BlacklistEntry;
import com.gs5.screening.domain.ScreeningError;
import com.gs5.screening.domain.Surname;
import com.gs5.screening.domain.shared.Result;

import java.util.List;

/** Use cases of the compliance officers who maintain the blacklist (reference data, deliberately not event sourced). */
public class BlacklistManagement {

    private final Blacklist blacklist;
    private final UnitOfWork unitOfWork;

    public BlacklistManagement(Blacklist blacklist, UnitOfWork unitOfWork) {
        this.blacklist = blacklist;
        this.unitOfWork = unitOfWork;
    }

    public Result<BlacklistEntry, ScreeningError> add(String surname, String reason) {
        return unitOfWork.inTransaction(() -> BlacklistEntry.of(surname, reason).map(entry -> {
            blacklist.add(entry);
            return entry;
        }));
    }

    public Result<Surname, ScreeningError> remove(String surname) {
        return unitOfWork.inTransaction(() -> Surname.of(surname).map(parsed -> {
            blacklist.remove(parsed);
            return parsed;
        }));
    }

    public List<BlacklistEntry> entries() {
        return unitOfWork.readOnly(blacklist::entries);
    }
}
