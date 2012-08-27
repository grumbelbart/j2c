package se.arnetheduck.j2c.test.switchx;

import se.arnetheduck.j2c.test.enums.SimpleEnum;

public class SwitchEnum {
	public int m(SimpleEnum et) {
		int v = 0; // Same name as fake switch var
		switch (et) {
		case RED:
		case GREEN:
			v = 1;
			return 0;
		case BLUE:
			break;
		default:
			return 5;

		}

		return v;
	}

	public int x(SimpleEnum et) {
		switch (et) {
		case RED:
			return 0;
		case BLUE:
		default:
			return 5;
		}
	}

	public int y(SimpleEnum et, int i) {
		switch (et) {
		case RED:
			if (i == 5) {
				break;
			}

			return 0;
		}

		return i;
	}
}
