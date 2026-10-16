package com.FinancIA.api.repository;

import com.FinancIA.api.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Búsqueda sin distinguir mayúsculas/minúsculas (emails). */
    Optional<User> findByEmailIgnoreCase(String email);

    /** Usuarios que habilitaron notificaciones push. */
    List<User> findAllByPushTokenNotNull();
}
