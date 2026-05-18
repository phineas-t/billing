package com.saas.billing.organization;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    //used during login to look up the user
    Optional<User> findByEmail(String email);

    //used during registration to check if the email is already taken
    boolean existsByEmail(String email);
}