package com.pedisur.backend.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.pedisur.backend.auth.entity.RefreshToken;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	@Modifying(clearAutomatically = true)
	@Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.staffUser.id = :staffUserId AND r.revoked = false")
	int revokeAllByStaffUserId(@Param("staffUserId") Integer staffUserId);
}
