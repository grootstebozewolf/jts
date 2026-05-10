/*
 * Copyright (c) 2016 Vivid Solutions.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */

package org.locationtech.jtstest.util;

/**
 * Cleans text strings which are supposed
 * to contain valid text for Geometries 
 * (either WKB, WKB, or GML) 
 * 
 * @author mbdavis
 *
 */
public class GeometryTextCleaner
{
	public static final String WKT_SYMBOLS = "(),.-";

	public static String cleanWKT(String input)
	{
		return clean(stripComments(input), WKT_SYMBOLS);
	}

	/**
	 * Strips SQL-style {@code -- line} and C-style {@code /* block *}{@code /}
	 * comments from a WKT input string before character filtering.
	 * <p>
	 * Must run before {@link #clean(String, String)} because the cleaner
	 * removes {@code /} and {@code *} (neither is valid in WKT), which
	 * would orphan the comment body and leak it into the parser as
	 * gibberish — including any literal {@code ...} elision a human
	 * left inside a comment.
	 */
	private static String stripComments(String wkt)
	{
		if (wkt == null) return null;
		// Block comments first: /* ... */, multi-line, non-greedy.
		String s = wkt.replaceAll("(?s)/\\*.*?\\*/", "");
		// Line comments: -- to end of line (or EOF).
		s = s.replaceAll("--[^\r\n]*", "");
		return s;
	}
	
	private static String clean(String input, String allowedSymbols)
	{
		StringBuffer buf = new StringBuffer();
		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			if (isAllowed(c, allowedSymbols))
				buf.append(c);
		}
		return buf.toString();
	}
	
	private static boolean isAllowed(char c, String allowedSymbols)
	{
		if (Character.isWhitespace(c)) return true;
		if (Character.isLetterOrDigit(c)) return true;
		if (allowedSymbols.indexOf(c) >= 0) return true;
		return false;		
	}
	
}
