package info.kgeorgiy.ja.chernatsky.i18n;

import java.text.MessageFormat;
import java.text.ParsePosition;
import java.util.Locale;
import java.util.TreeSet;

public abstract class AbstractDecimalStat<T extends Comparable<T>> extends AbstractCategoryStat<T> {
    final MessageFormat inputFormatter;

    protected AbstractDecimalStat(final String pattern, final Locale inputLocale, final Locale outputLocale) {
        super(outputLocale);
        this.inputFormatter = new MessageFormat(pattern, inputLocale);
        elements = new TreeSet<>();
    }

    protected String inputFormat(final Object... values) {
        return inputFormatter.format(values);
    }

    @Override
    protected String elemToString(final T elem) {
        return inputFormat(elem);
    }

    @Override
    public String getMeanValueString() {
        return inputFormat(sum / count);
    }

    @Override
    public void process(final String text) {
        for (int i = 0; i < text.length(); ) {
            i += Math.max(1, tryConsume(text, i));
        }
    }

    protected abstract void castAndConsume(final Object obj, final int length);

    protected int tryConsume(final String str, final int begin) {
        final ParsePosition position = new ParsePosition(begin);
        final Object[] result = inputFormatter.parse(str, position);
        final int length = position.getIndex() - begin;
        if (length > 0) {
            castAndConsume(result[0], length);
        }
        return length;
    }
}
