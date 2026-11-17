package com.example.restriction.domain;

public interface ClientRestrictionRepository {

    /** Never empty: a client without history is simply unrestricted. */
    ClientRestriction findById(CustomerId customerId);

    void save(ClientRestriction restriction);
}
