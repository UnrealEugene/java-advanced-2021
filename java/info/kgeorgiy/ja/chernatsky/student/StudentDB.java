package info.kgeorgiy.ja.chernatsky.student;

import info.kgeorgiy.java.advanced.student.AdvancedQuery;
import info.kgeorgiy.java.advanced.student.Group;
import info.kgeorgiy.java.advanced.student.GroupName;
import info.kgeorgiy.java.advanced.student.Student;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class StudentDB implements AdvancedQuery {
    private static final Comparator<Student> COMPARING_STUDENT_NAMES =
            Comparator.comparing(Student::getLastName)
            .thenComparing(Student::getFirstName).reversed()
            .thenComparing(Student::getId);

    private static String studentFullName(Student student) {
        return String.join(" ", student.getFirstName(), student.getLastName());
    }

    private List<Group> mapToGroupsList(Map<GroupName, List<Student>> map, Comparator<Student> comp) {
        return map.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new Group(e.getKey(), sortStudentsBy(e.getValue(), comp)))
                .collect(Collectors.toList());
    }

    private List<Group> getGroupsBy(Collection<Student> students, Comparator<Student> comp) {
        return students
                .stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.groupingBy(Student::getGroup),
                        map -> mapToGroupsList(map, comp)
                )
        );
    }

    @Override
    public List<Group> getGroupsByName(Collection<Student> students) {
        return getGroupsBy(students, COMPARING_STUDENT_NAMES);
    }

    @Override
    public List<Group> getGroupsById(Collection<Student> students) {
        return getGroupsBy(students, Comparator.comparing(Student::getId));
    }

    private <T, I extends Comparable<? super I>> T getLargestFieldWith(
            Collection<Student> students,
            Function<Student, T> getter,
            Collector<Student, ?, I> collector,
            Comparator<Map.Entry<T, I>> comp,
            T defaultValue) {
        return students
                .stream()
                .collect(Collectors.groupingBy(getter, collector))
                .entrySet()
                .stream()
                .max(comp)
                .map(Map.Entry::getKey)
                .orElse(defaultValue);
    }

    private <I extends Comparable<? super I>> GroupName getLargestGroupWith(
            Collection<Student> students,
            Collector<Student, ?, I> collector,
            Comparator<Map.Entry<GroupName, I>> compIfEquals) {
        return getLargestFieldWith(
                students,
                Student::getGroup,
                collector,
                Map.Entry.<GroupName, I>comparingByValue().thenComparing(compIfEquals),
                null
        );
    }

    @Override
    public GroupName getLargestGroup(Collection<Student> students) {
        return getLargestGroupWith(
                students,
                Collectors.counting(),
                Map.Entry.comparingByKey()
        );
    }

    private static <T, S> Collector<T, ?, Integer> distinctCountingBy(Function<T, S> getter) {
        return Collectors.mapping(
                getter,
                Collectors.collectingAndThen(Collectors.toSet(), Set<S>::size)
        );
    }

    @Override
    public GroupName getLargestGroupFirstName(Collection<Student> students) {
        return getLargestGroupWith(
                students,
                distinctCountingBy(Student::getFirstName),
                Map.Entry.<GroupName, Integer>comparingByKey().reversed()
        );
    }

    private <T, R> R getFields(
            List<Student> students,
            Function<Student, T> getter,
            Collector<T, ?, R> collector) {
        return students
                .stream()
                .map(getter)
                .collect(collector);
    }

    private <T> List<T> getFieldsList(
            List<Student> students,
            Function<Student, T> getter) {
        return getFields(students, getter, Collectors.toList());
    }

    @Override
    public List<String> getFirstNames(List<Student> students) {
        return getFieldsList(students, Student::getFirstName);
    }

    @Override
    public List<String> getLastNames(List<Student> students) {
        return getFieldsList(students, Student::getLastName);
    }

    @Override
    public List<GroupName> getGroups(List<Student> students) {
        return getFieldsList(students, Student::getGroup);
    }

    @Override
    public List<String> getFullNames(List<Student> students) {
        return getFieldsList(students, StudentDB::studentFullName);
    }

    @Override
    public Set<String> getDistinctFirstNames(List<Student> students) {
        return getFields(students, Student::getFirstName, Collectors.toCollection(TreeSet::new));
    }

    @Override
    public String getMaxStudentFirstName(List<Student> students) {
        return students
                .stream()
                .max(Comparator.comparing(Student::getId))
                .map(Student::getFirstName)
                .orElse("");
    }

    private List<Student> sortStudentsBy(Collection<Student> students, Comparator<Student> comp) {
        return students
                .stream()
                .sorted(comp)
                .collect(Collectors.toList());
    }

    @Override
    public List<Student> sortStudentsById(Collection<Student> students) {
        return sortStudentsBy(students, Comparator.comparing(Student::getId));
    }

    @Override
    public List<Student> sortStudentsByName(Collection<Student> students) {
        return sortStudentsBy(students, COMPARING_STUDENT_NAMES);
    }

    private <T, R> R findStudentsBy(
            Collection<Student> students,
            T value,
            Function<Student, T> getter,
            Collector<Student, ?, R> collector) {
        return students
                .stream()
                .filter(x -> getter.apply(x).equals(value))
                .sorted(COMPARING_STUDENT_NAMES)
                .collect(collector);
    }

    @Override
    public List<Student> findStudentsByFirstName(Collection<Student> students, String name) {
        return findStudentsBy(students, name, Student::getFirstName, Collectors.toList());
    }

    @Override
    public List<Student> findStudentsByLastName(Collection<Student> students, String name) {
        return findStudentsBy(students, name, Student::getLastName, Collectors.toList());
    }

    @Override
    public List<Student> findStudentsByGroup(Collection<Student> students, GroupName group) {
        return findStudentsBy(students, group, Student::getGroup, Collectors.toList());
    }

    @Override
    public Map<String, String> findStudentNamesByGroup(Collection<Student> students, GroupName group) {
        return findStudentsBy(students, group, Student::getGroup, Collectors.groupingBy(
                        Student::getLastName,
                        Collectors.collectingAndThen(
                                Collectors.minBy(Comparator.comparing(Student::getFirstName)),
                                opt -> opt.map(Student::getFirstName).orElse(null))
        ));
    }

    @Override
    public String getMostPopularName(Collection<Student> collection) {
        return getLargestFieldWith(
                collection,
                Student::getFirstName,
                Collectors.collectingAndThen(
                        Collectors.toMap(
                                Student::getGroup,
                                x -> x,
                                (x, y) -> x
                        ),
                        map -> map.values().size()
                ),
                Map.Entry.<String, Integer>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey()),
                ""
        );
    }

    private <T> List<T> getFieldsFromList(List<Student> students, Function<Student, T> getter, int[] ints) {
        return Arrays
                .stream(ints)
                .boxed()
                .map(students::get)
                .map(getter)
                .collect(Collectors.toList());
    }

    private <T> List<T> getFields(Collection<Student> students, Function<Student, T> getter, int[] ints) {
        return getFieldsFromList(List.copyOf(students), getter, ints);
    }

    @Override
    public List<String> getFirstNames(Collection<Student> collection, int[] ints) {
        return getFields(collection, Student::getFirstName, ints);
    }

    @Override
    public List<String> getLastNames(Collection<Student> collection, int[] ints) {
        return getFields(collection, Student::getLastName, ints);
    }

    @Override
    public List<GroupName> getGroups(Collection<Student> collection, int[] ints) {
        return getFields(collection, Student::getGroup, ints);
    }

    @Override
    public List<String> getFullNames(Collection<Student> collection, int[] ints) {
        return getFields(collection, StudentDB::studentFullName, ints);
    }
}
