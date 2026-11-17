package com.gs5.screening.application;

import com.gs5.screening.domain.Blacklist;
import com.gs5.screening.domain.BlacklistEntry;
import com.gs5.screening.domain.ScreeningError;
import com.gs5.screening.domain.Surname;
import com.gs5.screening.domain.shared.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BlacklistManagement {

    private final Blacklist blacklist;

    public BlacklistManagement(Blacklist blacklist) {
        this.blacklist = blacklist;
    }

    @Transactional
    public Result<BlacklistEntry, ScreeningError> add(String surname, String reason) {
        return BlacklistEntry.of(surname, reason).map(entry -> {
            blacklist.add(entry);
            return entry;
        });
    }

    @Transactional
    public Result<Surname, ScreeningError> remove(String surname) {
        return Surname.of(surname).map(parsed -> {
            blacklist.remove(parsed);
            return parsed;
        });
    }

    @Transactional(readOnly = true)
    public List<BlacklistEntry> entries() {
        return blacklist.entries();
    }
}
