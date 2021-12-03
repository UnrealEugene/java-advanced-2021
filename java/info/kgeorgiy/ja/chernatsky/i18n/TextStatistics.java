package info.kgeorgiy.ja.chernatsky.i18n;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.*;

public class TextStatistics {
    private static final String STATISTICS_BUNDLE_NAME =
            TextStatistics.class.getPackageName() + ".StatisticsResourceBundle";

    private static List<AbstractCategoryStat<?>> categories;

    public static AbstractCategoryStat<?> getCategory(final int index) {
        return categories.get(index);
    }

    private static ResourceBundle getBundle(final Locale locale) {
        return ResourceBundle.getBundle(STATISTICS_BUNDLE_NAME, locale);
    }

    private static void printError(final ResourceBundle bundle, final String key, final String message) {
        System.err.println(bundle.getString(key) + ": " + message);
    }

    private static void printError(final ResourceBundle bundle, final String key, final Exception e) {
        printError(bundle, key, e.getLocalizedMessage());
    }

    private static void writeStat(
            final MessageFormat formatter,
            final BufferedWriter outputWriter,
            final Object... values
    ) throws IOException {
        outputWriter.write(formatter.format(values));
        outputWriter.newLine();
    }

    private static void performTextStatistics(
            final ResourceBundle outputBundle,
            final String inputText,
            final BufferedWriter outputWriter,
            final String inputFileName
    ) {
        try {
            outputWriter.write(outputBundle.getString("analysing_file") + " \"" + inputFileName + "\"");
            outputWriter.newLine();
            final MessageFormat smallLayout = new MessageFormat(outputBundle.getString("layout_2"));
            final MessageFormat largeLayout = new MessageFormat(outputBundle.getString("layout_3"));
            for (final AbstractCategoryStat<?> stat : categories) {
                stat.process(inputText);
                final String categoryName = stat.getCategoryName();
                outputWriter.write(outputBundle.getString("stats_for_" + categoryName));
                outputWriter.newLine();
                writeStat(smallLayout, outputWriter, outputBundle.getString("count_" + categoryName),
                        stat.getCountString());
                if (stat.getCount() > 0) {
                    writeStat(smallLayout, outputWriter, outputBundle.getString("unique_count_" + categoryName),
                            stat.getUniqueCountString());
                    writeStat(smallLayout, outputWriter, outputBundle.getString("min_" + categoryName),
                            stat.getMinElementString());
                    writeStat(smallLayout, outputWriter, outputBundle.getString("max_" + categoryName),
                            stat.getMaxElementString());
                    writeStat(largeLayout, outputWriter, outputBundle.getString("min_length_" + categoryName),
                            stat.getMinLengthString(), stat.getMinLengthElementString());
                    writeStat(largeLayout, outputWriter, outputBundle.getString("max_length_" + categoryName),
                            stat.getMaxLengthString(), stat.getMaxLengthElementString());
                    writeStat(smallLayout, outputWriter, outputBundle.getString("mean_" + categoryName),
                            stat.getMeanValueString());
                }
            }
        } catch (final IOException e) {
            printError(outputBundle, "output_write_error", e);
        }
    }

    public static void main(final String[] args) {
        ResourceBundle defaultBundle;
        try {
            defaultBundle = getBundle(Locale.getDefault());
        } catch (final MissingResourceException e) {
            defaultBundle = getBundle(Locale.US);
        }

        if (args == null || args.length != 4 || Arrays.stream(args).anyMatch(Objects::isNull)) {
            printError(defaultBundle, "usage", String.format(
                    "java %s <%s> <%s> <%s> <%s>",
                    TextStatistics.class.getCanonicalName(),
                    defaultBundle.getString("input_locale"),
                    defaultBundle.getString("output_locale"),
                    defaultBundle.getString("input_file"),
                    defaultBundle.getString("output_file")
            ));
            return;
        }

        final Locale outputLocale = Locale.forLanguageTag(args[1]);
        final ResourceBundle outputBundle;
        try {
            outputBundle = getBundle(outputLocale);
        } catch (final MissingResourceException e) {
            printError(defaultBundle,"unsupported_locale", e);
            return;
        }

        final String inputFileName;
        final Locale inputLocale = Locale.forLanguageTag(args[0]);
        final String inputText;
        try {
            final Path path = Path.of(args[2]);
            inputText = Files.readString(path, StandardCharsets.UTF_8);
            inputFileName = path.getFileName().toString();
        } catch (final InvalidPathException e) {
            printError(outputBundle, "invalid_path_error", e);
            return;
        } catch (final IOException e) {
            printError(outputBundle, "input_reading_error", e);
            return;
        }

        categories = List.of(
                new SentenceStat(inputLocale, outputLocale),
                new WordStat(inputLocale, outputLocale),
                new NumberStat(inputLocale, outputLocale),
                new CurrencyStat(inputLocale, outputLocale),
                new DateStat(inputLocale, outputLocale)
        );

        try (final BufferedWriter outputWriter = Files.newBufferedWriter(Path.of(args[3]), StandardCharsets.UTF_8)) {
            performTextStatistics(outputBundle, inputText, outputWriter, inputFileName);
        } catch (final InvalidPathException e) {
            printError(outputBundle, "invalid_path_error", e);
        } catch (final IOException e) {
            printError(outputBundle, "output_access_error", e);
        }
    }
}
