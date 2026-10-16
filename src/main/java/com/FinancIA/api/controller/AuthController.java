package com.FinancIA.api.controller;

import jakarta.validation.Valid;
import com.FinancIA.api.domain.User;
import com.FinancIA.api.dto.AuthRequestDTO;
import com.FinancIA.api.dto.AuthResponseDTO;
import com.FinancIA.api.dto.RegisterRequestDTO;
import com.FinancIA.api.repository.UserRepository;
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

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public AuthResponseDTO register(@RequestBody @Valid RegisterRequestDTO request) {
        // Normalizamos el email a minúsculas para evitar duplicados por case
        String email = normalizeEmail(request.getEmail());

        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new IllegalArgumentException("El email ya está registrado");
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);

        return new AuthResponseDTO(jwtService.generateToken(user.getEmail()),
                user.getEmail(), user.getInvestorProfile());
    }

    @PostMapping("/login")
    public AuthResponseDTO login(@RequestBody @Valid AuthRequestDTO request) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.getEmail()))
                .orElseThrow(() -> new IllegalArgumentException("Credenciales inválidas"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Credenciales inválidas");
        }

        return new AuthResponseDTO(jwtService.generateToken(user.getEmail()),
                user.getEmail(), user.getInvestorProfile());
    }

    /** Trim + minúsculas: "Maxi@Mail.com" → "maxi@mail.com". */
    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
