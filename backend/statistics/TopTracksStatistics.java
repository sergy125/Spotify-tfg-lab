package statistics;

import models.Song;

import java.util.HashMap;

public record TopTracksStatistics(HashMap<Integer, Song> shortTerm,
                                  HashMap<Integer, Song> mediumTerm,
                                  HashMap<Integer, Song> longTerm) {}
