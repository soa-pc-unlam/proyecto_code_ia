import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.util.UUID

sealed class WsMessage {
    abstract val type: String
}

/**
 * create_game
 */

data class CreateGameMessage(
    override val type: String,
) : WsMessage()

/*
exit_game

*/
data class ExitGameMessage(
    override val type: String
) : WsMessage()


/**
 * join_game
 */

data class JoinGameMessage(
    override val type: String,
    val payload: JoinGamePayload
) : WsMessage()

data class JoinGamePayload(
    val gameId: String
) {
    init {
        require(isValidUUID(gameId)) { "gameId must be a valid UUID" }
    }

    private fun isValidUUID(s: String): Boolean =
        runCatching { UUID.fromString(s) }.isSuccess
}
/**
 * join_viewer_game
 */
data class JoinGameSpectatorMessage(
    override val type: String,
    val payload: JoinGameSpectatorPayload
) : WsMessage()

data class JoinGameSpectatorPayload(
    val gameId: String
) {
    init {
        require(isValidUUID(gameId)) { "gameId must be a valid UUID" }
    }

    private fun isValidUUID(s: String): Boolean =
        runCatching { UUID.fromString(s) }.isSuccess
}

/**
 * make_move
 */

data class MakeMoveMessage(
    override val type: String,
    val payload: MakeMovePayload
) : WsMessage()

data class MakeMovePayload(
    val from: String,
    val to: String
) {
    init {
        require(isValidSquare(from)) { "Invalid from square: $from" }
        require(isValidSquare(to)) { "Invalid to square: $to" }
    }

    private fun isValidSquare(s: String): Boolean =
        s.length == 2 &&
                s[0] in 'A'..'H' &&
                s[1] in '1'..'8'
}

/**
 * leave_game
 */

data class LeaveGameMessage(
    override val type: String,
) : WsMessage()

/**
 * list_games
 */
data class ListSubGamesMessage(
    override val type: String
) : WsMessage()
data class ListUnsGamesMessage(
    override val type: String
) : WsMessage()


@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
    visible = true,
)
@JsonSubTypes(
    JsonSubTypes.Type(MakeMoveMessage::class, name = "make_move"),
    JsonSubTypes.Type(CreateGameMessage::class, name = "create_game"),
    JsonSubTypes.Type(ExitGameMessage::class, name = "exit_game"),
    JsonSubTypes.Type(JoinGameMessage::class, name = "join_game"),
    JsonSubTypes.Type(JoinGameSpectatorMessage::class, name = "join_viewer_game"),
    JsonSubTypes.Type(LeaveGameMessage::class, name = "leave_game"),
    JsonSubTypes.Type(ListSubGamesMessage::class, name = "subscribe_list_games"),
    JsonSubTypes.Type(ListUnsGamesMessage::class, name = "unsubscribe_list_games")
)
abstract class WsMessageMixin

