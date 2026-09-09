package com.FinancIA.api.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Genera y valida tokens JWT.
 *
 * La clave secreta se configura con la variable de entorno JWT_SECRET
 * (mínimo 32 caracteres — HMAC-SHA256). Nunca se hardcodea.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expiracionMs;

    public JwtService(@Value("${jwt.secret:}") String secret,
                      @Value("${jwt.expiracion-horas:24}") long horas) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET no configurada o muy corta: definir una clave de al menos 32 caracteres");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiracionMs = horas * 3_600_000L;
    }

    /** Genera un token firmado con el email como subject. */
    public String generarToken(String email) {
        Date ahora = new Date();
        return Jwts.builder()
                .subject(email)
                .issuedAt(ahora)
                .expiration(new Date(ahora.getTime() + expiracionMs))
                .signWith(key)
                .compact();
    }

    /**
     * Extrae el email (subject) de un token.
     * @throws io.jsonwebtoken.JwtException si el token es inválido o expiró
     */
    public String extraerEmail(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
}
