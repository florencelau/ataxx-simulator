package ataxx;

/* Author: P. N. Hilfinger, (C) 2008. */

import java.util.Observable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;
import java.util.List;
import java.util.Arrays;
import java.util.Formatter;

import static ataxx.PieceColor.*;
import static ataxx.GameException.error;

/** An Ataxx board.   The squares are labeled by column (a char value between
 *  'a' - 2 and 'g' + 2) and row (a char value between '1' - 2 and '7'
 *  + 2) or by linearized index, an integer described below.  Values of
 *  the column outside 'a' and 'g' and of the row outside '1' to '7' denote
 *  two layers of border squares, which are always blocked.
 *  This artificial border (which is never actually printed) is a common
 *  trick that allows one to avoid testing for edge conditions.
 *  For example, to look at all the possible moves from a square, sq,
 *  on the normal board (i.e., not in the border region), one can simply
 *  look at all squares within two rows and columns of sq without worrying
 *  about going off the board. Since squares in the border region are
 *  blocked, the normal logic that prevents moving to a blocked square
 *  will apply.
 *
 *  For some purposes, it is useful to refer to squares using a single
 *  integer, which we call its "linearized index".  This is simply the
 *  number of the square in row-major order (counting from 0).
 *
 *  Moves on this board are denoted by Moves.
 *  @author Florence Lau
 */
class Board extends Observable {

    /** Number of squares on a side of the board. */
    static final int SIDE = 7;
    /** Length of a side + an artificial 2-deep border region. */
    static final int EXTENDED_SIDE = SIDE + 4;

    /** Number of non-extending moves before game ends. */
    static final int JUMP_LIMIT = 25;

    /** A new, cleared board at the start of the game. */
    Board() {
        _board = new PieceColor[EXTENDED_SIDE * EXTENDED_SIDE];
        clear();
    }

    /** A copy of B. */
    Board(Board b) {
        _board = b._board.clone();
        _whoseMove = b._whoseMove;
        numRed = b.numRed;
        numBlue = b.numBlue;
        numMoves = b.numMoves;
        numJumps = b.numJumps;
        _allMoves = b._allMoves;
        changesStack = b.changesStack;
        piecesStack = b.piecesStack;
    }

    /** Return the linearized index of square COL ROW. */
    static int index(char col, char row) {
        return (row - '1' + 2) * EXTENDED_SIDE + (col - 'a' + 2);
    }

    /** Return the linearized index of the square that is DC columns and DR
     *  rows away from the square with index SQ. */
    static int neighbor(int sq, int dc, int dr) {
        return sq + dc + dr * EXTENDED_SIDE;
    }

    /** Clear me to my starting state, with pieces in their initial
     *  positions and no blocks. */
    void clear() {
        _whoseMove = RED;
        numRed = 0;
        numBlue = 0;
        numMoves = 0;
        numJumps = 0;
        _allMoves = new ArrayList<>();
        changesStack = new Stack<>();
        piecesStack = new Stack<>();
        for (int i = 0; i < EXTENDED_SIDE * EXTENDED_SIDE; i++) {
            _board[i] = BLOCKED;
        }
        for (char c = 'a'; c <= 'g'; c++) {
            for (char r = '1'; r <= '7'; r++) {
                unrecordedSet(c, r, EMPTY);
            }
        }
        unrecordedSet('a', '1', BLUE);
        unrecordedSet('a', '7', RED);
        unrecordedSet('g', '1', RED);
        unrecordedSet('g', '7', BLUE);
        setChanged();
        notifyObservers();
    }

    /** Return true iff the game is over: i.e., if neither side has
     *  any moves, if one side has no pieces, or if there have been
     *  MAX_JUMPS consecutive jumps without intervening extends. */
    boolean gameOver() {
        if ((!canMove(BLUE) && !canMove(RED)) || redPieces() == 0
                || bluePieces() == 0 || numJumps() == JUMP_LIMIT) {
            return true;
        }
        return false;
    }

    /** Return number of red pieces on the board. */
    int redPieces() {
        return numPieces(RED);
    }

    /** Return number of blue pieces on the board. */
    int bluePieces() {
        return numPieces(BLUE);
    }

    /** Return number of COLOR pieces on the board. */
    int numPieces(PieceColor color) {
        if (color == RED) {
            return numRed;
        } else {
            return numBlue;
        }
    }

    /** Increment numPieces(COLOR) by K. */
    private void incrPieces(PieceColor color, int k) {
        if (color == RED) {
            numRed += k;
        } else if (color == BLUE) {
            numBlue += k;
        }
    }

    /** The current contents of square CR, where 'a'-2 <= C <= 'g'+2, and
     *  '1'-2 <= R <= '7'+2.  Squares outside the range a1-g7 are all
     *  BLOCKED.  Returns the same value as get(index(C, R)). */
    PieceColor get(char c, char r) {
        return _board[index(c, r)];
    }

    /** Return the current contents of square with linearized index SQ. */
    PieceColor get(int sq) {
        return _board[sq];
    }

    /** Set get(C, R) to V, where 'a' <= C <= 'g', and
     *  '1' <= R <= '7'. */
    private void set(char c, char r, PieceColor v) {
        set(index(c, r), v);
    }

    /** Set square with linearized index SQ to V.  This operation is
     *  undoable. */
    private void set(int sq, PieceColor v) {
        if (get(sq) != v) {
            _board[sq] = v;
            incrPieces(v, 1);
        }
    }

    /** Set square at C R to V (not undoable). */
    private void unrecordedSet(char c, char r, PieceColor v) {
        if (get(index(c, r)) != v) {
            _board[index(c, r)] = v;
            incrPieces(v, 1);
        }
    }

    /** Set square at linearized index SQ to V (not undoable). */
    private void unrecordedSet(int sq, PieceColor v) {
        if (get(sq) != v) {
            _board[sq] = v;
            incrPieces(v, 1);
        }
    }

    /** Return true iff MOVE is legal on the current board. */
    boolean legalMove(Move move) {
        if (whoseMove() != get(move.fromIndex())
                || get(move.toIndex()) != EMPTY || !(move.isJump()
                || move.isExtend())) {
            return false;
        }
        return true;
    }

    /** Return true iff player WHO can move, ignoring whether it is
     *  that player's move and whether the game is over. */
    boolean canMove(PieceColor who) {
        for (char r = '7'; r >= '1'; r--) {
            for (char c = 'a'; c <= 'g'; c++) {
                if (get(c, r) == who) {
                    int i = index(c, r);
                    for (int a = 2; a >= -2; a--) {
                        for (int b = 2; b >= -2; b--) {
                            if (!(a == 0 && b == 0)) {
                                if (get(neighbor(i, a, b)) == EMPTY) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    /** Return the color of the player who has the next move.  The
     *  value is arbitrary if gameOver(). */
    PieceColor whoseMove() {
        return _whoseMove;
    }

    /** Return total number of moves and passes since the last
     *  clear or the creation of the board. */
    int numMoves() {
        return numMoves;
    }

    /** Return number of non-pass moves made in the current game since the
     *  last extend move added a piece to the board (or since the
     *  start of the game). Used to detect end-of-game. */
    int numJumps() {
        return numJumps;
    }

    /** Perform the move C0R0-C1R1, or pass if C0 is '-'.  For moves
     *  other than pass, assumes that legalMove(C0, R0, C1, R1). */
    void makeMove(char c0, char r0, char c1, char r1) {
        if (c0 == '-') {
            makeMove(Move.pass());
        } else {
            makeMove(Move.move(c0, r0, c1, r1));
        }
    }

    /** Make the MOVE on this Board, assuming it is legal. */
    void makeMove(Move move) {
        if (move.isPass()) {
            pass();
            return;
        }
        if (!legalMove(move)) {
            throw error("that move is illegal.");
        }
        assert legalMove(move);
        _allMoves.add(move);
        int[] changes = new int[4];
        changes[0] = redPieces();
        changes[1] = bluePieces();
        changes[2] = numJumps();
        changes[3] = numMoves();
        changesStack.push(changes);
        ArrayList<Integer> pieces = new ArrayList<>();
        PieceColor target = get(move.col0(), move.row0());
        if (move.isJump()) {
            set(move.col1(), move.row1(), target);
            set(move.col0(), move.row0(), EMPTY);
            incrPieces(target, -1);
            numJumps += 1;
        } else if (move.isExtend()) {
            set(move.col1(), move.row1(), target);
            numJumps = 0;
        }
        int i = index(move.col1(), move.row1());
        for (int a = 1; a >= -1; a--) {
            for (int b = 1; b >= -1; b--) {
                if (get(neighbor(i, a, b)) == target.opposite()) {
                    set(neighbor(i, a, b), target);
                    pieces.add(neighbor(i, a, b));
                    incrPieces(target.opposite(), -1);
                }
            }
        }
        piecesStack.push(pieces);
        numMoves += 1;
        PieceColor opponent = _whoseMove.opposite();
        _whoseMove = opponent;
        setChanged();
        notifyObservers();
    }

    /** Make an unrecorded MOVE on this Board. Only used for setup. */
    void makeUnrecordedMove(Move move) {
        if (!legalMove(move)) {
            throw error("that move is illegal.");
        }
        assert legalMove(move);
        if (move.isPass()) {
            pass();
            return;
        }
        PieceColor target = get(move.col0(), move.row0());
        if (move.isJump()) {
            unrecordedSet(move.col1(), move.row1(), target);
            unrecordedSet(move.col0(), move.row0(), EMPTY);
            incrPieces(target, -1);
        } else if (move.isExtend()) {
            unrecordedSet(move.col1(), move.row1(), target);
        }
        unrecordedSetNeighbors(move, target);
        PieceColor opponent = _whoseMove.opposite();
        _whoseMove = opponent;
        setChanged();
        notifyObservers();
    }

    /** Set squares around DEST to the TARGET color. */
    void setNeighbors(Move dest, PieceColor target) {
        int i = index(dest.col1(), dest.row1());
        for (int a = 1; a >= -1; a--) {
            for (int b = 1; b >= -1; b--) {
                if (get(neighbor(i, a, b)) == target.opposite()) {
                    set(neighbor(i, a, b), target);
                }
            }
        }
    }

    /** Set squares around DEST to the TARGET color without recording.
     * Only for setup. */
    void unrecordedSetNeighbors(Move dest, PieceColor target) {
        int i = index(dest.col1(), dest.row1());
        for (int a = 1; a >= -1; a--) {
            for (int b = 1; b >= -1; b--) {
                if (get(neighbor(i, a, b)) == target.opposite()) {
                    unrecordedSet(neighbor(i, a, b), target);
                    incrPieces(target.opposite(), -1);
                }
            }
        }
    }

    /** Update to indicate that the current player passes, assuming it
     *  is legal to do so.  The only effect is to change whoseMove(). */
    void pass() {
        if (canMove(_whoseMove)) {
            throw error("that move is illegal.");
        } else {
            PieceColor opponent = _whoseMove.opposite();
            _whoseMove = opponent;
            numMoves += 1;
            setChanged();
            notifyObservers();
        }
    }

    /** Undo the last move. */
    void undo() {
        int last = _allMoves.size() - 1;
        Move lastMove = _allMoves.get(last);
        PieceColor target = get(lastMove.toIndex()).opposite();
        ArrayList<Integer> myPieces = piecesStack.pop();
        for (int sq : myPieces) {
            set(sq, target);
        }
        if (lastMove.isJump()) {
            set(lastMove.fromIndex(), get(lastMove.toIndex()));
            set(lastMove.toIndex(), EMPTY);
        } else if (lastMove.isExtend()) {
            set(lastMove.toIndex(), EMPTY);
        }
        int[] myChanges = changesStack.pop();
        numRed = myChanges[0];
        numBlue = myChanges[1];
        numJumps = myChanges[2];
        numMoves = myChanges[3];
        _whoseMove = _whoseMove.opposite();
        _allMoves.remove(last);
        setChanged();
        notifyObservers();
    }

    /** Return true iff it is legal to place a block at C R. */
    boolean legalBlock(char c, char r) {
        if (get(c, r) != EMPTY || (c == 'a' && r == '1')
                || (c == 'a' && r == '7') || (c == 'g' && r == '1')
                || (c == 'g' && r == '7')) {
            return false;
        }
        return true;
    }

    /** Return true iff it is legal to place a block at CR. */
    boolean legalBlock(String cr) {
        return legalBlock(cr.charAt(0), cr.charAt(1));
    }

    /** Set a block on the square C R and its reflections across the middle
     *  row and/or column, if that square is unoccupied and not
     *  in one of the corners. Has no effect if any of the squares is
     *  already occupied by a block.  It is an error to place a block on a
     *  piece. */
    void setBlock(char c, char r) {
        if (!legalBlock(c, r)) {
            throw error("illegal block placement");
        }
        unrecordedSet(c, r, BLOCKED);
        char reflectedC = reflections.get(c);
        char reflectedR = reflections.get(r);
        unrecordedSet(reflectedC, reflectedR, BLOCKED);
        unrecordedSet(c, reflectedR, BLOCKED);
        unrecordedSet(reflectedC, r, BLOCKED);
        setChanged();
        notifyObservers();
    }

    /** Place a block at CR. */
    void setBlock(String cr) {
        setBlock(cr.charAt(0), cr.charAt(1));
    }

    /** Return a list of all moves made since the last clear (or start of
     *  game). */
    List<Move> allMoves() {
        return _allMoves;
    }

    @Override
    public String toString() {
        return toString(false);
    }

    /* .equals used only for testing purposes. */
    @Override
    public boolean equals(Object obj) {
        Board other = (Board) obj;
        return (Arrays.equals(_board, other._board)
                && _allMoves.equals(other._allMoves)
                && _whoseMove.equals(other._whoseMove)
                && numRed == other.numRed
                && numBlue == other.numBlue
                && numMoves == other.numMoves
                && numJumps == other.numJumps);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(_board);
    }

    /** Return a text depiction of the board (not a dump).  If LEGEND,
     *  supply row and column numbers around the edges. */
    String toString(boolean legend) {
        Formatter out = new Formatter();
        String colNum = "    a b c d e f g";
        boolean end = false;
        if (legend) {
            char rowNum = '0';
            for (char r = '7'; r >= '1'; r--) {
                for (char c = 'a'; c <= 'g'; c++) {
                    if (c == 'a') {
                        rowNum += 1;
                        out.format(" %s", rowNum);
                    }
                    if (get(c, r) == EMPTY) {
                        out.format(" -");
                    } else if (get(c, r) == BLOCKED) {
                        out.format(" X");
                    } else if (get(c, r) == RED) {
                        out.format(" r");
                    } else if (get(c, r) == BLUE) {
                        out.format(" b");
                    }
                }
                out.format("%n");
            }
            out.format(colNum);
        } else {
            for (char r = '7'; r >= '1'; r--) {
                out.format(" ");
                for (char c = 'a'; c <= 'g'; c++) {
                    if (r == '1' && c == 'g') {
                        end = true;
                    }
                    if (get(c, r) == EMPTY) {
                        out.format(" -");
                    } else if (get(c, r) == BLOCKED) {
                        out.format(" X");
                    } else if (get(c, r) == RED) {
                        out.format(" r");
                    } else if (get(c, r) == BLUE) {
                        out.format(" b");
                    }
                }
                if (!end) {
                    out.format("%n");
                }
            }
        }
        return out.toString();
    }

    /** For reasons of efficiency in copying the board,
     *  we use a 1D array to represent it, using the usual access
     *  algorithm: row r, column c => index(r, c).
     *
     *  Next, instead of using a 7x7 board, we use an 11x11 board in
     *  which the outer two rows and columns are blocks, and
     *  row 2, column 2 actually represents row 0, column 0
     *  of the real board.  As a result of this trick, there is no
     *  need to special-case being near the edge: we don't move
     *  off the edge because it looks blocked.
     *
     *  Using characters as indices, it follows that if 'a' <= c <= 'g'
     *  and '1' <= r <= '7', then row c, column r of the board corresponds
     *  to board[(c -'a' + 2) + 11 (r - '1' + 2) ], or by a little
     *  re-grouping of terms, board[c + 11 * r + SQUARE_CORRECTION]. */
    private final PieceColor[] _board;

    /** Stack of arrays of instance variables
     * for the purposes of undo. */
    private Stack<int[]> changesStack;

    /** Stack of ArrayLists of pieces that have been flipped. */
    private Stack<ArrayList<Integer>> piecesStack;

    /** List of all moves. */
    private List<Move> _allMoves;

    /** Player that is on move. */
    private PieceColor _whoseMove;

    /** Number of red pieces. */
    private int numRed;

    /** Number of blue pieces. */
    private int numBlue;

    /** Number of total moves made. */
    private int numMoves;

    /** Number of jumps. */
    private int numJumps;

    /** Hashmap of reflections. */
    private final HashMap<Character, Character> reflections
            = new HashMap<>();
    {
        reflections.put('a', 'g');
        reflections.put('g', 'a');
        reflections.put('b', 'f');
        reflections.put('f', 'b');
        reflections.put('c', 'e');
        reflections.put('e', 'c');
        reflections.put('d', 'd');
        reflections.put('1', '7');
        reflections.put('7', '1');
        reflections.put('2', '6');
        reflections.put('6', '2');
        reflections.put('3', '5');
        reflections.put('5', '3');
        reflections.put('4', '4');
    }
}
