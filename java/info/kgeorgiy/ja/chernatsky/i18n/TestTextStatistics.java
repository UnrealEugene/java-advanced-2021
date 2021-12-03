package info.kgeorgiy.ja.chernatsky.i18n;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TestTextStatistics {
//    private static final String ROOT = "java-solutions/info/kgeorgiy/ja/chernatsky/i18n/tests";
    private static final String ROOT = "java/info/kgeorgiy/ja/chernatsky/i18n/tests";
    private static Path getFilePath(final String fileName) {
        return Path.of(ROOT).resolve(fileName);
    }

    private void test(final String name, final String inputLocale, final String outputLocale, final boolean valid) {
        System.out.println("=== Running " + name);
        TextStatistics.main(new String[] {
                inputLocale,
                outputLocale,
                getFilePath(name + ".in").toString(),
                getFilePath(name + ".out").toString()
        });

        final String output;
        try {
             output = Files.readString(getFilePath(name + ".out"));
        } catch (final IOException e) {
            throw new AssertionError("Can't read output file", e);
        }

        if (valid) {
            final String answer;
            try {
                answer = Files.readString(getFilePath(name + ".ans"));
            } catch (final IOException e) {
                throw new AssertionError("Can't read answer file", e);
            }

            Assert.assertEquals("Files are not identical", output, answer);
        }
    }

    @Test
    public void testStatementRU() {
        test("statementRU", "ru-RU", "ru-RU", true);
    }

    @Test
    public void testBengaliUS() {
        test("bengaliUS", "bn-BD", "en-US", true);
    }

    @Test
    public void testChineseRU() {
        test("chineseRU", "zh-CN", "ru-RU", true);
    }

    @Test
    public void testLoremUS() {
        test("loremUS", "en-US", "en-US", true);
    }

    @Test
    public void testUnknownFile() {
        try {
            test("unknownFile", "ru-RU", "en-US", false);
        } catch (final AssertionError e) {
            return; // ok
        }
        Assert.fail("Output file found");
    }

    @Test
    public void testArabRU() {
        test("arabRU", "ar-EG", "ru-RU", true);
    }

    @Test
    public void testJapaneseUS() {
        test("japaneseUS", "ja-JP", "en-US", true);
    }
}
