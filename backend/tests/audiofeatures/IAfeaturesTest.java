package audiofeatures;

import com.google.gson.*;
import database.MusicDatabase;
import models.Song;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.DriverManager;
import java.util.*;

/** Pruebas sin red ni consumo de créditos: ejecutar con -ea. */
public class IAfeaturesTest {
    static class FakeIA extends IAfeatures {
        int calls;
        String invalid;
        protected String request(JsonObject body) {
            calls++;
            String prompt = body.getAsJsonArray("contents").get(0).getAsJsonObject()
                    .getAsJsonArray("parts").get(0).getAsJsonObject().get("text").getAsString();
            JsonArray input = JsonParser.parseString(prompt.substring(prompt.indexOf("Songs: ") + 7)).getAsJsonArray();
            assert input.size() <= 20;
            JsonArray result = new JsonArray();
            for (JsonElement item : input) {
                JsonObject row = new JsonObject(); row.add("id", item.getAsJsonObject().get("id"));
                for (String field : List.of("acousticness", "danceability", "energy", "instrumentalness",
                        "liveness", "speechiness", "valence")) row.addProperty(field, .5);
                row.addProperty("popularity", 65);
                row.addProperty("key", 4); row.addProperty("mode", 1);
                row.addProperty("tempo", 120); row.addProperty("loudness", -6);
                result.add(row);
            }
            if (invalid != null) {
                JsonObject row = result.get(0).getAsJsonObject();
                switch (invalid) {
                    case "null" -> row.add("energy", JsonNull.INSTANCE);
                    case "popularity" -> row.addProperty("popularity", 101);
                    case "range" -> row.addProperty("energy", 2);
                    case "fraction" -> row.addProperty("key", 1.5);
                    case "missing" -> result.remove(0);
                    case "duplicate" -> result.add(row);
                    case "unknown" -> row.addProperty("id", "unknown");
                }
            }
            return result.toString();
        }
    }
    static Song song(String id) { Song song = new Song(); song.setId(id); return song; }
    public static void main(String[] args) throws Exception {
        FakeIA ai = new FakeIA();
        List<Song> songs = new ArrayList<>();
        for (int i = 0; i < 41; i++) songs.add(song("song-" + i));
        songs.get(0).setEnergy(.91);
        songs.get(0).setPopularidad(88);
        songs.add(songs.get(0));
        ai.addFeatures(songs);
        assert ai.calls == 3 : "Expected three batches with duplicates removed";
        for (Song song : songs) assert !song.hasMissingFeatures() && song.isAi();
        assert songs.get(0).getPopularidad() == 88;
        assert songs.get(1).getPopularidad() == 65;
        assert songs.get(0).getEnergy() == .91 : "Existing feature overwritten";
        ai.addFeatures(songs); assert ai.calls == 3 : "Complete songs sent again";
        for (String invalid : List.of("null", "popularity", "range", "fraction", "missing", "duplicate", "unknown")) {
            FakeIA bad = new FakeIA(); bad.invalid = invalid;
            Song song = song("test");
            try { bad.addFeatures(List.of(song)); throw new AssertionError(invalid); }
            catch (IOException expected) { assert song.hasMissingFeatures() && !song.isAi(); }
        }
        // Encontrada sin audiofeatures y canción ausente: ambas pasan a IA.
        FakeIA fallback = new FakeIA();
        ReccoBeats recco = new ReccoBeats() {
            JsonObject get(String endpoint) {
                if (endpoint.contains("audio-features")) return null;
                return JsonParser.parseString(endpoint.contains("absent") ? "{\"content\":[]}"
                        : "{\"content\":[{\"id\":\"found-recco\",\"popularity\":72}]}").getAsJsonObject();
            }
        };
        Song found = song("found"); Song absent = song("absent");
        new Features(recco, fallback).addFeatures(List.of(found, absent));
        assert fallback.calls == 1;
        // Si ReccoBeats devuelve audiofeatures completas pero no popularidad, no necesita IA.
        ReccoBeats complete = new ReccoBeats() {
            JsonObject get(String endpoint) {
                return JsonParser.parseString(endpoint.contains("audio-features")
                        ? "{\"acousticness\":0.2,\"danceability\":0.5,\"energy\":0.8,\"instrumentalness\":0,\"key\":4,\"liveness\":0.1,\"loudness\":-6,\"mode\":1,\"speechiness\":0.1,\"tempo\":120,\"valence\":0.5}"
                        : "{\"content\":[{\"id\":\"complete\"}]}").getAsJsonObject();
            }
        };
        Song completeSong = song("complete");
        new Features(complete, fallback).addFeatures(List.of(completeSong));
        assert fallback.calls == 1 && !completeSong.isAi() && !completeSong.hasMissingFeatures();
        assert !found.hasMissingFeatures() && found.isAi() && found.getPopularidad() == 72;
        assert !absent.hasMissingFeatures() && absent.isAi() && absent.getPopularidad() == 65;
        // Un fallo de conexión tampoco debe activar la IA.
        ReccoBeats broken = new ReccoBeats() {
            JsonObject get(String endpoint) throws IOException { throw new IOException("offline"); }
        };
        try { new Features(broken, fallback).addFeatures(List.of(song("offline"))); throw new AssertionError(); }
        catch (IOException expected) { assert fallback.calls == 1; }
        var path = Files.createTempFile("ia-database-", ".sqlite");
        MusicDatabase db = new MusicDatabase(path); db.createAllTables();
        Song existing = song("existing"); db.createSong(existing);
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + path);
             var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE Songs DROP COLUMN ai");
        }
        db.createSongsTable(); db.createSongsTable();
        assert !db.getSong("existing").orElseThrow().isAi();
        db.createSong(songs.get(0));
        assert db.getSong(songs.get(0).getId()).orElseThrow().isAi();
        existing.setAi(true); db.updateSong(existing);
        assert db.getSong("existing").orElseThrow().isAi();
        existing.setAi(false); db.updateSong(existing);
        assert !db.getSong("existing").orElseThrow().isAi();
        // El procesador reutiliza una canción completa sin llamar a ReccoBeats ni Gemini.
        var processor = new processor.SpotifyProcessor(db);
        String history = "{\"items\":[{\"track\":{\"id\":\"song-0\"},\"played_at\":\"2026-10-26T12:00:00Z\"}]}";
        processor.addHistory(history, "listener");
        processor.addHistory(history, "listener");
        processor.addHistory("{\"items\":[]}", "listener");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + path);
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT count(*) FROM Historial")) {
            assert result.next() && result.getInt(1) == 1;
        }
        Files.delete(path);
        System.out.println("PASS: batching, deduplication, preserved features, no repeat calls, invalid JSON values/ids rejected, migration, ai CRUD.");
    }
}
