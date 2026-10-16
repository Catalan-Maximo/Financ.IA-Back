package com.FinancIA.api.controller;

import jakarta.validation.Valid;
import com.FinancIA.api.domain.User;
import com.FinancIA.api.dto.ProfileRequestDTO;
import com.FinancIA.api.exception.ResourceNotFoundException;
import com.FinancIA.api.repository.UserRepository;
import com.FinancIA.api.service.InvestorProfileService;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository userRepository;
    private final InvestorProfileService investorProfileService;

    public UserController(UserRepository userRepository,
                             InvestorProfileService investorProfileService) {
        this.userRepository = userRepository;
        this.investorProfileService = investorProfileService;
    }

    /**
     * POST /api/v1/usuarios/perfil
     *
     * Recibe la suma de puntos del test (8 a 49) y actualiza el perfil
     * del usuario autenticado (identidad tomada del JWT). Ya no se crea
     * un usuario nuevo con datos fijos.
     *
     * Ejemplo de body:
     * { "puntaje": 28 }
     */
    @PostMapping("/profile")
    public User saveProfile(@RequestBody @Valid ProfileRequestDTO request) {
        // El endpoint está protegido por Spring Security: el email viene del JWT
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = (authentication != null) ? authentication.getName() : null;

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado no encontrado"));

        user.setScore(request.getScore());
        user.setInvestorProfile(investorProfileService.classify(request.getScore()));
        return userRepository.save(user);
    }

    /**
     * POST /api/v1/usuarios/push-token
     *
     * Guarda el Expo push token del dispositivo en el usuario autenticado,
     * para recibir señales cripto cuando el backend detecta cambios.
     *
     * Ejemplo de body: { "token": "ExponentPushToken[xxxxxxxx]" }
     */
    @PostMapping("/push-token")
    public Map<String, String> savePushToken(@RequestBody Map<String, String> request) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication != null ? authentication.getName() : null;

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado no encontrado"));

        user.setPushToken(request.get("token"));
        userRepository.save(user);
        return Map.of("status", "ok");
    }
}
