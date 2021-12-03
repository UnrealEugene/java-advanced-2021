package info.kgeorgiy.ja.chernatsky.i18n;

import java.util.Locale;

@SuppressWarnings("unchecked")
public class CurrencyStat extends AbstractDecimalStat<Double> {
    protected CurrencyStat(final Locale inputLocale, final Locale outputLocale) {
        super("{0,number,currency}", inputLocale, outputLocale);
    }

    @Override
    public String getCategoryName() {
        return "currency";
    }

    @Override
    protected double getValue(final Double elem) {
        return elem;
    }

    @Override
    protected void castAndConsume(final Object obj, final int length) {
        consume(((Number) obj).doubleValue(), length);
    }
}
