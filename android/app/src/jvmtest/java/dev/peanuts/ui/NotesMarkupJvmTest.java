// PEANUTS: NotesMarkup on the JVM (peanuts/tests/test_notes_markup.py); prints "ok <n>" or throws
package dev.peanuts.ui;

public final class NotesMarkupJvmTest {
    private static int n;

    private static void eq(String want, String got) {
        n++;
        if (!want.equals(got)) throw new AssertionError("\nwant: " + want + "\n got: " + got);
    }

    public static void main(String[] a) {
        // a contributor's badge: the name from the image's alt text, no HTML left
        String badge = "Cult of the Lamb (01002E7016C46000), game versions 1.5.27 and 1.5.28.\n\n"
                + "<a href=\"https://github.com/AlterDigga\"><img src=\"https://img.shields.io/badge/x\" style=\"vertical-align: middle;\" alt=\"AlterDigga\"></a> Companion by AlterDigga.\n";
        eq("<p>Cult of the Lamb (01002E7016C46000), game versions 1.5.27 and 1.5.28.</p><p>AlterDigga Companion by AlterDigga.</p>", NotesMarkup.html(badge));
        eq("Cult of the Lamb (01002E7016C46000), game versions 1.5.27 and 1.5.28.", NotesMarkup.plain(badge));
        eq("AlterDigga Companion by AlterDigga.", NotesMarkup.plain("<a href=\"x\"><img alt=\"AlterDigga\"></a> Companion by AlterDigga."));

        // a bullet wrapped onto indented lines, bold, a paragraph after the list
        String list = "Game (01008CF01BAAC000), game version 1.1.0.\n\n"
                + "- **Fixed:** the three echo circles are your own picks again, kept per save\n"
                + "  while that save has the Echo learned (#31).\n"
                + "- Second.\n\n"
                + "Everything else is unchanged.\n";
        eq("<p>Game (01008CF01BAAC000), game version 1.1.0.</p><ul><li><b>Fixed:</b> the three echo circles are your own picks again, kept per save"
                + " while that save has the Echo learned (#31).</li><li>Second.</li></ul><p>Everything else is unchanged.</p>", NotesMarkup.html(list));

        // headings, code, links' words, escaping, Windows line ends, entities
        eq("<p><b>New</b></p><p>Use <tt>DM_X=1</tt> on the docs page, a &lt; b &amp; c.</p>",
                NotesMarkup.html("## New\r\n\r\nUse `DM_X=1` on [the docs page](https://example.org), a &lt; b &amp; c.\r\n"));
        eq("New", NotesMarkup.plain("\n\n## New\nmore"));
        eq("Fixed: works", NotesMarkup.plain("- **Fixed:** works"));
        eq("", NotesMarkup.html(null));
        eq("", NotesMarkup.plain("<img src=\"x\">\n\n"));
        System.out.println("ok " + n);
    }
}
