package models;

public class Song {
    private String id;
    private String nombre;
    private String artistas;
    private String album;
    private Integer duracionMs;
    private Integer popularidad;
    private String href;
    private String isrc;
    private Double acousticness;
    private Double danceability;
    private Double energy;
    private Double instrumentalness;
    private Integer key;
    private Double liveness;
    private Double loudness;
    private Integer mode;
    private Double speechiness;
    private Double tempo;
    private Double valence;

    public Song() {
    }

    public Song(String id, String nombre, String artistas, String album,
                Integer duracionMs, Integer popularidad, String href, String isrc) {
        this.id = id;
        this.nombre = nombre;
        this.artistas = artistas;
        this.album = album;
        this.duracionMs = duracionMs;
        this.popularidad = popularidad;
        this.href = href;
        this.isrc = isrc;
    }

    public Song(String id, String nombre, String artistas, String album, Integer duracionMs, Integer popularidad, String href, String isrc, Double acousticness, Double danceability, Double energy, Double instrumentalness, Integer key, Double liveness, Double loudness, Integer mode, Double speechiness, Double tempo, Double valence) {
        this.id = id;
        this.nombre = nombre;
        this.artistas = artistas;
        this.album = album;
        this.duracionMs = duracionMs;
        this.popularidad = popularidad;
        this.href = href;
        this.isrc = isrc;
        this.acousticness = acousticness;
        this.danceability = danceability;
        this.energy = energy;
        this.instrumentalness = instrumentalness;
        this.key = key;
        this.liveness = liveness;
        this.loudness = loudness;
        this.mode = mode;
        this.speechiness = speechiness;
        this.tempo = tempo;
        this.valence = valence;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getArtistas() {
        return artistas;
    }

    public void setArtistas(String artistas) {
        this.artistas = artistas;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public Integer getDuracionMs() {
        return duracionMs;
    }

    public void setDuracionMs(Integer duracionMs) {
        this.duracionMs = duracionMs;
    }

    public Integer getPopularidad() {
        return popularidad;
    }

    public void setPopularidad(Integer popularidad) {
        this.popularidad = popularidad;
    }

    public String getHref() {
        return href;
    }

    public void setHref(String href) {
        this.href = href;
    }

    public String getIsrc() {
        return isrc;
    }

    public void setIsrc(String isrc) {
        this.isrc = isrc;
    }

    public Double getAcousticness() {
        return acousticness;
    }

    public void setAcousticness(Double acousticness) {
        this.acousticness = acousticness;
    }

    public Double getDanceability() {
        return danceability;
    }

    public void setDanceability(Double danceability) {
        this.danceability = danceability;
    }

    public Double getEnergy() {
        return energy;
    }

    public void setEnergy(Double energy) {
        this.energy = energy;
    }

    public Double getInstrumentalness() {
        return instrumentalness;
    }

    public void setInstrumentalness(Double instrumentalness) {
        this.instrumentalness = instrumentalness;
    }

    public Integer getKey() {
        return key;
    }

    public void setKey(Integer key) {
        this.key = key;
    }

    public Double getLiveness() {
        return liveness;
    }

    public void setLiveness(Double liveness) {
        this.liveness = liveness;
    }

    public Double getLoudness() {
        return loudness;
    }

    public void setLoudness(Double loudness) {
        this.loudness = loudness;
    }

    public Integer getMode() {
        return mode;
    }

    public void setMode(Integer mode) {
        this.mode = mode;
    }

    public Double getSpeechiness() {
        return speechiness;
    }

    public void setSpeechiness(Double speechiness) {
        this.speechiness = speechiness;
    }

    public Double getTempo() {
        return tempo;
    }

    public void setTempo(Double tempo) {
        this.tempo = tempo;
    }

    public Double getValence() {
        return valence;
    }

    public void setValence(Double valence) {
        this.valence = valence;
    }
}
