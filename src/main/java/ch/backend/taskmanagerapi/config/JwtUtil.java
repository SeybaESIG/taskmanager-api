package ch.backend.taskmanagerapi.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

/**
 * Utility class responsible for generating and validating JWT tokens.
 * This implementation uses a symmetric secret key with the HS256 algorithm.
 */
@Component
public class JwtUtil {

    // Keep secrets outside code in production (env vars or secure vault).
    private static final String SECRET = "change-this-secret-key-to-a-long-random-value-32chars-minimum";
    private static final long EXPIRATION_MILLIS = 1000 * 60 * 60; // 1 hour

    private final Key signingKey;

    public JwtUtil() {
        this.signingKey = Keys.hmacShaKeyFor(SECRET.getBytes());
    }

    // Issues a signed token for a subject and sets standard iat/exp claims.
    public String generateToken(String subject) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + EXPIRATION_MILLIS);

        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    // Reads the token subject (username/email) after signature verification.
    public String extractSubject(String token) {
        return getClaims(token).getSubject();
    }

    // Accepts a token only if subject matches and expiration has not passed.
    public boolean isTokenValid(String token, String subject) {
        String tokenSubject = extractSubject(token);
        return tokenSubject.equals(subject) && !isTokenExpired(token);
    }

    // Expiration check based on the exp claim.
    private boolean isTokenExpired(String token) {
        Date expiration = getClaims(token).getExpiration();
        return expiration.before(new Date());
    }

    // Centralized parsing ensures all claim reads verify signature with the same key.
    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
