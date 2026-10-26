package es.sergy.servicelab.database;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Persistencia del perfil de la cuenta que ha completado el login. */
@Repository
public class MusicDatabase {
    private final JdbcTemplate jdbc;

    public MusicDatabase(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void createUsersTable() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    spotify_id TEXT PRIMARY KEY NOT NULL,
                    display_name TEXT,
                    updated_at TEXT NOT NULL
                )
                """);
    }

    public void saveUser(String spotifyId, String displayName) {
        jdbc.update("""
                INSERT INTO users (spotify_id, display_name, updated_at)
                VALUES (?, ?, ?)
                ON CONFLICT(spotify_id) DO UPDATE SET
                    display_name = excluded.display_name,
                    updated_at = excluded.updated_at
                """, spotifyId, displayName, java.time.Instant.now().toString());
    }
}
