package com.example.screening.application;

import com.example.screening.domain.Blacklist;
import com.example.screening.domain.BlacklistEntry;
import com.example.screening.domain.Surname;
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
    public BlacklistEntry add(String surname, String reason) {
        var entry = new BlacklistEntry(new Surname(surname), reason);
        blacklist.add(entry);
        return entry;
    }

    @Transactional
    public void remove(String surname) {
        blacklist.remove(new Surname(surname));
    }

    @Transactional(readOnly = true)
    public List<BlacklistEntry> entries() {
        return blacklist.entries();
    }
}
