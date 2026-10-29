package processor;

import audiofeatures.Features;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import database.MusicDatabase;
import models.Artist;
import models.Event;
import models.Song;
import models.User;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

public class SpotifyProcessor {
    private final MusicDatabase database;
    private final Features features = new Features();

    public SpotifyProcessor(MusicDatabase database) {
        this.database = database;
    }

    public void addHistory(String json, String userId) throws SQLException, IOException, InterruptedException {
        JsonArray items = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("items");
        List<Song> songs = createSongs(items, true);
        List<Event> events = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            String time = items.get(i).getAsJsonObject().get("played_at").getAsString();
            events.add(new Event(new User(userId), songs.get(i), time));
        }

        // Conserva las escuchas anteriores; la identidad es usuario + canción + played_at.
        for (Event event : events) {
            if (database.getEvent(event).isEmpty()) {
                database.createEvent(event);
            }
        }
    }

    public void addTopLong(String json, String userId) throws SQLException, IOException, InterruptedException {
        List<Song> songs = createSongs(JsonParser.parseString(json)
                .getAsJsonObject().getAsJsonArray("items"), false);
        database.deleteTopLong(userId);
        for (int i = 0; i < songs.size(); i++) {
            database.createTopLong(userId, i + 1, songs.get(i).getId());
        }
    }

    public void addTopMedium(String json, String userId) throws SQLException, IOException, InterruptedException {
        List<Song> songs = createSongs(JsonParser.parseString(json)
                .getAsJsonObject().getAsJsonArray("items"), false);
        database.deleteTopMedium(userId);
        for (int i = 0; i < songs.size(); i++) {
            database.createTopMedium(userId, i + 1, songs.get(i).getId());
        }
    }

    public void addTopShort(String json, String userId) throws SQLException, IOException, InterruptedException {
        List<Song> songs = createSongs(JsonParser.parseString(json)
                .getAsJsonObject().getAsJsonArray("items"), false);
        database.deleteTopShort(userId);
        for (int i = 0; i < songs.size(); i++) {
            database.createTopShort(userId, i + 1, songs.get(i).getId());
        }
    }

    public void addArtistTopShort(String json, String userId) throws SQLException, IOException {
        database.replaceArtistTopShort(userId, parseArtists(json));
    }

    public void addArtistTopMedium(String json, String userId) throws SQLException, IOException {
        database.replaceArtistTopMedium(userId, parseArtists(json));
    }

    public void addArtistTopLong(String json, String userId) throws SQLException, IOException {
        database.replaceArtistTopLong(userId, parseArtists(json));
    }

    private List<Artist> parseArtists(String json) throws IOException {
        try {
            JsonArray items = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("items");
            List<Artist> artists = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            for (JsonElement item : items) {
                JsonObject artist = item.getAsJsonObject();
                String id = getString(artist, "id");
                String name = getString(artist, "name");
                if (id == null || id.isBlank() || name == null || name.isBlank() || !ids.add(id))
                    throw new IllegalArgumentException();
                artists.add(new Artist(id, name, getString(artist, "href")));
            }
            return artists;
        } catch (RuntimeException ex) {
            throw new IOException("Spotify devolvió un top de artistas no válido; se conserva el top anterior.");
        }
    }

    private List<Song> createSongs(JsonArray items, boolean history) throws SQLException, IOException, InterruptedException {
        List<Song> songs = new ArrayList<>();
        Map<String, Song> unique = new LinkedHashMap<>();
        for (JsonElement item : items) {
            JsonObject track = history
                    ? item.getAsJsonObject().getAsJsonObject("track")
                    : item.getAsJsonObject();
            String songId = track.get("id").getAsString();
            Song song = unique.get(songId);
            if (song == null) song = database.getSong(songId).orElse(null);

            if (song == null) {
                StringBuilder artists = new StringBuilder();
                for (JsonElement artist : track.getAsJsonArray("artists")) {
                    if (artists.length() > 0) artists.append(", ");
                    artists.append(artist.getAsJsonObject().get("name").getAsString());
                }

                song = new Song(songId, getString(track, "name"), artists.toString(),
                        getString(track.getAsJsonObject("album"), "name"),
                        getInteger(track, "duration_ms"), getInteger(track, "popularity"),
                        getString(track, "href"),
                        getString(track.getAsJsonObject("external_ids"), "isrc"));


            }
            unique.put(songId, song);
            songs.add(song);
        }
        List<Song> pending = unique.values().stream().filter(song -> song.hasMissingFeatures() || song.getPopularidad() == null).toList();
        features.addFeatures(pending);
        for (Song song : unique.values()) {
            if (database.getSong(song.getId()).isEmpty()) database.createSong(song);
            else if (pending.contains(song)) database.updateSong(song);
        }
        return songs;
    }

    private String getString(JsonObject object, String field) {
        return object == null || !object.has(field) || object.get(field).isJsonNull()
                ? null : object.get(field).getAsString();
    }

    private Integer getInteger(JsonObject object, String field) {
        return !object.has(field) || object.get(field).isJsonNull()
                ? null : object.get(field).getAsInt();
    }
}
