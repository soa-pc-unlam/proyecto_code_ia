import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import org.eclipse.jetty.websocket.api.Session

data class SimpleMove(val from: String, val to: String) {
    fun toMove(promotion: Piece = Piece.NONE): Move {
        return Move(Square.valueOf(from), Square.valueOf(to), promotion)
    }
}
data class Player(val id: String, val session: Session?, val color: String)
data class Spectator(val id: String, val session: Session?)
