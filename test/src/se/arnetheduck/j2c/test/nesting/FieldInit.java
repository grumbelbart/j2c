package se.arnetheduck.j2c.test.nesting;

public class FieldInit {
	static int x = 5;

	static Object m() {
		return new Object() {
			final int y = x;
		};
	}

	static Object closure(final int z) {
		return new Object() {
			final int y = z;
		};
	}
}
