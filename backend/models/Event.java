package models;

public class Event {
    private User listener;
    private Song song;
    private String time;

    public Event() {
    }

    public Event(User listener, Song song, String time) {
        this.listener = listener;
        this.song = song;
        this.time = time;
    }

    public User getListener() {
        return listener;
    }

    public void setListener(User listener) {
        this.listener = listener;
    }

    public Song getSong() {
        return song;
    }

    public void setSong(Song song) {
        this.song = song;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }
}
