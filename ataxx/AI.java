package ataxx;

import java.util.ArrayList;

import static ataxx.PieceColor.*;
import static java.lang.Math.min;
import static java.lang.Math.max;

/** A Player that computes its own moves.
 *  @author Florence Lau
 */
class AI extends Player {

    /** Maximum minimax search depth before going to static evaluation. */
    private static final int MAX_DEPTH = 4;
    /** A position magnitude indicating a win (for red if positive, blue
     *  if negative). */
    private static final int WINNING_VALUE = Integer.MAX_VALUE - 1;
    /** A magnitude greater than a normal value. */
    private static final int INFTY = Integer.MAX_VALUE;

    /** A new AI for GAME that will play MYCOLOR. */
    AI(Game game, PieceColor myColor) {
        super(game, myColor);
    }

    @Override
    Move myMove() {
        if (!board().canMove(myColor())) {
            return Move.pass();
        }
        Move move = findMove();
        return move;
    }

    /** Return an array list of all legal moves for
     *  whoever's turn it is in BOARD. */
    ArrayList<Move> potentialMoves(Board board) {
        ArrayList<Move> result = new ArrayList<>();
        for (char r = '7'; r >= '1'; r--) {
            for (char c = 'a'; c <= 'g'; c++) {
                if (board.get(board.index(c, r)) == board.whoseMove()) {
                    for (int a = 2; a >= -2; a--) {
                        for (int b = 2; b >= -2; b--) {
                            char movedC = (char) (c + a);
                            char movedR = (char) (r + b);
                            if (!(movedC == c && movedR == r)) {
                                Move temp = Move.move(c, r,
                                        (char) (c + a), (char) (r + b));
                                if (board.legalMove(temp)) {
                                    result.add(temp);
                                }
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    /** Return the best possible move for the
     * maximizer player in the board B using a
     * basic game tree search that is pruned
     * when BETA <= ALPHA. Adapted from
     * Professor Hilfinger's Lecture #22. */
    Move basicMax(Board b, int alpha, int beta) {
        if (b.gameOver()) {
            if (b.redPieces() > b.bluePieces()) {
                return Move.inftyMove();
            } else {
                return Move.negInftyMove();
            }
        } else {
            Move bestMove = Move.negInftyMove();
            int bestScore = -INFTY;
            for (Move m : potentialMoves(b)) {
                b.makeMove(m);
                if (staticScore(b, m) >= bestScore
                        || bestMove.equals(Move.negInftyMove())) {
                    bestMove = m;
                    bestScore = staticScore(b, m);
                    b.undo();
                    alpha = max(bestScore, alpha);
                    if (beta <= alpha) {
                        break;
                    }
                }
            }
            return bestMove;
        }
    }

    /** Return the best possible move for the
     * minimizer player in the board B using a
     * basic game tree search that is pruned
     * when BETA <= ALPHA. Adapted from
     * Professor Hilfinger's Lecture #22. */
    Move basicMin(Board b, int alpha, int beta) {
        if (b.gameOver()) {
            if (b.redPieces() > b.bluePieces()) {
                return Move.inftyMove();
            } else {
                return Move.negInftyMove();
            }
        } else {
            Move bestMove = Move.inftyMove();
            int bestScore = INFTY;
            for (Move m : potentialMoves(b)) {
                b.makeMove(m);
                if (staticScore(b, m) <= bestScore
                        || bestMove.equals(Move.inftyMove())) {
                    bestMove = m;
                    bestScore = staticScore(b, m);
                    b.undo();
                    beta = min(bestScore, beta);
                    if (beta <= alpha) {
                        break;
                    }
                }
            }
            return bestMove;
        }
    }

    /** Return a game tree search for the best possible
     * move on BOARD for the maximizer player that prunes
     * the tree when BETA <= ALPHA up to DEPTH. Adapted from
     * Professor Hilfinger's Lecture #22.*/
    private Move findMax(Board board, int depth, int alpha, int beta) {
        if (depth == 0 || board.gameOver()) {
            return basicMax(board, alpha, beta);
        } else {
            Move bestMove = Move.negInftyMove();
            int bestScore = -INFTY;
            for (Move m : potentialMoves(board)) {
                board.makeMove(m);
                int curr = staticScore(board, m);
                Move opponent = findMin(board, depth - 1, alpha, beta);
                int opponentScore = staticScore(board, opponent);
                board.undo();
                if (opponentScore >= bestScore
                        || bestMove.equals(Move.negInftyMove())) {
                    bestMove = m;
                    bestScore = curr;
                    alpha = max(opponentScore, alpha);
                    if (beta <= alpha) {
                        break;
                    }
                }
            }
            return bestMove;
        }
    }

    /** Return a game tree search for the best possible
     * move on BOARD for the minimizer player that prunes the tree
     * when BETA <= ALPHA up to DEPTH. Adapted from
     * Professor Hilfinger's Lecture #22. */
    private Move findMin(Board board, int depth, int alpha, int beta) {
        if (depth == 0 || board.gameOver()) {
            return basicMin(board, alpha, beta);
        } else {
            Move bestMove = Move.inftyMove();
            int bestScore = INFTY;
            for (Move m : potentialMoves(board)) {
                board.makeMove(m);
                int curr = staticScore(board, m);
                Move opponent = findMax(board, depth - 1, alpha, beta);
                int opponentScore = staticScore(board, opponent);
                board.undo();
                if (opponentScore <= bestScore
                        || bestMove.equals(Move.inftyMove())) {
                    bestMove = m;
                    bestScore = curr;
                    beta = min(opponentScore, beta);
                    if (beta <= alpha) {
                        break;
                    }
                }
            }
            return bestMove;
        }
    }

    /** Return a move for me from the current position, assuming there
     *  is a move. Adapted from Professor Hilfinger's Lecture #22.*/
    private Move findMove() {
        Board b = new Board(board());
        if (myColor() == RED) {
            return findMax(b, MAX_DEPTH, -INFTY, INFTY);
        } else {
            return findMin(b, MAX_DEPTH, -INFTY, INFTY);
        }
    }

    /** Used to communicate best moves found by findMove, when asked for. */
    private Move _lastFoundMove;

    /** Return a heuristic value for BOARD while
     * checking if MOVE has a static score of
     * negative infinity or infinity. */
    private int staticScore(Board board, Move move) {
        if (move.isInftyMove()) {
            return INFTY;
        } else if (move.isNegInftyMove()) {
            return INFTY * -1;
        } else {
            return (board.redPieces() - board.bluePieces());
        }
    }
}
