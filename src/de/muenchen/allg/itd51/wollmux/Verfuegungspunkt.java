package de.muenchen.allg.itd51.wollmux;

import java.util.Vector;

/**
 * Repräsentiert einen vollständigen Verfügungspunkt, der aus Überschrift (römische
 * Ziffer + Überschrift) und Inhalt besteht. Die Klasse bietet Methden an, über die
 * auf alle für den Druck wichtigen Eigenschaften des Verfügungspunktes zugegriffen
 * werden kann (z.B. Überschrift, Anzahl Zuleitungszeilen, ...)
 *
 * @author christoph.lutz
 */
public class Verfuegungspunkt {
    /**
     * Enthält den vollständigen Text der erste Zeile des Verfügungspunktes
     * einschließlich der römischen Ziffer.
     */
    protected String heading;

    /**
     * Vector of String, der alle Zuleitungszeilen enthält, die mit addParagraph
     * hinzugefügt wurden.
     */
    protected Vector<String> zuleitungszeilen;

    /**
     * Enthält die Anzahl der Ausdrucke, die mindestens ausgedruckt werden sollen.
     */
    protected int minNumberOfCopies;

    /**
     * Erzeugt einen neuen Verfügungspunkt, wobei firstPar der Absatz ist, der die
     * erste Zeile mit der römischen Ziffer und der Überschrift enthält.
     *
     * @param heading
     *            Text der ersten Zeile des Verfügungspunktes mit der römischen Ziffer
     *            und der Überschrift.
     */
    public Verfuegungspunkt(final String heading) {
        this.heading = heading;
        this.zuleitungszeilen = new Vector<String>();
        this.minNumberOfCopies = 0;
    }

    /**
     * Fügt einen weitere Zuleitungszeile des Verfügungspunktes hinzu (wenn paragraph
     * nicht null ist).
     *
     * @param paragraph
     *            XTextRange, das den gesamten Paragraphen der Zuleitungszeile enthält.
     */
    public void addZuleitungszeile(final String zuleitung) {
        zuleitungszeilen.add(zuleitung);
    }

    /**
     * Liefert die Anzahl der Ausfertigungen zurück, mit denen der Verfügungspunkt
     * ausgeduckt werden soll; Die Anzahl erhöht sich mit jeder hinzugefügten
     * Zuleitungszeile. Der Mindestwert kann mit setMinNumberOfCopies gesetzt werden.
     *
     * @return Anzahl der Ausfertigungen mit denen der Verfügungspunkt gedruckt
     *         werden soll.
     */
    public int getNumberOfCopies() {
        if (zuleitungszeilen.size() > minNumberOfCopies)
            return zuleitungszeilen.size();
        else
            return minNumberOfCopies;
    }

    /**
     * Setzt die Anzahl der Ausfertigungen, die Mindestens ausgedruckt werden sollen,
     * auch dann wenn z.B. keine Zuleitungszeilen vorhanden sind.
     *
     * @param minNumberOfCopies
     *            Anzahl der Ausfertigungen mit denen der Verfügungspunkt mindestens
     *            ausgedruckt werden soll.
     */
    public void setMinNumberOfCopies(final int minNumberOfCopies) {
        this.minNumberOfCopies = minNumberOfCopies;
    }

    /**
     * Liefert einen Vector of Strings, der die Texte der Zuleitungszeilen
     * beinhaltet, die dem Verfügungspunkt mit addParagraph hinzugefügt wurden.
     *
     * @return Vector of Strings mit den Texten der Zuleitungszeilen.
     */
    public Vector<String> getZuleitungszeilen() {
        return zuleitungszeilen;
    }

    /**
     * Liefert den Text der Überschrift des Verfügungspunktes einschließlich der
     * römischen Ziffer als String zurück, wobei mehrfache Leerzeichen (\s+) durch
     * einfache Leerzeichen ersetzt werden.
     *
     * @return römischer Ziffer + Überschrift
     */
    public String getHeading() {
        String text = heading;

        // Tabs und Spaces durch single spaces ersetzen
        text = text.replaceAll("\\s+", " ");

        return text;
    }

}
