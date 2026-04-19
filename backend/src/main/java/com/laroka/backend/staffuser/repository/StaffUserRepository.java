package com.laroka.backend.staffuser.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.laroka.backend.staffuser.entity.StaffUser;

@Repository
public interface StaffUserRepository extends JpaRepository<StaffUser, Integer> {
	Optional<StaffUser> findByEmail(String email);
}
