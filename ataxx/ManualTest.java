package ataxx;

import org.junit.Test;
import java.io.InputStreamReader;

import static org.junit.Assert.*;

/** Tests of the Manual class.
 *  @author Florence Lau
 */
public class ManualTest {
    Manual makeBoard() {
        Board myBoard = new Board();
        Game myGame = new Game(myBoard,
                new ReaderSource(new InputStreamReader(System.in),
                        true), new TextReporter());
        return new Manual(myGame, PieceColor.RED);
    }

    @Test
    public void testManualBoard() {
        Manual player = makeBoard();
        assertEquals(player.myColor(), PieceColor.RED);
        assertEquals(player.myColor().opposite(), PieceColor.BLUE);
    }
}
