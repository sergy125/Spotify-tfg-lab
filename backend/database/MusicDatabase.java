package database;

import models.Event;
import models.Song;
import models.User;

import java.nio.file.Path;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

/** SQLite access. Requires the org.xerial SQLite JDBC driver at runtime. */
public class MusicDatabase {
    private final String url;

    /** Supports running from either the repository root or backend. */
    public MusicDatabase() {
        this(Files.isDirectory(Path.of("backend", "database"))
                ? Path.of("backend", "database", "lab")
                : Path.of("database", "lab"));
    }

    public MusicDatabase(Path databasePath) {
        url = "jdbc:sqlite:" + databasePath.toAbsolutePath();
    }

    private Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection(url);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }
        return connection;
    }

    public void deleteAllTables() throws SQLException {
        execute("DROP TABLE IF EXISTS Historial");
        execute("DROP TABLE IF EXISTS top_short");
        execute("DROP TABLE IF EXISTS top_medium");
        execute("DROP TABLE IF EXISTS top_long");
        execute("DROP TABLE IF EXISTS Songs");
    }

    public void createAllTables() throws SQLException {
        createSongsTable();
        createHistorialTable();
        createTopShortTable();
        createTopMediumTable();
        createTopLongTable();
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    public void createSongsTable() throws SQLException {
        execute("CREATE TABLE IF NOT EXISTS Songs (\"id\" TEXT PRIMARY KEY NOT NULL, \"nombre\" TEXT, \"artistas\" TEXT, \"album\" TEXT, \"duracionMs\" INTEGER, \"popularidad\" INTEGER, \"href\" TEXT, \"isrc\" TEXT, \"acousticness\" REAL, \"danceability\" REAL, \"energy\" REAL, \"instrumentalness\" REAL, \"key\" INTEGER, \"liveness\" REAL, \"loudness\" REAL, \"mode\" INTEGER, \"speechiness\" REAL, \"tempo\" REAL, \"valence\" REAL)");
    }

    public void createHistorialTable() throws SQLException {
        execute("CREATE TABLE IF NOT EXISTS Historial (user_id TEXT NOT NULL, song_id TEXT NOT NULL REFERENCES Songs(id), played_at TEXT NOT NULL, PRIMARY KEY (user_id, song_id, played_at))");
    }

    public void createTopShortTable() throws SQLException {
        execute("CREATE TABLE IF NOT EXISTS top_short (user_id TEXT NOT NULL, position INTEGER NOT NULL CHECK (position > 0), song_id TEXT NOT NULL REFERENCES Songs(id), PRIMARY KEY (user_id, position))");
    }

    public void createTopMediumTable() throws SQLException {
        execute("CREATE TABLE IF NOT EXISTS top_medium (user_id TEXT NOT NULL, position INTEGER NOT NULL CHECK (position > 0), song_id TEXT NOT NULL REFERENCES Songs(id), PRIMARY KEY (user_id, position))");
    }

    public void createTopLongTable() throws SQLException {
        execute("CREATE TABLE IF NOT EXISTS top_long (user_id TEXT NOT NULL, position INTEGER NOT NULL CHECK (position > 0), song_id TEXT NOT NULL REFERENCES Songs(id), PRIMARY KEY (user_id, position))");
    }

    public void createSong(Song song) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO Songs (\"id\", \"nombre\", \"artistas\", \"album\", \"duracionMs\", \"popularidad\", \"href\", \"isrc\", \"acousticness\", \"danceability\", \"energy\", \"instrumentalness\", \"key\", \"liveness\", \"loudness\", \"mode\", \"speechiness\", \"tempo\", \"valence\") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setObject(1, song.getId());
            statement.setObject(2, song.getNombre());
            statement.setObject(3, song.getArtistas());
            statement.setObject(4, song.getAlbum());
            statement.setObject(5, song.getDuracionMs());
            statement.setObject(6, song.getPopularidad());
            statement.setObject(7, song.getHref());
            statement.setObject(8, song.getIsrc());
            statement.setObject(9, song.getAcousticness());
            statement.setObject(10, song.getDanceability());
            statement.setObject(11, song.getEnergy());
            statement.setObject(12, song.getInstrumentalness());
            statement.setObject(13, song.getKey());
            statement.setObject(14, song.getLiveness());
            statement.setObject(15, song.getLoudness());
            statement.setObject(16, song.getMode());
            statement.setObject(17, song.getSpeechiness());
            statement.setObject(18, song.getTempo());
            statement.setObject(19, song.getValence());
            statement.executeUpdate();
        }
    }

    public Optional<Song> getSong(String songId) throws SQLException {
        try (Connection connection = connect()) {
            return findSong(connection, songId);
        }
    }

    private Optional<Song> findSong(Connection connection, String songId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM Songs WHERE id = ?")) {
            statement.setString(1, songId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                Song song = new Song();
                song.setId(result.getString("id"));
                song.setNombre(result.getString("nombre"));
                song.setArtistas(result.getString("artistas"));
                song.setAlbum(result.getString("album"));
                song.setDuracionMs(result.getObject("duracionMs") == null ? null : result.getInt("duracionMs"));
                song.setPopularidad(result.getObject("popularidad") == null ? null : result.getInt("popularidad"));
                song.setHref(result.getString("href"));
                song.setIsrc(result.getString("isrc"));
                song.setAcousticness(result.getObject("acousticness") == null ? null : result.getDouble("acousticness"));
                song.setDanceability(result.getObject("danceability") == null ? null : result.getDouble("danceability"));
                song.setEnergy(result.getObject("energy") == null ? null : result.getDouble("energy"));
                song.setInstrumentalness(result.getObject("instrumentalness") == null ? null : result.getDouble("instrumentalness"));
                song.setKey(result.getObject("key") == null ? null : result.getInt("key"));
                song.setLiveness(result.getObject("liveness") == null ? null : result.getDouble("liveness"));
                song.setLoudness(result.getObject("loudness") == null ? null : result.getDouble("loudness"));
                song.setMode(result.getObject("mode") == null ? null : result.getInt("mode"));
                song.setSpeechiness(result.getObject("speechiness") == null ? null : result.getDouble("speechiness"));
                song.setTempo(result.getObject("tempo") == null ? null : result.getDouble("tempo"));
                song.setValence(result.getObject("valence") == null ? null : result.getDouble("valence"));
                return Optional.of(song);
            }
        }
    }

    public boolean updateSong(Song song) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("UPDATE Songs SET \"nombre\" = ?, \"artistas\" = ?, \"album\" = ?, \"duracionMs\" = ?, \"popularidad\" = ?, \"href\" = ?, \"isrc\" = ?, \"acousticness\" = ?, \"danceability\" = ?, \"energy\" = ?, \"instrumentalness\" = ?, \"key\" = ?, \"liveness\" = ?, \"loudness\" = ?, \"mode\" = ?, \"speechiness\" = ?, \"tempo\" = ?, \"valence\" = ? WHERE id = ?")) {
            statement.setObject(1, song.getNombre());
            statement.setObject(2, song.getArtistas());
            statement.setObject(3, song.getAlbum());
            statement.setObject(4, song.getDuracionMs());
            statement.setObject(5, song.getPopularidad());
            statement.setObject(6, song.getHref());
            statement.setObject(7, song.getIsrc());
            statement.setObject(8, song.getAcousticness());
            statement.setObject(9, song.getDanceability());
            statement.setObject(10, song.getEnergy());
            statement.setObject(11, song.getInstrumentalness());
            statement.setObject(12, song.getKey());
            statement.setObject(13, song.getLiveness());
            statement.setObject(14, song.getLoudness());
            statement.setObject(15, song.getMode());
            statement.setObject(16, song.getSpeechiness());
            statement.setObject(17, song.getTempo());
            statement.setObject(18, song.getValence());
            statement.setObject(19, song.getId());
            return statement.executeUpdate() > 0;
        }
    }

    public boolean deleteSong(String songId) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM Songs WHERE id = ?")) {
            statement.setString(1, songId);
            return statement.executeUpdate() > 0;
        }
    }

    public void createEvent(Event event) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO Historial (user_id, song_id, played_at) VALUES (?, ?, ?)")) {
            bindEvent(statement, event, 1);
            statement.executeUpdate();
        }
    }

    public Optional<Event> getEvent(Event event) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT user_id, song_id, played_at FROM Historial WHERE user_id = ? AND song_id = ? AND played_at = ?")) {
            bindEvent(statement, event, 1);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                Song song = findSong(connection, result.getString("song_id"))
                        .orElseThrow(() -> new SQLException("Song missing for history event"));
                return Optional.of(new Event(new User(result.getString("user_id")), song,
                        result.getString("played_at")));
            }
        }
    }

    /** All three original fields identify the listen being replaced. */
    public boolean updateEvent(Event original, Event updated) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE Historial SET user_id = ?, song_id = ?, played_at = ? WHERE user_id = ? AND song_id = ? AND played_at = ?")) {
            bindEvent(statement, updated, 1);
            bindEvent(statement, original, 4);
            return statement.executeUpdate() > 0;
        }
    }

    public boolean deleteEvent(Event event) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM Historial WHERE user_id = ? AND song_id = ? AND played_at = ?")) {
            bindEvent(statement, event, 1);
            return statement.executeUpdate() > 0;
        }
    }
    //BORRAR POR USUARIO
    public void deleteHistory(String userId) throws SQLException {
        deleteUserList("Historial", userId);
    }

    public void deleteTopShort(String userId) throws SQLException {
        deleteUserList("top_short", userId);
    }

    public void deleteTopMedium(String userId) throws SQLException {
        deleteUserList("top_medium", userId);
    }

    public void deleteTopLong(String userId) throws SQLException {
        deleteUserList("top_long", userId);
    }

    public void createTopShort(String userId, int position, String songId) throws SQLException {
        createTopEntry("top_short", userId, position, songId);
    }

    public void createTopMedium(String userId, int position, String songId) throws SQLException {
        createTopEntry("top_medium", userId, position, songId);
    }

    public void createTopLong(String userId, int position, String songId) throws SQLException {
        createTopEntry("top_long", userId, position, songId);
    }

    // Table names only come from the fixed public methods above.
    private void deleteUserList(String table, String userId) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM " + table + " WHERE user_id = ?")) {
            statement.setString(1, userId);
            statement.executeUpdate();
        }
    }

    private void createTopEntry(String table, String userId, int position, String songId) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO " + table + " (user_id, position, song_id) VALUES (?, ?, ?)")) {
            statement.setString(1, userId);
            statement.setInt(2, position);
            statement.setString(3, songId);
            statement.executeUpdate();
        }
    }

    private void bindEvent(PreparedStatement statement, Event event, int start) throws SQLException {
        statement.setString(start, event.getListener().getUserId());
        statement.setString(start + 1, event.getSong().getId());
        statement.setString(start + 2, event.getTime());
    }
}
