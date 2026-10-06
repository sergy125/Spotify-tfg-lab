import { useRef, useState } from 'react';
import {
    ActivityIndicator, Button, KeyboardAvoidingView, Platform,
    ScrollView, StyleSheet, Text, TextInput, View,
} from 'react-native';
import { ManualTokenProvider } from '../../spotify/auth/ManualTokenProvider';
import { SpotifyApiClient } from '../../spotify/api/SpotifyApiClient';
import { SpotifyService } from '../../spotify/services/SpotifyService';
import type { SpotifyArtist } from '../../spotify/models/SpotifyArtist';
import type { SpotifyTrack } from '../../spotify/models/SpotifyTrack';
import { SpotifyLabViewModel } from './SpotifyLabViewModel';

type Results =
    | { kind: 'tracks'; items: SpotifyTrack[] }
    | { kind: 'artists'; items: SpotifyArtist[] };

export function SpotifyLabScreen() {
    const [{ tokenProvider, viewModel }] = useState(() => {
        const tokenProvider = new ManualTokenProvider();
        const apiClient = new SpotifyApiClient(tokenProvider);
        const service = new SpotifyService(apiClient);
        return { tokenProvider, viewModel: new SpotifyLabViewModel(service) };
    });
    const [token, setToken] = useState('');
    const [results, setResults] = useState<Results | null>(null);
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const requestInProgress = useRef(false);

    async function load(kind: Results['kind']) {
        if (requestInProgress.current) return;
        requestInProgress.current = true;
        setLoading(true);
        setError('');
        setResults(null);
        tokenProvider.setToken(token);
        try {
            if (kind === 'tracks') {
                setResults({ kind, items: await viewModel.loadTopTracks() });
            } else {
                setResults({ kind, items: await viewModel.loadTopArtists() });
            }
        } catch (error: unknown) {
            setError(error instanceof Error ? error.message : 'No se han podido cargar los resultados.');
        } finally {
            requestInProgress.current = false;
            setLoading(false);
        }
    }

    return (
        <KeyboardAvoidingView
            style={styles.container}
            behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        >
            <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
                <Text style={styles.title}>Spotify Lab</Text>
                <Text style={styles.label}>Access token</Text>
                <TextInput
                    style={styles.input}
                    accessibilityLabel="Access token de Spotify"
                    placeholder="Pega aquí tu access token"
                    value={token}
                    onChangeText={setToken}
                    autoCapitalize="none"
                    autoCorrect={false}
                    secureTextEntry
                    editable={!loading}
                />
                <View style={styles.buttons}>
                    <Button title="Cargar Top 5 Tracks" onPress={() => load('tracks')} disabled={loading} />
                    <Button title="Cargar Top 5 Artists" onPress={() => load('artists')} disabled={loading} />
                </View>
                {loading && <ActivityIndicator style={styles.feedback} accessibilityLabel="Cargando" />}
                {error !== '' && <Text style={styles.error} accessibilityRole="alert">{error}</Text>}
                {results && (
                    <View style={styles.results}>
                        <Text style={styles.heading}>
                            {results.kind === 'tracks' ? 'Top 5 Tracks' : 'Top 5 Artists'}
                        </Text>
                        {results.items.length === 0 && <Text>No hay resultados disponibles.</Text>}
                        {results.kind === 'tracks'
                            ? results.items.map((track, index) => (
                                <View key={track.id} style={styles.item}>
                                    <Text style={styles.itemName}>{index + 1}. {track.name}</Text>
                                    <Text>{track.artists.map(artist => artist.name).join(', ')}</Text>
                                </View>
                            ))
                            : results.items.map((artist, index) => (
                                <View key={artist.id} style={styles.item}>
                                    <Text style={styles.itemName}>{index + 1}. {artist.name}</Text>
                                </View>
                            ))}
                    </View>
                )}
            </ScrollView>
        </KeyboardAvoidingView>
    );
}

const styles = StyleSheet.create({
    container: { flex: 1, backgroundColor: 'white' },
    content: { padding: 24, paddingTop: 60, paddingBottom: 48 },
    title: { fontSize: 30, fontWeight: 'bold', marginBottom: 24 },
    label: { marginBottom: 8 },
    input: { borderWidth: 1, borderColor: '#aaa', borderRadius: 8, padding: 12, marginBottom: 16 },
    buttons: { gap: 12 },
    feedback: { marginTop: 20 },
    error: { marginTop: 20, color: '#b00020' },
    results: { marginTop: 30 },
    heading: { fontSize: 20, fontWeight: '600', marginBottom: 16 },
    item: { marginBottom: 20 },
    itemName: { fontSize: 17, fontWeight: '600' },
});
