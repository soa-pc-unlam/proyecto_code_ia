import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

object GameStore {
    private val games: MutableMap<String, GameHandler> = ConcurrentHashMap()
    
    private val cleaner: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private const val CLEAN_INTERVAL_SECONDS: Long = 60
    private val STALE_THRESHOLD_MS: Long = TimeUnit.MINUTES.toMillis(30)
    
    fun newGame(): GameHandler {
        val game = GameHandler()
        games[game.id] = game;
        return game
    }

    fun getGame(gameId: String): GameHandler? {
        return games[gameId]
    }

    fun removeGame(gameId: String) {
        games.remove(gameId)
    }

    fun getAllGamesReadOnly(): Collection<GameHandler> {
        return games.values
    }

    fun startCleaner() {
        cleaner.scheduleAtFixedRate({
            try {
                val now = System.currentTimeMillis()
                val toRemove = mutableListOf<String>()
                for ((id, game) in games) {
                    val last = game.lastActivityMillis.get()
                    if (game.players.isEmpty() || (now - last) > STALE_THRESHOLD_MS) {
                        toRemove.add(id)
                    }
                }
                toRemove.forEach { id ->
                    games.remove(id)
                    println("Cleaner removed game: $id")
                }
            } catch (e: Exception) {
                println("GameStore cleaner error: ${e.message}")
            }
        }, CLEAN_INTERVAL_SECONDS, CLEAN_INTERVAL_SECONDS, TimeUnit.SECONDS)
    }

    fun stopCleaner() {
        try { cleaner.shutdownNow() } catch (_: Exception) {}
    }

}