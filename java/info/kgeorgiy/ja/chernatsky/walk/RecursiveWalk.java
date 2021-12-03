package info.kgeorgiy.ja.chernatsky.walk;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;

public class RecursiveWalk {
    protected enum RecursiveWalkError {
        INPUT("Ошибка чтения входного файла"),
        OUTPUT("Ошибка записи в выходной файл"),
        INPUT_PATH("Некорретный формат пути входного файла"),
        OUTPUT_PATH("Некорретный формат пути выходного файла"),
        INPUT_MISSING("Входного файла по указанному пути не существует"),
        INPUT_ACCESS("Нет доступа ко входному файлу"),
        OUTPUT_DIR_CREATION("Ошибка при создании выходного файла"),
        OUTPUT_ACCESS("Нет доступа к выходному файлу");

        protected final String message;

        public String getMessage() {
            return message;
        }

        RecursiveWalkError(String message) {
            this.message = message;
        }
    }

    public static long hashPJW64(long accumulator, byte[] bytes, int size) {
        if (bytes == null) {
            return accumulator;
        }

        for (int i = 0; i < size; i++) {
            accumulator = (accumulator << 8) + (bytes[i] & 0xFF);
            long high = accumulator & 0xFF00000000000000L;
            if (high != 0) {
                accumulator ^= high >> 48;
                accumulator &= ~high;
            }
        }
        return accumulator;
    }

    public static void writeHash(BufferedWriter writer, long hash, String path) throws IOException {
        writer.write(String.format("%016x %s%n", hash, path));
    }

    private static final int BUFFER_SIZE = 1024;

    private static class RecursiveWalker extends SimpleFileVisitor<Path> {
        private final BufferedWriter writer;

        public RecursiveWalker(BufferedWriter writer) {
            this.writer = writer;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            long hash = 0;
            byte[] bytes = new byte[BUFFER_SIZE];
            int read;
            try (BufferedInputStream byteStream = new BufferedInputStream(Files.newInputStream(file))) {
                while ((read = byteStream.read(bytes)) >= 0) {
                    hash = hashPJW64(hash, bytes, read);
                }
            } catch (IOException e) {
                hash = 0;
            }

            writeHash(writer, hash, file.toString());
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            writeHash(writer, 0, file.toString());
            return FileVisitResult.CONTINUE;
        }
    }

    private static Path getPath(String path, RecursiveWalkError error) throws WalkException {
        try {
            return Path.of(path);
        } catch (InvalidPathException e) {
            throw new WalkException(error, e);
        }
    }

    public static void walk(String input, String output) throws WalkException {
        Path inputPath, outputPath;

        inputPath = getPath(input, RecursiveWalkError.INPUT_PATH);
        outputPath = getPath(output, RecursiveWalkError.OUTPUT_PATH);

        try (BufferedReader reader = Files.newBufferedReader(inputPath)) {
            try {
                if (outputPath.getParent() == null || Files.createDirectories(outputPath.getParent()) == null) {
                    throw new IOException(outputPath.toString());
                }
            } catch (IOException e) {
                throw new WalkException(RecursiveWalkError.OUTPUT_DIR_CREATION, e);
            }

            try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
                FileVisitor<Path> visitor = new RecursiveWalker(writer);

                String line;
                Path path;
                while (true) {
                    try {
                        line = reader.readLine();
                        if (line == null) {
                            break;
                        }
                    } catch (IOException e) {
                        throw new WalkException(RecursiveWalkError.INPUT, e);
                    }

                    try {
                        path = Path.of(line);
                    } catch (InvalidPathException e) {
                        writeHash(writer, 0, line);
                        continue;
                    }

                    Files.walkFileTree(path, visitor);
                }
            } catch (AccessDeniedException e) {
                throw new WalkException(RecursiveWalkError.OUTPUT_ACCESS, e);
            } catch (IOException e) {
                throw new WalkException(RecursiveWalkError.OUTPUT, e);
            }
        } catch (AccessDeniedException e) {
            throw new WalkException(RecursiveWalkError.INPUT_ACCESS, e);
        } catch (NoSuchFileException e) {
            throw new WalkException(RecursiveWalkError.INPUT_MISSING, e);
        } catch (IOException e) {
            throw new WalkException(RecursiveWalkError.INPUT, e);
        }
    }

    public static void main(String[] args) {
        if (args == null || args.length != 2 || Arrays.asList(args).contains(null)) {
            System.out.println("Usage: RecursiveWalk <input file> <output file>");
            return;
        }

        try {
            walk(args[0], args[1]);
        } catch (WalkException e) {
            System.err.println(e.getMessage());
        }
    }
}
