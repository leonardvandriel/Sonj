package net.sf.sonj.io;

import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Map;

import net.sf.sonj.collection.CollectionFactory;

/**
 * Deserializes a JSON file into a tree of {@link Map} and {@link List}. Simply
 * put, it fits the characters read from the provided {@link Reader} to the
 * collections provided by the {@link CollectionFactory}, according to the JSON
 * specification on <a href="http://www.json.org">json.org</a>.
 * 
 * The correct JSON formatting is assumed here. If an unexpected character
 * appears, the reader will throw
 * 
 * This package provides special JSON collections JsonObject and JsonArray,
 * which extend respectively Map and List. To have Reader use these collections
 * you should provide a collection factory that instantiates these, e.g.
 * JsonFactory. NB: The use of these collections is <em>not</em> mandatory. Both
 * JsonReader and JsonWriter can full operate using standard java.util
 * collections.
 */
public class JsonReader {
	private Reader reader;
	private CollectionFactory factory;
	private int currentCodePoint;

	/**
	 * Constructs an instance by setting a reader and factory.
	 * 
	 * @param reader
	 *            A reader that provides complete and consistent serialized
	 *            JSON.
	 * @param factory
	 *            A factor from which the maps and lists will be taken.
	 */
	public JsonReader(Reader reader, CollectionFactory factory) {
		this.reader = reader;
		this.factory = factory;
	}

	/**
	 * Reads a single collection type, i.e. Map or List from the reader.
	 * 
	 * @return Either an instance of Map or List, generated by the
	 *         CollectionFactory.
	 * @throws JsonReadException
	 *             If the read content does not represent a well-formatted json
	 *             structure.
	 */
	public Object read() {
		readWhiteSpaces();
		return readJsonInternal();
	}

	/**
	 * Reads a single JSON object and returns it as a Map, using on the provided
	 * {@link CollectionFactory}. This function assumes that the first
	 * non-whitespace character will be '{', which is the start of an object.
	 * 
	 * @return An instance implementing Map, generated by the CollectionFactory.
	 * @throws JsonReadException
	 *             If the read content does not represent a well-formatted json
	 *             object.
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> readMap() {
		if (readWhiteSpaces() != JsonIo.objectOpen) {
			throwExpectedException(new int[] { JsonIo.objectOpen }, currentCodePoint);
		}
		return (Map<String, Object>) readJsonInternal();
	}

	/**
	 * Reads a single JSON array and returns it as a List, using on the provided
	 * {@link CollectionFactory}. This function assumes that the first
	 * non-whitespace character will be '[', which is the start of an array.
	 * 
	 * @return An instance implementing List, generated by the
	 *         CollectionFactory.
	 * @throws JsonReadException
	 *             If the read content does not represent a well-formatted json
	 *             array.
	 */
	@SuppressWarnings("unchecked")
	public List<Object> readList() {
		if (readWhiteSpaces() != JsonIo.arrayOpen) {
			throwExpectedException(new int[] { JsonIo.arrayOpen }, currentCodePoint);
		}
		return (List<Object>) readJsonInternal();
	}

	private Object readJsonInternal() {
		switch (currentCodePoint) {
		case JsonIo.objectOpen:
			return readObject();
		case JsonIo.arrayOpen:
			return readArray();
		case JsonIo.stringDelimiter:
			return readString();
		default:
			return readPrimitive();
		}
	}

	private Map<String, Object> readObject() {
		Map<String, Object> object = factory.getMap();
		readFromReader();
		int c = readWhiteSpaces();
		if (c == JsonIo.objectClose) {
			readFromReader();
			return object;
		}
		for (;;) {
			if (c != JsonIo.stringDelimiter) {
				throwExpectedException(new int[] { JsonIo.stringDelimiter, JsonIo.objectClose }, c);
			}
			String key = readString();
			c = readWhiteSpaces();
			if (c != JsonIo.pairSeparator) {
				throwExpectedException(new int[] { JsonIo.pairSeparator }, c);
			}
			readFromReader();
			c = readWhiteSpaces();
			object.put(key, readJsonInternal());
			c = readWhiteSpaces();
			switch (c) {
			case JsonIo.objectClose:
				readFromReader();
				return object;
			case JsonIo.itemSeparator:
				readFromReader();
				c = readWhiteSpaces();
				break;
			default:
				throwExpectedException(new int[] { JsonIo.itemSeparator, JsonIo.objectClose }, c);
			}
		}
	}

	private List<Object> readArray() {
		List<Object> array = factory.getList();
		readFromReader();
		int c = readWhiteSpaces();
		if (c == JsonIo.arrayClose) {
			readFromReader();
			return array;
		}
		for (;;) {
			array.add(readJsonInternal());
			c = readWhiteSpaces();
			switch (c) {
			case JsonIo.arrayClose:
				readFromReader();
				return array;
			case JsonIo.itemSeparator:
				readFromReader();
				c = readWhiteSpaces();
				break;
			default:
				throwExpectedException(new int[] { JsonIo.itemSeparator, JsonIo.arrayClose }, c);
			}
		}
	}

	private String readString() {
		StringBuilder builder = new StringBuilder();
		for (;;) {
			int c = readFromReader();
			if (c < JsonIo.highestWhiteSpace) {
				throw new JsonReadException("Character unexpected: " + c);
			}
			switch (c) {
			case JsonIo.escapeCharacter:
				c = readFromReader();
				if (c == JsonIo.unicodeCharacter) {
					int[] hexCode = new int[JsonIo.unicodeDigitCount];
					for (int i = 0; i < hexCode.length; i++) {
						hexCode[i] = readFromReader();
					}
					String hexString = new String(hexCode, 0, hexCode.length);
					c = Integer.valueOf(hexString, 16).intValue();
				} else {
					c = JsonIo.unescapeArray[c];
					if (c == 0) {
						throw new JsonReadException("Unknown escape code point: " + c);
					}
				}
				break;
			case JsonIo.stringDelimiter:
				readFromReader();
				return builder.toString();
			}
			builder.appendCodePoint(c);
		}
	}

	private Object readPrimitive() {
		StringBuilder builder = new StringBuilder();
		int c = currentCodePoint;
		while (c > JsonIo.highestWhiteSpace && c != JsonIo.objectClose && c != JsonIo.arrayClose
				&& c != JsonIo.itemSeparator) {
			builder.appendCodePoint(c);
			c = readFromReader();
		}
		String primitive = builder.toString();
		if (primitive.equals("null")) {
			return null;
		} else if (primitive.equals("true")) {
			return Boolean.TRUE;
		} else if (primitive.equals("false")) {
			return Boolean.FALSE;
		}
		try {
			if (primitive.contains(".") || primitive.contains("e") || primitive.contains("E")) {
				return Double.valueOf(primitive);
			}
			return Integer.valueOf(primitive);
		} catch (NumberFormatException e) {
			throw new JsonReadException("Expecting primitive value instead of: " + primitive);
		}
	}

	private int readWhiteSpaces() {
		int c = currentCodePoint;
		while (c >= 0 && c <= JsonIo.highestWhiteSpace) {
			c = readFromReader();
		}
		return c;
	}

	private int readFromReader() {
		try {
			currentCodePoint = reader.read();
		} catch (IOException e) {
			throw new JsonReadException("Unable to read character: " + e.getMessage(), e);
		}
		return currentCodePoint;
	}

	private void throwExpectedException(int[] expected, int found) {
		StringBuilder builder = new StringBuilder();
		boolean addSeparator = false;
		for (int i = 0; i < expected.length; i++) {
			if (addSeparator) {
				builder.append("' or '");
			} else {
				addSeparator = true;
			}
			builder.append(new String(new int[] { expected[i] }, 0, 1));
		}
		String expectedString = builder.toString();
		String foundString;
		try {
			foundString = new String(new int[] { found }, 0, 1);
		} catch (IllegalArgumentException e) {
			foundString = Integer.toString(found);
		}
		throw new JsonReadException(String.format("Expecting '%s', found '%s' instead", expectedString, foundString));
	}
}
