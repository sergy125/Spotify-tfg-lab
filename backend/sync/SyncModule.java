package sync;

import audiofeatures.ReccoBeats;
import database.MusicDatabase;
import models.Song;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * Ejecuta la sincronización en un único hilo de fondo, un trabajo cada vez.
 * SQLite contiene las cuentas y los datos importados. Los turnos y reintentos
 * se guardan en memoria y se vuelven a programar al arrancar.
 */
public final class SyncModule {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final System.Logger LOG = System.getLogger(SyncModule.class.getName());
    private static final Duration HISTORY_INTERVAL = Duration.ofMinutes(15);
    private static final Duration DAILY_INTERVAL = Duration.ofDays(1);
    private static final Duration RETRY_DELAY = Duration.ofMinutes(1);
    private static final long POLL_MILLIS = Duration.ofSeconds(5).toMillis();

    private final MusicDatabase database;
    private final UserSync userSync;
    private final ReccoBeats reccoBeats = new ReccoBeats();
    private final Map<String, UserSchedule> users = new LinkedHashMap<>();
    private final Queue<String> songsToReview = new ArrayDeque<>();
    private Instant nextReview = Instant.EPOCH;
    private Thread worker;

    public SyncModule(MusicDatabase database) {
        this.database = database;
        this.userSync = new UserSync(database);
    }

    /** Prepara las tablas de SQLite y arranca el hilo una sola vez. Devuelve el control al main. */
    public synchronized void run() throws SQLException {
        if (worker != null) {
            return;
        }
        database.createAllTables();
        worker = new Thread(this::work, "spotify-sync");
        worker.start();
        System.out.println("Sincronizador iniciado.");
    }

    /**
     * Ejecuta trabajos mientras haya alguno pendiente. Cuando no hay trabajo,
     * o falla la lectura de SQLite, espera cinco segundos antes de volver a mirar.
     * Una interrupción detiene el hilo.
     */
    private void work() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    if (runNextJob()) {
                        continue;
                    }
                } catch (SQLException failure) {
                    LOG.log(System.Logger.Level.WARNING, "Could not read synchronization data", failure);
                }
                Thread.sleep(POLL_MILLIS);
            }
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Vuelve a leer los usuarios de SQLite, incorpora las altas y retira las bajas.
     * Conserva los turnos de las cuentas que siguen conectadas. Da prioridad al
     * historial, luego a los tops y por último a ReccoBeats. Entre los usuarios
     * de cada tipo elige el turno más antiguo y ejecuta como máximo un trabajo.
     */
    private boolean runNextJob() throws SQLException, InterruptedException {
        List<String> currentUsers = database.listUsers();
        users.keySet().retainAll(currentUsers);
        for (String userId : currentUsers) {
            users.computeIfAbsent(userId, ignored -> new UserSchedule());
        }

        Instant now = Instant.now();
        String historyUser = null;
        String topsUser = null;
        Instant earliestHistory = Instant.MAX;
        Instant earliestTops = Instant.MAX;

        for (var entry : users.entrySet()) {
            UserSchedule schedule = entry.getValue();
            Instant historyDue = schedule.historyDue();
            if (historyDue.isBefore(earliestHistory)) {
                historyUser = entry.getKey();
                earliestHistory = historyDue;
            }
            if (schedule.nextTops.isBefore(earliestTops)) {
                topsUser = entry.getKey();
                earliestTops = schedule.nextTops;
            }
        }

        if (!earliestHistory.isAfter(now)) {
            syncHistory(historyUser, now);
            return true;
        }
        if (!earliestTops.isAfter(now)) {
            syncTops(topsUser, now);
            return true;
        }
        return reviewNextSong(now);
    }

    /**
     * Cada 24 horas carga en una cola las canciones con ai=1. Revisa una canción
     * por vuelta para que el historial y los tops puedan entrar entre canciones.
     */
    private boolean reviewNextSong(Instant now) throws SQLException, InterruptedException {
        if (songsToReview.isEmpty() && !now.isBefore(nextReview)) {
            songsToReview.addAll(database.listAiSongIds());
            System.out.println("Canciones con IA pendientes de revisar en ReccoBeats: " + songsToReview.size());
            nextReview = now.plus(DAILY_INTERVAL);
        }
        String songId = songsToReview.poll();
        if (songId != null) {
            reviewSong(songId);
            return true;
        }
        return false;
    }

    /**
     * Pide las últimas 50 reproducciones de una cuenta. Cada turno normal
     * programa el siguiente para 15 minutos después. Si falla, programa un solo
     * reintento al minuto, siempre que llegue antes que el siguiente turno normal.
     * Si el trabajo acaba después de ese turno, se saltan los turnos vencidos
     * y se vuelve a contar desde que terminó.
     */
    private void syncHistory(String userId, Instant started) throws InterruptedException {
        UserSchedule schedule = users.get(userId);
        boolean regularTurn = !started.isBefore(schedule.nextHistory);
        schedule.retryAt = Instant.MAX;
        if (regularTurn) {
            schedule.nextHistory = started.plus(HISTORY_INTERVAL);
        }

        String action = regularTurn ? "Actualizando" : "Reintentando";
        System.out.println(action + " el historial de " + userId + "...");
        try {
            userSync.syncHistory(userId);
            System.out.println("Historial actualizado para " + userId + " a las " + LocalTime.now().format(TIME_FORMAT) + ".");
        } catch (IOException | SQLException failure) {
            LOG.log(System.Logger.Level.WARNING, "Could not sync history for " + userId, failure);
            if (regularTurn) {
                Instant retryAt = Instant.now().plus(RETRY_DELAY);
                if (retryAt.isBefore(schedule.nextHistory)) {
                    schedule.retryAt = retryAt;
                    System.out.println("Reintento del historial de " + userId + " programado en un minuto.");
                }
            }
        }

        Instant finished = Instant.now();
        if (!finished.isBefore(schedule.nextHistory)) {
            schedule.nextHistory = finished.plus(HISTORY_INTERVAL);
        }
    }

    /**
     * Actualiza los tops de canciones y artistas de corto, medio y largo plazo.
     * Programa el siguiente intento para 24 horas después del inicio.
     */
    private void syncTops(String userId, Instant started) throws InterruptedException {
        users.get(userId).nextTops = started.plus(DAILY_INTERVAL);
        System.out.println("Actualizando los tops de canciones y artistas de " + userId + "...");
        try {
            userSync.syncTops(userId);
            System.out.println("Tops actualizados para " + userId + " a las " + LocalTime.now().format(TIME_FORMAT) + ".");
        } catch (IOException | SQLException failure) {
            LOG.log(System.Logger.Level.WARNING, "Could not sync tops for " + userId, failure);
        }
    }

    /**
     * Busca en ReccoBeats las características de audio de una canción.
     * Si obtiene todas, guarda los datos y pone ai=0. Si no las encuentra
     * completas o la consulta falla, se conservan los datos guardados.
     */
    private void reviewSong(String songId) throws InterruptedException {
        try {
            System.out.println("Buscando en ReccoBeats la canción " + songId + "...");
            Song song = database.getSong(songId).orElse(null);
            if (song != null && reccoBeats.findFeatures(song) && !song.hasMissingFeatures()) {
                song.setAi(false);
                if (database.updateSong(song)) {
                    System.out.println("Características actualizadas desde ReccoBeats para "
                            + song.getNombre() + " (ai=0) a las " + LocalTime.now().format(TIME_FORMAT) + ".");
                }
            }
        } catch (IOException | SQLException failure) {
            LOG.log(System.Logger.Level.WARNING, "Could not review AI features for " + songId, failure);
        }
    }

    /**
     * Guarda el próximo turno de historial y de tops de una cuenta.
     * Instant.EPOCH hace que el primer turno esté pendiente desde el alta.
     * Instant.MAX indica que no hay un reintento de historial programado.
     */
    private static final class UserSchedule {
        private Instant nextHistory = Instant.EPOCH;
        private Instant retryAt = Instant.MAX;
        private Instant nextTops = Instant.EPOCH;

        private Instant historyDue() {
            return retryAt.isBefore(nextHistory) ? retryAt : nextHistory;
        }
    }
}
