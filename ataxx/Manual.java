package ataxx;

import static ataxx.GameException.error;

/** A Player that receives its moves from its Game's getMoveCmnd method.
 *  @author Florence Lau
 */
class Manual extends Player {

    /** A Player that will play MYCOLOR on GAME, taking its moves from
     *  GAME. */
    Manual(Game game, PieceColor myColor) {
        super(game, myColor);
        _game = game;
        _myColor = myColor;
    }

    @Override
    Move myMove() {
        Command myCommand = _game.getMoveCmnd(_myColor + ": ");
        if (myCommand.commandType().equals(Command.Type.PASS)) {
            return Move.pass();
        } else {
            String[] moveArray = myCommand.operands();
            Move result = Move.move(moveArray[0].charAt(0),
                    moveArray[1].charAt(0), moveArray[2].charAt(0),
                    moveArray[3].charAt(0));
            if (result.equals(null)) {
                throw error("that move is illegal.");
            } else {
                return result;
            }
        }
    }

    /** Current game. */
    private Game _game;
    /** Current player. */
    private PieceColor _myColor;

}

