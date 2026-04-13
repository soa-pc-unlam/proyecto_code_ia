import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import utils.NoOpWriteCallback
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

class GameHandler {
    val id: String = UUID.randomUUID().toString()
    val board: Board = Board()
    val players: MutableList<Player> = CopyOnWriteArrayList()
    val spectators: MutableList<Spectator> = CopyOnWriteArrayList()
    var lastMove: SimpleMove? = null
    var gameEnded: Boolean = false 
    private set 

    // timestamp de última actividad (join, move, mensaje relevante)
    @Volatile
    var lastActivityMillis = AtomicLong(System.currentTimeMillis())
        private set

    private fun touch() {
        lastActivityMillis.set(System.currentTimeMillis())
    }


    val mapper = jacksonObjectMapper()

    fun handlePlayerJoin(player: Player): Boolean {
        if (players.size >= 2) {
            return false
        }
        players.add(player)
        touch()
        if (players.size == 2) {
            broadcastState()
        }
        LobbyManager.broadcastGamesList()
        return true
    }

    fun handleSpectatorJoin(spectator: Spectator) {
        spectators.add(spectator)
        touch()
        sendStateToSpectator(spectator)
    }

    fun handleMove(simpleMove: SimpleMove, playerId: String): Boolean {
        val currentPlayer = players.find { it.id == playerId }
        if (currentPlayer == null || currentPlayer.color != board.sideToMove.toString()) {
            return false
        }

        val promotion = getPromotionPiece(simpleMove)
        val moveToMake = simpleMove.toMove(promotion)

        if (!board.isMoveLegal(moveToMake, true)) {
            return false
        }

        board.doMove(moveToMake)
        lastMove = simpleMove
        touch()

        broadcastState()

        if(board.isMated || board.isDraw) {
            handleGameOver()
        }

        return true
    }

    private fun getPromotionPiece(move: SimpleMove): Piece {
        val fullMove = move.toMove()
        val piece = board.getPiece(fullMove.from)
        val promotion = when (piece) {
            Piece.WHITE_PAWN if fullMove.to.rank.ordinal == 7 -> {
                Piece.WHITE_QUEEN
            }

            Piece.BLACK_PAWN if fullMove.to.rank.ordinal == 0 -> {
                Piece.BLACK_QUEEN
            }
            
            else -> {
                Piece.NONE
            }
        }
        
        return promotion
    }

    private fun buildGameState(): String {
        val payload = mutableMapOf(
            "gameId" to id,
            "boardState" to board.boardToArray(),
            "allowedMoves" to board.legalMoves().map { move -> SimpleMove(move.from.toString(), move.to.toString()) },
            "playerTurn" to players.find { it.color == board.sideToMove.toString() }?.id,
            "gameOver" to (board.isMated || board.isDraw),
            "winner" to when {
                board.isMated -> if (board.sideToMove.toString() == "WHITE") "BLACK" else "WHITE"
                else -> null
            },
            "players" to players.associate { p -> p.id to mapOf("color" to p.color) },
        )
        lastMove?.let {
            payload["lastMove"] = mapOf(
                "from" to it.from,
                "to" to it.to
            )
        }

        val stateMessage = mapOf(
            "type" to "game_state",
            "payload" to payload
        )
        return mapper.writeValueAsString(stateMessage)
    }

    fun handlePlayerLeave(playerId: String) {
        if (gameEnded) {
            return
        }
        if(spectators.find { it.id == playerId } != null) {
            spectators.removeIf { it.id == playerId }
            return
        }
        players.removeIf { it.id == playerId }
        if (players.size == 1) {
            val remaining = players.first()
            val opponentLeftMsg = mapOf(
                "type" to "opponent_left",
                "payload" to mapOf("playerId" to playerId)
            )
            val opponentLeftJson = mapper.writeValueAsString(opponentLeftMsg)

            remaining.session?.remote?.sendString(opponentLeftJson, NoOpWriteCallback)

        }
        val spectatorMsg = mapOf(
            "type" to "player_left",
            "payload" to mapOf("playerId" to playerId)
        )
        val spectatorJson = mapper.writeValueAsString(spectatorMsg)

        spectators.forEach { it.session?.remote?.sendString(spectatorJson, NoOpWriteCallback) }
              
        try {
            GameStore.removeGame(id)
            LobbyManager.broadcastGamesList()
        } catch (e: Exception) {
            println("Failed to remove game from store: ${e.message}")
        }
    }

    private fun sendStateToSpectator(spectator: Spectator)  {
        spectator.session?.remote?.sendString(buildGameState(), NoOpWriteCallback)
    }

    private fun broadcastState() {
        val stateMessage = buildGameState()
        val allSessions = players.map { it.session } + spectators.map { it.session }
        allSessions.filter { it?.isOpen == true }.forEach { s ->
            try {
                s?.remote?.sendString(stateMessage, NoOpWriteCallback)
            } catch (_: Exception) {
            }
        }
    }

    private fun handleGameOver() {
        gameEnded = true
        GameStore.removeGame(id)
        players.clear()
        spectators.clear()
    }
}