package info.kgeorgiy.ja.chernatsky.i18n;

import java.text.BreakIterator;
import java.util.Locale;

public class SentenceStat extends AbstractStringStat {
    protected SentenceStat(final Locale inputLocale, final Locale outputLocale) {
        super(inputLocale, outputLocale);
    }

    @Override
    public String getCategoryName() {
        return "sentence";
    }

    @Override
    protected BreakIterator getBreakIterator() {
        return BreakIterator.getSentenceInstance(inputLocale);
    }
}
