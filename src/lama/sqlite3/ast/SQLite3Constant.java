package lama.sqlite3.ast;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import lama.Randomly;
import lama.sqlite3.SQLite3Visitor;
import lama.sqlite3.ast.SQLite3Expression.BinaryComparisonOperation.BinaryComparisonOperator;
import lama.sqlite3.gen.SQLite3Cast;
import lama.sqlite3.schema.SQLite3DataType;
import lama.sqlite3.schema.SQLite3Schema.Column.CollateSequence;

public abstract class SQLite3Constant extends SQLite3Expression {

	public static class SQLite3NullConstant extends SQLite3Constant {

		@Override
		public boolean isNull() {
			return true;
		}

		@Override
		public Object getValue() {
			return null;
		}

		@Override
		public SQLite3DataType getDataType() {
			return SQLite3DataType.NULL;
		}

		@Override
		public List<BinaryComparisonOperator> compare(SQLite3Constant cons, boolean shouldBeTrue) {

			List<BinaryComparisonOperator> values;
			if (cons instanceof SQLite3NullConstant) {
				values = Arrays.asList(BinaryComparisonOperator.IS);
			} else {
				values = Arrays.asList(BinaryComparisonOperator.IS_NOT);
			}
			if (shouldBeTrue) {
				return values;
			} else {
				return getReversed(values);
			}
		}

		@Override
		public SQLite3Constant applyEquals(SQLite3Constant right, CollateSequence collate) {
			return SQLite3Constant.createNullConstant();
		}

		@Override
		public SQLite3Constant applyNumericAffinity() {
			return this;
		}

		@Override
		public SQLite3Constant applyTextAffinity() {
			return this;
		}

		@Override
		String getStringRepresentation() {
			return "NULL";
		}

		@Override
		public SQLite3Constant castToBoolean() {
			return SQLite3Cast.asBoolean(this);
		}

	}

	public static class SQLite3IntConstant extends SQLite3Constant {

		private final long value;

		public SQLite3IntConstant(long value) {
			this.value = value;
		}

		@Override
		public boolean isNull() {
			return false;
		}

		@Override
		public long asInt() {
			return value;
		}

		@Override
		public Object getValue() {
			return value;
		}

		@Override
		public SQLite3DataType getDataType() {
			return SQLite3DataType.INT;
		}

		@Override
		public SQLite3Constant applyEquals(SQLite3Constant right, CollateSequence collate) {
			if (right instanceof SQLite3RealConstant) {
				if (Double.isInfinite(right.asDouble())) {
					return SQLite3Constant.createFalse();
				}
				BigDecimal otherColumnValue = BigDecimal.valueOf(right.asDouble());
				BigDecimal thisColumnValue = BigDecimal.valueOf(value);
				return SQLite3Constant.createBoolean(thisColumnValue.compareTo(otherColumnValue) == 0);
			} else if (right instanceof SQLite3IntConstant) {
				return SQLite3Constant.createBoolean(value == right.asInt());
			} else if (right instanceof SQLite3NullConstant) {
				return SQLite3Constant.createNullConstant();
			} else {
				return SQLite3Constant.createFalse();
			}
		}

		@Override
		public List<BinaryComparisonOperator> compare(SQLite3Constant cons, boolean shouldBeTrue) {
			List<BinaryComparisonOperator> values;
			if (cons instanceof SQLite3RealConstant) {
				if (cons.asDouble() == Double.POSITIVE_INFINITY) {
					values = smallerThanList();
				} else if (cons.asDouble() == Double.NEGATIVE_INFINITY) {
					values = greaterThanList();
				} else if (Double.isNaN(cons.asDouble())) {
					values = Collections.emptyList();
				} else {
					BigDecimal otherColumnValue = BigDecimal.valueOf(cons.asDouble());
					BigDecimal thisColumnValue = BigDecimal.valueOf(value);
					if (thisColumnValue.compareTo(otherColumnValue) > 0) {
						values = greaterThanList();
					} else if (thisColumnValue.compareTo(otherColumnValue) < 0) {
						values = smallerThanList();
					} else {
						values = equalsList(false);
					}
				}
			} else if (cons instanceof SQLite3NullConstant) {
				// SELECT 0 IS NULL; -- 0
				// SELECT 0 IS NOT NULL; -- 1
				// SELECT 0 > NULL; -- NULL
				values = Arrays.asList(BinaryComparisonOperator.IS_NOT);
			} else if (cons instanceof SQLite3IntConstant) {
				long otherColumnValue = cons.asInt();
				if (value > otherColumnValue) {
					values = greaterThanList();
				} else if (value < otherColumnValue) {
					values = smallerThanList();
				} else {
					values = equalsList(true);
				}
			} else if (cons instanceof SQLite3TextConstant) {
				// SELECT 3.0 > "50"; -- 0
				// SELECT 3.0 > "2.0"; -- 0
				// SELECT 3.0 > ""; -- 0
				// SELECT 3.0 <= ""; --1
				// SELECT 3.0 LIKE "3.0"; -- 1
				// SELECT 3.0 GLOB "3.0"; -- 1
				// SELECT 3.0 != "3.0"; -- 1
				// SELECT 3.0 IS NOT "3.0"; -- 1
				values = smallerThanList();
//				if (String.valueOf(value).contentEquals(cons.asString())) {
//					values.addAll(Arrays.asList(BinaryOperator.LIKE, BinaryOperator.GLOB));
//				}
				return Collections.emptyList();

			} else {
				assert cons instanceof SQLite3BinaryConstant;
				values = smallerThanList();
			}
			if (shouldBeTrue) {
				return values;
			} else {
				return getReversed(values);
			}
		}

		@Override
		public SQLite3Constant applyNumericAffinity() {
			return this;
		}

		@Override
		public SQLite3Constant applyTextAffinity() {
			return SQLite3Constant.createTextConstant(String.valueOf(value));
		}

		@Override
		String getStringRepresentation() {
			return String.valueOf(value);
		}

		@Override
		public SQLite3Constant castToBoolean() {
			return SQLite3Cast.asBoolean(this);
		}

	}

	public static class SQLite3RealConstant extends SQLite3Constant {

		private final double value;

		public SQLite3RealConstant(double value) {
			this.value = value;
		}

		@Override
		public boolean isNull() {
			return false;
		}

		@Override
		public double asDouble() {
			return value;
		}

		@Override
		public Object getValue() {
			return value;
		}

		@Override
		public SQLite3DataType getDataType() {
			return SQLite3DataType.REAL;
		}

		@Override
		public SQLite3Constant applyEquals(SQLite3Constant right, CollateSequence collate) {
			if (right instanceof SQLite3RealConstant) {
				return SQLite3Constant.createBoolean(value == right.asDouble());
			} else if (right instanceof SQLite3IntConstant) {
				if (Double.isInfinite(value)) {
					return SQLite3Constant.createFalse();
				}
				BigDecimal thisColumnValue = BigDecimal.valueOf(value);
				BigDecimal otherColumnValue = BigDecimal.valueOf(right.asInt());
				return SQLite3Constant.createBoolean(thisColumnValue.compareTo(otherColumnValue) == 0);
			} else if (right instanceof SQLite3NullConstant) {
				return SQLite3Constant.createNullConstant();
			} else {
				return SQLite3Constant.createFalse();
			}
		}

		@Override
		public List<BinaryComparisonOperator> compare(SQLite3Constant cons, boolean shouldBeTrue) {
			List<BinaryComparisonOperator> values;
			if (cons instanceof SQLite3RealConstant) {
				// When an INTEGER or REAL is compared to another INTEGER or REAL, a numerical
				// comparison is performed.
				double otherColumnValue = cons.asDouble();
				if (value > otherColumnValue) {
					values = greaterThanList();
				} else if (value < otherColumnValue) {
					values = smallerThanList();
				} else {
					values = equalsList(true);
				}
			} else if (cons instanceof SQLite3NullConstant) {
				// A value with storage class NULL is considered less than any other value
				// (including another value with storage class NULL).

				// SELECT 0 IS NULL; -- 0
				// SELECT 0 IS NOT NULL; -- 1
				// SELECT 0 > NULL; -- NULL
				values = Arrays.asList(BinaryComparisonOperator.IS_NOT);
			} else if (cons instanceof SQLite3IntConstant) {
				if (value == Double.POSITIVE_INFINITY) {
					values = greaterThanList();
				} else if (value == Double.NEGATIVE_INFINITY) {
					values = smallerThanList();
				} else if (Double.isNaN(value)) {
					values = Collections.emptyList();
				} else {
					BigDecimal otherColumnValue = BigDecimal.valueOf(cons.asInt());
					BigDecimal thisColumnValue = BigDecimal.valueOf(value);
					if (thisColumnValue.compareTo(otherColumnValue) > 0) {
						values = greaterThanList();
					} else if (thisColumnValue.compareTo(otherColumnValue) < 0) {
						values = smallerThanList();
					} else {
						values = equalsList(false);
					}
				}
			} else if (cons instanceof SQLite3TextConstant) {
				// An INTEGER or REAL value is less than any TEXT or BLOB value.
				// SELECT 3.0 > "50"; -- 0
				// SELECT 3.0 > "2.0"; -- 0
				// SELECT 3.0 > ""; -- 0
				// SELECT 3.0 <= ""; --1
				// SELECT 3.0 LIKE "3.0"; -- 1
				// SELECT 3.0 GLOB "3.0"; -- 1
				// SELECT 3.0 != "3.0"; -- 1
				// SELECT 3.0 IS NOT "3.0"; -- 1
				values = new ArrayList<>(
						Arrays.asList(BinaryComparisonOperator.SMALLER, BinaryComparisonOperator.SMALLER_EQUALS,
								BinaryComparisonOperator.IS_NOT, BinaryComparisonOperator.NOT_EQUALS));
//				if (String.valueOf(value).contentEquals(cons.asString())) {
//					values.addAll(Arrays.asList(BinaryOperator.LIKE, BinaryOperator.GLOB));
//				}
				return Collections.emptyList();

			} else {
				assert cons instanceof SQLite3BinaryConstant;
				// An INTEGER or REAL value is less than any TEXT or BLOB value.
				values = new ArrayList<>(
						Arrays.asList(BinaryComparisonOperator.SMALLER, BinaryComparisonOperator.SMALLER_EQUALS,
								BinaryComparisonOperator.IS_NOT, BinaryComparisonOperator.NOT_EQUALS));
				// TODO what about like and glob
			}
			if (shouldBeTrue) {
				return values;
			} else {
				return getReversed(values);
			}
		}

		@Override
		public SQLite3Constant applyNumericAffinity() {
			return this;
		}

		@Override
		public SQLite3Constant applyTextAffinity() {
			return SQLite3Constant.createTextConstant(String.valueOf(value));
		}

		@Override
		String getStringRepresentation() {
			return String.valueOf(value);
		}

		@Override
		public SQLite3Constant castToBoolean() {
			return SQLite3Cast.asBoolean(this);
		}

	}

	private static List<BinaryComparisonOperator> equalsList(boolean withGlob) {
		List<BinaryComparisonOperator> values;
		if (withGlob) {
			values = Arrays.asList(BinaryComparisonOperator.EQUALS, BinaryComparisonOperator.IS,
					BinaryComparisonOperator.GREATER_EQUALS, BinaryComparisonOperator.SMALLER_EQUALS); // ,
																										// BinaryOperator.GLOB
		} else {
			values = Arrays.asList(BinaryComparisonOperator.EQUALS, BinaryComparisonOperator.IS,
					BinaryComparisonOperator.GREATER_EQUALS, BinaryComparisonOperator.SMALLER_EQUALS);
		}
		return values;
	}

	private static List<BinaryComparisonOperator> smallerThanList() {
		List<BinaryComparisonOperator> values;
		values = Arrays.asList(BinaryComparisonOperator.SMALLER, BinaryComparisonOperator.SMALLER_EQUALS,
				BinaryComparisonOperator.IS_NOT, BinaryComparisonOperator.NOT_EQUALS);
		return values;
	}

	private static List<BinaryComparisonOperator> greaterThanList() {
		List<BinaryComparisonOperator> values;
		values = Arrays.asList(BinaryComparisonOperator.GREATER, BinaryComparisonOperator.GREATER_EQUALS,
				BinaryComparisonOperator.IS_NOT, BinaryComparisonOperator.NOT_EQUALS);
		return values;
	}

	public List<BinaryComparisonOperator> getReversed(List<BinaryComparisonOperator> list) {
		List<BinaryComparisonOperator> l = new ArrayList<>(list);
		l.remove(BinaryComparisonOperator.GLOB);
		return l.stream().map(op -> op.reverse()).collect(Collectors.toList());
	}

	public static class SQLite3TextConstant extends SQLite3Constant {

		private final String text;

		public SQLite3TextConstant(String text) {
			this.text = text;
		}

		@Override
		public boolean isNull() {
			return false;
		}

		@Override
		public String asString() {
			return text;
		}

		@Override
		public Object getValue() {
			return text;
		}

		@Override
		public SQLite3DataType getDataType() {
			return SQLite3DataType.TEXT;
		}

		@Override
		public List<BinaryComparisonOperator> compare(SQLite3Constant cons, boolean shouldBeTrue) {
			List<BinaryComparisonOperator> values;
			if (cons instanceof SQLite3RealConstant || cons instanceof SQLite3IntConstant) {
				values = greaterThanList();
			} else if (cons instanceof SQLite3BinaryConstant) {
				values = smallerThanList();
			} else if (cons instanceof SQLite3NullConstant) {
				values = Arrays.asList(BinaryComparisonOperator.IS_NOT);
			} else {
				assert cons instanceof SQLite3TextConstant;
//				String otherText = cons.asString();
				// TODO implement collate
//				int compareTo = text.compareToIgnoreCase(otherText);
//				if (compareTo == 0) {
//					values = equalsList(true);
//				} else if (compareTo > 0) {
//					values = smallerThanList();
//				} else {
//					values = greaterThanList();
//				}
				return Collections.emptyList();
			}
			if (shouldBeTrue) {
				return values;
			} else {
				return getReversed(values);
			}
		}

		@Override
		public SQLite3Constant applyEquals(SQLite3Constant right, CollateSequence collate) {
			if (right.isNull()) {
				return SQLite3Constant.createNullConstant();
			} else if (right instanceof SQLite3TextConstant) {
				String other = right.asString();
				boolean equals;
				switch (collate) {
				case BINARY:
					equals = text.equals(other);
					break;
				case NOCASE:
					equals = text.equalsIgnoreCase(other);
					break;
				case RTRIM:
					equals = rtrim(text).equals(rtrim(other));
					break;
				default:
					throw new AssertionError(collate);
				}
				return SQLite3Constant.createBoolean(equals);
			} else {
				return SQLite3Constant.createFalse();
			}
		}

		private static String rtrim(String s) {
			int i = s.length() - 1;
			while (i >= 0 && Character.isWhitespace(s.charAt(i))) {
				i--;
			}
			return s.substring(0, i + 1);
		}

		@Override
		public SQLite3Constant applyNumericAffinity() {
			Pattern leadingDigitPattern = Pattern
					.compile("[-+]?((\\d(\\d)*(\\.(\\d)*)?)|\\.(\\d)(\\d)*)([Ee][+-]?(\\d)(\\d)*)?");
			String trimmedString = text.trim();
			if (trimmedString.isEmpty()) {
				return this;
			}
			Matcher matcher = leadingDigitPattern.matcher(trimmedString);
			if (matcher.matches()) {
				SQLite3Constant castValue = SQLite3Cast.castToNumeric(this);
				return castValue;
			} else {
				return this;
			}
		}

		@Override
		public SQLite3Constant applyTextAffinity() {
			return this;
		}

		@Override
		String getStringRepresentation() {
			return text;
		}

		@Override
		public SQLite3Constant castToBoolean() {
			return SQLite3Cast.asBoolean(this);
		}
	}

	public static class SQLite3BinaryConstant extends SQLite3Constant {

		private final byte[] bytes;

		public SQLite3BinaryConstant(byte[] bytes) {
			this.bytes = bytes;
		}

		@Override
		public boolean isNull() {
			return false;
		}

		@Override
		public SQLite3DataType getDataType() {
			return SQLite3DataType.BINARY;
		}

		@Override
		public Object getValue() {
			return bytes;
		}

		@Override
		public byte[] asBinary() {
			return bytes;
		}

		@Override
		public List<BinaryComparisonOperator> compare(SQLite3Constant cons, boolean shouldBeTrue) {
			List<BinaryComparisonOperator> values = new ArrayList<>();
			if (cons instanceof SQLite3IntConstant || cons instanceof SQLite3RealConstant
					|| cons instanceof SQLite3TextConstant) {
				// An INTEGER or REAL value is less than any TEXT or BLOB value.
				// A TEXT value is less than a BLOB value.
				values = greaterThanList();
			} else if (cons instanceof SQLite3BinaryConstant) {
				// When two BLOB values are compared, the result is determined using memcmp().
				byte[] val = cons.asBinary();
				int commonLength = Math.min(val.length, bytes.length);
				for (int i = 0; i < commonLength; i++) {
					if ((bytes[i] & 0xFF) > (val[i] & 0xFF)) {
						values = greaterThanList();
						break;
					} else if ((bytes[i] & 0xFF) < (val[i] & 0xFF)) {
						values = smallerThanList();
						break;
					}
				}
				if (values.isEmpty()) {
					if (bytes.length == val.length) {
						assert Arrays.equals(val, bytes);
						values = equalsList(false); // TODO glob?
					} else if (bytes.length > val.length) {
						values = greaterThanList();
					} else {
						values = smallerThanList();
					}
				}
			} else {
				assert cons instanceof SQLite3NullConstant;
				// A value with storage class NULL is considered less than any other value
				// (including another value with storage class NULL).
				values = Arrays.asList(BinaryComparisonOperator.IS_NOT);
			}
			if (shouldBeTrue) {
				return values;
			} else {
				return getReversed(values);
			}
		}

		@Override
		public SQLite3Constant applyNumericAffinity() {
			return this;
		}

		@Override
		public SQLite3Constant applyTextAffinity() {
			if (bytes.length == 0) {
				return this;
			} else {
				StringBuilder sb = new StringBuilder();
				for (byte b : bytes) {
					if (isPrintableChar(b)) {
						sb.append(b);
					}
				}
				return SQLite3Constant.createTextConstant(sb.toString());
			}
		}

		public boolean isPrintableChar(byte b) {
			return b >= 32;
		}

		@Override
		String getStringRepresentation() {
			String hexRepr = SQLite3Visitor.byteArrayToHex(bytes);
			return String.format("x'%s'", hexRepr);
		}

		@Override
		public SQLite3Constant applyEquals(SQLite3Constant right, CollateSequence collate) {
			// FIXME
			return null;
		}

		@Override
		public SQLite3Constant castToBoolean() {
			return SQLite3Cast.asBoolean(this);
		}

	}

	abstract String getStringRepresentation();

	public abstract boolean isNull();

	public abstract Object getValue();

	public abstract List<BinaryComparisonOperator> compare(SQLite3Constant cons, boolean shouldBeTrue);

	public long asInt() {
		throw new UnsupportedOperationException(this.getDataType().toString());
	}

	public double asDouble() {
		throw new UnsupportedOperationException(this.getDataType().toString());
	}

	public byte[] asBinary() {
		throw new UnsupportedOperationException(this.getDataType().toString());
	}

	public String asString() {
		throw new UnsupportedOperationException(this.getDataType().toString());
	}

	public abstract SQLite3DataType getDataType();

	public static SQLite3Constant createIntConstant(long val) {
		return new SQLite3IntConstant(val);
	}

	public static SQLite3Constant createBinaryConstant(byte[] val) {
		return new SQLite3BinaryConstant(val);
	}

	public static SQLite3Constant createBinaryConstant(String val) {
		return new SQLite3BinaryConstant(SQLite3Visitor.hexStringToByteArray(val));
	}

	public static SQLite3Constant createRealConstant(double real) {
		return new SQLite3RealConstant(real);
	}

	public static SQLite3Constant createTextConstant(String text) {
		return new SQLite3TextConstant(text);
	}

	public static SQLite3Constant createNullConstant() {
		return new SQLite3NullConstant();
	}

	public static SQLite3Constant getRandomBinaryConstant() {
		int size = Randomly.smallNumber();
		byte[] arr = new byte[size];
		Randomly.getBytes(arr);
		return new SQLite3BinaryConstant(arr);
	}

	@Override
	public SQLite3Constant getExpectedValue() {
		return this;
	}

	@Override
	public CollateSequence getExplicitCollateSequence() {
		return null;
	}

	@Override
	public String toString() {
		return String.format("(%s) %s", getDataType(), getStringRepresentation());
	}

	public abstract SQLite3Constant applyNumericAffinity();

	public abstract SQLite3Constant applyTextAffinity();

	public static SQLite3Constant createTrue() {
		return new SQLite3Constant.SQLite3IntConstant(1);
	}

	public static SQLite3Constant createFalse() {
		return new SQLite3Constant.SQLite3IntConstant(0);
	}

	public static SQLite3Constant createBoolean(boolean tr) {
		return new SQLite3Constant.SQLite3IntConstant(tr ? 1 : 0);
	}

	public abstract SQLite3Constant applyEquals(SQLite3Constant right, CollateSequence collate);

	public abstract SQLite3Constant castToBoolean();

	public SQLite3Constant applyEquals(SQLite3Constant right) {
		return applyEquals(right, null);
	}

}