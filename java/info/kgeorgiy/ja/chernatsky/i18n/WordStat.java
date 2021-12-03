package info.kgeorgiy.ja.chernatsky.i18n;

import java.text.BreakIterator;
import java.util.Locale;

public class WordStat extends AbstractStringStat {
    protected WordStat(final Locale inputLocale, final Locale outputLocale) {
        super(inputLocale, outputLocale);
    }

    @Override
    public String getCategoryName() {
        return "word";
    }

    @Override
    protected BreakIterator getBreakIterator() {
        return BreakIterator.getWordInstance(inputLocale);
    }

    @Override
    protected boolean isValid(final String str) {
        return str.codePoints().anyMatch(Character::isLetter);
    }
}
