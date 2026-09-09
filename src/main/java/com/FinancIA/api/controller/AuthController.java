package com.FinancIA.api.controller;

import com.FinancIA.api.domain.Usuario;
import com.FinancIA.api.dto.AuthRequestDTO;
import com.FinancIA.api.dto.AuthResponseDTO;
import com.FinancIA.api.dto.RegisterRequestDTO;
import com.FinancIA.api.repository.UsuarioRepository;
import com.FinancIA.api.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de autenticación (públicos).
 *
 * POST /api/v1/auth/register → crea usuario (password BCrypt) y devuelve JWT.
 * POST /api/v1/auth/login    → valida credenciales y devuelve JWT.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(UsuarioRepository usuarioRepository,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public AuthResponseDTO registrar(@RequestBody RegisterRequestDTO request) {
        // Normalizamos el email a minúsculas para evitar duplicados por case
        String email = normalizarEmail(request.getEmail());

        if (usuarioRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new IllegalArgumentException("El email ya está registrado");
        }

        Usuario usuario = new Usuario();
        usuario.setNombre(request.getNombre());
        usuario.setEmail(email);
        usuario.setPassword(passwordEncoder.encode(request.getPassword()));
        usuarioRepository.save(usuario);

        return new AuthResponseDTO(jwtService.generarToken(usuario.getEmail()),
                usuario.getEmail(), usuario.getPerfilInversor());
    }

    @PostMapping("/login")
    public AuthResponseDTO login(@RequestBody AuthRequestDTO request) {
        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(normalizarEmail(request.getEmail()))
                .orElseThrow(() -> new IllegalArgumentException("Credenciales inválidas"));

        if (!passwordEncoder.matches(request.getPassword(), usuario.getPassword())) {
            throw new IllegalArgumentException("Credenciales inválidas");
        }

        return new AuthResponseDTO(jwtService.generarToken(usuario.getEmail()),
                usuario.getEmail(), usuario.getPerfilInversor());
    }

    /** Trim + minúsculas: "Maxi@Mail.com" → "maxi@mail.com". */
    private String normalizarEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
