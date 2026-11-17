package dev.peanuts.ui;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A companion release's notes (GitHub Markdown with a little HTML, such as a contributor's badge) for the versions list:
 * simple HTML for Html.fromHtml, and one plain line. Pictures are left out (a badge repeats the name the text says).
 * No Android classes, so it runs on the JVM.
 */
final class NotesMarkup {
    private static final Pattern BR = Pattern.compile("(?i)<br\\s*/?>");
    private static final Pattern TAG = Pattern.compile("</?[a-zA-Z][^>]*>");
    private static final Pattern BULLET = Pattern.compile("^\\s*[-*+]\\s+(.*)$");
    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s+(.*?)\\s*#*$");
    private static final Pattern BOLD = Pattern.compile("(\\*\\*|__)(.+?)\\1");
    private static final Pattern CODE = Pattern.compile("`([^`]+)`");
    private static final Pattern IMAGE = Pattern.compile("!\\[[^\\]]*\\]\\([^)]*\\)");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]*)\\]\\([^)]*\\)");

    private NotesMarkup() {}

    /** Paragraphs, lists, bold and code; a link is its words; no other HTML is kept */
    static String html(String notes) {
        if (notes == null) return "";
        StringBuilder out = new StringBuilder(), para = new StringBuilder(), item = new StringBuilder();
        boolean list = false;
        for (String raw : untag(notes).split("\n", -1)) {
            String line = words(raw);
            Matcher b = BULLET.matcher(raw), h = HEADING.matcher(line);
            if (line.isEmpty()) {
                flush(out, para);
                list = closeList(out, item, list);
            } else if (b.matches()) {
                flush(out, para);
                if (!list) { out.append("<ul>"); list = true; }
                if (item.length() > 0) out.append("<li>").append(item).append("</li>");
                item.setLength(0);
                item.append(inline(words(b.group(1))));
            } else if (h.matches()) {
                flush(out, para);
                list = closeList(out, item, list);
                out.append("<p><b>").append(inline(h.group(1))).append("</b></p>");
            } else if (list && Character.isWhitespace(raw.charAt(0))) {
                item.append(' ').append(inline(line));   // a bullet's text wrapped onto the next line
            } else {
                list = closeList(out, item, list);
                if (para.length() > 0) para.append(' ');
                para.append(inline(line));
            }
        }
        flush(out, para);
        closeList(out, item, list);
        return out.toString();
    }

    /** The first line with words, without markup, at most 140 characters */
    static String plain(String notes) {
        if (notes == null) return "";
        for (String raw : untag(notes).split("\n")) {
            String s = words(raw);
            Matcher h = HEADING.matcher(s), b = BULLET.matcher(s);
            if (h.matches()) s = h.group(1); else if (b.matches()) s = b.group(1);
            s = BOLD.matcher(s).replaceAll("$2");
            s = CODE.matcher(s).replaceAll("$1").trim();
            if (!s.isEmpty()) return s.length() > 140 ? s.substring(0, 140) + "…" : s;
        }
        return "";
    }

    /** HTML to text: a line break a newline, every other tag (pictures too) goes; entities are decoded */
    private static String untag(String s) {
        s = BR.matcher(s.replace("\r\n", "\n")).replaceAll("\n");
        s = TAG.matcher(s).replaceAll("");
        return s.replace("&nbsp;", " ").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }

    /** A line without Markdown pictures, a link as its words, single spaces */
    private static String words(String s) {
        s = LINK.matcher(IMAGE.matcher(s).replaceAll("")).replaceAll("$1");
        return s.replaceAll("[ \\t]{2,}", " ").trim();
    }

    /** One line's words as HTML: escaped, then bold and code */
    private static String inline(String s) {
        s = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        s = BOLD.matcher(s).replaceAll("<b>$2</b>");
        return CODE.matcher(s).replaceAll("<tt>$1</tt>");
    }

    private static void flush(StringBuilder out, StringBuilder para) {
        if (para.length() == 0) return;
        out.append("<p>").append(para).append("</p>");
        para.setLength(0);
    }

    private static boolean closeList(StringBuilder out, StringBuilder item, boolean list) {
        if (!list) return false;
        if (item.length() > 0) out.append("<li>").append(item).append("</li>");
        item.setLength(0);
        out.append("</ul>");
        return false;
    }
}
