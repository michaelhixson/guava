package com.google.common.reflect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Streams;
import java.io.Serializable;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
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
                TimeUnit.class);

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertEquals(
        Types.newParameterizedType(Class.class, TimeUnit.class),
        param0Type);

    assertEquals(
        Types.newParameterizedType(
            EnumMap.class,
            TimeUnit.class,
            method.getTypeParameters()[1]),
        returnType);
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
        new ArrayList<Optional<Integer>>(),
        new ArrayList<Optional<Integer>>());
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

    List<?> a = null;
    List<String> b = null;
    a = b;

    List<List<?>> c = null;
    List<List<String>> d = null;
    //c = d;
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
  public void badVariance7() throws Exception {
    Method method = StrictTypeResolverTest.class.getMethod("badVariance7", List.class);

    // TODO: Look more closely into why this should fail.
    // Currently, we're enforcing this through a check that type variables can't
    // be set to wildcards, with a very ugly exception (not the thrown kind)
    // for TypeToken.resolveTypeArgsForSubclass().

    try {
      new TypeResolver()
          .where(
              method.getGenericParameterTypes()[0],
              new TypeCapture<List<List<?>>>() {}.capture());
      fail();
    } catch (IllegalArgumentException expected) {}

    //badVariance7((List<List<?>>) null);
  }

  public static <T> void badVariance7(List<List<T>> b) {}

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

    // In later versions of Java, String and Integer share more interfaces than
    // Serializable and Comparable.
    Type expectedParamType =
        Types.newParameterizedType(
            List.class,
            new Types.WildcardTypeImpl(
                new Type[0],
                new Type[] {
                    Serializable.class,
                    Types.newParameterizedType(
                        Comparable.class,
                        Types.subtypeOf(Object.class))
                }));

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[1]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertTrue(TypeToken.of(param0Type).isSubtypeOf(expectedParamType));
    assertTrue(TypeToken.of(param1Type).isSubtypeOf(expectedParamType));
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
        (Consumer<CharSequence>) any -> {},
        (Consumer<String>) any -> {});
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

    //convergeLowerBounds((Consumer<String>) any -> {}, (Consumer<Integer>) any -> {});
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
    //convergeLowerBounds((Consumer<Foo>) any -> {}, (Consumer<? super SubFoo>) any -> {});
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
    //convergeLowerBounds((Consumer<Foo>) any -> {}, (Consumer<? extends SubFoo>) any -> {});
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
        (Consumer<Serializable>) any -> {},
        (Consumer<Number>) any -> {}, new ArrayList<Integer>());
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
        new ArrayList<Number>(),
        new ArrayList<Serializable>(),
        new ArrayList<Integer>());
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

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[1]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertEquals(
        new TypeCapture<List<? super Number>>() {}.capture(),
        param0Type);

    assertEquals(
        new TypeCapture<List<? super Integer>>() {}.capture(),
        param1Type);

    assertEquals(
        Integer.class,
        returnType);

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

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type param1Type = resolver.resolveType(method.getGenericParameterTypes()[1]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    Type expectedParam1Type =
        Types.newParameterizedType(
            List.class,
            new Types.WildcardTypeImpl(
                new Type[0],
                new Type[] {
                    Number.class,
                    Types.newParameterizedType(
                        Comparable.class,
                        Types.subtypeOf(Object.class))
                }));

    assertEquals(
        new TypeCapture<List<? extends Integer>>() {}.capture(),
        param0Type);

    assertTrue(TypeToken.of(param1Type).isSubtypeOf(expectedParam1Type));
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

    List<String> a = typeChain(new ArrayList<Number>());
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

    TimeUnit b = typeChain(new ArrayList<Optional<Integer>>());
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

    Type param0Type = resolver.resolveType(method.getGenericParameterTypes()[0]);
    Type returnType = resolver.resolveType(method.getGenericReturnType());

    assertEquals(
        new TypeCapture<List<? super Number>>() {}.capture(),
        param0Type);

    assertEquals(
        new TypeCapture<List<? super Object>>() {}.capture(),
        returnType);

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
    assertTrue(param0Type instanceof ParameterizedType);
    ParameterizedType param0ParameterizedType = (ParameterizedType) param0Type;
    assertEquals(List.class, param0ParameterizedType.getRawType());
    Type[] param0TypeArgs = param0ParameterizedType.getActualTypeArguments();
    assertEquals(1, param0TypeArgs.length);
    Type param0TypeArg = param0TypeArgs[0];
    assertTrue(param0TypeArg.getTypeName(), TypeToken.of(param0TypeArg).isSupertypeOf(AccessibleObject.class));
    assertTrue(param0TypeArg.getTypeName(), TypeToken.of(param0TypeArg).isSupertypeOf(Member.class));

    // List<? super AccessibleObject & Member>
    assertTrue(param1Type instanceof ParameterizedType);
    ParameterizedType param1ParameterizedType = (ParameterizedType) param1Type;
    assertEquals(List.class, param1ParameterizedType.getRawType());
    Type[] param1TypeArgs = param1ParameterizedType.getActualTypeArguments();
    assertEquals(1, param1TypeArgs.length);
    Type param1TypeArg = param1TypeArgs[0];
    assertTrue(param1TypeArg.getTypeName(), TypeToken.of(param1TypeArg).isSupertypeOf(AccessibleObject.class));
    assertTrue(param1TypeArg.getTypeName(), TypeToken.of(param1TypeArg).isSupertypeOf(Member.class));

    assertEquals(
        Object.class,
        returnType);

    otherTypeChain(new ArrayList<AccessibleObject>(), new ArrayList<Member>());
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
    assertTrue(param0Type instanceof ParameterizedType);
    ParameterizedType param0ParameterizedType = (ParameterizedType) param0Type;
    assertEquals(Consumer.class, param0ParameterizedType.getRawType());
    Type[] param0TypeArgs = param0ParameterizedType.getActualTypeArguments();
    assertEquals(1, param0TypeArgs.length);
    Type param0TypeArg = param0TypeArgs[0];
    assertTrue(param0TypeArg.getTypeName(), TypeToken.of(param0TypeArg).isSupertypeOf(AccessibleObject.class));
    assertTrue(param0TypeArg.getTypeName(), TypeToken.of(param0TypeArg).isSupertypeOf(Member.class));

    // Consumer<? super AccessibleObject & Member>
    assertTrue(param1Type instanceof ParameterizedType);
    ParameterizedType param1ParameterizedType = (ParameterizedType) param1Type;
    assertEquals(Consumer.class, param0ParameterizedType.getRawType());
    Type[] param1TypeArgs = param1ParameterizedType.getActualTypeArguments();
    assertEquals(1, param1TypeArgs.length);
    Type param1TypeArg = param1TypeArgs[0];
    assertTrue(TypeToken.of(param1TypeArg).isSupertypeOf(AccessibleObject.class));
    assertTrue(TypeToken.of(param1TypeArg).isSupertypeOf(Member.class));

    // AccessibleObject & Member
    assertTrue(TypeToken.of(returnType).isSubtypeOf(AccessibleObject.class));
    assertTrue(TypeToken.of(returnType).isSubtypeOf(Member.class));

    canAccess2(
        (Consumer<AccessibleObject>) any -> {},
        (Consumer<Member>) any -> {});

    AccessibleObject a =
        canAccess2(
            (Consumer<AccessibleObject>) any -> {},
            (Consumer<Member>) any -> {});

    Member b =
        canAccess2(
            (Consumer<AccessibleObject>) any -> {},
            (Consumer<Member>) any -> {});
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

    assertTrue(param0Type instanceof ParameterizedType);
    assertEquals(List.class, ((ParameterizedType) param0Type).getRawType());
    assertEquals(1, ((ParameterizedType) param0Type).getActualTypeArguments().length);
    Type param0TypeArg = ((ParameterizedType) param0Type).getActualTypeArguments()[0];
    assertTrue(TypeToken.of(param0TypeArg).isSubtypeOf(AccessibleObject.class));
    assertTrue(TypeToken.of(param0TypeArg).isSubtypeOf(Member.class));

    assertTrue(param1Type instanceof ParameterizedType);
    assertEquals(List.class, ((ParameterizedType) param1Type).getRawType());
    assertEquals(1, ((ParameterizedType) param1Type).getActualTypeArguments().length);
    Type param1TypeArg = ((ParameterizedType) param1Type).getActualTypeArguments()[0];
    assertTrue(TypeToken.of(param1TypeArg).isSubtypeOf(AccessibleObject.class));
    assertTrue(TypeToken.of(param1TypeArg).isSubtypeOf(Member.class));

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

    List<? extends Number[]> list = null;
    array(list);
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

    List<? extends Number[]> list = null;
    // IntelliJ doesn't catch this one:
    //arraySuper(list);
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

    List<? extends Comparable<?>[]> list = null;
    //array(list);
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
    assertTrue(param0Type instanceof ParameterizedType);
    ParameterizedType param0ParameterizedType = (ParameterizedType) param0Type;
    assertEquals(List.class, param0ParameterizedType.getRawType());
    Type[] param0TypeArgs = param0ParameterizedType.getActualTypeArguments();
    assertEquals(1, param0TypeArgs.length);
    Type param0TypeArg = param0TypeArgs[0];
    assertTrue(param0TypeArg.getTypeName(), TypeToken.of(param0TypeArg).isSupertypeOf(new TypeCapture<Comparable<?>[]>() {}.capture()));
    assertTrue(param0TypeArg.getTypeName(), TypeToken.of(param0TypeArg).isSupertypeOf(new TypeCapture<Serializable[]>() {}.capture()));

    // Serializable & Comparable<?>
    assertTrue(TypeToken.of(returnType).isSubtypeOf(Comparable.class));
    assertTrue(TypeToken.of(returnType).isSubtypeOf(Serializable.class));

    Serializable a = arraySuper((List<? super Comparable<?>[]>) null);
    Comparable<?> b = arraySuper((List<? super Comparable<?>[]>) null);
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

    // List<? super Serializable & capture of ? super Comparable<?>[]>
    assertTrue(param0Type instanceof ParameterizedType);
    ParameterizedType param0ParameterizedType = (ParameterizedType) param0Type;
    assertEquals(List.class, param0ParameterizedType.getRawType());
    Type[] param0TypeArgs = param0ParameterizedType.getActualTypeArguments();
    assertEquals(1, param0TypeArgs.length);
    Type param0TypeArg = param0TypeArgs[0];
    assertTrue(param0TypeArg.getTypeName(), TypeToken.of(param0TypeArg).isSupertypeOf(new TypeCapture<Comparable<?>[]>() {}.capture()));
    assertTrue(param0TypeArg.getTypeName(), TypeToken.of(param0TypeArg).isSupertypeOf(new TypeCapture<Serializable[]>() {}.capture()));

    // Serializable & capture of ? super Comparable<?>[]
    assertTrue(returnType.getTypeName(), TypeToken.of(returnType).isSubtypeOf(new TypeCapture<Comparable<?>[]>() {}.capture()));
    assertTrue(returnType.getTypeName(), TypeToken.of(returnType).isSubtypeOf(new TypeCapture<Serializable[]>() {}.capture()));

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
              new TypeCapture<List<? super List<Number>>>() {}.capture());
      fail();
    } catch (IllegalArgumentException expected) {}

    //parameterizedTypeToWildcard((List<? super List<Number>>) null);
  }

  @Test
  public void parameterizedTypeToWildcardSuper2() throws Exception {
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
    //       where ParameterizedType SUBTYPE_OF X.

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
}
