package ataxx;

import org.junit.Test;

import java.io.InputStreamReader;
import java.util.ArrayList;
import static org.junit.Assert.*;

/** Tests of the AI class.
 *  @author Florence Lau
 */

public class AITest {
    AI makeBoard() {
        Board myBoard = new Board();
        Game myGame = new Game(myBoard,
                new ReaderSource(new InputStreamReader(System.in),
                        true), new TextReporter());
        return new AI(myGame, PieceColor.BLUE);
    }

    @Test
    public void testMyMove() {
        AI myAI = makeBoard();
        Move foundMove = myAI.myMove();
        Move bestMove = Move.move('g', '1', 'e', '1');
        ArrayList<Move> moves = myAI.potentialMoves(myAI.board());
        for (Move m : moves) {
            assertTrue(myAI.board().legalMove(m));
        }
        assertEquals(foundMove, bestMove);
    }
}
