/*
 * Copyright 2015 Dmitry Ustalov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mtsar.api.sql;

import org.apache.commons.lang3.ArrayUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

/**
 * This is class provides {@link java.sql.Array} interface for PostgreSQL <code>text</code> array.
 * http://tech.valgog.com/2009/02/passing-arrays-to-postgresql-database.html
 *
 * @author Valentine Gogichashvili
 */
public class PostgreSQLTextArray implements java.sql.Array {

    private final String[] stringArray;
    private final String stringValue;

    public PostgreSQLTextArray(Collection<String> collection) {
        this(ArrayUtils.nullToEmpty(collection.toArray(new String[collection.size()])));
    }

    /**
     * Initializing constructor
     *
     * @param stringArray
     */
    public PostgreSQLTextArray(String[] stringArray) {
        this.stringArray = stringArray;
        this.stringValue = stringArrayToPostgreSQLTextArray(this.stringArray);

    }

    @Override
    public String toString() {
        return stringValue;
    }

    private static final String NULL = "NULL";

    /**
     * This static method can be used to convert an string array to string representation of PostgreSQL text array.
     *
     * @param stringArray a source String array
     * @return string representation of a given text array
     */
    public static String stringArrayToPostgreSQLTextArray(String[] stringArray) {
        final int arrayLength;
        if (stringArray == null) {
            return NULL;
        } else if ((arrayLength = stringArray.length) == 0) {
            return "{}";
        }
        // count the string length and if need to quote
        int neededBufferLentgh = 2; // count the beginning '{' and the ending '}' brackets
        boolean[] shouldQuoteArray = new boolean[stringArray.length];
        for (int si = 0; si < arrayLength; si++) {
            // count the comma after the first element
            if (si > 0) neededBufferLentgh++;

            boolean shouldQuote;
            final String s = stringArray[si];
            if (s == null) {
                neededBufferLentgh += 4;
                shouldQuote = false;
            } else {
                final int l = s.length();
                neededBufferLentgh += l;
                if (l == 0 || s.equalsIgnoreCase(NULL)) {
                    shouldQuote = true;
                } else {
                    shouldQuote = false;
                    // scan for commas and quotes
                    for (int i = 0; i < l; i++) {
                        final char ch = s.charAt(i);
                        switch (ch) {
                            case '"':
                            case '\\':
                                shouldQuote = true;
                                // we will escape these characters
                                neededBufferLentgh++;
                                break;
                            case ',':
                            case '\'':
                            case '{':
                            case '}':
                                shouldQuote = true;
                                break;
                            default:
                                if (Character.isWhitespace(ch)) {
                                    shouldQuote = true;
                                }
                                break;
                        }
                    }
                }
                // count the quotes
                if (shouldQuote) neededBufferLentgh += 2;
            }
            shouldQuoteArray[si] = shouldQuote;
        }

        // construct the String
        final StringBuilder sb = new StringBuilder(neededBufferLentgh);
        sb.append('{');
        for (int si = 0; si < arrayLength; si++) {
            final String s = stringArray[si];
            if (si > 0) sb.append(',');
            if (s == null) {
                sb.append(NULL);
            } else {
                final boolean shouldQuote = shouldQuoteArray[si];
                if (shouldQuote) sb.append('"');
                for (int i = 0, l = s.length(); i < l; i++) {
                    final char ch = s.charAt(i);
                    if (ch == '"' || ch == '\\') sb.append('\\');
                    sb.append(ch);
                }
                if (shouldQuote) sb.append('"');
            }
        }
        sb.append('}');
        assert sb.length() == neededBufferLentgh;
        return sb.toString();
    }


    @Override
    public Object getArray() throws SQLException {
        return stringArray == null ? null : Arrays.copyOf(stringArray, stringArray.length);
    }

    @Override
    public Object getArray(Map<String, Class<?>> map) throws SQLException {
        return getArray();
    }

    @Override
    public Object getArray(long index, int count) throws SQLException {
        return stringArray == null ? null : Arrays.copyOfRange(stringArray, (int) index, (int) index + count);
    }

    @Override
    public Object getArray(long index, int count, Map<String, Class<?>> map) throws SQLException {
        return getArray(index, count);
    }

    @Override
    public int getBaseType() throws SQLException {
        return java.sql.Types.VARCHAR;
    }

    @Override
    public String getBaseTypeName() throws SQLException {
        return "text";
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResultSet getResultSet(Map<String, Class<?>> map) throws SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResultSet getResultSet(long index, int count) throws SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResultSet getResultSet(long index, int count, Map<String, Class<?>> map) throws SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void free() throws SQLException {
    }
}