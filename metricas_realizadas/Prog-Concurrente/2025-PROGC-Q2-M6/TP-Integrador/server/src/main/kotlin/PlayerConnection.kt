@file:Suppress("unused")

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.WebSocketListener
import utils.NoOpWriteCallback
import java.util.UUID


class PlayerConnection : WebSocketListener {
    val id: String = UUID.randomUUID().toString()
    private var session: Session? = null
    private var isInGame: Boolean = false
    private var gameHandler: GameHandler? = null
    private var subscribedToLobby: Boolean = false
    private val normalClosure = 1000

    val mapper: ObjectMapper = jacksonObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .apply {
            addMixIn(WsMessage::class.java, WsMessageMixin::class.java)
        }

    override fun onWebSocketConnect(session: Session) {
        this.session = session
        println("Player connected: $id")
        session.remote.sendString("""{"type": "connected", "payload": {"playerId": "$id"}}""")
    }

    override fun onWebSocketClose(statusCode: Int, reason: String?) {
        println("Player disconnected: $id, $statusCode, $reason")
        handleUnsubListGames()
        // Si estaba en una partida, delegar la limpieza al GameHandler.
        try {
            if (isInGame && gameHandler != null) {
                // notificar a rivales/espectadores
                gameHandler?.handlePlayerLeave(id)
            }
        } catch (e: Exception) {
            println("Error removing player from game on disconnect: ${e.message}")
        } finally {
            // limpiar estado local
            isInGame = false
            gameHandler = null
            session = null
        }
    }

    override fun onWebSocketText(message: String) {
        println("Received message from $id: $message")
        val data = try {
            mapper.readValue(message, WsMessage::class.java)
        } catch (e: Exception) {
            println("Failed to parse message: ${e.message}")
            this.session!!.remote.sendString("""{"type": "error", "payload": "Invalid message format"}""")
            return
        }

        println(data)

        when (data) {
            is CreateGameMessage -> handleCreateGame()
            is ExitGameMessage -> handleExitGame()
            is JoinGameMessage -> handleJoinGame(data)
            is MakeMoveMessage -> handleMakeMove(data)
            is LeaveGameMessage -> handleLeaveGame()
            is JoinGameSpectatorMessage -> handleJoinSpectatorGame(data)
            is ListSubGamesMessage -> handleSubListGames()
            is ListUnsGamesMessage -> handleUnsubListGames()
            else -> {
                this.session!!.remote.sendString("""{"type": "error", "payload": "Unknown message type"}""")
            }
        }
    }

    fun handleCreateGame() {
        val game = GameStore.newGame()
        if (game.handlePlayerJoin(Player(id, session!!, "WHITE"))) {
            this.gameHandler = game
            this.isInGame = true
            session?.remote?.sendString("""{"type": "game_created", "payload": {"gameId": "${game.id}"}}""")
        } else {
            session?.remote?.sendString("""{"type": "error", "payload": "Failed to create game"}""")
        }
    }
    fun handleExitGame() {
        println("Player $id exiting game")
        // Cerrar la sesión desde SERVIDOR
        try {
            session?.close(normalClosure, "Client exit button")
        } catch (e: Exception) {
            println("Error closing session: ${e.message}")
        }
    }


    fun handleJoinGame(message: JoinGameMessage) {
        val gameId = message.payload.gameId
        val game = GameStore.getGame(gameId)

        if(game == null) {
            session?.remote?.sendString("""{"type": "error", "payload": "Game not found"}""")
            return
        }

        handleUnsubListGames()

        val player = Player(id, session!!, "BLACK")
        if (!game.handlePlayerJoin(player)) {
            session?.remote?.sendString("""{"type": "error", "payload": "Game is full"}""")
            return
        }
        this.gameHandler = game
        this.isInGame = true
        session?.remote?.sendString("""{"type": "joined_game", "payload": {"gameId": "${game.id}"}}""")
    }

    fun handleJoinSpectatorGame(message: JoinGameSpectatorMessage) {
        val gameId = message.payload.gameId
        val game = GameStore.getGame(gameId)
        
        
        if (game == null ) {
            session?.remote?.sendString("""{"type": "error", "payload": "Game not found"}""")
            return
        }
         if(game.players.size < 2) {
            session?.remote?.sendString("""{"type": "error", "payload": "Cannot join as spectator to a game with less than 2 players"}""")
            return
        }

        val spectator = Spectator(id, session!!)
        game.handleSpectatorJoin(spectator)
        this.gameHandler = game
        this.isInGame = true
        session?.remote?.sendString("""{"type": "joined_as_spectator", "payload": {"gameId": "${game.id}"}}""")
    }

    fun handleLeaveGame() {
        if (!isInGame || gameHandler == null) {
            session?.remote?.sendString("""{"type": "error", "payload": "Not in a game"}""")
            return
        }
        println("Player $id left game ${gameHandler?.id}")
        this.gameHandler?.handlePlayerLeave(id)
        this.isInGame = false
        this.gameHandler = null
        session?.remote?.sendString("""{"type": "left_game", "payload": {}}""")
    }

    fun handleMakeMove(message: MakeMoveMessage) {
        if (!isInGame || gameHandler == null) {
            session?.remote?.sendString("""{"type": "error", "payload": "Not in a game"}""")
            return
        }
        val from = message.payload.from
        val to = message.payload.to
        val move = SimpleMove(from, to)
        gameHandler?.handleMove(move, id)?.let {
            if (!it) {
                session?.remote?.sendString("""{"type": "error", "payload": "Illegal move or not your turn"}""")
                return
            }
        }
        session?.remote?.sendString("""{"type": "move_made", "payload": {"from": "$from", "to": "$to"}}""")
    }


    fun handleSubListGames() {
        // si no estaba suscripto, lo agregamos
        if (!subscribedToLobby) {
            LobbyManager.subscribe(this)
            subscribedToLobby = true
        }
        // enviar lista actual
        LobbyManager.sendGamesListTo(this)
    }
    fun handleUnsubListGames() {
        // si estaba suscripto, lo removemos
        if (subscribedToLobby) {
            LobbyManager.unsubscribe(this)
            subscribedToLobby = false
        }
    }
    fun send(data: Any) {
        session?.remote?.sendString(mapper.writeValueAsString(data), NoOpWriteCallback)
    }
}