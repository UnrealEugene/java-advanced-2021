package info.kgeorgiy.ja.chernatsky.i18n;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.SortedSet;

public abstract class AbstractCategoryStat<T> {
    protected final NumberFormat outputFormatter;
    protected int minLength;
    protected int maxLength;
    protected T minLengthElem;
    protected T maxLengthElem;
    protected int count;
    protected double sum;
    protected SortedSet<T> elements;

    protected AbstractCategoryStat(final Locale outputLocale) {
        this.outputFormatter = NumberFormat.getNumberInstance(outputLocale);
    }

    protected abstract String elemToString(T elem);
    protected abstract double getValue(T elem);
    public abstract void process(String text);
    public abstract String getCategoryName();

    protected void consume(final T element, final int length) {
        if (count == 0) {
            minLength = maxLength = length;
            minLengthElem = maxLengthElem = element;
        } else {
            if (length < minLength) {
                minLength = length;
                minLengthElem = element;
            }
            if (maxLength < length) {
                maxLength = length;
                maxLengthElem = element;
            }
        }

        count++;
        sum += getValue(element);
        elements.add(element);
    }

    public String getMinElementString() {
        return elemToString(elements.first());
    }

    public String getMaxElementString() {
        return elemToString(elements.last());
    }

    public String getMinLengthString() {
        return outputFormatter.format(minLength);
    }

    public String getMaxLengthString() {
        return outputFormatter.format(maxLength);
    }

    public String getMinLengthElementString() {
        return elemToString(minLengthElem);
    }

    public String getMaxLengthElementString() {
        return elemToString(maxLengthElem);
    }

    public int getCount() {
        return count;
    }

    public String getCountString() {
        return outputFormatter.format(count);
    }

    public String getUniqueCountString() {
        return outputFormatter.format(elements.size());
    }

    public String getMeanValueString() {
        return outputFormatter.format(sum / count);
    }
}
