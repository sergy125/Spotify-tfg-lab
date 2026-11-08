package statistics;

import models.Artist;

import java.util.HashMap;

public record TopArtistsStatistics(HashMap<Integer, Artist> shortTerm,
                                   HashMap<Integer, Artist> mediumTerm,
                                   HashMap<Integer, Artist> longTerm) {}
