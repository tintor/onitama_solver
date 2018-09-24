import java.util.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.io.IOException;
import java.util.concurrent.*;

// TODO Player interface: Human, Single-threaded, Multi-threaded (with args)
// TODO Experiment with different hash functions (how to combine pawns and masters? maybe square the pawn diff).
// TODO Precompute masters for heuristic
// TODO Remove remaining_states counter
// TODO Pre-allocate max_depth x State[40] in each Minimax to reduce GC pressure

// Board chars
// 'R' 'r'
// 'B' 'b'
// ' '
// 'x' jump
// Red goes down
// Blue goes up

class Bits {
	static long clear(long a, int i) {
		long m = 0x1f;
		m <<= i * 5;
		return a & ~m;
	}
	
	static int get(long a, int i) {
		int e = (int)(a >> (i * 5));
		return e & 0x1F;
	}

	static long add(int i, int v) {
		return ((long)v) << (i * 5);
	}
	
	static long write(long a, int i, int v) {
		return clear(a, i) | add(i, v);
	}
}

class Board {
	// format is 10 pieces x 5 bits: BbbbbRrrrr
	// 5 bits decode to board position 1-25 or 0 (captured)
	// pawn positions are sorted
	static long encode(String board) {
		long a = 0;
		int blue = 1, red = 6;
		for (int i = 0; i < 25; i++) {
			char c = board.charAt(i);
			if (c == 'B') a |= Bits.add(0, i + 1);
			if (c == 'R') a |= Bits.add(5, i + 1);
			if (c == 'b') a |= Bits.add(blue++, i + 1);
			if (c == 'r') a |= Bits.add(red++, i + 1);
		}
		return a;
	}

	static long encode(char[] board) {
		long a = 0;
		int blue = 1, red = 6;
		for (int i = 0; i < 25; i++) {
			char c = board[i];
			if (c == 'B') a |= Bits.add(0, i + 1);
			if (c == 'R') a |= Bits.add(5, i + 1);
			if (c == 'b') a |= Bits.add(blue++, i + 1);
			if (c == 'r') a |= Bits.add(red++, i + 1);
		}
		return a;
	}

	static char[] decode(char[] a, long board) {
		Arrays.fill(a, ' ');
		for (int i = 0; i < 10; i++) {
			int v = Bits.get(board, i);
			if (v != 0) {
				char c = (i < 5) ? 'b' : 'r';
				if (i == 0) c = 'B';
				if (i == 5) c = 'R';
				a[v - 1] = c;
			}
		}
		return a;
	}

	static long jump(char[] a, long board, int ord, int dest) {
		assert 0 <= ord && ord < 10;
		assert 0 <= dest && dest < 25;
		//if (ord == 0 || ord == 4 || ord == 5 || ord == 9)
		//	return Bits.clear(board, ord) | Bits.add(ord, pos);
		int src = Bits.get(board, ord) - 1;
		decode(a, board);
		a[dest] = a[src];
		a[src] = ' ';
		return encode(a);
	}
	
    /*static int read_pos(long board, boolean red, int ord) {
		return Bits.get(board, red ? ord + 5 : ord);
	}

	// will also shift others
	static long remove_pos(long board, boolean red, int ord) {
		if (red)
			ord += 5;		
		if (ord == 0 || ord == 5)
			return Bits.clear(board, ord);
		board = Bits.clear(board, ord);
		while (ord % 5 != 4) {
			board = Bits.add();
		} 
	}

	long jump(int piece, int dest) {
		long b = board;
		int src = my_piece_pos(piece);

		for (int i = 0; i < 5; i++)
			if (read_pos(b, next != RED, i) == dest) {
				b = write
				b = sort(b, 
				b = remove_pos(b, next != RED, i);
				break;
			}
		b = Bits.write(b, piece, dest);

		b = remove_pos(b, next == RED, piece);
		b = insert_pos(b, next == RED, dest);
		return b;

		// TODO perform the jump in place (avoid decode & encode) (also may need to remove enemy piece)
		/*char[] a = decode(board);
		a[dest] = a[src];
		a[src] = ' ';
		return encode(new String(a));
	}*/

	static final long initial = encode(
		"rrRrr"+
		"     "+
		"     "+
		"     "+
		"bbBbb");
}

class Card {
	String name;
	String print;
	int[] moves;

	static String readFile(String path) {
		try {
			byte[] encoded = Files.readAllBytes(Paths.get(path));
			return new String(encoded, Charset.defaultCharset());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	static void init() {
		String[] lines = readFile("cards.txt").split("\n");
		cards = new Card[lines.length / 6];
		for (int i = 0; i < cards.length; i++) {
			Card c = cards[i] = new Card();			
			c.name = lines[i*6];
			c.print = lines[i*6+1] + lines[i*6+2] + lines[i*6+3] + lines[i*6+4] + lines[i*6+5];

			int m = 0;
			for (int j = 0; j < 25; j++)
				if (c.print.charAt(j) == 'x')
					m += 1;
			c.moves = new int[m];
			m = 0;
			for (int j = 0; j < 25; j++)
				if (c.print.charAt(j) == 'x')
					c.moves[m++] = j;
		}
	}

	static Card[] cards;
	
	static byte[] deal(Random r) {
		byte[] w = new byte[cards.length];
		for (int i = 0; i < cards.length; i++)
			w[i] = (byte)i;
		for (int i = cards.length - 1; i >= 0; i--) {
			int j = r.nextInt(i + 1);
			byte t = w[i];
			w[i] = w[j];
			w[j] = t;
		}
		return w;
	}

	static int jump_dest(int p, boolean rotate, int i) {
		int xi = i%5 - 2, yi = i/5 - 2;
		if (rotate) { xi = -xi; yi = -yi; }
		int x = p%5 + xi, y = p/5 + yi;
		if (0 <= x && x < 5 && 0 <= y && y < 5)
			return x + y * 5;
		return -1;
	}
}

class State {
	final long board;
	final byte blue0, blue1, red0, red1, extra;
	final byte next;
	static final byte NONE = 0, RED = 1, BLUE = 2;
	final int depth;
	
	State(long board, byte blue0, byte blue1, byte red0, byte red1, byte extra, byte next, int depth) {
		this.board = board;
		this.blue0 = (byte)Math.min(blue0, blue1);
		this.blue1 = (byte)Math.max(blue0, blue1);
		this.red0 = (byte)Math.min(red0, red1);
		this.red1 = (byte)Math.max(red0, red1);
		this.extra = extra;
		this.next = next;
		this.depth = depth;
	}

	@Override
	public boolean equals(Object o) {
		State b = (State)o;
		return board == b.board && extra == b.extra && next == b.next
				&& blue0 == b.blue0 && blue1 == b.blue1 && red0 == b.red0 && red1 == b.red1;
	}

	@Override
	public int hashCode() {
		int h = 0;
		h = h * 37 + next;
		h = h * 37 + Long.hashCode(board);
		h = h * 37 + blue0;
		h = h * 37 + blue1;
		h = h * 37 + red0;
		h = h * 37 + red1;
		h = h * 37 + extra;
		return h;
	}

	int pawn_diff() {
		int d = 0;
		for (int i = 1; i < 5; i++)
			if (piece_pos(i) != -1)
				d += 1;
		for (int i = 6; i < 10; i++)
			if (piece_pos(i) != -1)
				d -= 1;
		return d;
	}

	int master_diff() {
		int b = piece_pos(0);
		int r = piece_pos(5);
		// TODO instead of Manhattan distance, use moves from the cards! or even better continue playing game just with masters
		// TODO can we precompute end games with just masters?
		return - (Math.max(Math.abs(b / 5), Math.abs(b % 5 - 2)) - Math.max(Math.abs(r / 5 - 4), Math.abs(r % 5 - 2)));
	}

	// returns -1 if piece is captured
	int piece_pos(int ord) {
		assert 0 <= ord && ord < 10;
		return Bits.get(board, ord) - 1;
	}
	
	int my_piece_pos(int ord) {
		return piece_pos(next == RED ? ord + 5 : ord);
	}

	State[] next(char[] a) {
		State[] states = new State[40];
		int count = 0;
		for (int piece = 0; piece < 5; piece++) {
			int pos = my_piece_pos(piece);
			if (pos == -1)
				break;
			for (int c = 0; c < 2; c++) {
				byte _extra = (next == BLUE) ? (c == 0 ? blue0 : blue1) : (c == 0 ? red0 : red1);
				for (int e : Card.cards[_extra].moves) {
					int m = Card.jump_dest(pos, next == RED, e);
					if (m != -1 && m != my_piece_pos(0) && m != my_piece_pos(1) && m != my_piece_pos(2)
							&& m != my_piece_pos(3) && m != my_piece_pos(4)) {
						byte _blue0 = blue0;
						byte _blue1 = blue1;
						byte _red0 = red0;
						byte _red1 = red1;
						if (next == BLUE) {
							if (c == 0) _blue0 = extra; else _blue1 = extra;
						} else {
							if (c == 0) _red0 = extra; else _red1 = extra;
						}
						states[count++] = new State(
								Board.jump(a, board, piece + (next == RED ? 5 : 0), m),
								_blue0, _blue1, _red0, _red1, _extra,
								(next == RED) ? BLUE : RED, depth + 1);
					}
				}
			}
		}
		return states;
	}
	
	boolean canMove() {
		for (int piece = 0; piece < 5; piece++) {
			int pos = my_piece_pos(piece);
			if (pos == -1)
				break;
			for (int c = 0; c < 2; c++) {
				byte _extra = (next == BLUE) ? (c == 0 ? blue0 : blue1) : (c == 0 ? red0 : red1);
				for (int e : Card.cards[_extra].moves) {
					int m = Card.jump_dest(pos, next == RED, e);
					if (m != -1 && m != my_piece_pos(0) && m != my_piece_pos(1) && m != my_piece_pos(2)
							&& m != my_piece_pos(3) && m != my_piece_pos(4))
						return true;
				}
			}
		}
		return false;
	}

	static String emoji(String a, String e) {
		String r = "";
		for (int i = 0; i < 5; i++) switch (a.charAt(i)) {
		case ' ': r += "  "; break;
		case 'x': r += "▫️ "; break;
		case '*': r += e; break;
		}
		return r;
	}

	static String row(int a, int r, String e) { return emoji(Card.cards[a].print.substring(r*5, r*5+5), e); }

	static String code(char c) {
		switch (c) {
		case ' ': return "⬛";
		case 'B': return "🔷";
		case 'b': return "🔹";
		case 'R': return "🔶";
		case 'r': return "🔸";
		}
		return null;
	}

	void print() {
		char[] a = Board.decode(new char[25], board);
		byte[] c = new byte[] { blue0, blue1, extra, red0, red1 };
		for (int i = 0; i < 5; i++) {
			String e = code(a[i*5]) + code(a[i*5+1]) + code(a[i*5+2]) + code(a[i*5+3]) + code(a[i*5+4]);
			String p = "  ";
			if (i == 2 && !blue_wins() && !red_wins())
				p = code(next == State.RED ? 'R' : 'B');
			if (i % 2 == 1 && (blue_wins() || red_wins()))
				p = code(red_wins() ? 'R' : 'B');
			
			System.out.printf("%s  %s %s%s%s%s%s %s\n", p, e, row(blue0, i, "🔷"), row(blue1, i, "🔷"),
					row(extra, i, "🔺"), row(red0, i, "🔶"), row(red1, i, "🔶"), Card.cards[c[i]].name);
		}
	}

	boolean blue_wins() {
		return piece_pos(5) == -1 || piece_pos(0) == 2 || (next == State.RED && !canMove());
	}

	boolean red_wins() {
		return piece_pos(0) == -1 || piece_pos(5) == 22 || (next == State.BLUE && !canMove());
	}
}

class Minimax {
	final Random random = new Random();
	int remaining_states;
	final char[] aa = new char[25];

	static class Result {
		Result(State move, int value) {
			this.move = move;
			this.score = value;
		}
		final State move;
		final int score; // +100 win, 0 tie, -100 loose (otherwise material advantage + master distance to temple advantage)
	}

	int bestMoveScore(State a, int max_depth) {
		State best_b = null;
		int best_score = a.next == State.BLUE ? -100 : 100;
		int same_score = 0;
		for (State b : a.next(aa)) {
			if (b == null)
				break;
			int score;
			if (b.blue_wins()) {
				score = 100;
			} else if (b.red_wins()) {
				score = -100;
			} else if (b.depth >= max_depth || remaining_states <= 0) {
				score = b.pawn_diff() + b.master_diff();
			} else {
				remaining_states -= 1;
				score = bestMoveScore(b, max_depth);
			}
			if (best_b == null || (a.next == State.BLUE && score > best_score) || (a.next == State.RED && score < best_score)) {
				best_b = b;
				best_score = score;
				same_score = 1;
				if (score == 100 && a.next == State.BLUE)
					break;
				if (score == -100 && a.next == State.RED)
					break;
			} else if (score == best_score) {
				if (random.nextDouble() * (same_score + 1) > same_score) {
					best_b = b;
				}
				same_score += 1;
			}
		}
		return best_score;
	}
	
	Result bestMove(State a, int max_depth) {
		State best_b = null;
		int best_score = a.next == State.BLUE ? -100 : 100;
		int same_score = 0;
		for (State b : a.next(aa)) {
			if (b == null)
				break;
			int score;
			if (b.blue_wins()) {
				score = 100;
			} else if (b.red_wins()) {
				score = -100;
			} else if (b.depth >= max_depth || remaining_states <= 0) {
				score = b.pawn_diff() + b.master_diff();
			} else {
				remaining_states -= 1;
				score = bestMoveScore(b, max_depth);
			}
			if (best_b == null || (a.next == State.BLUE && score > best_score) || (a.next == State.RED && score < best_score)) {
				best_b = b;
				best_score = score;
				same_score = 1;
				if (score == 100 && a.next == State.BLUE)
					break;
				if (score == -100 && a.next == State.RED)
					break;
			} else if (score == best_score) {
				if (random.nextDouble() * (same_score + 1) > same_score) {
					best_b = b;
				}
				same_score += 1;
			}
		}
		return new Result(best_b, best_score);
	}
}

class ParallelMinimax {
	private final Random random = new Random();
	final ThreadPoolExecutor executor = (ThreadPoolExecutor)Executors.newFixedThreadPool(8);
	private long start;

	private double elapsed() {
		return (System.nanoTime() - start) * 1e-9;
	}

	// TODO split tasks at second level to make tasks smaller!
	Minimax.Result bestMove(State a, int max_depth, int remaining_states)
			throws InterruptedException, ExecutionException {
		start = System.nanoTime();
		State[] next = a.next(new char[25]);
		Future<Integer>[] futures = new Future[next.length];
		Minimax[] minimax = new Minimax[next.length];
		int count = 0;
		for (int i = 0; i < next.length; i++) {
			State b = next[i];
			if (b == null)
				break;
			if (b.blue_wins() || b.red_wins())
				continue;
			Minimax m = minimax[i] = new Minimax();
			m.remaining_states = remaining_states;
			futures[i] = executor.submit(() -> m.bestMoveScore(b, max_depth));
			count += 1;
		}
		State best_b = null;
		int best_score = a.next == State.BLUE ? -100 : 100;
		int same_score = 0;
		int done = 0;
		System.out.printf("%.3f: active %s, queued %s\n", elapsed(), executor.getActiveCount(), executor.getQueue().size());
		for (int i = 0; i < next.length; i++) {
			State b = next[i];
			if (b == null)
				break;
			int score;
			if (b.blue_wins()) {
				score = 100;
			} else if (b.red_wins()) {
				score = -100;
			} else if (b.depth >= max_depth || remaining_states == 0) {
				score = b.pawn_diff() + b.master_diff();
			} else {
				if (remaining_states > 0)
					remaining_states -= 1;
				while (true) {
					try {
						score = futures[i].get(20, TimeUnit.SECONDS);
						break;
					} catch (TimeoutException e) {
						System.out.printf("%.3f: %d/%d (remaining states %s) active %s, queued %s\n", elapsed(),
							done, count, minimax[i].remaining_states, executor.getActiveCount(), executor.getQueue().size());
					}
				}
				done += 1;
				if (minimax[i].remaining_states == 0)
					System.out.printf("Move %d is incomplete\n", i);
				System.out.printf("%.3f: %d/%d score %d (remaining states %s) active %s, queued %s\n",
					elapsed(), done, count, score, minimax[i].remaining_states, executor.getActiveCount(), executor.getQueue().size());
			}
			if (best_b == null || (a.next == State.BLUE && score > best_score) || (a.next == State.RED && score < best_score)) {
				best_b = b;
				best_score = score;
				same_score = 1;
				if (score == 100 && a.next == State.BLUE)
					break;
				if (score == -100 && a.next == State.RED)
					break;
			} else if (score == best_score) {
				if (random.nextDouble() * (same_score + 1) > same_score) {
					best_b = b;
				}
				same_score += 1;
			}
		}
		// attempt to stop all unfinished threads
		for (int i = 0; i < next.length; i++) {
			if (next[i] == null)
				break;
			if (minimax[i] != null)
				// not reliable as [remaining_states] isn't volatile
				minimax[i].remaining_states = 0;
		}
		return new Minimax.Result(best_b, best_score);
	}
}

class Main {
	// TODO permute cards
	// TODO make RED play first too!
	public static void solveMasters(byte[] cards) throws InterruptedException, ExecutionException {
		ParallelMinimax pmx = new ParallelMinimax();
		for (int r = 0; r < 25; r++)
			for (int b = 0; b < 25; b++)
				if (r != b) {
					char[] board = new char[25];
					for (int i = 0; i < 25; i++) board[i] = ' ';
					board[b] = 'B';
					board[r] = 'R';
					State s = new State(Board.encode(new String(board)), cards[0], cards[1], cards[2], cards[3], cards[4], State.BLUE, 0);
					s.print();
		
					while (!s.blue_wins() && !s.red_wins()) {
						if (s.depth == 100)
							break;
						Minimax.Result m = pmx.bestMove(s, s.depth + 16, 2000*1000*1000);
						System.out.printf("move %s\n", m.move.depth);
						m.move.print();
						System.out.println();
						s = m.move;
					}
					if (s.blue_wins()) System.out.println("Blue wins!");
					else if (s.red_wins()) System.out.println("Red wins!");
					else System.out.println("Draw!");
				}
	}

	public static void main(String[] args) throws InterruptedException, ExecutionException {
		Card.init();
		byte[] cards = Card.deal(new Random());

		solveMasters(cards);

		State s = new State(Board.initial, cards[0], cards[1], cards[2], cards[3], cards[4],
				new Random().nextBoolean() ? State.BLUE : State.RED, 0);
		System.out.printf("move %s\n", s.depth);
		s.print();
		System.out.println();

		Minimax mx = new Minimax();
		ParallelMinimax pmx = new ParallelMinimax();
		
		double totalTimeBlue = 0;
		double totalTimeRed = 0;
		while (!s.blue_wins() && !s.red_wins()) {
			Minimax.Result m = null;
			long startTime = System.nanoTime();
			if (s.next == State.BLUE) {
				double timeDiff = totalTimeBlue - totalTimeRed;
				int start_depth = 9;
			    if (timeDiff >= 60 || s.depth == 0)
					start_depth = 8;
				int max_depth = 1000;
				for (int depth = s.depth + start_depth; depth <= s.depth + max_depth; depth++) {
					long start = System.nanoTime();
					Minimax.Result m0 = pmx.bestMove(s, depth, 100*1000*1000);
					float duration = (System.nanoTime() - start) * 1e-9f;
					System.out.printf("depth %s, time %.2f, score %s\n",
						depth - s.depth, duration, m0.score);
					m = m0;
					if ((m.score == 100 && s.next == State.BLUE) || (m.score == -100 && s.next == State.RED))
						break;
					if ((System.nanoTime() - startTime) * 1e-9 >= 10)
						break;
				}
			} else {
				mx.remaining_states = 20*1000*1000;
				int max_depth = 1000;
				for (int depth = s.depth + 6; depth <= s.depth + max_depth; depth++) {
					long start = System.nanoTime();
					Minimax.Result m0 = mx.bestMove(s, depth);
					float duration = (System.nanoTime() - start) * 1e-9f;
					System.out.printf("%sdepth %s, time %.2f, score %s\n",
						(mx.remaining_states == 0) ? "incomplete " : "", depth - s.depth, duration, m0.score);
					if (mx.remaining_states == 0)
						break;
					m = m0;
					if ((m.score == 100 && s.next == State.BLUE) || (m.score == -100 && s.next == State.RED))
						break;
				}
			}
			long endTime = System.nanoTime();
			double elapsed = (endTime - startTime) * 1e-9;
			if (s.next == State.BLUE) totalTimeBlue += elapsed; else totalTimeRed += elapsed;

			System.out.printf("move %s (blue time %.1f min, red time %.1f min)\n",
				m.move.depth, totalTimeBlue / 60, totalTimeRed / 60);
			m.move.print();
			System.out.println();
			s = m.move;
		}
		System.out.println(s.blue_wins() ? "Blue wins!" : "Red wins!");
		pmx.executor.shutdownNow();
	}
}
