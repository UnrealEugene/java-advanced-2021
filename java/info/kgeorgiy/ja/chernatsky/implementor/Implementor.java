package info.kgeorgiy.ja.chernatsky.implementor;

import info.kgeorgiy.java.advanced.implementor.ImplerException;
import info.kgeorgiy.java.advanced.implementor.JarImpler;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.CodeSource;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Instances of this class provides a method {@link #implement(Class, Path)} for implementing
 * classes and interfaces and {@link #implementJar(Class, Path)} for creating .jar file containing
 * generated implementations.
 * <p>
 * Usage example:
 * <blockquote>
 *     {@code new Implementor().implement(java.util.List.class, Path("./impl_out"))}
 *     <p>
 *     {@code new Implementor().implementJar(java.util.List.class, Path("List.jar"))}
 * </blockquote>
 *
 */
public class Implementor implements JarImpler {
    /**
     * Anonymous implementation of {@link FileVisitor} for
     * deleting directories and its contents.
     */
    private static final SimpleFileVisitor<Path> DELETE_VISITOR = new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
        }
    };

    /**
     * Wrapper for {@link Executable}
     * which compares only executable {@link Executable#getName() names}
     * and {@link Executable#getParameterTypes() parameter types}
     * when checking equality. Inner executable can be returned with {@link #getExecutable()}.
     * <p>
     * Also instances are able to generate Java source code for themselves with
     * methods {@link #toCodeTokens()} which needs to be implemented in inheritors.
     */
    private static abstract class ExecutableSignature {
        /**
         * Wrapped executable.
         */
        private final Executable executable;

        /**
         * Creates an instance with given executable.
         *
         * @param executable Given executable.
         */
        private ExecutableSignature(Executable executable) {
            this.executable = executable;
        }

        /**
         * Returns inner executable wrapped in this object.
         *
         * @return wrapped executable.
         */
        protected Executable getExecutable() {
            return executable;
        }

        /**
         * Indicates whether this object is equal to another object.
         * <p>
         * Two {@link ExecutableSignature} instances are equal
         * if their executable {@link Executable#getName() names}
         * and {@link Executable#getParameterTypes() parameter types}
         * are equal.
         *
         * @param obj Another object to compare to.
         * @return {@code true} if objects are equal; {@code false}
         * otherwise.
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ExecutableSignature)) {
                return false;
            }
            ExecutableSignature other = (ExecutableSignature) obj;
            return executable.getName().equals(other.executable.getName()) &&
                    Arrays.equals(executable.getParameterTypes(), other.executable.getParameterTypes());
        }

        /**
         * Returns a hash value for this object.
         *
         * @return a hash code value for this object.
         */
        @Override
        public int hashCode() {
            return Objects.hash(executable.getName(), Arrays.hashCode(executable.getParameterTypes()));
        }

        /**
         * Generates Java source code for wrapped executable which
         * can be inserted in the source code without compilation
         * errors as if it ws overridden by another class.
         *
         * @return Java source code for given executable so it
         * can be inserted in the source code of inheritor.
         */
        public abstract String toCodeTokens();
    }

    /**
     * Implementation of {@link ExecutableSignature} for methods.
     */
    private static class MethodSignature extends ExecutableSignature {
        /**
         * Creates an instance with given method.
         *
         * @param method Given method.
         */
        private MethodSignature(Method method) {
            super(method);
        }

        /**
         * Returns inner method wrapped in this object.
         *
         * @return wrapped method.
         */
        private Method getMethod() {
            return (Method) getExecutable();
        }

        /**
         * Generates Java source code for wrapped method which
         * can be inserted in the source code without compilation
         * errors as if it were overridden by another class.
         * <p>
         * Generated methods are public, they ignore their
         * arguments and always return {@link #defaultValueCodeToken(Class)
         * default value} of their return type.
         *
         * @return Java source code for given method so it
         * can be inserted in the source code of inheritor.
         */
        @Override
        public String toCodeTokens() {
            Method method = getMethod();
            return String.format("\tpublic %s %s(%s) { return %s; }%n",
                    method.getReturnType().getCanonicalName(),
                    method.getName(),
                    methodParametersCodeTokens(method, true),
                    defaultValueCodeToken(method.getReturnType()));
        }
    }

    /**
     * Implementation of {@link ExecutableSignature} for constructors.
     */
    private static class ConstructorSignature extends ExecutableSignature {
        /**
         * Creates an instance with given executable.
         *
         * @param constructor Given constructor.
         */
        private ConstructorSignature(Constructor<?> constructor) {
            super(constructor);
        }

        /**
         * Returns inner constructor wrapped in this object.
         *
         * @return wrapped constructor.
         */
        private Constructor<?> getConstructor() {
            return (Constructor<?>) getExecutable();
        }

        /**
         * Generates Java source code for wrapped constructor which
         * can be inserted in the source code without compilation
         * errors as if it were overridden by another class.
         * <p>
         * Generated constructors are public and they always call
         * constructor with the same arguments from the superclass.
         *
         * @return Java source code for given constructor so it
         * can be inserted in the source code of inheritor.
         */
        @Override
        public String toCodeTokens() {
            Constructor<?> constructor = getConstructor();
            return String.format("\tpublic %s(%s)%s { super(%s); }%n",
                    constructor.getDeclaringClass().getSimpleName() + "Impl",
                    methodParametersCodeTokens(constructor, true),
                    throwsCodeTokens(constructor),
                    methodParametersCodeTokens(constructor, false));
        }
    }

    /**
     * Construct a new {@link Implementor} object.
     */
    public Implementor() { }

    /**
     * Returns Java source code token which can be inserted
     * between implemented class name and parent's
     * {@link Class#getCanonicalName() canonical name}.
     *
     * @param parent Parent type token.
     * @return {@code "implements"} if parent represents interface;
     * {@code "extends"} otherwise.
     */
    private static String inheritanceCodeToken(Class<?> parent) {
        return parent.isInterface() ? "implements" : "extends";
    }

    /**
     * Returns Java source code token - the default value of given
     * type which can be inserted after method's return operator.
     *
     * @param returnType Method's return type token.
     * @return empty string for {@code void.class};
     * {@code "false"} for {@code boolean.class};
     * {@code "0"} for primitive types;
     * {@code "null"} otherwise.
     */
    private static String defaultValueCodeToken(Class<?> returnType) {
        return  void.class.equals(returnType)    ? "" :
                boolean.class.equals(returnType) ? "false" :
                        returnType.isPrimitive()         ? "0" : "null";
    }

    /**
     * Returns string of Java source code tokens which
     * can be inserted as executable declaration parameters (if
     * boolean argument is {@code true}), or can be inserted as
     * executable call arguments (if boolean argument is {@code
     * false}). Every argument is separated with comma and single
     * whitespace.
     *
     * @param executable Executable providing parameter types.
     * @param needTypes Determines if parameter type names are
     *                  needed before argument names.
     * @return string of pairs of parameter types and
     * names if boolean argument is {@code true};
     * string of just parameter names otherwise.
     */
    private static String methodParametersCodeTokens(Executable executable, boolean needTypes) {
        Class<?>[] params = executable.getParameterTypes();
        final String ARG_PREFIX = "arg";
        return IntStream.range(0, params.length)
                .mapToObj(i -> (needTypes ? params[i].getCanonicalName() + " " : "") + ARG_PREFIX + i)
                .collect(Collectors.joining(", "));
    }

    /**
     * Returns string of Java source code tokens which can be inserted
     * after declaring executable parameter types to indicate which
     * checked exception could be thrown by this executable.
     * <p>
     * If no checked exceptions can be thrown by this executable, returns
     * empty string. Otherwise it returns {@code " throws "} continued with exception's
     * {@link Class#getCanonicalName() canonical type name}
     * separated with comma and single whitespace.
     *
     * @param executable Executable providing exception types which
     *                   could be thrown.
     * @return {@code " throws "} continued by exception canonical
     * type names if at least one exception can be thrown;
     * empty string if no exceptions can be thrown.
     */
    private static String throwsCodeTokens(Executable executable) {
        if (executable.getExceptionTypes().length == 0) {
            return "";
        }
        return " throws " + Arrays.stream(executable.getExceptionTypes())
                .map(Class::getCanonicalName)
                .collect(Collectors.joining(", "));
    }

    /**
     * Returns Java source code tokens declaring class package,
     * which can be inserted in the beginning of the source
     * code. If invoking {@link Class#getPackageName()}
     * on this class returns null, then this method returns empty string.
     * Otherwise it returns {@code "package "} continued with
     * package name obtained by {@link Class#getPackageName()} and ending in semicolon and two line separators.
     *
     * @param clazz Class type token.
     * @return {@code "package "} continued with package name
     * or empty string if class is declared in default package.
     */
    private static String packageCodeTokens(Class<?> clazz) {
        return clazz.getPackageName().isEmpty() ? "" : String.format("package %s;%n%n", clazz.getPackageName());
    }

    /**
     * Determines if executable doesn't have access modifier {@link Modifier#isPrivate private}.
     *
     * @param executable Given executable.
     * @return {@code false} if executable is private;
     * {@code true} otherwise.
     */
    private static boolean isNotPrivate(Executable executable) {
        return !Modifier.isPrivate(executable.getModifiers());
    }

    /**
     * Converts array of {@link Method methods} into set of
     * {@link MethodSignature method signatures}.
     *
     * @param methods Array of methods.
     * @return set of method signatures.
     */
    private Set<MethodSignature> toMethodSignatureSet(Method[] methods) {
        return Arrays.stream(methods)
                .map(MethodSignature::new)
                .collect(Collectors.toSet());
    }

    /**
     * Returns set of {@link Method methods}
     * wrapped in {@link ExecutableSignature} which are needed
     * to be overridden in order to implement parent class or interface without
     * compilation errors.
     *
     * @param parent Parent type token.
     * @return set of methods wrapped in {@code ExecutableSignature} which are needed
     * to be overridden.
     * @see #getConstructorsToImplement(Class)
     */
    private Set<ExecutableSignature> getMethodsToImplement(Class<?> parent) {
        Set<MethodSignature> result = toMethodSignatureSet(parent.getMethods());

        while (parent != null) {
            result.addAll(toMethodSignatureSet(parent.getDeclaredMethods()));
            parent = parent.getSuperclass();
        }

        return result.stream()
                .filter(m -> Modifier.isAbstract(m.getMethod().getModifiers()))
                .collect(Collectors.toSet());
    }

    /**
     * Returns set of {@link Constructor constructors}
     * wrapped in {@link ExecutableSignature} which are needed
     * to be overridden in order to implement parent class without compilation
     * errors. If interface is provided, returns empty set.
     *
     * @param parent Parent type token.
     * @return set of methods wrapped in {@code ExecutableSignature} which are needed
     * to be overridden.
     * @see #getMethodsToImplement(Class)
     */
    private Set<ExecutableSignature> getConstructorsToImplement(Class<?> parent) {
        return Arrays.stream(parent.getDeclaredConstructors())
                .filter(Implementor::isNotPrivate)
                .map(ConstructorSignature::new)
                .collect(Collectors.toSet());
    }

    /**
     * Returns set of {@link Executable executables}
     * wrapped in {@link ExecutableSignature} which are needed
     * to be overridden in order to implement parent class or interface without
     * compilation errors. The only executables which are needed to be overridden actually
     * are {@link Method methods} and {@link Constructor constructors}.
     *
     * @param parent Parent type token.
     * @return set of executables wrapped in {@code ExecutableSignature} which are needed
     * to be overridden.
     * @see #getMethodsToImplement(Class)
     * @see #getConstructorsToImplement(Class)
     */
    private Set<ExecutableSignature> getExecutablesToImplement(Class<?> parent) {
        return Stream.of(getMethodsToImplement(parent), getConstructorsToImplement(parent))
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }

    /**
     * Constructs exception which is going to be thrown when trying implementing
     * some forbidden cases.
     *
     * @param name Forbidden case name.
     * @return corresponding exception.
     */
    private ImplerException classRequirementError(String name) {
        return new ImplerException(name + " implementations are forbidden!");
    }

    /**
     * Returns the path which should correspond to the source file
     * of generated class.
     * <p>
     * This path starts at the {@code root} and ends in the package
     * corresponding to the package of parent class token. Generated
     * source file name is parent's {@link Class#getSimpleName() simple name}
     * with {@code "Impl.java"} at the end.
     * @param root Generated Java source files root.
     * @param clazz Class token of parent of generated class.
     * @return path to the source file of the generated class.
     */
    private Path getSourceFilePath(Path root, Class<?> clazz) {
        return root.resolve(clazz.getPackageName().replace('.', File.separatorChar))
                .resolve(clazz.getSimpleName().concat("Impl.java"));
    }

    /**
     * Returns Java unicode escape sequence which can be replaced
     * with given character in the source code.
     * @param c Given character's integer representation.
     * @return Java unicode escape sequence for given character.
     */
    private static String toUnicodeEscapeToken(int c) {
        return String.format("\\u%04x", c);
    }

    /**
     * Produces code implementing class or interface specified by provided
     * {@link Object#getClass() token}.
     * <p>
     * Generated classes have the same name with their parents with {@code Impl} suffix
     * added. Generated source code is placed in the correct subdirectory of the specified
     * {@code root} directory corresponding to class package and have correct file name.
     * <p>
     * Generic types are not supported. All generic types occurrences will be
     * replaced with corresponding raw types.
     * <p>
     * Source code can't be generated if given type is one of these types:
     * <ul>
     *     <li>Primitive type ({@code int}, {@code boolean}, etc).</li>
     *     <li>Array type.</li>
     *     <li>Final class.</li>
     *     <li>Private nested class or interface.</li>
     *     <li>Enumeration ({@code enum})</li>
     *     <li>{@link Enum java.lang.Enum}</li>
     * </ul>
     *
     * <p>
     * For example, the implementation of the interface
     * {@link List} should go to {@code $root/java/util/ListImpl.java}
     *
     * @param token type token to create implementation for.
     * @param root root directory.
     * @throws ImplerException when implementation cannot be
     * generated.
     */
    @Override
    public void implement(Class<?> token, Path root) throws ImplerException {
        if (token.isPrimitive()) {
            throw classRequirementError("Primitive");
        }
        if (token.isArray()) {
            throw classRequirementError("Array");
        }
        if (Modifier.isFinal(token.getModifiers())) {
            throw classRequirementError("Final class");
        }
        if (Modifier.isPrivate(token.getModifiers())) {
            throw classRequirementError("Private class/interface");
        }
        if (token.isEnum()) {
            throw classRequirementError("Enumeration");
        }
        if (token.equals(Enum.class)) {
            throw classRequirementError("java.lang.Enum");
        }
        Constructor<?>[] constructors = token.getDeclaredConstructors();
        if (constructors.length > 0 && Arrays.stream(constructors).noneMatch(Implementor::isNotPrivate)) {
            throw classRequirementError("Class with only private constructors");
        }

        Path path = getSourceFilePath(root, token);
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException e) {
            throw new ImplerException("Can't create directory corresponding to class package!", e);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            String sourceCode = String.format("%spublic class %sImpl %s %s {%n%s}%n",
                    packageCodeTokens(token),
                    token.getSimpleName(),
                    inheritanceCodeToken(token),
                    token.getCanonicalName(),
                    getExecutablesToImplement(token)
                            .stream()
                            .map(ExecutableSignature::toCodeTokens)
                            .collect(Collectors.joining()));
            writer.write(sourceCode.chars()
                    .mapToObj(Implementor::toUnicodeEscapeToken)
                    .collect(Collectors.joining()));
        } catch (IOException e) {
            throw new ImplerException("An error has occurred while writing in the file!", e);
        }
    }

    /**
     * Tries to delete non empty directory given by its path.
     * If fails prints error message in {@code stderr}.
     * @param path Directory path which is needed to be deleted.
     */
    private void deleteNonEmptyDirectory(Path path) {
        try {
            Files.walkFileTree(path, DELETE_VISITOR);
        } catch (IOException e) {
            System.err.println("Couldn't delete temporary directory! " + e.getMessage());
        }
    }

    /**
     * Exception which is being thrown in {@link #compile(Class, Path, File)} method
     * if errors during compilation occur.
     */
    private static class CompilerException extends Exception {
        /**
         * Constructs a new {@link CompilerException} with {@code null}
         * as its detail message.
         */
        public CompilerException() {
            super();
        }

        /**
         * Constructs a new {@link CompilerException} with {@code message}
         * as its detail message.
         */
        public CompilerException(String message) {
            super(message);
        }

        /**
         * Constructs a new {@link CompilerException} with {@code null}
         * as its detail message and {@code cause} as its cause.
         */
        public CompilerException(Exception cause) {
            super(cause);
        }
    }

    /**
     * Returns compiler flags which need to be inserted after {@code javac}
     * command for successful given source file compilation.
     *
     * @param token Generated class' parent token.
     * @param root Source file root.
     * @param file Source file.
     * @return Class path for compilation.
     * @throws CompilerException when class path can not be acquired.
     */
    private String[] getCompilerFlags(Class<?> token, Path root, File file) throws CompilerException {
        CodeSource codeSource = token.getProtectionDomain().getCodeSource();
        if (codeSource == null) {
            return new String[] {
                    "--patch-module",
                    token.getModule().getName() + "=" + root,
                    file.toString()
            };
        }
        try {
            return new String[] {
                    "-cp",
                    Path.of(codeSource.getLocation().toURI()).toString(),
                    file.toString()
            };
        } catch (URISyntaxException e)  {
            throw new CompilerException(e);
        }
    }

    /**
     * Compiles Java source file and places it in the same
     * directory as source file.
     *
     * @param token Generated class' parent token.
     * @param root Source file root directory.
     * @param file Source file which needs to be compiled.
     * @throws CompilerException if error occurs during compilation
     * of the file.
     */
    private void compile(Class<?> token, Path root, File file) throws CompilerException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new CompilerException("Could not find java compiler, include tools.jar to classpath!");
        }

        String[] compilerFlags;
        try {
            compilerFlags = getCompilerFlags(token, root, file);
        } catch (CompilerException e) {
            // :NOTE: you should not lose exception context
            throw new CompilerException("Could not resolve classpath!");
        }

        int exitCode = compiler.run(null, null, null, compilerFlags);
        if (exitCode != 0) {
            throw new CompilerException(
                    "Java compiler exited with nonzero code " + exitCode + " on generated .java file!");
        }
    }

    /**
     * Generates Jar file with compiled Java code consisting
     * of single class or interface specified by provided {@code token}.
     * <p>
     * More specifically, calls {@link #implement(Class, Path)} method for
     * temporary directory, compiles generated Java source file
     * and creates Jar file from this temporary directory.
     * @param token type token to create implementation for.
     * @param filePath output Jar file path.
     * @throws ImplerException when Jar file can not be generated.
     */
    @Override
    public void implementJar(Class<?> token, Path filePath) throws ImplerException {
        try {
            Files.createDirectories(filePath.getParent());
        } catch (IOException e) {
            // :NOTE: no need to interrupt the process
//            throw new ImplerException("Can't create directory corresponding to .jar file!", e);
        }

        Path path;
        try {
            path = Files.createTempDirectory(filePath.getParent(), "implement-");
        } catch (IOException e) {
            throw new ImplerException("Couldn't create temporary directory!", e);
        }
        // :NOTE: if this throws your temp path will not be deleted
        implement(token, path);

        try {
            Path sourcePath = getSourceFilePath(path, token);

            try {
                compile(token, path, sourcePath.toFile());
            } catch (CompilerException e) {
                throw new ImplerException("Couldn't compile generated .java file! " + e.getMessage());
            }

            String classFileName = token.getSimpleName() + "Impl.class";

            try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(filePath))) {
                ZipEntry entry = new ZipEntry(token.getPackageName().replace('.', '/') + "/" + classFileName);
                zipOutputStream.putNextEntry(entry);
                // :NOTE: readAllBytes reads everything in-memory
                zipOutputStream.write(Files.readAllBytes(sourcePath.resolveSibling(classFileName)));
            } catch (IOException e) {
                throw new ImplerException("Couldn't create .jar file! " + e.getMessage());
            }
        } finally {
            deleteNonEmptyDirectory(path);
        }
    }

    /**
     * Main method for implementing classes. Calls {@link #implement(Class, Path)} or
     * {@link #implementJar(Class, Path)} based on number of arguments passed.
     * <p>
     * If array of two elements is passed, calls {@link #implement(Class, Path)} with
     * {@code args[0]} parsed into the class token as the first argument and
     * {@code args[1]} parsed into output directory path as the second
     * argument.
     * <p>
     * If array of three elements is passed, calls {@link #implementJar(Class, Path)}
     * with {@code args[1]} parsed into the class token as the first argument and
     * {@code args[2]} parsed into output Jar file path as the second
     * argument.
     *
     * @param args array of arguments for the application. Must be contained of
     *             two or three non null strings.
     */
    public static void main(String[] args) {
        if (args == null || (args.length != 2 && args.length != 3) || Arrays.asList(args).contains(null)) {
            System.err.println("Usage: java -jar JarImplementor.jar [-jar] <class_name> <output>");
            return;
        }

        try {
            Class<?> clazz = Class.forName(args[args.length - 2]);
            Path outputPath = Path.of(args[args.length - 1]);
            if (args.length == 2) {
                new Implementor().implement(clazz, outputPath);
            } else {
                new Implementor().implementJar(clazz, outputPath);
            }
        } catch (InvalidPathException e) {
            System.err.println("Invalid output directory: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.err.println("Can't find class corresponding to given class name! " + e.getMessage());
        } catch (ImplerException e) {
            System.err.println("Implementor error occurred: " + e.getMessage());
        }
    }
}