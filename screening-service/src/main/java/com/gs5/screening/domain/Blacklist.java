package com.gs5.screening.domain;

import java.util.List;
import java.util.Optional;

/**
 * Port: the blacklist of surnames. It is reference data maintained by compliance officers, not a process with
 * its own lifecycle, so it is deliberately <b>not</b> event sourced - the screening decisions made against it are.
 */
public interface Blacklist {

    Optional<BlacklistEntry> findEntryMatching(Surname surname);

    void add(BlacklistEntry entry);

    void remove(Surname surname);

    List<BlacklistEntry> entries();
}
