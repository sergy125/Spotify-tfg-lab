import type { SpotifyService } from '../../spotify/services/SpotifyService';
import type { SpotifyTrack } from '../../spotify/models/SpotifyTrack';
import type { SpotifyArtist } from '../../spotify/models/SpotifyArtist';

export class SpotifyLabViewModel {
    constructor(private readonly spotifyService: SpotifyService) {}

    loadTopTracks(): Promise<SpotifyTrack[]> {
        return this.spotifyService.getTopTracks(5);
    }

    loadTopArtists(): Promise<SpotifyArtist[]> {
        return this.spotifyService.getTopArtists(5);
    }
}
