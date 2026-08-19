package com.FinancIA.api.controller;

import com.FinancIA.api.domain.Usuario;
import com.FinancIA.api.dto.PerfilRequestDTO;
import com.FinancIA.api.repository.UsuarioRepository;
import com.FinancIA.api.service.PerfilInversorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/usuarios")
public class UsuarioController {

    private final UsuarioRepository usuarioRepository;
    private final PerfilInversorService perfilInversorService;

    public UsuarioController(UsuarioRepository usuarioRepository,
                             PerfilInversorService perfilInversorService) {
        this.usuarioRepository = usuarioRepository;
        this.perfilInversorService = perfilInversorService;
    }

    /**
     * POST /api/v1/usuarios/perfil
     *
     * Recibe la suma de puntos del test (8 a 49), clasifica el perfil
     * con {@link PerfilInversorService} y persiste el usuario.
     *
     * Ejemplo de body:
     * { "nombre": "Usuario Demo", "email": "demo@financia.com", "puntaje": 28 }
     */
    @PostMapping("/perfil")
    public Usuario guardarPerfil(@RequestBody PerfilRequestDTO request) {
        Usuario usuario = new Usuario();
        usuario.setNombre(request.getNombre());
        usuario.setEmail(request.getEmail());
        usuario.setPuntaje(request.getPuntaje());
        usuario.setPerfilInversor(perfilInversorService.clasificar(request.getPuntaje()));
        return usuarioRepository.save(usuario);
    }

    @GetMapping("/{id}")
    public Usuario obtenerUsuario(@PathVariable Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }
}
