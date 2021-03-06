package ataxx;

/* Author: P. N. Hilfinger */

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Random;
import java.util.function.Consumer;

import static ataxx.PieceColor.*;
import static ataxx.Game.State.*;
import static ataxx.Command.Type.*;
import static ataxx.GameException.error;

/** Controls the play of the game.
 *  @author Florence Lau
 */
class Game {

    /** States of play. */
    static enum State {
        SETUP, PLAYING, FINISHED;
    }

    /** A new Game, using BOARD to play on, reading initially from
     *  BASESOURCE and using REPORTER for error and informational messages. */
    Game(Board board, CommandSource baseSource, Reporter reporter) {
        _inputs.addSource(baseSource);
        _board = board;
        _reporter = reporter;
    }

    /** Execute moves for player P in process. */
    void executeMoves(Player p) {
        Move move = p.myMove();
        if (_state == PLAYING) {
            _board.makeMove(move);
            if (p.myColor() == RED) {
                if (redAuto) {
                    if (move.isPass()) {
                        reportMove("Red passes.");
                    } else {
                        reportMove("Red moves %s.", move);
                    }
                }
            } else {
                if (blueAuto) {
                    if (move.isPass()) {
                        reportMove("Blue passes.");
                    } else {
                        reportMove("Blue moves %s.", move);
                    }
                }
            }
        }
    }

    /** Run a session of Ataxx gaming.  Use an AtaxxGUI iff USEGUI. */
    void process(boolean useGUI) {
        Player red, blue;
        _state = SETUP;
        GameLoop:
        while (true) {
            doClear(null);
            SetupLoop:
            while (_state == SETUP) {
                doCommand();
            }
            if (!redAuto) {
                red = new Manual(this, RED);
            } else {
                red = new AI(this, RED);
            }
            if (!blueAuto) {
                blue = new Manual(this, BLUE);
            } else {
                blue = new AI(this, BLUE);
            }
            _state = PLAYING;
            while (_state != SETUP && !_board.gameOver()) {
                try {
                    if (_board.whoseMove() == RED) {
                        executeMoves(red);
                    } else {
                        executeMoves(blue);
                    }
                } catch (GameException excp) {
                    reportError(excp.getMessage());
                }
            }
            if (_state != SETUP) {
                reportWinner();
            }
            if (_state == PLAYING) {
                _state = FINISHED;
            }
            while (_state == FINISHED) {
                doCommand();
            }
        }
    }

    /** Return a view of my game board that should not be modified by
     *  the caller. */
    Board board() {
        return _board;
    }

    /** Perform the next command from our input source. */
    void doCommand() {
        try {
            Command cmnd =
                Command.parseCommand(_inputs.getLine("ataxx: "));
            if (cmnd.commandType() == QUIT
                    || cmnd.commandType() == CLEAR
                    || cmnd.commandType() == HELP
                    || cmnd.commandType() == DUMP
                    || cmnd.commandType() == ERROR
                    || cmnd.commandType() == START) {
                _commands.get(cmnd.commandType()).accept(null);
            } else {
                String[] args = cmnd.operands();
                _commands.get(cmnd.commandType()).accept(args);
            }
        } catch (GameException excp) {
            _reporter.errMsg(excp.getMessage());
        }
    }

    /** Read and execute commands until encountering a move or until
     *  the game leaves playing state due to one of the commands. Return
     *  the terminating move command, or null if the game first drops out
     *  of playing mode. If appropriate to the current input source, use
     *  PROMPT to prompt for input. */
    Command getMoveCmnd(String prompt) {
        while (_state == PLAYING) {
            try {
                Command cmnd = Command.parseCommand(_inputs.getLine(prompt));
                if (cmnd.commandType() == PIECEMOVE
                        || cmnd.commandType() == PASS) {
                    return cmnd;
                } else {
                    if (cmnd.commandType() == QUIT
                            || cmnd.commandType() == CLEAR
                            || cmnd.commandType() == HELP
                            || cmnd.commandType() == DUMP
                            || cmnd.commandType() == ERROR) {
                        _commands.get(cmnd.commandType()).accept(null);
                    } else {
                        String[] args = cmnd.operands();
                        _commands.get(cmnd.commandType()).accept(args);
                    }
                }
            } catch (GameException excp) {
                reportError(excp.getMessage());
            }
        }
        return null;
    }

    /** Return random integer between 0 (inclusive) and MAX>0 (exclusive). */
    int nextRandom(int max) {
        return _randoms.nextInt(max);
    }

    /** Report a move, using a message formed from FORMAT and ARGS as
     *  for String.format. */
    void reportMove(String format, Object... args) {
        _reporter.moveMsg(format, args);
    }

    /** Report an error, using a message formed from FORMAT and ARGS as
     *  for String.format. */
    void reportError(String format, Object... args) {
        _reporter.errMsg(format, args);
    }

    /* Command Processors */

    /** Perform the command 'auto OPERANDS[0]'. */
    void doAuto(String[] operands) {
        checkState("auto", SETUP);
        if (operands[0].matches("(?i)red")) {
            redAuto = true;
        } else if (operands[0].matches("(?i)blue")) {
            blueAuto = true;
        }
    }

    /** Perform a 'help' command. */
    void doHelp(String[] unused) {
        InputStream helpIn =
            Game.class.getClassLoader().getResourceAsStream("ataxx/help.txt");
        if (helpIn == null) {
            System.err.println("No help available.");
        } else {
            try {
                BufferedReader r
                    = new BufferedReader(new InputStreamReader(helpIn));
                while (true) {
                    String line = r.readLine();
                    if (line == null) {
                        break;
                    }
                    System.out.println(line);
                }
                r.close();
            } catch (IOException e) {
                /* Ignore IOException */
            }
        }
    }

    /** Perform the command 'load OPERANDS[0]'. */
    void doLoad(String[] operands) {
        try {
            FileReader reader = new FileReader(operands[0]);
            CommandSource mySource = new ReaderSource(reader, false);
            _inputs.addSource(mySource);
        } catch (IOException e) {
            throw error("Cannot open file %s", operands[0]);
        }
    }

    /** Perform the command 'manual OPERANDS[0]'. */
    void doManual(String[] operands) {
        checkState("manual", SETUP);
        if (operands[0].matches("(?i)red")) {
            redAuto = false;
        } else if (operands[0].matches("(?i)blue")) {
            blueAuto = false;
        }

    }

    /** Exit the program. */
    void doQuit(String[] unused) {
        System.exit(0);
    }

    /** Perform the command 'start'. */
    void doStart(String[] unused) {
        checkState("start", SETUP);
        _state = PLAYING;
    }

    /** Perform the move OPERANDS[0]. */
    void doMove(String[] operands) {
        try {
            Move myMove = Move.move(operands[0].charAt(0),
                    operands[1].charAt(0), operands[2].charAt(0),
                    operands[3].charAt(0));
            if (myMove == null) {
                reportError("that move is illegal.");
            } else {
                _board.makeUnrecordedMove(myMove);
                firstMove = true;
            }
        } catch (GameException excp) {
            reportError(excp.getMessage());
        }
    }

    /** Cause current player to pass. */
    void doPass(String[] unused) {
        try {
            _board.pass();
        } catch (GameException excp) {
            reportError(excp.getMessage());
        }
    }

    /** Perform the command 'clear'. */
    void doClear(String[] unused) {
        if (_state == PLAYING) {
            _board.clear();
            process(false);
        } else {
            _board.clear();
        }
        _state = SETUP;
        redAuto = false;
        blueAuto = true;
        firstMove = false;
    }

    /** Perform the command 'dump'. */
    void doDump(String[] unused) {
        System.out.println("===");
        System.out.println(_board.toString());
        System.out.println("===");
    }

    /** Execute 'seed OPERANDS[0]' command, where
     * the operand is a string of decimal digits.
     * Silently substitutes another value if too large. */
    void doSeed(String[] operands) {
    }

    /** Execute the command 'block OPERANDS[0]'. */
    void doBlock(String[] operands) {
        checkState("block", SETUP);
        if (!firstMove) {
            try {
                _board.setBlock(operands[0]);
            } catch (GameException excp) {
                reportError(excp.getMessage());
            }
        } else {
            reportError("Can only add blocks to "
                    + "initial configuration.");
        }
    }

    /** Execute the artificial 'error' command. */
    void doError(String[] unused) {
        throw error("Command not understood");
    }

    /** Report the outcome of the current game. */
    void reportWinner() {
        String msg;
        if (_board.redPieces() == _board.bluePieces()) {
            msg = "Draw.";
        } else if (_board.redPieces() < _board.bluePieces()) {
            msg = "Blue wins.";
        } else {
            msg = "Red wins.";
        }
        _reporter.outcomeMsg(msg);
    }

    /** Check that game is currently in one of the states STATES, assuming
     *  CMND is the command to be executed. */
    private void checkState(Command cmnd, State... states) {
        for (State s : states) {
            if (s == _state) {
                return;
            }
        }
        throw error("'%s' command is not allowed now.", cmnd.commandType());
    }

    /** Check that game is currently in one of the states STATES, using
     *  CMND in error messages as the name of the command to be executed. */
    private void checkState(String cmnd, State... states) {
        for (State s : states) {
            if (s == _state) {
                return;
            }
        }
        throw error("'%s' command is not allowed now.", cmnd);
    }

    /** Mapping of command types to methods that process them. */
    private final HashMap<Command.Type, Consumer<String[]>> _commands =
        new HashMap<>();

    {
        _commands.put(AUTO, this::doAuto);
        _commands.put(BLOCK, this::doBlock);
        _commands.put(CLEAR, this::doClear);
        _commands.put(DUMP, this::doDump);
        _commands.put(HELP, this::doHelp);
        _commands.put(MANUAL, this::doManual);
        _commands.put(PASS, this::doPass);
        _commands.put(PIECEMOVE, this::doMove);
        _commands.put(SEED, this::doSeed);
        _commands.put(START, this::doStart);
        _commands.put(LOAD, this::doLoad);
        _commands.put(QUIT, this::doQuit);
        _commands.put(ERROR, this::doError);
        _commands.put(EOF, this::doQuit);
    }

    /** Input source. */
    private final CommandSources _inputs = new CommandSources();

    /** My board. */
    private Board _board;
    /** Current game state. */
    private State _state;
    /** Used to send messages to the user. */
    private Reporter _reporter;
    /** Source of pseudo-random numbers (used by AIs). */
    private Random _randoms = new Random();
    /** True iff red is AI and false iff red is manual. */
    private boolean redAuto = false;
    /** True iff blue is AI and false iff blue is manual. */
    private boolean blueAuto = true;
    /** True iff first move has been made on board. */
    private boolean firstMove = false;
}
