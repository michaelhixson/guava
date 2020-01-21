package com.google.common.reflect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Streams;
import java.io.Serializable;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Test;

public final class StrictTypeResolverTest {
  @Test
  public void listsReverse() throws Exception {
    Method method = Lists.class.getMethod("reverse", List.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<ArrayList<String>>() {}.capture());

    assertEquals(
        new TypeCapture<List<String>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[0]));

    assertEquals(
        new TypeCapture<List<String>>() {}.capture(),
        resolver.resolveType(method.getGenericReturnType()));
  }

  @Test
  public void collectionsMin() throws Exception {
    Method method = Collections.class.getMethod("min", Collection.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<Collection<String>>() {}.capture());

    assertEquals(
        new TypeCapture<Collection<? extends String>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[0]));

    assertEquals(
        new TypeCapture<String>() {}.capture(),
        resolver.resolveType(method.getGenericReturnType()));
  }

  @Test
  public void collectionsMinBadArg() throws Exception {
    Method method = Collections.class.getMethod("min", Collection.class);

    try {
      new TypeResolver()
          .where(
              method.getGenericParameterTypes()[0],
              new TypeCapture<Collection<Number>>() {}.capture());
      fail();
    } catch (IllegalArgumentException expected) {}
  }

  @Test
  public void mapsNewEnumMap() throws Exception {
    Method method = Maps.class.getMethod("newEnumMap", Class.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                TimeUnit.class);

    assertEquals(
        Types.newParameterizedType(Class.class, TimeUnit.class),
        resolver.resolveType(method.getGenericParameterTypes()[0]));

    assertEquals(
        Types.newParameterizedType(
            EnumMap.class,
            TimeUnit.class,
            method.getTypeParameters()[1]),
        resolver.resolveType(method.getGenericReturnType()));
  }

  @Test
  public void mapsNewLinkedHashMap() throws Exception {
    Method method = Maps.class.getMethod("newLinkedHashMap", Map.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<Map<String, Integer>>() {}.capture());

    assertEquals(
        new TypeCapture<Map<? extends String, ? extends Integer>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[0]));

    assertEquals(
        new TypeCapture<LinkedHashMap<String, Integer>>() {}.capture(),
        resolver.resolveType(method.getGenericReturnType()));

    Maps.newLinkedHashMap(new HashMap<String, Integer>());
  }

  @Test
  public void mapsImmutableEntry() throws Exception {
    Method method = Maps.class.getMethod("immutableEntry", Object.class, Object.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                String.class)
            .where(
                method.getGenericParameterTypes()[1],
                Integer.class);

    assertEquals(
        String.class,
        resolver.resolveType(method.getGenericParameterTypes()[0]));

    assertEquals(
        Integer.class,
        resolver.resolveType(method.getGenericParameterTypes()[1]));

    assertEquals(
        new TypeCapture<Map.Entry<String, Integer>>() {}.capture(),
        resolver.resolveType(method.getGenericReturnType()));
  }

  @Test
  public void mapsTransformValues() throws Exception {
    Method method = Maps.class.getMethod("transformValues", Map.class, com.google.common.base.Function.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<Map<String, Integer>>() {}.capture())
            .where(
                method.getGenericParameterTypes()[1],
                new TypeCapture<com.google.common.base.Function<Integer, Long>>() {}.capture());

    assertEquals(
        new TypeCapture<Map<String, Integer>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[0]));

    assertEquals(
        new TypeCapture<com.google.common.base.Function<? super Integer, Long>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[1]));

    assertEquals(
        new TypeCapture<Map<String, Long>>() {}.capture(),
        resolver.resolveType(method.getGenericReturnType()));
  }

  @Test
  public void collectorsToMap() throws Exception {
    Method method = Collectors.class.getMethod("toMap", Function.class, Function.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<Function<String, Integer>>() {}.capture())
            .where(
                method.getGenericParameterTypes()[1],
                new TypeCapture<Function<String, Long>>() {}.capture());

    assertEquals(
        new TypeCapture<Function<? super String, ? extends Integer>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[0]));

    assertEquals(
        new TypeCapture<Function<? super String, ? extends Long>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[1]));

    assertEquals(
        new TypeCapture<Collector<String, ?, Map<Integer, Long>>>() {}.capture(),
        resolver.resolveType(method.getGenericReturnType()));

    Collectors.toMap(
        (Function<String, Integer>) Integer::parseInt,
        (Function<String, Long>) Long::parseLong);
  }

  @Test
  public void streamsConcat() throws Exception {
    Method method = Streams.class.getMethod("concat", Stream[].class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<Stream<String>[]>() {}.capture());

    assertEquals(
        new TypeCapture<Stream<? extends String>[]>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[0]));

    assertEquals(
        new TypeCapture<Stream<String>>() {}.capture(),
        resolver.resolveType(method.getGenericReturnType()));
  }

  @Test
  public void reuseTypeParams() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("reuseTypeParams", Map.Entry.class, Map.Entry.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<Map.Entry<Integer, String>>() {}.capture())
            .where(
                method.getGenericParameterTypes()[1],
                new TypeCapture<Map.Entry<String, Integer>>() {}.capture());

    assertEquals(
        new TypeCapture<Map.Entry<Integer, String>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[0]));

    assertEquals(
        new TypeCapture<Map.Entry<String, Integer>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[1]));

    assertEquals(
        Void.TYPE,
        resolver.resolveType(method.getGenericReturnType()));
  }

  public static <T, U> void reuseTypeParams(Map.Entry<T, U> a, Map.Entry<U, T> b) {}

  @Test
  public void nonObjectUpperBound() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("nonObjectUpperBound", List.class, List.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<ArrayList<Optional<Integer>>>() {}.capture())
            .where(
                method.getGenericParameterTypes()[1],
                new TypeCapture<ArrayList<Optional<Integer>>>() {}.capture());

    assertEquals(
        new TypeCapture<List<? extends Optional<? extends Integer>>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[0]));

    assertEquals(
        new TypeCapture<List<? extends Optional<? super Integer>>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[1]));

    assertEquals(
        Integer.class,
        resolver.resolveType(method.getGenericReturnType()));

    nonObjectUpperBound(
        new ArrayList<Optional<Integer>>(),
        new ArrayList<Optional<Integer>>());
  }

  public static <T extends Number> T nonObjectUpperBound(List<? extends Optional<? extends T>> a,
                                                         List<? extends Optional<? super T>> b) { return null; }

  @Test
  public void variance() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("variance", List.class);

    // FIXME: This should be rejected, but it isn't.
    if (false) {
      try {
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<ArrayList<Optional<String>>>() {}.capture());
        fail();
      } catch (IllegalArgumentException expected) {}
    }

    Optional<?> a = null;
    Optional<String> b = null;
    a = b;

    List<Optional<?>> c = null;
    List<Optional<String>> d = null;
    //c = d;
    //variance(new ArrayList<Optional<String>>());
  }

  public static Object variance(List<Optional<?>> b) { return null; }

  @Test
  public void foo() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("foo", List.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<Integer>>() {}.capture());

    assertEquals(
        new TypeCapture<List<Integer>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[0]));

    assertEquals(
        new TypeCapture<List<Integer>>() {}.capture(),
        resolver.resolveType(method.getGenericReturnType()));

    foo(new ArrayList<Integer>());
  }

  public static <T extends List<? extends Comparable<?>>> T foo(T input) { return null; }

  @Test
  public void bar() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("bar", List.class, List.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<Integer>>() {}.capture())
            .where(
                method.getGenericParameterTypes()[1],
                new TypeCapture<List<List<Integer>>>() {}.capture());

    assertEquals(
        new TypeCapture<List<Integer>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[0]));

    assertEquals(
        new TypeCapture<List<List<Integer>>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[1]));

    assertEquals(
        Integer.class,
        resolver.resolveType(method.getGenericReturnType()));

    bar(new ArrayList<Integer>(), new ArrayList<List<Integer>>());
  }

  public static <T extends List<U>, U extends Comparable<? super U>> U bar(List<U> a, List<T> b) { return null; }

  @Test
  public void convergeUpperBounds() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("convergeUpperBounds", List.class, List.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<String>>() {}.capture())
            .where(
                method.getGenericParameterTypes()[1],
                new TypeCapture<List<Integer>>() {}.capture());

    // TODO: This test is too brittle.  Refactor it.
    // We shouldn't depend on the ordering of the type arguments.  Also, in
    // later versions of Java, String and Integer share more supertypes:
    // Constable and ConstantDesc.

    assertEquals(
        Types.newParameterizedType(
            List.class,
            new Types.WildcardTypeImpl(
                new Type[0],
                new Type[] {
                    Serializable.class,
                    Types.newParameterizedType(
                        Comparable.class,
                        Types.subtypeOf(Object.class))
                })),
        resolver.resolveType(method.getGenericParameterTypes()[0]));

    assertEquals(
        Types.newParameterizedType(
            List.class,
            new Types.WildcardTypeImpl(
                new Type[0],
                new Type[] {
                    Serializable.class,
                    Types.newParameterizedType(
                        Comparable.class,
                        Types.subtypeOf(Object.class))
                })),
        resolver.resolveType(method.getGenericParameterTypes()[1]));

    Type returnType = resolver.resolveType(method.getGenericReturnType());
    assertTrue(TypeToken.of(returnType).isSubtypeOf(Serializable.class));
    assertTrue(TypeToken.of(returnType).isSubtypeOf(Comparable.class));

    convergeUpperBounds(new ArrayList<String>(), new ArrayList<Integer>());
  }

  @Test
  public void convergeUpperBoundsObj() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("convergeUpperBounds", List.class, List.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<String>>() {}.capture())
            .where(
                method.getGenericParameterTypes()[1],
                new TypeCapture<List<Void>>() {}.capture());

    assertEquals(
        new TypeCapture<List<?>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[0]));

    assertEquals(
        new TypeCapture<List<?>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[1]));

    assertEquals(
        Object.class,
        resolver.resolveType(method.getGenericReturnType()));

    convergeUpperBounds(new ArrayList<String>(), new ArrayList<Void>());
  }

  public static <T> T convergeUpperBounds(List<? extends T> a, List<? extends T> b) { return null; }

  @Test
  public void convergeLowerBounds() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("convergeLowerBounds", Consumer.class, Consumer.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<Consumer<CharSequence>>() {}.capture())
            .where(
                method.getGenericParameterTypes()[1],
                new TypeCapture<Consumer<String>>() {}.capture());

    assertEquals(
        new TypeCapture<Consumer<? super String>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[0]));

    assertEquals(
        new TypeCapture<Consumer<? super String>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[1]));

    assertEquals(
        String.class,
        resolver.resolveType(method.getGenericReturnType()));

    convergeLowerBounds((Consumer<CharSequence>) any -> {}, (Consumer<String>) any -> {});
  }

  public static <T> T convergeLowerBounds(Consumer<? super T> a, Consumer<? super T> b) { return null; }

  @Test
  public void convergeLowerBoundsBad() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("convergeLowerBounds", Consumer.class, Consumer.class);

    try {
      new TypeResolver()
          .where(
              method.getGenericParameterTypes()[0],
              new TypeCapture<Consumer<String>>() {}.capture())
          .where(
              method.getGenericParameterTypes()[1],
              new TypeCapture<Consumer<Integer>>() {}.capture());
      fail();
    } catch (IllegalArgumentException expected) {}

    try {
      new TypeResolver()
          .where(
              method.getGenericParameterTypes()[0],
              new TypeCapture<Consumer<List<String>>>() {}.capture())
          .where(
              method.getGenericParameterTypes()[1],
              new TypeCapture<Consumer<List<Integer>>>() {}.capture());
      fail();
    } catch (IllegalArgumentException expected) {}

    try {
      new TypeResolver()
          .where(
              method.getGenericParameterTypes()[0],
              new TypeCapture<Consumer<List<String>>>() {}.capture())
          .where(
              method.getGenericParameterTypes()[1],
              new TypeCapture<Consumer<List<Integer>>>() {}.capture());
      fail();
    } catch (IllegalArgumentException expected) {}

    class Foo {}
    class SubFoo extends Foo {}

    try {
      new TypeResolver()
          .where(
              method.getGenericParameterTypes()[0],
              new TypeCapture<Consumer<Foo>>() {}.capture())
          .where(
              method.getGenericParameterTypes()[1],
              new TypeCapture<Consumer<? extends SubFoo>>() {}.capture());
      fail();
    } catch (IllegalArgumentException expected) {}

    // FIXME: This is failing.  Something to do with wildcard capture?
    if (false) {
      try {
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<Consumer<Foo>>() {}.capture())
            .where(
                method.getGenericParameterTypes()[1],
                new TypeCapture<Consumer<? super SubFoo>>() {}.capture());
        fail();
      } catch (IllegalArgumentException expected) {}
    }

    //convergeLowerBounds((Consumer<String>) any -> {}, (Consumer<Integer>) any -> {});
    //convergeLowerBounds((Consumer<List<Foo>>) any -> {}, (Consumer<List<Bar>>) any -> {});
    //convergeLowerBounds((Consumer<Foo>) any -> {}, (Consumer<? extends SubFoo>) any -> {});
    //convergeLowerBounds((Consumer<Foo>) any -> {}, (Consumer<? super SubFoo>) any -> {});
  }

  @Test
  public void convergeAllBounds() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("convergeAllBounds", Consumer.class, Consumer.class, List.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<Consumer<Serializable>>() {}.capture())
            .where(
                method.getGenericParameterTypes()[1],
                new TypeCapture<Consumer<Number>>() {}.capture())
            .where(
                method.getGenericParameterTypes()[2],
                new TypeCapture<List<Integer>>() {}.capture());

    assertEquals(
        new TypeCapture<Consumer<? super Integer>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[0]));

    assertEquals(
        new TypeCapture<Consumer<? super Integer>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[1]));

    assertEquals(
        new TypeCapture<List<? extends Integer>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[2]));

    assertEquals(
        Integer.class,
        resolver.resolveType(method.getGenericReturnType()));

    convergeAllBounds((Consumer<Serializable>) any -> {}, (Consumer<Number>) any -> {}, new ArrayList<Integer>());
  }

  public static <T> T convergeAllBounds(Consumer<? super T> a, Consumer<? super T> b, List<? extends T> c) { return null; }

  @Test
  public void convergeAllBoundsBad() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("convergeAllBounds", Consumer.class, Consumer.class, List.class);

    try {
      new TypeResolver()
          .where(
              method.getGenericParameterTypes()[0],
              new TypeCapture<Consumer<Serializable>>() {}.capture())
          .where(
              method.getGenericParameterTypes()[1],
              new TypeCapture<Consumer<Number>>() {}.capture())
          .where(
              method.getGenericParameterTypes()[2],
              new TypeCapture<List<String>>() {}.capture());
      fail();
    } catch (IllegalArgumentException expected) {}
  }

  @Test
  public void exactPlusExact() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("exactPlusExact", List.class, List.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<Integer>>() {}.capture())
            .where(
                method.getGenericParameterTypes()[1],
                new TypeCapture<List<Integer>>() {}.capture());

    assertEquals(
        new TypeCapture<List<Integer>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[0]));

    assertEquals(
        new TypeCapture<List<? extends Integer>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[1]));

    assertEquals(
        Integer.class,
        resolver.resolveType(method.getGenericReturnType()));

    exactPlusExact(new ArrayList<Integer>(), new ArrayList<Integer>());
  }

  @Test
  public void exactPlusExactBad() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("exactPlusExact", List.class, List.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<Number>>() {}.capture())
            .where(
                method.getGenericParameterTypes()[1],
                new TypeCapture<List<Integer>>() {}.capture());

    assertEquals(
        new TypeCapture<List<Number>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[0]));

    assertEquals(
        new TypeCapture<List<? extends Number>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[1]));

    assertEquals(
        Number.class,
        resolver.resolveType(method.getGenericReturnType()));

    exactPlusExact(new ArrayList<Number>(), new ArrayList<Integer>());
  }

  public static <T> T exactPlusExact(List<T> a, List<? extends T> b) { return null; }

  public void exactPlusLower() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("exactPlusLower", List.class, List.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<Number>>() {}.capture())
            .where(
                method.getGenericParameterTypes()[1],
                new TypeCapture<List<Integer>>() {}.capture());

    assertEquals(
        new TypeCapture<Map<String, Integer>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[0]));

    assertEquals(
        new TypeCapture<Map<String, Integer>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[1]));

    assertEquals(
        new TypeCapture<LinkedHashMap<String, Integer>>() {}.capture(),
        resolver.resolveType(method.getGenericReturnType()));

    exactPlusLower(new ArrayList<Number>(), new ArrayList<Integer>());
  }

  @Test
  public void exactPlusLowerBad() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("exactPlusLower", List.class, List.class);

    try {
      new TypeResolver()
          .where(
              method.getGenericParameterTypes()[0],
              new TypeCapture<List<Integer>>() {}.capture())
          .where(
              method.getGenericParameterTypes()[1],
              new TypeCapture<List<Number>>() {}.capture());
      fail();
    } catch (IllegalArgumentException expected) {}

    //exactPlusLower(new ArrayList<Integer>(), new ArrayList<Number>());
  }

  public static <T> T exactPlusLower(List<? super T> a, List<T> b) { return null; }

  @Test
  public void exactPlusUpper() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("exactPlusUpper", List.class, List.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<Number>>() {}.capture())
            .where(
                method.getGenericParameterTypes()[1],
                new TypeCapture<List<Integer>>() {}.capture());

    assertEquals(
        new TypeCapture<List<Number>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[0]));

    assertEquals(
        new TypeCapture<List<? extends Number>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[1]));

    assertEquals(
        Number.class,
        resolver.resolveType(method.getGenericReturnType()));

    exactPlusUpper(new ArrayList<Number>(), new ArrayList<Integer>());
  }

  @Test
  public void exactPlusUpperBad() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("exactPlusUpper", List.class, List.class);

    try {
      new TypeResolver()
          .where(
              method.getGenericParameterTypes()[0],
              new TypeCapture<List<Integer>>() {}.capture())
          .where(
              method.getGenericParameterTypes()[1],
              new TypeCapture<List<Number>>() {}.capture());
      fail();
    } catch (IllegalArgumentException expected) {}

    //exactPlusUpper(new ArrayList<Integer>(), new ArrayList<Number>());
  }

  public static <T> T exactPlusUpper(List<T> a, List<? extends T> b) { return null; }

  @Test
  public void exactLowerUpper() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("exactLowerUpper", List.class, List.class, List.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<Number>>() {}.capture())
            .where(
                method.getGenericParameterTypes()[1],
                new TypeCapture<List<Serializable>>() {}.capture())
            .where(
                method.getGenericParameterTypes()[2],
                new TypeCapture<List<Integer>>() {}.capture());

    assertEquals(
        new TypeCapture<List<Number>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[0]));

    assertEquals(
        new TypeCapture<List<? super Number>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[1]));

    assertEquals(
        new TypeCapture<List<? super Number>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[1]));

    assertEquals(
        Number.class,
        resolver.resolveType(method.getGenericReturnType()));

    exactLowerUpper(new ArrayList<Number>(), new ArrayList<Serializable>(), new ArrayList<Integer>());
  }

  @Test
  public void exactLowerUpperBad() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("exactLowerUpper", List.class, List.class, List.class);

    try {
      new TypeResolver()
          .where(
              method.getGenericParameterTypes()[0],
              new TypeCapture<List<Number>>() {}.capture())
          .where(
              method.getGenericParameterTypes()[1],
              new TypeCapture<List<Serializable>>() {}.capture())
          .where(
              method.getGenericParameterTypes()[2],
              new TypeCapture<List<String>>() {}.capture());
      fail();
    } catch (IllegalArgumentException expected) {}
  }

  public static <T> T exactLowerUpper(List<T> a, List<? super T> b, List<? extends T> c) { return null; }

  @Test
  public void variableInBounds0() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("variableInBounds", List.class, List.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<Number>>() {}.capture())
            .where(
                method.getGenericParameterTypes()[1],
                new TypeCapture<List<Serializable>>() {}.capture());

    assertEquals(
        new TypeCapture<List<? super Number>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[0]));

    assertEquals(
        new TypeCapture<List<? super Number>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[1]));

    assertEquals(
        Number.class,
        resolver.resolveType(method.getGenericReturnType()));

    variableInBounds(new ArrayList<Number>(), new ArrayList<Serializable>());
  }

  public static <T extends Number, U extends T> T variableInBounds(List<? super U> a, List<? super T> b) { return null; }

  @Test
  public void variableInBounds1() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("variableInBounds", List.class, List.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<Number>>() {}.capture())
            .where(
                method.getGenericParameterTypes()[1],
                new TypeCapture<List<Integer>>() {}.capture());

    assertEquals(
        new TypeCapture<List<? super Number>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[0]));

    assertEquals(
        new TypeCapture<List<? super Integer>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[1]));

    assertEquals(
        Integer.class,
        resolver.resolveType(method.getGenericReturnType()));

    variableInBounds(new ArrayList<Number>(), new ArrayList<Integer>());
  }

  @Test
  public void variableInBounds2() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("variableInBounds2", List.class, List.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<Integer>>() {}.capture())
            .where(
                method.getGenericParameterTypes()[1],
                new TypeCapture<List<Double>>() {}.capture());

    assertEquals(
        new TypeCapture<List<? extends Integer>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[0]));

    // TODO: Refactor this.
    assertEquals(
        Types.newParameterizedType(
            List.class,
            new Types.WildcardTypeImpl(
                new Type[0],
                new Type[] {
                    Number.class,
                    Types.newParameterizedType(
                        Comparable.class,
                        Types.subtypeOf(Object.class))
                })),
        resolver.resolveType(method.getGenericParameterTypes()[1]));

    Type returnType = resolver.resolveType(method.getGenericReturnType());
    assertTrue(TypeToken.of(returnType).isSubtypeOf(Number.class));
    assertTrue(TypeToken.of(returnType).isSubtypeOf(Comparable.class));

    variableInBounds2(new ArrayList<Integer>(), new ArrayList<Double>());
  }

  public static <T extends Number, U extends T> T variableInBounds2(List<? extends U> a, List<? extends T> b) { return null; }

  @Test
  public void variableInBounds3() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("variableInBounds3", List.class, List.class);

    try {
      new TypeResolver()
          .where(
              method.getGenericParameterTypes()[0],
              new TypeCapture<List<Number>>() {}.capture())
          .where(
              method.getGenericParameterTypes()[1],
              new TypeCapture<List<String>>() {}.capture());
      fail();
    } catch (IllegalArgumentException expected) {}

    //variableInBounds3(new ArrayList<Number>(), new ArrayList<String>());
  }

  public static <T, U extends T> T variableInBounds3(List<? super U> a, List<? super T> b) { return null; }

  @Test
  public void testBox() throws Exception {
    Field keyField = Box.class.getField("key");
    Field valueField = Box.class.getField("value");
    Method method = Box.class.getMethod("transformValue", Function.class, Supplier.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                keyField.getGenericType(),
                new TypeCapture<List<String>>() {}.capture())
            .where(
                valueField.getGenericType(),
                String.class)
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<Function<String, Long>>() {}.capture())
            .where(
                method.getGenericParameterTypes()[1],
                new TypeCapture<Supplier<Long>>() {}.capture());

    assertEquals(
        new TypeCapture<Function<? super String, ? extends Long>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[0]));

    assertEquals(
        new TypeCapture<Supplier<? extends Long>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[1]));

    assertEquals(
        new TypeCapture<Map.Entry<List<String>, Long>>() {}.capture(),
        resolver.resolveType(method.getGenericReturnType()));

    assertEquals(
        new TypeCapture<List<String>>() {}.capture(),
        resolver.resolveType(keyField.getGenericType()));

    assertEquals(
        String.class,
        resolver.resolveType(valueField.getGenericType()));

    Box<String, List<String>> box = new Box<String, List<String>>();

    Map.Entry<List<String>, Long> entry =
        box.transformValue(
            (Function<String, Long>) Long::parseLong,
            (Supplier<Long>) () -> Long.MAX_VALUE);
  }

  @Test
  public void testBoxBad() throws Exception {
    Field keyField = Box.class.getField("key");
    Field valueField = Box.class.getField("value");
    Method method = Box.class.getMethod("transformValue", Function.class, Supplier.class);

    try {
      new TypeResolver()
          .where(
              keyField.getGenericType(),
              new TypeCapture<List<Integer>>() {}.capture())
          // Throws.  "? super T" is Integer because of the first binding,
          // so "? super T" cannot also be String in this second binding.
          .where(
              method.getGenericParameterTypes()[0],
              new TypeCapture<Function<String, Long>>() {}.capture());
      fail();
    } catch (IllegalArgumentException expected) {}
  }

  public static final class Box<
      TBoxValueField extends Serializable,
      TBoxKeyField extends Iterable<? super TBoxValueField>> {

    public @Nullable TBoxValueField value;
    public @Nullable TBoxKeyField key;

    public <TBoxMethodValue extends Comparable<? super TBoxMethodValue>>
    Map.Entry<TBoxKeyField, TBoxMethodValue> transformValue(
        Function<? super TBoxValueField, ? extends TBoxMethodValue> transformer,
        Supplier<? extends TBoxMethodValue> supplier) {

      TBoxMethodValue u = (value == null) ? supplier.get() : transformer.apply(value);
      return Maps.immutableEntry(key, u);
    }
  }

  @Test
  public void typeChain() throws Exception {
    // TODO: Understand why this works, and then implement it in TypeResolver.
    List<String> a = typeChain(new ArrayList<Number>());
    TimeUnit b = typeChain(new ArrayList<Optional<Integer>>());

    Method method = StrictTypeResolverTest.class.getMethod("typeChain", List.class);

    if (false) {
      TypeResolver resolver =
          new TypeResolver()
              .where(
                  method.getGenericParameterTypes()[0],
                  new TypeCapture<ArrayList<Number>>() {}.capture())
              .where(
                  method.getGenericReturnType(),
                  new TypeCapture<List<String>>() {}.capture());

      assertEquals(
          new TypeCapture<ArrayList<Number>>() {}.capture(),
          resolver.resolveType(method.getGenericParameterTypes()[0]));

      assertEquals(
          new TypeCapture<List<String>>() {}.capture(),
          resolver.resolveType(method.getGenericReturnType()));
    }

    if (false) {
      TypeResolver resolver =
          new TypeResolver()
              .where(
                  method.getGenericParameterTypes()[0],
                  new TypeCapture<ArrayList<Optional<Integer>>>() {}.capture())
              .where(
                  method.getGenericReturnType(),
                  TimeUnit.class);

      assertEquals(
          new TypeCapture<ArrayList<Optional<Integer>>>() {}.capture(),
          resolver.resolveType(method.getGenericParameterTypes()[0]));

      assertEquals(
          TimeUnit.class,
          resolver.resolveType(method.getGenericReturnType()));
    }
  }

  public static <
      A,
      B extends List<A>,
      C extends List<? super B>>
  A typeChain(C c) { return null; }

  @Test
  public void nestedWildcardUpperBounds() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("nestedWildcardUpperBounds", List.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<Number>>() {}.capture());

    assertEquals(
        new TypeCapture<List<? extends Number>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[0]));

    assertEquals(
        new TypeCapture<List<? extends Number>>() {}.capture(),
        resolver.resolveType(method.getGenericReturnType()));

    nestedWildcardUpperBounds(new ArrayList<Number>());
  }

  public static <T, U extends T> List<? extends T> nestedWildcardUpperBounds(List<? extends U> a) { return null; }

  @Test
  public void nestedWildcardLowerBounds() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("nestedWildcardLowerBounds", List.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<Number>>() {}.capture());

    assertEquals(
        new TypeCapture<List<? super Number>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[0]));

    assertEquals(
        new TypeCapture<List<? super Object>>() {}.capture(),
        resolver.resolveType(method.getGenericReturnType()));

    assertEquals(
        new TypeCapture<List<? super Object>>() {}.capture(),
        resolver.where(method.getGenericReturnType(),
                       new TypeCapture<List<? super Object>>() {}.capture())
                .resolveType(method.getGenericReturnType()));

    assertEquals(
        new TypeCapture<List<? super Serializable>>() {}.capture(),
        resolver.where(method.getGenericReturnType(),
                       new TypeCapture<List<? super Serializable>>() {}.capture())
                .resolveType(method.getGenericReturnType()));

    assertEquals(
        new TypeCapture<List<? super Number>>() {}.capture(),
        resolver.where(method.getGenericReturnType(),
                       new TypeCapture<List<? super Number>>() {}.capture())
                .resolveType(method.getGenericReturnType()));

    assertEquals(
        new TypeCapture<List<? super Integer>>() {}.capture(),
        resolver.where(method.getGenericReturnType(),
                       new TypeCapture<List<? super Integer>>() {}.capture())
                .resolveType(method.getGenericReturnType()));

    assertEquals(
        new TypeCapture<List<? super CharSequence>>() {}.capture(),
        resolver.where(method.getGenericReturnType(),
                       new TypeCapture<List<? super CharSequence>>() {}.capture())
                .resolveType(method.getGenericReturnType()));

    try {
      resolver.where(method.getGenericReturnType(),
                     new TypeCapture<List<? super String>>() {}.capture())
              .resolveType(method.getGenericReturnType());
      fail();
    } catch (IllegalArgumentException expected) {}

    nestedWildcardLowerBounds(new ArrayList<Number>());
  }

  public static <T, U extends T> List<? super T> nestedWildcardLowerBounds(List<? super U> a) { return null; }

  @Test
  public void nestedWildcardLowerBounds2() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("nestedWildcardLowerBounds2", List.class, List.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<Number>>() {}.capture())
            .where(
                method.getGenericParameterTypes()[1],
                new TypeCapture<List<Number>>() {}.capture());

    assertEquals(
        new TypeCapture<List<? super Number>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[0]));

    assertEquals(
        new TypeCapture<List<? super Number>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[1]));

    assertEquals(
        new TypeCapture<List<? super Number>>() {}.capture(),
        resolver.resolveType(method.getGenericReturnType()));

    nestedWildcardLowerBounds2(new ArrayList<Number>(), new ArrayList<Number>());
  }

  public static <T, U extends T> List<? super T> nestedWildcardLowerBounds2(List<? super U> a, List<? super T> b) { return null; }

  @Test
  public void superOfWildcard() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("superOfWildcard", List.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<? super Number>>() {}.capture());

    assertEquals(
        new TypeCapture<List<? super Number>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[0]));

    assertEquals(
        new TypeCapture<List<? super Number>>() {}.capture(),
        resolver.resolveType(method.getGenericReturnType()));
  }

  public static <T> List<? super T> superOfWildcard(List<T> a) { return null; }

  @Test
  public void typeChain2() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("typeChain2", List.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<Number>>() {}.capture());

    assertEquals(
        new TypeCapture<List<? super Number>>() {}.capture(),
        resolver.resolveType(method.getGenericParameterTypes()[0]));

    assertEquals(
        Object.class,
        resolver.resolveType(method.getGenericReturnType()));

    typeChain2(new ArrayList<Number>());
  }

  public static <A, B extends A, C extends B> A typeChain2(List<? super C> a) { return null; }

  @Test
  public void canAccess() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("canAccess", AccessibleObject.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                Method.class);

    assertEquals(
        Method.class,
        resolver.resolveType(method.getGenericParameterTypes()[0]));

    assertEquals(
        Method.class,
        resolver.resolveType(method.getGenericReturnType()));

    canAccess(method);
  }

  public static <T extends AccessibleObject & Member> T canAccess(T a) { return null; }

  @Test
  public void canAccess2() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("canAccess2", Consumer.class, Consumer.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<Consumer<AccessibleObject>>() {}.capture())
            .where(
                method.getGenericParameterTypes()[1],
                new TypeCapture<Consumer<Member>>() {}.capture());

    assertEquals(
        Types.newParameterizedType(
            Consumer.class,
            new Types.WildcardTypeImpl(
                new Type[] {
                    AccessibleObject.class,
                    Member.class
                },
                new Type[] { Object.class })),
        resolver.resolveType(method.getGenericParameterTypes()[0]));

    assertEquals(
        Types.newParameterizedType(
            Consumer.class,
            new Types.WildcardTypeImpl(
                new Type[] {
                    AccessibleObject.class,
                    Member.class
                },
                new Type[] { Object.class })),
        resolver.resolveType(method.getGenericParameterTypes()[1]));

    assertEquals(
        new Types.WildcardTypeImpl(
            new Type[0],
            new Type[] {
                AccessibleObject.class,
                Member.class
            }),
        resolver.resolveType(method.getGenericReturnType()));

    canAccess2((Consumer<AccessibleObject>) any -> {}, (Consumer<Member>) any -> {});
  }

  public static <T> T canAccess2(Consumer<? super T> a, Consumer<? super T> b) { return null; }

  @Test
  public void canAccess3() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("canAccess3", List.class, List.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<Field>>() {}.capture())
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<Method>>() {}.capture());

    assertEquals(
        Types.newParameterizedType(
            List.class,
            new Types.WildcardTypeImpl(
                new Type[0],
                new Type[] {
                    AccessibleObject.class,
                    Member.class
                })),
        resolver.resolveType(method.getGenericParameterTypes()[0]));

    assertEquals(
        Types.newParameterizedType(
            List.class,
            new Types.WildcardTypeImpl(
                new Type[0],
                new Type[] {
                    AccessibleObject.class,
                    Member.class
                })),
        resolver.resolveType(method.getGenericParameterTypes()[1]));

    assertEquals(
        new Types.WildcardTypeImpl(
            new Type[0],
            new Type[] {
                AccessibleObject.class,
                Member.class
            }),
        resolver.resolveType(method.getGenericReturnType()));

    Method m = null;
    Field f = null;
    canAccess3(
        (List<Field>) Collections.singletonList(f),
        (List<Method>) Collections.singletonList(m));
  }

  public static <T extends AccessibleObject & Member> T canAccess3(List<? extends T> a, List<? extends T> b) { return null; }
}
