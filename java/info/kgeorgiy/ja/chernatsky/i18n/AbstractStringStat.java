package info.kgeorgiy.ja.chernatsky.i18n;

import java.text.BreakIterator;
import java.text.Collator;
import java.util.Locale;
import java.util.TreeSet;

public abstract class AbstractStringStat extends AbstractCategoryStat<String> {
    protected Locale inputLocale;

    protected AbstractStringStat(final Locale inputLocale, final Locale outputLocale) {
        super(outputLocale);
        this.inputLocale = inputLocale;
        elements = new TreeSet<>(Collator.getInstance(inputLocale)::compare);
    }

    @Override
    protected String elemToString(final String elem) {
        return "\"" + elem.replaceAll("\\s+", " ") + "\"";
    }

    @Override
    protected double getValue(final String elem) {
        return elem.trim().length();
    }

    protected abstract BreakIterator getBreakIterator();
    @Override
    public void process(final String text) {
        final BreakIterator boundary = getBreakIterator();
        boundary.setText(text);
        for (
                int begin = boundary.first(), end = boundary.next();
                end != BreakIterator.DONE;
                begin = end, end = boundary.next()
        ) {
            tryConsume(text, begin, end);
        }
    }

    protected boolean isValid(final String str) {
        return true;
    }

    protected void tryConsume(final String str, final int begin, final int end) {
        final String substr = str.substring(begin, end).trim();
        if (isValid(substr)) {
            consume(substr, substr.length());
        }
    }
}
