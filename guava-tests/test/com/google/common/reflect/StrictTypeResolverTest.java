package com.google.common.reflect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Streams;
import com.google.common.collect.testing.AnEnum;
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

// javac --debug=verboseResolution=all

public final class StrictTypeResolverTest {
  @Test
  public void listsReverse() throws Exception {
    Method method = Lists.class.getMethod("reverse", List.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<ArrayList<String>>() {}.capture());

    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertEquals(
        new TypeCapture<List<String>>() {}.capture(),
        param1Type);

    assertEquals(
        new TypeCapture<List<String>>() {}.capture(),
        returnType);

    Lists.reverse(new ArrayList<String>());
  }

  @Test
  public void collectionsMin() throws Exception {
    Method method = Collections.class.getMethod("min", Collection.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<Collection<String>>() {}.capture());

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertEquals(
        new TypeCapture<Collection<? extends String>>() {}.capture(),
        param0Type);

    assertEquals(
        new TypeCapture<String>() {}.capture(),
        returnType);
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
                new TypeCapture<Class<TimeUnit>>() {}.capture());

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertEquals(
        new TypeCapture<Class<TimeUnit>>() {}.capture(),
        param0Type);

    // EnumMap<TimeUnit, V>
    assertTrue(TypeToken.of(returnType).isSubtypeOf(new TypeCapture<EnumMap<TimeUnit, ?>>() {}.capture()));
  }

  @Test
  public void mapsNewLinkedHashMap() throws Exception {
    Method method = Maps.class.getMethod("newLinkedHashMap", Map.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<Map<String, Integer>>() {}.capture());

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertEquals(
        new TypeCapture<Map<? extends String, ? extends Integer>>() {}.capture(),
        param0Type);

    assertEquals(
        new TypeCapture<LinkedHashMap<String, Integer>>() {}.capture(),
        returnType);

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

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[1]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertEquals(
        String.class,
        param0Type);

    assertEquals(
        Integer.class,
        param1Type);

    assertEquals(
        new TypeCapture<Map.Entry<String, Integer>>() {}.capture(),
        returnType);
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

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[1]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertEquals(
        new TypeCapture<Map<String, Integer>>() {}.capture(),
        param0Type);

    assertEquals(
        new TypeCapture<com.google.common.base.Function<? super Integer, Long>>() {}.capture(),
        param1Type);

    assertEquals(
        new TypeCapture<Map<String, Long>>() {}.capture(),
        returnType);
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

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[1]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertEquals(
        new TypeCapture<Function<? super String, ? extends Integer>>() {}.capture(),
        param0Type);

    assertEquals(
        new TypeCapture<Function<? super String, ? extends Long>>() {}.capture(),
        param1Type);

    assertEquals(
        new TypeCapture<Collector<String, ?, Map<Integer, Long>>>() {}.capture(),
        returnType);

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

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertEquals(
        new TypeCapture<Stream<? extends String>[]>() {}.capture(),
        param0Type);

    assertEquals(
        new TypeCapture<Stream<String>>() {}.capture(),
        returnType);
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

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[1]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertEquals(
        new TypeCapture<Map.Entry<Integer, String>>() {}.capture(),
        param0Type);

    assertEquals(
        new TypeCapture<Map.Entry<String, Integer>>() {}.capture(),
        param1Type);

    assertEquals(
        Void.TYPE,
        returnType);
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

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[1]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertEquals(
        new TypeCapture<List<? extends Optional<? extends Integer>>>() {}.capture(),
        param0Type);

    assertEquals(
        new TypeCapture<List<? extends Optional<? super Integer>>>() {}.capture(),
        param1Type);

    assertEquals(
        Integer.class,
        returnType);

    nonObjectUpperBound(
        (ArrayList<Optional<Integer>>) null,
        (ArrayList<Optional<Integer>>) null);
  }

  public static <T extends Number> T nonObjectUpperBound(List<? extends Optional<? extends T>> a,
                                                         List<? extends Optional<? super T>> b) { return null; }

  @Test
  public void badVariance() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("badVariance", List.class);

    try {
      new TypeResolver()
          .where(
              method.getGenericParameterTypes()[0],
              new TypeCapture<ArrayList<List<String>>>() {}.capture());
      fail();
    } catch (IllegalArgumentException expected) {}

    //badVariance((List<List<String>>) null);
  }

  public static Object badVariance(List<List<?>> b) { return null; }

  @Test
  public void badVariance2() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("badVariance2", List.class);

    try {
      new TypeResolver()
          .where(
              method.getGenericParameterTypes()[0],
              new TypeCapture<ArrayList<List<String>>>() {}.capture());
      fail();
    } catch (IllegalArgumentException expected) {}

    //badVariance2((List<List<Integer>>) null);
  }

  public static Object badVariance2(List<List<Integer>> b) { return null; }

  @Test
  public void badVariance3() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("badVariance3", Map.class);

    try {
      new TypeResolver()
          .where(
              method.getGenericParameterTypes()[0],
              new TypeCapture<Map<String, List<Integer>>>() {}.capture());
      fail();
    } catch (IllegalArgumentException expected) {}

    //badVariance3((Map<String, List<Integer>>) null);
  }

  public static <K> void badVariance3(Map<K, List<?>> a) {}


  @Test
  public void badVariance4() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("badVariance4", List.class);

    try {
      new TypeResolver()
          .where(
              method.getGenericParameterTypes()[0],
              new TypeCapture<List<List<String>>>() {}.capture());
      fail();
    } catch (IllegalArgumentException expected) {}

    //badVariance4((List<List<String>>) null);
  }

  public static <T> void badVariance4(List<List<? extends T>> b) {}

  @Test
  public void badVariance5() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("badVariance5", List.class);

    try {
      new TypeResolver()
          .where(
              method.getGenericParameterTypes()[0],
              new TypeCapture<List<List<? extends String[]>>>() {}.capture());
      fail();
    } catch (IllegalArgumentException expected) {}

    //badVariance5((List<List<? extends String[]>>) null);
  }

  public static <T> void badVariance5(List<List<T[]>> b) {}

  @Test
  public void badVariance6() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("badVariance6", List.class);

    try {
      new TypeResolver()
          .where(
              method.getGenericParameterTypes()[0],
              new TypeCapture<List<ArrayList<String>>>() {}.capture());
      fail();
    } catch (IllegalArgumentException expected) {}

    //badVariance6((List<ArrayList<String>>) null);
  }

  public static <T> void badVariance6(List<List<T>> b) {}

  @Test
  public <T> void badVariance7() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("badVariance7", List.class);

    // TODO: Look more closely into why this should fail.  Figure out a way to
    //       implement this without breaking other tests and ideally without
    //       introducing any new APIs.  Note that the behavior in javac that
    //       this wants to mimic seems specific to method invocations.
    if (false) {
      try {
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<List<?>>>() {}.capture());
        fail();
      } catch (IllegalArgumentException expected) {}
    }

    //badVariance7((List<List<?>>) null);

    new TypeResolver()
        .where(
            new TypeCapture<List<List<T>>>() {}.capture(),
            new TypeCapture<BadVariance7Interface<?>>() {}.capture());

    // Why is this valid but List<List<?>> is not?
    badVariance7((BadVariance7Interface<?>) null);
  }

  public static <T> void badVariance7(List<List<T>> b) {}
  public interface BadVariance7Interface<T> extends List<List<T>> {}

  @Test
  public void badVariance8() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("badVariance8", List.class);

    try {
      new TypeResolver()
          .where(
              method.getGenericParameterTypes()[0],
              new TypeCapture<List<List<?>>>() {}.capture());
      fail();
    } catch (IllegalArgumentException expected) {}

    //badVariance8((List<List<?>>) null);
  }

  public static <T> void badVariance8(List<List<List<T>>> b) {}

  @Test
  public void okVariance() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("okVariance", List.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<String>>() {}.capture());

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);

    assertEquals(
        new TypeCapture<List<?>>() {}.capture(),
        param0Type);

    okVariance((List<String>) null);
  }

  public static Object okVariance(List<?> b) { return null; }

  @Test
  public void okVariance2() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("okVariance2", List.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<List<? extends String>>>() {}.capture());

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);

    assertEquals(
        new TypeCapture<List<List<? extends String>>>() {}.capture(),
        param0Type);

    okVariance2((List<List<? extends String>>) null);
  }

  public static <T> void okVariance2(List<List<? extends T>> b) {}

  @Test
  public void okVariance3() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("okVariance3", List.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<List<String>>>() {}.capture());

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);

    assertEquals(
        new TypeCapture<List<? extends List<String>>>() {}.capture(),
        param0Type);

    okVariance3((List<List<String>>) null);
  }

  public static <T> void okVariance3(List<? extends List<T>> b) {}

  @Test
  public void okVariance4() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("okVariance4", List.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<List<String[]>>>() {}.capture());

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);

    assertEquals(
        new TypeCapture<List<List<String[]>>>() {}.capture(),
        param0Type);

    okVariance4((List<List<String[]>>) null);
  }

  public static <T> void okVariance4(List<List<T[]>> b) {}

  @Test
  public void foo() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("foo", List.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<Integer>>() {}.capture());

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertEquals(
        new TypeCapture<List<Integer>>() {}.capture(),
        param0Type);

    assertEquals(
        new TypeCapture<List<Integer>>() {}.capture(),
        returnType);

    foo((List<Integer>) null);
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

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[1]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertEquals(
        new TypeCapture<List<Integer>>() {}.capture(),
        param0Type);

    assertEquals(
        new TypeCapture<List<List<Integer>>>() {}.capture(),
        param1Type);

    assertEquals(
        Integer.class,
        returnType);

    bar((List<Integer>) null, (List<List<Integer>>) null);
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

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[1]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    // List<? extends Serializable & Comparable<? extends Serializable & Comparable<?>>>
    assertTrue(TypeToken.of(param0Type).isSubtypeOf(new TypeCapture<List<?>>() {}.capture()));
    assertTrue(TypeToken.of(param0Type).isSubtypeOf(new TypeCapture<List<? extends Serializable>>() {}.capture()));
    assertTrue(TypeToken.of(param0Type).isSubtypeOf(new TypeCapture<List<? extends Comparable<?>>>() {}.capture()));
    assertTrue(TypeToken.of(param0Type).isSubtypeOf(new TypeCapture<List<? extends Comparable<? extends Serializable>>>() {}.capture()));
    assertTrue(TypeToken.of(param0Type).isSubtypeOf(new TypeCapture<List<? extends Comparable<? extends Comparable<?>>>>() {}.capture()));

    // List<? extends Serializable & Comparable<? extends Serializable & Comparable<?>>>
    assertTrue(TypeToken.of(param1Type).isSubtypeOf(new TypeCapture<List<?>>() {}.capture()));
    assertTrue(TypeToken.of(param1Type).isSubtypeOf(new TypeCapture<List<? extends Serializable>>() {}.capture()));
    assertTrue(TypeToken.of(param1Type).isSubtypeOf(new TypeCapture<List<? extends Comparable<?>>>() {}.capture()));
    assertTrue(TypeToken.of(param1Type).isSubtypeOf(new TypeCapture<List<? extends Comparable<? extends Serializable>>>() {}.capture()));
    assertTrue(TypeToken.of(param1Type).isSubtypeOf(new TypeCapture<List<? extends Comparable<? extends Comparable<?>>>>() {}.capture()));

    // Serializable & Comparable<? extends Serializable & Comparable<?>>
    assertTrue(TypeToken.of(returnType).isSubtypeOf(Serializable.class));
    assertTrue(TypeToken.of(returnType).isSubtypeOf(new TypeCapture<Comparable<?>>() {}.capture()));
    assertTrue(TypeToken.of(returnType).isSubtypeOf(new TypeCapture<Comparable<? extends Serializable>>() {}.capture()));
    assertTrue(TypeToken.of(returnType).isSubtypeOf(new TypeCapture<Comparable<? extends Comparable<?>>>() {}.capture()));

    convergeUpperBounds((List<String>) null, (List<Integer>) null);
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

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[1]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertEquals(
        new TypeCapture<List<?>>() {}.capture(),
        param0Type);

    assertEquals(
        new TypeCapture<List<?>>() {}.capture(),
        param1Type);

    assertEquals(
        Object.class,
        returnType);

    convergeUpperBounds((List<String>) null, (List<Void>) null);
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

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[1]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertEquals(
        new TypeCapture<Consumer<? super String>>() {}.capture(),
        param0Type);

    assertEquals(
        new TypeCapture<Consumer<? super String>>() {}.capture(),
        param1Type);

    assertEquals(
        String.class,
        returnType);

    convergeLowerBounds(
        (Consumer<CharSequence>) null,
        (Consumer<String>) null);
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

    //convergeLowerBounds((Consumer<String>) null, (Consumer<Integer>) null);
  }

  @Test
  public void convergeLowerBoundsFoo1() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("convergeLowerBounds", Consumer.class, Consumer.class);

    class Foo {}
    class SubFoo extends Foo {}

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<Consumer<Foo>>() {}.capture())
            .where(
                method.getGenericParameterTypes()[1],
                new TypeCapture<Consumer<? super SubFoo>>() {}.capture());

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[1]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertEquals(
        new TypeCapture<Consumer<? super SubFoo>>() {}.capture(),
        param0Type);

    assertEquals(
        new TypeCapture<Consumer<? super SubFoo>>() {}.capture(),
        param1Type);

    assertEquals(
        SubFoo.class,
        returnType);

    // This does compile, but IntelliJ says it does not compile.
    //convergeLowerBounds((Consumer<Foo>) null, (Consumer<? super SubFoo>) null);
  }

  @Test
  public void convergeLowerBoundsFoo1b() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("convergeLowerBounds", Consumer.class, Consumer.class);

    class Foo {}
    class SubFoo extends Foo {}

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[1],
                new TypeCapture<Consumer<? super SubFoo>>() {}.capture())
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<Consumer<Foo>>() {}.capture());

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[1]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertEquals(
        new TypeCapture<Consumer<? super SubFoo>>() {}.capture(),
        param0Type);

    assertEquals(
        new TypeCapture<Consumer<? super SubFoo>>() {}.capture(),
        param1Type);

    assertEquals(
        SubFoo.class,
        returnType);

    // This does compile, but IntelliJ says it does not compile.
    //convergeLowerBounds((Consumer<Foo>) null, (Consumer<? super SubFoo>) null);
  }

  @Test
  public void convergeLowerBoundsFoo2() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("convergeLowerBounds", Consumer.class, Consumer.class);

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

    // This does not compile, but IntelliJ says it does compile.
    //convergeLowerBounds((Consumer<Foo>) null, (Consumer<? extends SubFoo>) null);
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

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[1]);
    Type param2Type = resolver.resolveType(method.getGenericParameterTypes()[2]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertEquals(
        new TypeCapture<Consumer<? super Integer>>() {}.capture(),
        param0Type);

    assertEquals(
        new TypeCapture<Consumer<? super Integer>>() {}.capture(),
        param1Type);

    assertEquals(
        new TypeCapture<List<? extends Integer>>() {}.capture(),
        param2Type);

    assertEquals(
        Integer.class,
        returnType);

    convergeAllBounds(
        (Consumer<Serializable>) null,
        (Consumer<Number>) null,
        (List<Integer>) null);
  }

  public static <T> T convergeAllBounds(
      Consumer<? super T> a,
      Consumer<? super T> b,
      List<? extends T> c) { return null; }

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

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[1]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertEquals(
        new TypeCapture<List<Integer>>() {}.capture(),
        param0Type);

    assertEquals(
        new TypeCapture<List<? extends Integer>>() {}.capture(),
        param1Type);

    assertEquals(
        Integer.class,
        returnType);

    exactPlusExact((List<Integer>) null, (List<Integer>) null);
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

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[1]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertEquals(
        new TypeCapture<List<Number>>() {}.capture(),
        param0Type);

    assertEquals(
        new TypeCapture<List<? extends Number>>() {}.capture(),
        param1Type);

    assertEquals(
        Number.class,
        returnType);

    exactPlusExact((List<Number>) null, (List<Integer>) null);
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

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[1]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertEquals(
        new TypeCapture<Map<String, Integer>>() {}.capture(),
        param0Type);

    assertEquals(
        new TypeCapture<Map<String, Integer>>() {}.capture(),
        param1Type);

    assertEquals(
        new TypeCapture<LinkedHashMap<String, Integer>>() {}.capture(),
        returnType);

    exactPlusLower((List<Number>) null, (List<Integer>) null);
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

    //exactPlusLower((List<Integer>) null, (List<Number>) null);
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

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[1]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertEquals(
        new TypeCapture<List<Number>>() {}.capture(),
        param0Type);

    assertEquals(
        new TypeCapture<List<? extends Number>>() {}.capture(),
        param1Type);

    assertEquals(
        Number.class,
        returnType);

    exactPlusUpper((List<Number>) null, (List<Integer>) null);
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

    //exactPlusUpper((List<Integer>) null, (List<Number>) null);
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

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[1]);
    Type param2Type = resolver.resolveType(method.getGenericParameterTypes()[2]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertEquals(
        new TypeCapture<List<Number>>() {}.capture(),
        param0Type);

    assertEquals(
        new TypeCapture<List<? super Number>>() {}.capture(),
        param1Type);

    assertEquals(
        new TypeCapture<List<? extends Number>>() {}.capture(),
        param2Type);

    assertEquals(
        Number.class,
        returnType);

    exactLowerUpper(
        (List<Number>) null,
        (List<Serializable>) null,
        (List<Integer>) null);
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

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[1]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertEquals(
        new TypeCapture<List<? super Number>>() {}.capture(),
        param0Type);

    assertEquals(
        new TypeCapture<List<? super Number>>() {}.capture(),
        param1Type);

    assertEquals(
        Number.class,
        returnType);

    variableInBounds((List<Number>) null, (List<Serializable>) null);
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

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[1]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertEquals(
        new TypeCapture<List<? super Integer>>() {}.capture(),
        param0Type);

    assertEquals(
        new TypeCapture<List<? super Integer>>() {}.capture(),
        param1Type);

    assertEquals(
        Integer.class,
        returnType);

    variableInBounds((List<Number>) null, (List<Integer>) null);
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

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[1]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertEquals(
        new TypeCapture<List<? extends Integer>>() {}.capture(),
        param0Type);

    // List<? extends Number & Comparable<? extends Number & Comparable<?>>>
    assertTrue(TypeToken.of(param1Type).isSubtypeOf(new TypeCapture<List<?>>() {}.capture()));
    assertTrue(TypeToken.of(param1Type).isSubtypeOf(new TypeCapture<List<? extends Number>>() {}.capture()));
    assertTrue(TypeToken.of(param1Type).isSubtypeOf(new TypeCapture<List<? extends Comparable<?>>>() {}.capture()));
    assertTrue(TypeToken.of(param1Type).isSubtypeOf(new TypeCapture<List<? extends Comparable<? extends Number>>>() {}.capture()));
    assertTrue(TypeToken.of(param1Type).isSubtypeOf(new TypeCapture<List<? extends Comparable<? extends Comparable<?>>>>() {}.capture()));

    // Number & Comparable<? extends Number & Comparable<?>>
    assertTrue(TypeToken.of(returnType).isSubtypeOf(Number.class));
    assertTrue(TypeToken.of(returnType).isSubtypeOf(new TypeCapture<Comparable<?>>() {}.capture()));
    assertTrue(TypeToken.of(returnType).isSubtypeOf(new TypeCapture<Comparable<? extends Number>>() {}.capture()));
    assertTrue(TypeToken.of(returnType).isSubtypeOf(new TypeCapture<Comparable<? extends Comparable<?>>>() {}.capture()));

    variableInBounds2((List<Integer>) null, (List<Double>) null);
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

    // inference variable U has incompatible upper bounds Object, String, Number, T.
    //variableInBounds3((List<Number>) null, (List<String>) null);
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

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[1]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());
    Type keyFieldType = resolver.resolveType(keyField.getGenericType());
    Type valueFieldType = resolver.resolveType(valueField.getGenericType());

    assertEquals(
        new TypeCapture<Function<? super String, ? extends Long>>() {}.capture(),
        param0Type);

    assertEquals(
        new TypeCapture<Supplier<? extends Long>>() {}.capture(),
        param1Type);

    assertEquals(
        new TypeCapture<Map.Entry<List<String>, Long>>() {}.capture(),
        returnType);

    assertEquals(
        new TypeCapture<List<String>>() {}.capture(),
        keyFieldType);

    assertEquals(
        String.class,
        valueFieldType);

    Map.Entry<List<String>, Long> entry =
        new Box<String, List<String>>().transformValue(
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
    Method method = StrictTypeResolverTest.class.getMethod("typeChain", List.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<ArrayList<Number>>() {}.capture())
            .where(
                method.getGenericReturnType(),
                new TypeCapture<List<String>>() {}.capture());

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertEquals(
        new TypeCapture<ArrayList<Number>>() {}.capture(),
        param0Type);

    assertEquals(
        new TypeCapture<List<String>>() {}.capture(),
        returnType);

    List<String> a = typeChain((ArrayList<Number>) null);
  }

  public static <
      A,
      B extends List<A>,
      C extends List<? super B>>
  A typeChain(C c) { return null; }

  @Test
  public void typeChain2() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("typeChain", List.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<ArrayList<Optional<Integer>>>() {}.capture())
            .where(
                method.getGenericReturnType(),
                TimeUnit.class);

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertEquals(
        new TypeCapture<ArrayList<Optional<Integer>>>() {}.capture(),
        param0Type);

    assertEquals(
        TimeUnit.class,
        returnType);

    TimeUnit b = typeChain((ArrayList<Optional<Integer>>) null);
  }

  @Test
  public void nestedWildcardUpperBounds() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("nestedWildcardUpperBounds", List.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<Number>>() {}.capture());

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertEquals(
        new TypeCapture<List<? extends Number>>() {}.capture(),
        param0Type);

    assertEquals(
        new TypeCapture<List<? extends Number>>() {}.capture(),
        returnType);

    nestedWildcardUpperBounds((List<Number>) null);
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

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertEquals(
        new TypeCapture<List<? super Number>>() {}.capture(),
        param0Type);

    assertEquals(
        new TypeCapture<List<? super Object>>() {}.capture(),
        returnType);

    nestedWildcardLowerBounds((List<Number>) null);

    assertEquals(
        new TypeCapture<List<? super Object>>() {}.capture(),
        resolver.where(method.getGenericReturnType(),
                       new TypeCapture<List<? super Object>>() {}.capture())
                .resolveType(method.getGenericReturnType()));

    List<? super Object> o = nestedWildcardLowerBounds((List<Number>) null);

    assertEquals(
        new TypeCapture<List<? super Serializable>>() {}.capture(),
        resolver.where(method.getGenericReturnType(),
                       new TypeCapture<List<? super Serializable>>() {}.capture())
                .resolveType(method.getGenericReturnType()));

    List<? super Serializable> t = nestedWildcardLowerBounds((List<Number>) null);

    assertEquals(
        new TypeCapture<List<? super Number>>() {}.capture(),
        resolver.where(method.getGenericReturnType(),
                       new TypeCapture<List<? super Number>>() {}.capture())
                .resolveType(method.getGenericReturnType()));

    List<? super Number> u = nestedWildcardLowerBounds((List<Number>) null);

    assertEquals(
        new TypeCapture<List<? super Integer>>() {}.capture(),
        resolver.where(method.getGenericReturnType(),
                       new TypeCapture<List<? super Integer>>() {}.capture())
                .resolveType(method.getGenericReturnType()));

    List<? super Integer> v = nestedWildcardLowerBounds((List<Number>) null);

    assertEquals(
        new TypeCapture<List<? super CharSequence>>() {}.capture(),
        resolver.where(method.getGenericReturnType(),
                       new TypeCapture<List<? super CharSequence>>() {}.capture())
                .resolveType(method.getGenericReturnType()));

    List<? super CharSequence> q = nestedWildcardLowerBounds((List<Number>) null);

    // IntelliJ thinks this does not compile, but it does.
    //
    //List<? super String> r = nestedWildcardLowerBounds((List<Number>) null);

    // Number is a supertype of U
    // T is a supertype of U
    // ----> seems to imply that Number and T are intersecting
    // so then
    // List<? super T> == List<? super String>
    // "a supertype of T == a supertype of String"
    // should NOT imply that T is a subtype of String,
    // but somehow our TypeResolver thinks it is implying that.
    // It's this chain of inferences where it goes wrong:
    //   set java.util.List<? super T> java.util.List<? super java.lang.String> SUBTYPE
    //   set ? super T ? super java.lang.String EQUAL
    //   set T java.lang.String SUPERTYPE
    // but if we make it avoid going down that path, then an earlier assertion fails
    // where we're trying to set the return type to List<? super Object>
    // and it goes:
    //   set java.util.List<? super T> java.util.List<? super java.lang.Object> SUBTYPE
    //   set ? super T ? super java.lang.Object EQUAL
    //   set T ? super java.lang.Object SUPERTYPE
    //
    //
    //
    // Note that the following code does not compile even though it seems like
    // it's doing the same thing here as the previous example - Setting
    // "List<? super T>" to "List<? super String>".  Why does javac reject this
    // but allow the previous example?
    //
    //
    //nestedWildcardLowerBounds((List<Number>) null, (List<? super String>) null);
    //
    //
    // Intellij:
    // "inferred type U not within its bounds.  should extend 'capture<? super String>'"
    //
    // javac:
    /*
Error:(1542, 5) java: no suitable method found for nestedWildcardLowerBounds(java.util.List<java.lang.Number>,java.util.List<capture#1 of ? super java.lang.String>)
    method com.google.common.reflect.StrictTypeResolverTest.<T,U>nestedWildcardLowerBounds(java.util.List<? super U>) is not applicable
      (cannot infer type-variable(s) T,U
        (actual and formal argument lists differ in length))
    method com.google.common.reflect.StrictTypeResolverTest.<T,U>nestedWildcardLowerBounds(java.util.List<? super U>,java.util.List<? super T>) is not applicable
      (inference variable U has incompatible upper bounds java.lang.Object,java.lang.String,java.lang.Number,T)
     */
    //
    // Perhaps that difference between the two examples shows that we shouldn't
    // use the same TypeResolver.where(...) to model parameter types and return
    // types.

    // FIXME: This one throws, but it shouldn't.
    if (false) {
      assertEquals(
          new TypeCapture<List<? super String>>() {}.capture(),
          resolver.where(method.getGenericReturnType(),
                         new TypeCapture<List<? super String>>() {}.capture())
                  .resolveType(method.getGenericReturnType()));
    }
  }

  public static <T, U extends T> List<? super T> nestedWildcardLowerBounds(List<? super U> a) { return null; }
  public static <T, U extends T> void nestedWildcardLowerBounds(List<? super U> a, List<? super T> b) {}

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

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[1]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertEquals(
        new TypeCapture<List<? super Number>>() {}.capture(),
        param0Type);

    assertEquals(
        new TypeCapture<List<? super Number>>() {}.capture(),
        param1Type);

    assertEquals(
        new TypeCapture<List<? super Number>>() {}.capture(),
        returnType);

    nestedWildcardLowerBounds2((List<Number>) null, (List<Number>) null);
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

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertEquals(
        new TypeCapture<List<? super Number>>() {}.capture(),
        param0Type);

    // List<? super ? super Number>
    assertTrue(TypeToken.of(returnType).isSubtypeOf(new TypeCapture<List<? super Number>>() {}.capture()));

    // LOOK!  A nested wildcard super!
    superOfWildcard((List<? super Number>) null);
  }

  public static <T> List<? super T> superOfWildcard(List<T> a) { return null; }

  @Test
  public void otherTypeChain() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("otherTypeChain", List.class, List.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<AccessibleObject>>() {}.capture())
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<Member>>() {}.capture());

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[1]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    // List<? super AccessibleObject & Member>
    assertTrue(param0Type.getTypeName(), TypeToken.of(param0Type).isSubtypeOf(new TypeCapture<List<?>>() {}.capture()));
    assertTrue(param0Type.getTypeName(), TypeToken.of(param0Type).isSupertypeOf(new TypeCapture<List<AccessibleObject>>() {}.capture()));
    assertTrue(param0Type.getTypeName(), TypeToken.of(param0Type).isSupertypeOf(new TypeCapture<List<Member>>() {}.capture()));

    // List<? super AccessibleObject & Member>
    assertTrue(param1Type.getTypeName(), TypeToken.of(param1Type).isSubtypeOf(new TypeCapture<List<?>>() {}.capture()));
    assertTrue(param1Type.getTypeName(), TypeToken.of(param1Type).isSupertypeOf(new TypeCapture<List<AccessibleObject>>() {}.capture()));
    assertTrue(param1Type.getTypeName(), TypeToken.of(param1Type).isSupertypeOf(new TypeCapture<List<Member>>() {}.capture()));

    assertEquals(
        Object.class,
        returnType);

    otherTypeChain((List<AccessibleObject>) null, (List<Member>) null);
  }

  public static <A, B extends A, C extends B> A otherTypeChain(List<? super C> a, List<? super C> b) { return null; }


  @Test
  public void badOtherTypeChain() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("otherTypeChain", List.class, List.class);

    try {
      new TypeResolver()
          .where(
              method.getGenericParameterTypes()[0],
              new TypeCapture<List<AccessibleObject>>() {}.capture())
          .where(
              method.getGenericParameterTypes()[0],
              new TypeCapture<List<Number>>() {}.capture());
      fail();
    } catch (IllegalArgumentException expected) {}
  }

  @Test
  public void canAccess() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("canAccess", AccessibleObject.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                Method.class);

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertEquals(
        Method.class,
        param0Type);

    assertEquals(
        Method.class,
        returnType);

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

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[1]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    // Consumer<? super AccessibleObject & Member>
    assertTrue(param0Type.getTypeName(), TypeToken.of(param0Type).isSubtypeOf(new TypeCapture<Consumer<?>>() {}.capture()));
    assertTrue(param0Type.getTypeName(), TypeToken.of(param0Type).isSupertypeOf(new TypeCapture<Consumer<AccessibleObject>>() {}.capture()));
    assertTrue(param0Type.getTypeName(), TypeToken.of(param0Type).isSupertypeOf(new TypeCapture<Consumer<Member>>() {}.capture()));

    // Consumer<? super AccessibleObject & Member>
    assertTrue(param1Type.getTypeName(), TypeToken.of(param1Type).isSubtypeOf(new TypeCapture<Consumer<?>>() {}.capture()));
    assertTrue(param1Type.getTypeName(), TypeToken.of(param1Type).isSupertypeOf(new TypeCapture<Consumer<AccessibleObject>>() {}.capture()));
    assertTrue(param1Type.getTypeName(), TypeToken.of(param1Type).isSupertypeOf(new TypeCapture<Consumer<Member>>() {}.capture()));

    // AccessibleObject & Member
    assertTrue(TypeToken.of(returnType).isSubtypeOf(AccessibleObject.class));
    assertTrue(TypeToken.of(returnType).isSubtypeOf(Member.class));

    canAccess2(
        (Consumer<AccessibleObject>) null,
        (Consumer<Member>) null);

    AccessibleObject a =
        canAccess2(
            (Consumer<AccessibleObject>) null,
            (Consumer<Member>) null);

    Member b =
        canAccess2(
            (Consumer<AccessibleObject>) null,
            (Consumer<Member>) null);
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

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[1]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    // List<? extends AccessibleObject & Member>
    assertTrue(param0Type.getTypeName(), TypeToken.of(param0Type).isSubtypeOf(new TypeCapture<List<?>>() {}.capture()));
    assertTrue(param0Type.getTypeName(), TypeToken.of(param0Type).isSubtypeOf(new TypeCapture<List<? extends AccessibleObject>>() {}.capture()));
    assertTrue(param0Type.getTypeName(), TypeToken.of(param0Type).isSubtypeOf(new TypeCapture<List<? extends Member>>() {}.capture()));

    // List<? extends AccessibleObject & Member>
    assertTrue(param1Type.getTypeName(), TypeToken.of(param1Type).isSubtypeOf(new TypeCapture<List<?>>() {}.capture()));
    assertTrue(param1Type.getTypeName(), TypeToken.of(param1Type).isSubtypeOf(new TypeCapture<List<? extends AccessibleObject>>() {}.capture()));
    assertTrue(param1Type.getTypeName(), TypeToken.of(param1Type).isSubtypeOf(new TypeCapture<List<? extends Member>>() {}.capture()));

    // AccessibleObject & Member
    assertTrue(TypeToken.of(returnType).isSubtypeOf(AccessibleObject.class));
    assertTrue(TypeToken.of(returnType).isSubtypeOf(Member.class));

    canAccess3(
        (List<Field>) null,
        (List<Method>) null);
  }

  public static <T extends AccessibleObject & Member> T canAccess3(List<? extends T> a, List<? extends T> b) { return null; }

  @Test
  public void array() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("array", List.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<Integer[]>>() {}.capture());

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertEquals(
        new TypeCapture<List<? extends Integer[]>>() {}.capture(),
        param0Type);

    assertEquals(
        Integer.class,
        returnType);

    array((List<Integer[]>) null);
  }

  public static <T extends Serializable> T array(List<? extends T[]> a) { return null; }

  @Test
  public void arrayWrongType() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("array", List.class);

    try {
      new TypeResolver()
          .where(
              method.getGenericParameterTypes()[0],
              new TypeCapture<List<CharSequence[]>>() {}.capture());
      fail();
    } catch (IllegalArgumentException expected) {}

    //array((List<CharSequence[]>) null);
  }

  @Test
  public void arrayAndWildcardExtends() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("array", List.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<? extends Number[]>>() {}.capture());

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertEquals(
        new TypeCapture<List<? extends Number[]>>() {}.capture(),
        param0Type);

    assertEquals(
        Number.class,
        returnType);

    array((List<? extends Number[]>) null);
  }

  @Test
  public void arraySuperAndWildcardExtends() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("arraySuper", List.class);

    try {
      new TypeResolver()
          .where(
              method.getGenericParameterTypes()[0],
              new TypeCapture<List<? extends Number[]>>() {}.capture());
      fail();
    } catch (IllegalArgumentException expected) {}

    // IntelliJ doesn't catch this one:
    //arraySuper((List<? extends Number[]>) null);
  }

  @Test
  public void arrayAndBadWildcardExtends() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("array", List.class);

    try {
      new TypeResolver()
          .where(
              method.getGenericParameterTypes()[0],
              new TypeCapture<List<? extends Comparable<?>[]>>() {}.capture());
      fail();
    } catch (IllegalArgumentException expected) {}

    //array((List<? extends Comparable<?>[]>) null);
  }

  @Test
  public void arrayAndWildcardSuper() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("arraySuper", List.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<? super Comparable<?>[]>>() {}.capture());

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    // List<? super Serializable & Comparable<?>[]>
    assertTrue(param0Type.getTypeName(), TypeToken.of(param0Type).isSubtypeOf(new TypeCapture<List<?>>() {}.capture()));
    assertTrue(param0Type.getTypeName(), TypeToken.of(param0Type).isSupertypeOf(new TypeCapture<List<Serializable[]>>() {}.capture()));
    assertTrue(param0Type.getTypeName(), TypeToken.of(param0Type).isSupertypeOf(new TypeCapture<List<Comparable<?>[]>>() {}.capture()));

    // Serializable & Comparable<?>
    assertTrue(TypeToken.of(returnType).isSubtypeOf(Comparable.class));
    assertTrue(TypeToken.of(returnType).isSubtypeOf(Serializable.class));

    arraySuper((List<? super Comparable<?>[]>) null);
    Serializable a = arraySuper((List<? super Comparable<?>[]>) null);
    Comparable<?> b = arraySuper((List<? super Comparable<?>[]>) null);

    // TODO: test these
    String c = arraySuper((List<? super Comparable<?>[]>) null);
    Method d = arraySuper((List<? super Comparable<?>[]>) null);
  }

  public static <T extends Serializable> T arraySuper(List<? super T[]> a) { return null; }

  @Test
  public void arrayAndWildcardSuper2() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("arraySuper2", List.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<? super Comparable<?>>>() {}.capture());

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    // List<? super Serializable & capture of ? super Comparable<?>>
    assertTrue(param0Type.getTypeName(), TypeToken.of(param0Type).isSubtypeOf(new TypeCapture<List<?>>() {}.capture()));
    assertTrue(param0Type.getTypeName(), TypeToken.of(param0Type).isSupertypeOf(new TypeCapture<List<Serializable>>() {}.capture()));
    // TODO: How to verify the Comparable<?> part?

    // Serializable & capture of ? super Comparable<?>[]
    assertTrue(returnType.getTypeName(), TypeToken.of(returnType).isSubtypeOf(new TypeCapture<Serializable[]>() {}.capture()));
    // TODO: How to verify the Comparable<?>[] part?

    arraySuper2((List<? super Comparable<?>>) null);
    Serializable[] a = arraySuper2((List<? super Comparable<?>>) null);
    Comparable<?>[] b = arraySuper2((List<? super Comparable<?>>) null);
  }

  public static <T extends Serializable> T[] arraySuper2(List<? super T> a) { return null; }

  @Test
  public void parameterizedTypeToWildcardExtends() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("parameterizedTypeToWildcard", List.class);

    try {
      new TypeResolver()
          .where(
              method.getGenericParameterTypes()[0],
              new TypeCapture<List<? extends List<Number>>>() {}.capture());
      fail();
    } catch (IllegalArgumentException expected) {}

    //parameterizedTypeToWildcard((List<? extends List<Number>>) null);
  }

  @Test
  public void parameterizedTypeToWildcardExtends2() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("parameterizedTypeToWildcard", List.class);

    try {
      new TypeResolver()
          .where(
              method.getGenericParameterTypes()[0],
              new TypeCapture<List<? extends List<Integer>>>() {}.capture());
      fail();
    } catch (IllegalArgumentException expected) {}

    // no instance of T exists such that capture of ? extends List<Integer> conforms to Collection<T>
    //parameterizedTypeToWildcard((List<? extends List<Integer>>) null);
  }

  @Test
  public void parameterizedTypeToWildcardSuper() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("parameterizedTypeToWildcard", List.class);

    try {
      new TypeResolver()
          .where(
              method.getGenericParameterTypes()[0],
              new TypeCapture<List<? super List<Integer>>>() {}.capture());
      fail();
    } catch (IllegalArgumentException expected) {}

    // Note: this is an instance of a wildcard capture with lower bounds.
    // no instance of T exists such that capture of ? super List<Integer> conforms to Collection<T>
    //parameterizedTypeToWildcard((List<? super List<Integer>>) null);
  }

  @Test
  public void parameterizedTypeToWildcardSuper2() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("parameterizedTypeToWildcard", List.class);

    try {
      new TypeResolver()
          .where(
              method.getGenericParameterTypes()[0],
              new TypeCapture<List<?>>() {}.capture());
      fail();
    } catch (IllegalArgumentException expected) {}

    //parameterizedTypeToWildcard((List<?>) null);
  }

  public static <T extends Comparable<? super T>> T parameterizedTypeToWildcard(List<Collection<T>> a) { return null; }

  @Test
  public void multipleExactTypes() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("multipleExactTypes", Object.class, Object.class, Object.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                AccessibleObject.class)
            .where(
                method.getGenericParameterTypes()[1],
                Member.class)
            .where(
                method.getGenericParameterTypes()[2],
                Field.class);

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[1]);
    Type param2Type = resolver.resolveType(method.getGenericParameterTypes()[2]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertEquals(
        Object.class,
        param0Type);

    assertEquals(
        Object.class,
        param1Type);

    assertEquals(
        Object.class,
        param2Type);

    assertEquals(
        Object.class,
        returnType);

    multipleExactTypes(
        (AccessibleObject) null,
        (Member) null,
        (Field) null);
  }

  public static <T> T multipleExactTypes(T a, T b, T c) { return null; }

  @Test
  public void multipleExactTypes2() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("multipleExactTypes", Object.class, Object.class, Object.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                Double.class)
            .where(
                method.getGenericParameterTypes()[1],
                Serializable.class)
            .where(
                method.getGenericParameterTypes()[2],
                Integer.class);

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[1]);
    Type param2Type = resolver.resolveType(method.getGenericParameterTypes()[2]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertEquals(
        Serializable.class,
        param0Type);

    assertEquals(
        Serializable.class,
        param1Type);

    assertEquals(
        Serializable.class,
        param2Type);

    assertEquals(
        Serializable.class,
        returnType);

    multipleExactTypes(
        (Double) null,
        (Serializable) null,
        (Integer) null);
  }

  @Test
  public void multipleExactTypes3() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("multipleExactTypes", Object.class, Object.class, Object.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                Double.class)
            .where(
                method.getGenericParameterTypes()[1],
                Long.class)
            .where(
                method.getGenericParameterTypes()[2],
                Integer.class);

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[1]);
    Type param2Type = resolver.resolveType(method.getGenericParameterTypes()[2]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    // Number & Comparable<? extends Number & Comparable<?>>
    for (Type type : ImmutableList.of(param0Type, param1Type, param2Type, returnType)) {
      assertTrue(type.getTypeName(), TypeToken.of(type).isSubtypeOf(Number.class));
      assertTrue(type.getTypeName(), TypeToken.of(type).isSubtypeOf(new TypeCapture<Comparable<?>>() {}.capture()));
      assertTrue(type.getTypeName(), TypeToken.of(type).isSubtypeOf(new TypeCapture<Comparable<? extends Number>>() {}.capture()));
      assertTrue(type.getTypeName(), TypeToken.of(type).isSubtypeOf(new TypeCapture<Comparable<? extends Comparable<?>>>() {}.capture()));
    }

    multipleExactTypes(
        (Double) null,
        (Long) null,
        (Integer) null);
  }

  @Test
  public void multipleExactTypes4() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("multipleExactTypes", Object.class, Object.class, Object.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<String>>() {}.capture())
            .where(
                method.getGenericParameterTypes()[1],
                new TypeCapture<List<Integer>>() {}.capture())
            .where(
                method.getGenericParameterTypes()[2],
                new TypeCapture<List<Double>>() {}.capture());

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[1]);
    Type param2Type = resolver.resolveType(method.getGenericParameterTypes()[2]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    // List<? extends Serializable & Comparable<? extends Serializable & Comparable<?>>
    for (Type type : ImmutableList.of(param0Type, param1Type, param2Type, returnType)) {
      assertTrue(type.getTypeName(), TypeToken.of(type).isSubtypeOf(new TypeCapture<List<?>>() {}.capture()));
      assertTrue(type.getTypeName(), TypeToken.of(type).isSubtypeOf(new TypeCapture<List<? extends Serializable>>() {}.capture()));
      assertTrue(type.getTypeName(), TypeToken.of(type).isSubtypeOf(new TypeCapture<List<? extends Comparable<?>>>() {}.capture()));
      assertTrue(type.getTypeName(), TypeToken.of(type).isSubtypeOf(new TypeCapture<List<? extends Comparable<? extends Serializable>>>() {}.capture()));
      assertTrue(type.getTypeName(), TypeToken.of(type).isSubtypeOf(new TypeCapture<List<? extends Comparable<? extends Comparable<?>>>>() {}.capture()));
    }

    multipleExactTypes(
        (List<String>) null,
        (List<Integer>) null,
        (List<Double>) null);
  }

  @Test
  public void multipleExactTypes5() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("multipleExactTypes", Object.class, Object.class, Object.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<IFooBarString>() {}.capture())
            .where(
                method.getGenericParameterTypes()[1],
                new TypeCapture<IFooBarInteger>() {}.capture())
            .where(
                method.getGenericParameterTypes()[2],
                new TypeCapture<IFooBarDouble>() {}.capture());

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[1]);
    Type param2Type = resolver.resolveType(method.getGenericParameterTypes()[2]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    //    IFoo<? extends Serializable & Comparable<? extends Serializable & Comparable<?>>
    //  & IBar<? extends Serializable & Comparable<? extends Serializable & Comparable<?>>
    for (Type type : ImmutableList.of(param0Type, param1Type, param2Type, returnType)) {
      assertTrue(type.getTypeName(), TypeToken.of(type).isSubtypeOf(new TypeCapture<IFoo<?>>() {}.capture()));
      assertTrue(type.getTypeName(), TypeToken.of(type).isSubtypeOf(new TypeCapture<IFoo<? extends Serializable>>() {}.capture()));
      assertTrue(type.getTypeName(), TypeToken.of(type).isSubtypeOf(new TypeCapture<IFoo<? extends Comparable<?>>>() {}.capture()));
      assertTrue(type.getTypeName(), TypeToken.of(type).isSubtypeOf(new TypeCapture<IFoo<? extends Comparable<? extends Serializable>>>() {}.capture()));
      assertTrue(type.getTypeName(), TypeToken.of(type).isSubtypeOf(new TypeCapture<IFoo<? extends Comparable<? extends Comparable<?>>>>() {}.capture()));
      assertTrue(type.getTypeName(), TypeToken.of(type).isSubtypeOf(new TypeCapture<IBar<?>>() {}.capture()));
      assertTrue(type.getTypeName(), TypeToken.of(type).isSubtypeOf(new TypeCapture<IBar<? extends Serializable>>() {}.capture()));
      assertTrue(type.getTypeName(), TypeToken.of(type).isSubtypeOf(new TypeCapture<IBar<? extends Comparable<?>>>() {}.capture()));
      assertTrue(type.getTypeName(), TypeToken.of(type).isSubtypeOf(new TypeCapture<IBar<? extends Comparable<? extends Serializable>>>() {}.capture()));
      assertTrue(type.getTypeName(), TypeToken.of(type).isSubtypeOf(new TypeCapture<IBar<? extends Comparable<? extends Comparable<?>>>>() {}.capture()));
    }

    multipleExactTypes(
        (IFooBarString) null,
        (IFooBarInteger) null,
        (IFooBarDouble) null);
  }

  interface IFoo<T> {}
  interface IBar<T> {}
  interface IFooBarString extends IFoo<String>, IBar<String> {}
  interface IFooBarInteger extends IFoo<Integer>, IBar<Integer> {}
  interface IFooBarDouble extends IFoo<Double>, IBar<Double> {}

  @Test
  public void multipleExactTypes6() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("multipleExactTypes", Object.class, Object.class, Object.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                AccessibleObject[].class)
            .where(
                method.getGenericParameterTypes()[1],
                Member[].class)
            .where(
                method.getGenericParameterTypes()[2],
                Field[].class);

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[1]);
    Type param2Type = resolver.resolveType(method.getGenericParameterTypes()[2]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertEquals(
        Object[].class,
        param0Type);

    assertEquals(
        Object[].class,
        param1Type);

    assertEquals(
        Object[].class,
        param2Type);

    assertEquals(
        Object[].class,
        returnType);

    multipleExactTypes(
        (AccessibleObject[]) null,
        (Member[]) null,
        (Field[]) null);
  }

  @Test
  public void multipleExactTypes7() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("multipleExactTypes", Object.class, Object.class, Object.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                Double[].class)
            .where(
                method.getGenericParameterTypes()[1],
                Long[].class)
            .where(
                method.getGenericParameterTypes()[2],
                Integer[].class);

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[1]);
    Type param2Type = resolver.resolveType(method.getGenericParameterTypes()[2]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    // (Number & Comparable<? extends Number & Comparable<?>>)[]
    for (Type type : ImmutableList.of(param0Type, param1Type, param2Type, returnType)) {
      assertTrue(type.getTypeName(), TypeToken.of(type).isSubtypeOf(Number[].class));
      assertTrue(type.getTypeName(), TypeToken.of(type).isSubtypeOf(new TypeCapture<Comparable<?>[]>() {}.capture()));
      assertTrue(type.getTypeName(), TypeToken.of(type).isSubtypeOf(new TypeCapture<Comparable<? extends Number>[]>() {}.capture()));
      assertTrue(type.getTypeName(), TypeToken.of(type).isSubtypeOf(new TypeCapture<Comparable<? extends Comparable<?>>[]>() {}.capture()));
    }

    multipleExactTypes(
        (Double[]) null,
        (Long[]) null,
        (Integer[]) null);
  }

  @Test
  public void multipleExactTypes8() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("multipleExactTypes", Object.class, Object.class, Object.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                Double[].class)
            .where(
                method.getGenericParameterTypes()[1],
                Long[].class)
            .where(
                method.getGenericParameterTypes()[2],
                int[].class);

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[1]);
    Type param2Type = resolver.resolveType(method.getGenericParameterTypes()[2]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    // Cloneable & Serializable
    for (Type type : ImmutableList.of(param0Type, param1Type, param2Type, returnType)) {
      assertTrue(type.getTypeName(), TypeToken.of(type).isSubtypeOf(Cloneable.class));
      assertTrue(type.getTypeName(), TypeToken.of(type).isSubtypeOf(Serializable.class));
      assertFalse(type.getTypeName(), TypeToken.of(type).isSubtypeOf(Object[].class));
    }

    multipleExactTypes(
        (Double[]) null,
        (Long[]) null,
        (int[]) null);
  }

  @Test
  public void multipleExactTypes9() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("multipleExactArrayTypes", Object[].class, Object[].class, Object[].class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                Double[].class)
            .where(
                method.getGenericParameterTypes()[1],
                Long[].class)
            .where(
                method.getGenericParameterTypes()[2],
                Integer[].class);

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[1]);
    Type param2Type = resolver.resolveType(method.getGenericParameterTypes()[2]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    // (Number & Comparable<? extends Number & Comparable<?>>)[]
    for (Type type : ImmutableList.of(param0Type, param1Type, param2Type, returnType)) {
      assertTrue(type.getTypeName(), TypeToken.of(type).isSubtypeOf(Number[].class));
      assertTrue(type.getTypeName(), TypeToken.of(type).isSubtypeOf(new TypeCapture<Comparable<?>[]>() {}.capture()));
      assertTrue(type.getTypeName(), TypeToken.of(type).isSubtypeOf(new TypeCapture<Comparable<? extends Number>[]>() {}.capture()));
      assertTrue(type.getTypeName(), TypeToken.of(type).isSubtypeOf(new TypeCapture<Comparable<? extends Comparable<?>>[]>() {}.capture()));
    }

    multipleExactArrayTypes(
        (Double[]) null,
        (Long[]) null,
        (Integer[]) null);
  }

  public static <T> T[] multipleExactArrayTypes(T[] a, T[] b, T[] c) { return null; }


  @Test
  public void multipleExactTypes10() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("multipleExactTypes", Object.class, Object.class, Object.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<String>>() {}.capture())
            .where(
                method.getGenericParameterTypes()[1],
                new TypeCapture<List<? extends Integer>>() {}.capture())
            .where(
                method.getGenericParameterTypes()[2],
                new TypeCapture<List<Double>>() {}.capture());

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[1]);
    Type param2Type = resolver.resolveType(method.getGenericParameterTypes()[2]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    // List<? extends Serializable & Comparable<? extends Serializable & Comparable<?>>
    for (Type type : ImmutableList.of(param0Type, param1Type, param2Type, returnType)) {
      assertTrue(type.getTypeName(), TypeToken.of(type).isSubtypeOf(new TypeCapture<List<?>>() {}.capture()));
      assertTrue(type.getTypeName(), TypeToken.of(type).isSubtypeOf(new TypeCapture<List<? extends Serializable>>() {}.capture()));
      assertTrue(type.getTypeName(), TypeToken.of(type).isSubtypeOf(new TypeCapture<List<? extends Comparable<?>>>() {}.capture()));
      assertTrue(type.getTypeName(), TypeToken.of(type).isSubtypeOf(new TypeCapture<List<? extends Comparable<? extends Serializable>>>() {}.capture()));
      assertTrue(type.getTypeName(), TypeToken.of(type).isSubtypeOf(new TypeCapture<List<? extends Comparable<? extends Comparable<?>>>>() {}.capture()));
    }

    // Note: This exercises a case that no other test does,
    //       populateTypeMappings(ParameterizedType, X, SUPERTYPE)

    multipleExactTypes(
        (List<String>) null,
        (List<? extends Integer>) null,
        (List<Double>) null);
  }

  @Test
  public void multipleExactTypes11() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("multipleExactTypes", Object.class, Object.class, Object.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<String>>() {}.capture())
            .where(
                method.getGenericParameterTypes()[1],
                new TypeCapture<List<? super Integer>>() {}.capture())
            .where(
                method.getGenericParameterTypes()[2],
                new TypeCapture<List<Double>>() {}.capture());

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[1]);
    Type param2Type = resolver.resolveType(method.getGenericParameterTypes()[2]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    // List<? extends Serializable & Comparable<? extends Serializable & Comparable<?>>
    for (Type type : ImmutableList.of(param0Type, param1Type, param2Type, returnType)) {
      assertTrue(type.getTypeName(), TypeToken.of(type).isSubtypeOf(new TypeCapture<List<?>>() {}.capture()));
      assertFalse(type.getTypeName(), TypeToken.of(type).isSubtypeOf(new TypeCapture<List<? extends Serializable>>() {}.capture()));
      assertFalse(type.getTypeName(), TypeToken.of(type).isSubtypeOf(new TypeCapture<List<? extends Comparable<?>>>() {}.capture()));
    }

    multipleExactTypes(
        (List<String>) null,
        (List<? super Integer>) null,
        (List<Double>) null);
  }

  @Test
  public void multipleExactTypes12() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("multipleExactComparable", Comparable.class, Comparable.class, Comparable.class);

    try {
      new TypeResolver()
          .where(
              method.getGenericParameterTypes()[0],
              Double.class)
          .where(
              method.getGenericParameterTypes()[1],
              Long.class)
          .where(
              method.getGenericParameterTypes()[2],
              Integer.class);
      fail();
    } catch (IllegalArgumentException expected) {}

    //multipleExactComparable((Double) null, (Long) null, (Integer) null);
  }

  @Test
  public void multipleExactTypes13() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("multipleExactTypes", Object.class, Object.class, Object.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<Class<TimeUnit>>() {}.capture())
            .where(
                method.getGenericParameterTypes()[1],
                new TypeCapture<Class<TimeUnit>>() {}.capture())
            .where(
                method.getGenericParameterTypes()[2],
                new TypeCapture<Class<AnEnum>>() {}.capture());

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[1]);
    Type param2Type = resolver.resolveType(method.getGenericParameterTypes()[2]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    // Number & Comparable<? extends Number & Comparable<?>>
    for (Type type : ImmutableList.of(param0Type, param1Type, param2Type, returnType)) {
      assertTrue(type.getTypeName(), TypeToken.of(type).isSubtypeOf(new TypeCapture<Class<?>>() {}.capture()));
      assertTrue(type.getTypeName(), TypeToken.of(type).isSubtypeOf(new TypeCapture<Class<? extends Enum<?>>>() {}.capture()));
      assertTrue(type.getTypeName(), TypeToken.of(type).isSubtypeOf(new TypeCapture<Class<? extends Enum<? extends Enum<?>>>>() {}.capture()));
    }

    multipleExactTypes(
        TimeUnit.class,
        TimeUnit.class,
        AnEnum.class);
  }

  public static <T extends Comparable<? super T>> T multipleExactComparable(T a, T b, T c) { return null; }

  @Test
  public void tokenResolve() throws Exception {
    class MinFinder<T extends Comparable<? super T>> {
      public T min(Collection<? extends T> collection) { return null; };
    }

    Method method = MinFinder.class.getMethod("min", Collection.class);

    Type param0Type = new TypeToken<MinFinder<String>>() {}.resolveType(method.getGenericParameterTypes()[0]).getType();
    Type returnType = new TypeToken<MinFinder<String>>() {}.resolveType(method.getGenericReturnType()).getType();

    assertEquals(
        new TypeCapture<Collection<? extends String>>() {}.capture(),
        param0Type);

    assertEquals(
        String.class,
        returnType);
  }

  @Test
  public <T> void collectingAndThen() throws Exception {
    Method method = Collectors.class.getMethod("collectingAndThen", Collector.class, Function.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<Collector<T, ?, List<T>>>() {}.capture())
            .where(
                method.getGenericParameterTypes()[1],
                new TypeCapture<Function<List<T>, List<T>>>() {}.capture());

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[1]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertEquals(
        new TypeCapture<Collector<T, ?, List<T>>>() {}.capture(),
        param0Type);

    assertEquals(
        new TypeCapture<Function<List<T>, List<T>>>() {}.capture(),
        param1Type);

    assertEquals(
        new TypeCapture<Collector<T, ?, List<T>>>() {}.capture(),
        returnType);

    Stream<T> stream = Stream.empty();
    List<T> result =
        stream.collect(
            Collectors.collectingAndThen(
                Collectors.toList(),
                Collections::unmodifiableList));
  }

  @Test
  public void intersectionTypesAsInput() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("intersectionTypesAsInput", Object.class, Object.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                IntersectABCD.class)
            .where(
                method.getGenericParameterTypes()[1],
                IntersectABCE.class);

    Type returnType1 = resolver.resolveType(method.getGenericReturnType());

    // A & B & C
    assertTrue(TypeToken.of(returnType1).isSubtypeOf(IntersectA.class));
    assertTrue(TypeToken.of(returnType1).isSubtypeOf(IntersectB.class));
    assertTrue(TypeToken.of(returnType1).isSubtypeOf(IntersectC.class));
    assertFalse(TypeToken.of(returnType1).isSubtypeOf(IntersectD.class));
    assertFalse(TypeToken.of(returnType1).isSubtypeOf(IntersectE.class));

    TypeResolver resolver2 =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                IntersectABCD.class)
            .where(
                method.getGenericParameterTypes()[1],
                IntersectABDE.class);

    // A & B & D
    Type returnType2 = resolver2.resolveType(method.getGenericReturnType());
    assertTrue(TypeToken.of(returnType2).isSubtypeOf(IntersectA.class));
    assertTrue(TypeToken.of(returnType2).isSubtypeOf(IntersectB.class));
    assertFalse(TypeToken.of(returnType2).isSubtypeOf(IntersectC.class));
    assertTrue(TypeToken.of(returnType2).isSubtypeOf(IntersectD.class));
    assertFalse(TypeToken.of(returnType2).isSubtypeOf(IntersectE.class));

    TypeResolver resolver3 =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                returnType1)
            .where(
                method.getGenericParameterTypes()[1],
                returnType2);

    // A & B
    Type returnType3 = resolver3.resolveType(method.getGenericReturnType());
    assertTrue(TypeToken.of(returnType3).isSubtypeOf(IntersectA.class));
    assertTrue(TypeToken.of(returnType3).isSubtypeOf(IntersectB.class));
    assertFalse(TypeToken.of(returnType3).isSubtypeOf(IntersectC.class));
    assertFalse(TypeToken.of(returnType3).isSubtypeOf(IntersectD.class));
    assertFalse(TypeToken.of(returnType3).isSubtypeOf(IntersectE.class));

    intersectionTypesAsInput((IntersectABCD) null, (IntersectABCE) null);
    intersectionTypesAsInput((IntersectABCD) null, (IntersectABDE) null);

    intersectionTypesAsInput(
        intersectionTypesAsInput((IntersectABCD) null, (IntersectABCE) null),
        intersectionTypesAsInput((IntersectABCD) null, (IntersectABDE) null));
  }

  interface IntersectA {}
  interface IntersectB {}
  interface IntersectC {}
  interface IntersectD {}
  interface IntersectE {}
  interface IntersectABCD extends IntersectA, IntersectB, IntersectC, IntersectD {}
  interface IntersectABCE extends IntersectA, IntersectB, IntersectC, IntersectE {}
  interface IntersectABDE extends IntersectA, IntersectB, IntersectD, IntersectE {}

  public static <T> T intersectionTypesAsInput(T a, T b) { return null; }

  @Test
  public void intersectionTypesAsInput2() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("intersectionTypesAsInput2", List.class, List.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<IntersectABCD>>() {}.capture())
            .where(
                method.getGenericParameterTypes()[1],
                new TypeCapture<List<IntersectABCE>>() {}.capture());

    Type returnType1 = resolver.resolveType(method.getGenericReturnType());

    // List<? extends A & B & C>
    assertTrue(returnType1.getTypeName(), TypeToken.of(returnType1).isSubtypeOf(new TypeCapture<List<? extends IntersectA>>() {}.capture()));
    assertTrue(returnType1.getTypeName(), TypeToken.of(returnType1).isSubtypeOf(new TypeCapture<List<? extends IntersectB>>() {}.capture()));
    assertTrue(returnType1.getTypeName(), TypeToken.of(returnType1).isSubtypeOf(new TypeCapture<List<? extends IntersectC>>() {}.capture()));
    assertFalse(returnType1.getTypeName(), TypeToken.of(returnType1).isSubtypeOf(new TypeCapture<List<? extends IntersectD>>() {}.capture()));
    assertFalse(returnType1.getTypeName(), TypeToken.of(returnType1).isSubtypeOf(new TypeCapture<List<? extends IntersectE>>() {}.capture()));

    TypeResolver resolver2 =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<IntersectABCD>>() {}.capture())
            .where(
                method.getGenericParameterTypes()[1],
                new TypeCapture<List<IntersectABDE>>() {}.capture());

    Type returnType2 = resolver2.resolveType(method.getGenericReturnType());

    // List<? extends A & B & D>
    assertTrue(returnType2.getTypeName(), TypeToken.of(returnType2).isSubtypeOf(new TypeCapture<List<? extends IntersectA>>() {}.capture()));
    assertTrue(returnType2.getTypeName(), TypeToken.of(returnType2).isSubtypeOf(new TypeCapture<List<? extends IntersectB>>() {}.capture()));
    assertFalse(returnType2.getTypeName(), TypeToken.of(returnType2).isSubtypeOf(new TypeCapture<List<? extends IntersectC>>() {}.capture()));
    assertTrue(returnType2.getTypeName(), TypeToken.of(returnType2).isSubtypeOf(new TypeCapture<List<? extends IntersectD>>() {}.capture()));
    assertFalse(returnType2.getTypeName(), TypeToken.of(returnType2).isSubtypeOf(new TypeCapture<List<? extends IntersectE>>() {}.capture()));

    TypeResolver resolver3 =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                returnType1)
            .where(
                method.getGenericParameterTypes()[1],
                returnType2);

    Type returnType3 = resolver3.resolveType(method.getGenericReturnType());

    // List<? extends A & B>
    assertTrue(returnType3.getTypeName(), TypeToken.of(returnType3).isSubtypeOf(new TypeCapture<List<? extends IntersectA>>() {}.capture()));
    assertTrue(returnType3.getTypeName(), TypeToken.of(returnType3).isSubtypeOf(new TypeCapture<List<? extends IntersectB>>() {}.capture()));
    assertFalse(returnType3.getTypeName(), TypeToken.of(returnType3).isSubtypeOf(new TypeCapture<List<? extends IntersectC>>() {}.capture()));
    assertFalse(returnType3.getTypeName(), TypeToken.of(returnType3).isSubtypeOf(new TypeCapture<List<? extends IntersectD>>() {}.capture()));
    assertFalse(returnType3.getTypeName(), TypeToken.of(returnType3).isSubtypeOf(new TypeCapture<List<? extends IntersectE>>() {}.capture()));

    intersectionTypesAsInput2((List<IntersectABCD>) null, (List<IntersectABCE>) null);
    intersectionTypesAsInput2((List<IntersectABCD>) null, (List<IntersectABDE>) null);

    intersectionTypesAsInput2(
        intersectionTypesAsInput2((List<IntersectABCD>) null, (List<IntersectABCE>) null),
        intersectionTypesAsInput2((List<IntersectABCD>) null, (List<IntersectABDE>) null));
  }

  public static <T> List<T> intersectionTypesAsInput2(List<? extends T> a, List<? extends T> b) { return null; }

  @Test
  public void extendsGenericToSuperActual() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("extendsGenericToSuperActual", List.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<? super String>>() {}.capture());

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    // List<? extends ? super String>
    assertEquals(
        Types.newParameterizedType(
            List.class,
            Types.subtypeOf(Types.supertypeOf(String.class))),
        param0Type);

    // ? super String
    assertEquals(
        Types.supertypeOf(String.class),
        returnType);

    extendsGenericToSuperActual((List<? super String>) null);
  }

  public static <T> T extendsGenericToSuperActual(List<? extends T> a) { return null; }

  @Test
  public void extendsGenericToExtendsActual() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("extendsGenericToExtendsActual", List.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<? extends String>>() {}.capture());

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    // List<? extends ? extends String>
    assertEquals(
        Types.newParameterizedType(
            List.class,
            Types.subtypeOf(Types.subtypeOf(String.class))),
        param0Type);

    // ? extends String
    assertEquals(
        Types.subtypeOf(String.class),
        returnType);

    extendsGenericToExtendsActual((List<? extends String>) null);
    extendsGenericToExtendsActual2((List<List<? extends String>>) null);
  }

  public static <T> T extendsGenericToExtendsActual(List<? extends T> a) { return null; }

  @Test
  public void extendsGenericToExtendsActual2() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("extendsGenericToExtendsActual2", List.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<List<? extends String>>>() {}.capture());

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    // List<List<? extends String>>
    assertEquals(
        new TypeCapture<List<List<? extends String>>>() {}.capture(),
        param0Type);

    // String
    assertEquals(
        String.class,
        returnType);

    extendsGenericToExtendsActual2((List<List<? extends String>>) null);
  }

  public static <T> T extendsGenericToExtendsActual2(List<List<? extends T>> a) { return null; }

  @Test
  public void superGenericToSuperActual() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("superGenericToSuperActual", List.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<? super String>>() {}.capture());

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    // List<? super ? super String>
    assertEquals(
        Types.newParameterizedType(
            List.class,
            Types.supertypeOf(Types.supertypeOf(String.class))),
        param0Type);

    // ? super String
    assertEquals(
        Types.supertypeOf(String.class),
        returnType);

    superGenericToSuperActual((List<? super String>) null);
  }

  public static <T> T superGenericToSuperActual(List<? super T> a) { return null; }

  @Test
  public void superGenericToSuperActual2() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("superGenericToSuperActual2", List.class);

    TypeResolver resolver =
        new TypeResolver()
            .where(
                method.getGenericParameterTypes()[0],
                new TypeCapture<List<? super String>>() {}.capture());

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    // List<? super ? super String>
    assertEquals(
        Types.newParameterizedType(
            List.class,
            Types.supertypeOf(Types.supertypeOf(String.class))),
        param0Type);

    // ? super String
    assertEquals(
        Types.supertypeOf(String.class),
        returnType);

    superGenericToSuperActual2((List<? super String>) null);
  }

  public static <T, U extends T> U superGenericToSuperActual2(List<? super U> a) { return null; }

  @Test
  public void superGenericToExtendsActual() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("superGenericToExtendsActual", List.class);

    try {
      new TypeResolver()
          .where(
              method.getGenericParameterTypes()[0],
              new TypeCapture<List<? extends String>>() {}.capture());
      fail();
    } catch (IllegalArgumentException expected) {}

    //superGenericToExtendsActual((List<? extends String>) null);
  }

  public static <T> T superGenericToExtendsActual(List<? super T> a) { return null; }

  @Test
  public <T> void incompatibleExtends() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("incompatibleExtends", List.class);

    try {
      new TypeResolver()
          .where(
              method.getGenericParameterTypes()[0],
              new TypeCapture<List<? extends Serializable>>() {}.capture());
      fail();
    } catch (IllegalArgumentException expected) {}

    //incompatibleExtends((List<? extends Serializable>) null);
  }

  public static void incompatibleExtends(List<? extends Member> a) {}

  @Test
  public <T> void incompatibleSuper() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("incompatibleSuper", List.class);

    try {
      new TypeResolver()
          .where(
              method.getGenericParameterTypes()[0],
              new TypeCapture<List<? super Serializable>>() {}.capture());
      fail();
    } catch (IllegalArgumentException expected) {}

    //incompatibleSuper((List<? super Serializable>) null);
  }

  public static void incompatibleSuper(List<? super Member> a) {}

  @Test
  public void testClassToInstanceMap() {
    // Similar to the case described in the comment of
    // https://github.com/google/guava/commit/7a3389afb9f97fe846c69e46d106ac1dbf59f51d
    // except this test seems correct.
    assertEquals(
        new TypeCapture<ClassToInstanceMap<String>>() {}.capture(),
        new TypeToken<Map<Class<? extends String>, String>>() {}
            .getSubtype(ClassToInstanceMap.class)
            .getType());
  }

  // TODO: Add tests involving primitive arrays
  //       especially ones asserting that it's wrong to map T[] to int[].
}
