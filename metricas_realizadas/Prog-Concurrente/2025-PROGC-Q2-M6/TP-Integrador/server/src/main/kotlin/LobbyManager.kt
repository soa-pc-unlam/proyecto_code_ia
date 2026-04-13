import java.util.concurrent.CopyOnWriteArraySet

object LobbyManager {
    private val subscribers = CopyOnWriteArraySet<PlayerConnection>()

    fun subscribe(player: PlayerConnection) {
        subscribers.add(player)
    }

    fun unsubscribe(player: PlayerConnection) {
        subscribers.remove(player)
    }

    fun sendGamesListTo(player: PlayerConnection) {
        val gamesInfo = GameStore.getAllGamesReadOnly().map { game ->
            mapOf(
                "id" to game.id,
                "players" to game.players.size,
                "spectators" to game.spectators.size
            )
        }

        val response = mapOf(
            "type" to "games_list",
            "payload" to mapOf("games" to gamesInfo)
        )

        player.send(response)
    }

    fun broadcastGamesList() {
        try {
            subscribers.forEach { sendGamesListTo(it) }
        } catch (e: Exception) {
            println("Failed to broadcast games list: ${e.message}")
        }
    }
}