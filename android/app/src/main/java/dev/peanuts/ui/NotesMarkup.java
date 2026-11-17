package dev.peanuts.ui;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A companion release's notes (GitHub Markdown with a little HTML, such as a contributor's badge) for the versions list:
 * simple HTML for Html.fromHtml, and one plain line. No Android classes, so it runs on the JVM.
 */
final class NotesMarkup {
    private static final Pattern IMG = Pattern.compile("(?is)<img\\b[^>]*>");
    private static final Pattern ALT = Pattern.compile("(?is)\\balt\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern BR = Pattern.compile("(?i)<br\\s*/?>");
    private static final Pattern TAG = Pattern.compile("</?[a-zA-Z][^>]*>");
    private static final Pattern BULLET = Pattern.compile("^\\s*[-*+]\\s+(.*)$");
    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s+(.*?)\\s*#*$");
    private static final Pattern BOLD = Pattern.compile("(\\*\\*|__)(.+?)\\1");
    private static final Pattern CODE = Pattern.compile("`([^`]+)`");
    private static final Pattern LINK = Pattern.compile("!?\\[([^\\]]*)\\]\\([^)]*\\)");

    private NotesMarkup() {}

    /** Paragraphs, lists, bold and code; an image is its alt text, a link its words; no other HTML is kept */
    static String html(String notes) {
        if (notes == null) return "";
        StringBuilder out = new StringBuilder(), para = new StringBuilder(), item = new StringBuilder();
        boolean list = false;
        for (String raw : untag(notes).split("\n", -1)) {
            String line = raw.trim();
            Matcher b = BULLET.matcher(raw), h = HEADING.matcher(line);
            if (line.isEmpty()) {
                flush(out, para);
                list = closeList(out, item, list);
            } else if (b.matches()) {
                flush(out, para);
                if (!list) { out.append("<ul>"); list = true; }
                if (item.length() > 0) out.append("<li>").append(item).append("</li>");
                item.setLength(0);
                item.append(inline(b.group(1).trim()));
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
            String s = raw.trim();
            Matcher h = HEADING.matcher(s), b = BULLET.matcher(s);
            if (h.matches()) s = h.group(1); else if (b.matches()) s = b.group(1);
            s = LINK.matcher(s).replaceAll("$1");
            s = BOLD.matcher(s).replaceAll("$2");
            s = CODE.matcher(s).replaceAll("$1").replaceAll("\\s+", " ").trim();
            if (!s.isEmpty()) return s.length() > 140 ? s.substring(0, 140) + "…" : s;
        }
        return "";
    }

    /** HTML to text: an image becomes its alt text, a line break a newline, every other tag goes; entities are decoded */
    private static String untag(String s) {
        s = s.replace("\r\n", "\n");
        Matcher m = IMG.matcher(s);
        StringBuffer b = new StringBuffer();   // the StringBuilder forms of appendReplacement/appendTail are API 34 (UPSTREAM-EDITS round 12)
        while (m.find()) {
            Matcher alt = ALT.matcher(m.group());
            m.appendReplacement(b, Matcher.quoteReplacement(alt.find() ? alt.group(1) : ""));
        }
        m.appendTail(b);
        s = BR.matcher(b).replaceAll("\n");
        s = TAG.matcher(s).replaceAll("");
        return s.replace("&nbsp;", " ").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }

    /** One line's words as HTML: escaped, then bold, code and a link's words */
    private static String inline(String s) {
        s = LINK.matcher(s).replaceAll("$1");
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
