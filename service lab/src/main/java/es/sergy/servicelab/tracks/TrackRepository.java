package es.sergy.servicelab.tracks;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TrackRepository extends JpaRepository<Track, String> {
    @Query("select t from Track t order by case when t.rank is null then 1 else 0 end, t.rank, t.spotifyId")
    List<Track> findAllOrderedByRank();
}
