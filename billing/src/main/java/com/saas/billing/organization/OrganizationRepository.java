package com.saas.billing.organization;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationRepository
        extends JpaRepository<Organization, UUID> {

    Optional<Organization> findByEmail(String email);

    boolean existsByEmail(String email);
}