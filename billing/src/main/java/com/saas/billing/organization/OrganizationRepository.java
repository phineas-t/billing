package com.saas.billing.organization;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationRepository
        extends JpaRepository<Organization, UUID> {
    //JpaRepository<Organization, UUID> — the first type parameter is the entity, the second is the type of its primary key. This gives you save(), findById(), findAll(), deleteById() and more for free.
    //findByEmail(String email) — you just declare this method. Spring reads the name findBy + Email and generates the SQL  automatically. This is called a derived query — you never write the SQL
    //Optional<Organization> — wraps the result so you never get a null pointer. You call .isPresent() to check if something was found, or .orElseThrow() to throw an exception if not.
    Optional<Organization> findByEmail(String email);

    boolean existsByEmail(String email);
}