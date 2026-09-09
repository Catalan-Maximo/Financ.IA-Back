package com.FinancIA.api.repository;

import com.FinancIA.api.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /** Búsqueda sin distinguir mayúsculas/minúsculas (emails). */
    Optional<Usuario> findByEmailIgnoreCase(String email);
}
