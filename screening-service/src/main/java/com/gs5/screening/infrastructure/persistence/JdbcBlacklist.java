package com.gs5.screening.infrastructure.persistence;

import com.gs5.screening.domain.Blacklist;
import com.gs5.screening.domain.BlacklistEntry;
import com.gs5.screening.domain.Surname;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** Lookup is done by the normalized surname computed in the domain, so the matching rule lives in one place. */
@Repository
class JdbcBlacklist implements Blacklist {

    private final JdbcClient jdbc;
    private final Clock clock;

    JdbcBlacklist(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public Optional<BlacklistEntry> findEntryMatching(Surname surname) {
        return jdbc.sql("SELECT surname, reason FROM blacklist_entry WHERE normalized_surname = :normalized")
                .param("normalized", surname.normalized())
                .query((rs, rowNum) -> new BlacklistEntry(new Surname(rs.getString("surname")), rs.getString("reason")))
                .optional();
    }

    @Override
    public void add(BlacklistEntry entry) {
        remove(entry.surname());
        jdbc.sql("""
                        INSERT INTO blacklist_entry (normalized_surname, surname, reason, added_at)
                        VALUES (:normalized, :surname, :reason, :addedAt)
                        """)
                .param("normalized", entry.surname().normalized())
                .param("surname", entry.surname().value())
                .param("reason", entry.reason())
                .param("addedAt", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                .update();
    }

    @Override
    public void remove(Surname surname) {
        jdbc.sql("DELETE FROM blacklist_entry WHERE normalized_surname = :normalized")
                .param("normalized", surname.normalized())
                .update();
    }

    @Override
    public List<BlacklistEntry> entries() {
        return jdbc.sql("SELECT surname, reason FROM blacklist_entry ORDER BY normalized_surname")
                .query((rs, rowNum) -> new BlacklistEntry(new Surname(rs.getString("surname")), rs.getString("reason")))
                .list();
    }
}
