package processor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import database.MusicDatabase;
import models.Event;
import models.Song;
import models.User;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class SpotifyProcessor {
    private final MusicDatabase database;

    public SpotifyProcessor(MusicDatabase database) {
        this.database = database;
    }

    public void addHistory(String json, String userId) throws SQLException {
        JsonArray items = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("items");
        List<Song> songs = createSongs(items, true);
        List<Event> events = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            String time = items.get(i).getAsJsonObject().get("played_at").getAsString();
            events.add(new Event(new User(userId), songs.get(i), time));
        }

        database.deleteHistory(userId);
        for (Event event : events) {
            if (database.getEvent(event).isEmpty()) {
                database.createEvent(event);
            }
        }
    }

    public void addTopLong(String json, String userId) throws SQLException {
        List<Song> songs = createSongs(JsonParser.parseString(json)
                .getAsJsonObject().getAsJsonArray("items"), false);
        database.deleteTopLong(userId);
        for (int i = 0; i < songs.size(); i++) {
            database.createTopLong(userId, i + 1, songs.get(i).getId());
        }
    }

    public void addTopMedium(String json, String userId) throws SQLException {
        List<Song> songs = createSongs(JsonParser.parseString(json)
                .getAsJsonObject().getAsJsonArray("items"), false);
        database.deleteTopMedium(userId);
        for (int i = 0; i < songs.size(); i++) {
            database.createTopMedium(userId, i + 1, songs.get(i).getId());
        }
    }

    public void addTopShort(String json, String userId) throws SQLException {
        List<Song> songs = createSongs(JsonParser.parseString(json)
                .getAsJsonObject().getAsJsonArray("items"), false);
        database.deleteTopShort(userId);
        for (int i = 0; i < songs.size(); i++) {
            database.createTopShort(userId, i + 1, songs.get(i).getId());
        }
    }

    private List<Song> createSongs(JsonArray items, boolean history) throws SQLException {
        List<Song> songs = new ArrayList<>();
        for (JsonElement item : items) {
            JsonObject track = history
                    ? item.getAsJsonObject().getAsJsonObject("track")
                    : item.getAsJsonObject();
            String songId = track.get("id").getAsString();
            Song song = database.getSong(songId).orElse(null);

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

                // TODO: Pass this new song to the song processing module to obtain audio features.
                // Until that module exists, store the Spotify data with null audio features.
                database.createSong(song);
            }
            // Existing songs are reused without replacing their data or audio features.
            songs.add(song);
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
