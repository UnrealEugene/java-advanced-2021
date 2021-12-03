package info.kgeorgiy.ja.chernatsky.i18n;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TreeSet;

@SuppressWarnings("unchecked")
public class DateStat extends AbstractDecimalStat<Date> {
    protected DateStat(final Locale inputLocale, final Locale outputLocale) {
        super("{0,date}", inputLocale, outputLocale);
    }

    @Override
    protected double getValue(final Date elem) {
        return elem.getTime();
    }

    @Override
    public String getCategoryName() {
        return "date";
    }

    @Override
    public String getMeanValueString() {
        final Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis((long) (sum / count));
        return inputFormat(calendar.getTime());
    }

    @Override
    protected void castAndConsume(final Object obj, final int length) {
        consume((Date) obj, length);
    }
}
