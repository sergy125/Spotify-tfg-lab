import type { SpotifyArtist } from './SpotifyArtist';

export interface SpotifyTrack {
    id: string;
    name: string;
    artists: SpotifyArtist[];
}
