import type { SpotifyApiClient } from '../api/SpotifyApiClient';
import type { SpotifyArtist } from '../models/SpotifyArtist';
import type { SpotifyTrack } from '../models/SpotifyTrack';

interface TopItemsResponse<T> {
    items: T[];
}

export class SpotifyService {
    constructor(private readonly apiClient: SpotifyApiClient) {}

    async getTopTracks(limit: number = 5): Promise<SpotifyTrack[]> {
        this.validateLimit(limit);
        const data = await this.apiClient.get<TopItemsResponse<SpotifyTrack>>(
            `/me/top/tracks?time_range=long_term&limit=${limit}`,
        );
        return data.items;
    }

    async getTopArtists(limit: number = 5): Promise<SpotifyArtist[]> {
        this.validateLimit(limit);
        const data = await this.apiClient.get<TopItemsResponse<SpotifyArtist>>(
            `/me/top/artists?time_range=long_term&limit=${limit}`,
        );
        return data.items;
    }

    private validateLimit(limit: number): void {
        if (!Number.isInteger(limit) || limit < 1 || limit > 50) {
            throw new Error('El límite debe ser un número entero entre 1 y 50.');
        }
    }
}
