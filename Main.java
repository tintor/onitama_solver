import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

// Board chars
// 'R' 'r'
// 'B' 'b'
// ' '
// 'x' jump
// Red goes down
// Blue goes up

// 5 bits + 2 * 25

class Bits {
	static long compress(String board) {
		// format is 10 pieces x 5 bits: BbbbbRrrrr
		// 5 bits decode to board position 0-24 or 31 (captured)
		// pawn positions are sorted
		int[] piece = new int[10];
		Arrays.fill(piece, 31);
		int red = 0, blue = 0;
		for (int i = 0; i < 25; i++) {
			char c = board.charAt(i);
			if (c == 'B') piece[0] = i;
			if (c == 'R') piece[5] = i;
			if (c == 'b') piece[1 + blue++] = i;
			if (c == 'r') piece[6 + red++] = i;
		}
		// TODO sort blue
		// TODO sort red
		long a = 0;
		for (int i = 0; i < 10; i++)
			a |= (piece[i] & 0x1F) << (i * 5);
		return a;
	}
	static String decompress(long board) {
		char[] a = new char[25];
		for (int i = 0; i < 25; i++) a[i] = ' ';
		for (int i = 0; i < 10; i++) {
			int v = (board >> (i * 5)) & 0x1F;
			if (v != 31) {
				char c = (i < 5) ? 'b' : 'r';
				if (i == 0) c = 'B';
				if (i == 5) c = 'R';
				a[v] = c;
			}
		}
		return new String(a).intern();
	}
	static char read(long board, int p) {
		for (int i = 0; i < 10; i++) {
			int v = (board >> (i * 5)) & 0x1F;
			if (v == p) {
				if (i == 0) return 'B';
				if (i == 5) return 'R';
				return (i < 5) ? 'b' : 'r';
			}
		}
		return ' ';
	}
}

class State {
	static String intern(String a) { return a.intern(); }

	static final String initial = intern(
		"rrRrr"+
		"     "+
		"     "+
		"     "+
		"bbBbb");
	static final String tiger = intern(
		"  x  "+
		"     "+
		"  *  "+
		"  x  "+
		"     ");
	static final String monkey = intern(
		"     "+
		" x x "+
		"  *  "+
		" x x "+
		"     ");
	static final String horse = intern(
		"     "+
		"  x  "+
		" x*  "+
		"  x  "+
		"     ");
	static final String mantis = intern(
		"     "+
		" x x "+
		"  *  "+
		"  x  "+
		"     ");
	static final String elephant = intern(
		"     "+
		" x x "+
		" x*x "+
		"     "+
		"     ");
	static final String crab = intern(
		"     "+
		"  x  "+
		"x * x"+
		"     "+
		"     ");
	static final String crane = intern(
		"     "+
		"  x  "+
		"  *  "+
		" x x "+
		"     ");
	static final String dragon = intern(
		"     "+
		"x   x"+
		"  *  "+
		" x x "+
		"     ");
	static final String boar = intern(
		"     "+
		"  x  "+
		" x*x "+
		"     "+
		"     ");
	static final String frog = intern(
		"     "+
		" x   "+
		"x *  "+
		"   x "+
		"     ");
	static final String goose = intern(
		"     "+
		" x   "+
		" x*x "+
		"   x "+
		"     ");
	static final String eel = intern(
		"     "+
		" x   "+
		"  *x "+
		" x   "+
		"     ");
	static final String[] cards = { tiger, monkey, horse, mantis, elephant, crab, crane, dragon, boar, frog, goose, eel };
	// rabbit, rooster, ox, cobra

	private static ArrayList<Integer> moves(int p, String card, char next) {
		ArrayList<Integer> moves = new ArrayList<Integer>();
		for (int i = 0; i < 25; i++) if (card.charAt(i) == 'x') {
			int xi = i%5 - 2, yi = i/5 - 2;
			if (next == 'r') { xi = -xi; yi = -yi; }
			int x = p%5 + xi, y = p/5 + yi;
			if (0 <= x && x < 5 && 0 <= y && y < 5)
				moves.add(x + y * 5);
		}
		return moves;
	}

	private static boolean equals(String a0, String a1, String b0, String b1) {
		return (a0 == b0 && a1 == b1) || (a0 == b1 && a1 == b0);
	}

	@Override
	public boolean equals(Object o) {
		State b = (State)o;
		return board == b.board && extra_card == b.extra_card && next == b.next && equals(red0, red1, b.red0, b.red1) && equals(blue0, blue1, b.blue0, b.blue1);
	}

	@Override
	public int hashCode() {
		int h = 0;
		h = h * 37 + (next == 'b' ? 234 : 578);
		h = h * 37 + board.hashCode();
		h = h * 37 + extra_card.hashCode();
		h = h * 37 + (red0.hashCode() ^ red1.hashCode());
		h = h * 37 + (blue0.hashCode() ^ blue1.hashCode());
		return h;
	}

	String board = initial;
	// cards are always relative to their owner
	String blue0 = tiger, blue1 = monkey;
	String red0 = horse, red1 = mantis;
	String extra_card = elephant;
	char next = 'b'; // 'r' or 'b'

	static char lower(char c) { return Character.toLowerCase(c); }

	static String replace(String s, int p1, char c1, int p2, char c2) { char[] m = s.toCharArray(); m[p1] = c1; m[p2] = c2; return new String(m).intern(); }

	Set<State> next() {
		Set<State> states = new HashSet<State>();
		for (int i = 0; i < 25; i++) if (lower(board.charAt(i)) == next)
			for (int c = 0; c < 2; c++)
				for (int m : moves(i, (next == 'b') ? blue_card[c] : red_card[c], next))
					if (lower(board.charAt(m)) != next) {
						State s = new State();
						s.red_card[0] = red_card[0];
						s.red_card[1] = red_card[1];
						s.blue_card[0] = blue_card[0];
						s.blue_card[1] = blue_card[1];
	
						s.board = replace(board, i, ' ', m, board.charAt(i));
						if (next == 'b') s.blue_card[c] = extra_card; else s.red_card[c] = extra_card;
						s.extra_card = (next == 'b') ? blue_card[c] : red_card[c];
						s.next = (next == 'b') ? 'r' : 'b';
						states.add(s);
					}
		return states;
	}

	static String row(String a, int r) { return a.substring(r*5, r*5+5); }

	void print() {
		System.out.printf("Board (next[%c], winner[%c])\n", next, winner());
		for (int i = 0; i < 5; i++)
			System.out.printf(".%s.\n", row(board, i));
		System.out.println("Blue        Extra        Red");
		for (int i = 0; i < 5; i++)
			System.out.printf("%s.%s.%s.%s.%s\n", row(blue_card[0], i), row(blue_card[1], i), row(extra_card, i), row(red_card[0], i), row(red_card[1], i));
	}

	char winner() {
		if (!board.contains("R") || board.charAt(2) == 'B') return 'b';
		if (!board.contains("B") || board.charAt(22) == 'R') return 'r';
		return ' ';
	}
}

class Main {
	/*class Result {
		Result(State move, int value): move(m), value(v) { }
		final State move;
		final int value; // +100 win, 0 tie, -100 loose (otherwise material advantage + master distance to temple advantage)
	}
	// or null if game is lost
	static Result bestMove(State a) {
		Set<State> moves = a.next();
		for (State b : moves)
			if (b.winner() == a.next)
				return new Result(b, 100);
		Result min_c = null;
		for (State b : moves) {
			Result c = bestMove(b);
			if (min_c == null || c.value < min_c.value) {
				min_c = c;
			}
		}
		return min_c;
	}*/

	public static void main(String[] args) {
		State s = new State();
		s.next = 'b';
		s.print();

		Set<State> visited = new HashSet<State>();
		visited.add(s);
		Deque<State> queue = new ArrayDeque<State>();
		queue.add(s);
		long leaves = 0;
		int c = 0;
		while (!queue.isEmpty()) {
			State a = queue.pollFirst();
			c += 1;
			if (c % 100000 == 0) {
				System.out.printf("visited %s, queue %s, leaves %s\n", visited.size(), queue.size(), leaves);
				a.print();
			}
			for (State b : a.next()) {
				if (b.winner() != ' ') {
					leaves += 1;
					continue;
				}
				if (!visited.contains(b)) {
					visited.add(b);
					queue.addLast(b);
				}
			}
		}
		//Result m = bestMove(s);
		//System.out.printf("Best move (%d):\n", m.value);
		//m.move.print();		
	}
}
