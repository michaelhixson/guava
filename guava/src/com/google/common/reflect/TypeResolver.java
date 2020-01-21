/*
 * Copyright (C) 2009 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.common.reflect;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Arrays.asList;

import com.google.common.annotations.Beta;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * An object of this class encapsulates type mappings from type variables. Mappings are established
 * with {@link #where} and types are resolved using {@link #resolveType}.
 *
 * <p>Note that usually type mappings are already implied by the static type hierarchy (for example,
 * the {@code E} type variable declared by class {@code List} naturally maps to {@code String} in
 * the context of {@code class MyStringList implements List<String>}. In such case, prefer to use
 * {@link TypeToken#resolveType} since it's simpler and more type safe. This class should only be
 * used when the type mapping isn't implied by the static type hierarchy, but provided through other
 * means such as an annotation or external configuration file.
 *
 * @author Ben Yu
 * @since 15.0
 */
@Beta
public final class TypeResolver {

  private final TypeTable typeTable;

  public TypeResolver() {
    this.typeTable = new TypeTable();
  }

  private TypeResolver(TypeTable typeTable) {
    this.typeTable = typeTable;
  }

  /**
   * Returns a resolver that resolves types "covariantly".
   *
   * <p>For example, when resolving {@code List<T>} in the context of {@code ArrayList<?>}, {@code
   * <T>} is covariantly resolved to {@code <?>} such that return type of {@code List::get} is
   * {@code <?>}.
   */
  static TypeResolver covariantly(Type contextType) {
    return new TypeResolver().where(TypeMappingIntrospector.getTypeMappings(contextType));
  }

  /**
   * Returns a resolver that resolves types "invariantly".
   *
   * <p>For example, when resolving {@code List<T>} in the context of {@code ArrayList<?>}, {@code
   * <T>} cannot be invariantly resolved to {@code <?>} because otherwise the parameter type of
   * {@code List::set} will be {@code <?>} and it'll falsely say any object can be passed into
   * {@code ArrayList<?>::set}.
   *
   * <p>Instead, {@code <?>} will be resolved to a capture in the form of a type variable {@code
   * <capture-of-? extends Object>}, effectively preventing {@code set} from accepting any type.
   */
  static TypeResolver invariantly(Type contextType) {
    Type invariantContext = WildcardCapturer.INSTANCE.capture(contextType);
    return new TypeResolver().where(TypeMappingIntrospector.getTypeMappings(invariantContext));
  }

  /**
   * Returns a new {@code TypeResolver} with type variables in {@code formal} mapping to types in
   * {@code actual}.
   *
   * <p>For example, if {@code formal} is a {@code TypeVariable T}, and {@code actual} is {@code
   * String.class}, then {@code new TypeResolver().where(formal, actual)} will {@linkplain
   * #resolveType resolve} {@code ParameterizedType List<T>} to {@code List<String>}, and resolve
   * {@code Map<T, Something>} to {@code Map<String, Something>} etc. Similarly, {@code formal} and
   * {@code actual} can be {@code Map<K, V>} and {@code Map<String, Integer>} respectively, or they
   * can be {@code E[]} and {@code String[]} respectively, or even any arbitrary combination
   * thereof.
   *
   * @param formal The type whose type variables or itself is mapped to other type(s). It's almost
   *     always a bug if {@code formal} isn't a type variable and contains no type variable. Make
   *     sure you are passing the two parameters in the right order.
   * @param actual The type that the formal type variable(s) are mapped to. It can be or contain yet
   *     other type variables, in which case these type variables will be further resolved if
   *     corresponding mappings exist in the current {@code TypeResolver} instance.
   */
  public TypeResolver where(Type formal, Type actual) {
    Constraints mappings = new Constraints();
    populateTypeMappings(
        mappings,
        ConstraintType.EXACT_TYPE,
        checkNotNull(formal),
        checkNotNull(actual));
    return where(mappings);
  }

  /** Returns a new {@code TypeResolver} with the additional specified {@code mappings}. */
  private TypeResolver where(Constraints mappings) {
    return new TypeResolver(typeTable.where(mappings));
  }

  private static void populateTypeMappings(
      final Constraints mappings,
      final ConstraintType constraintType,
      final Type from,
      final Type to) {
    if (from.equals(to)) {
      return;
    }
    new TypeVisitor() {
      @Override
      void visitTypeVariable(TypeVariable<?> typeVariable) {
        TypeVariableKey key = new TypeVariableKey(typeVariable);
        if (mappings.add(key, to, constraintType)) {
          for (Type bound : typeVariable.getBounds()) {
            if (!(bound instanceof Class)) {
              populateTypeMappings(
                  mappings,
                  constraintTypeForBound(constraintType),
                  bound,
                  to);
            } else if (!bound.equals(Object.class)) {
              // The Object.class bound is redundant, and it causes problems
              // when binding T[] to int[].
              mappings.add(key, bound, ConstraintType.LOWER_BOUND);
            }
          }
        }
      }

      @Override
      void visitWildcardType(WildcardType fromWildcardType) {
        if (!(to instanceof WildcardType)) {
          for (Type fromUpperBound : fromWildcardType.getUpperBounds()) {
            if (!(fromUpperBound instanceof Class)) {
              populateTypeMappings(
                  mappings,
                  ConstraintType.UPPER_BOUND,
                  fromUpperBound,
                  to);
            }
          }
          for (Type fromLowerBound : fromWildcardType.getLowerBounds()) {
            if (!(fromLowerBound instanceof Class)) {
              populateTypeMappings(
                  mappings,
                  ConstraintType.LOWER_BOUND,
                  fromLowerBound,
                  to);
            }
          }
          return;
        }
        WildcardType toWildcardType = (WildcardType) to;
        Type[] fromUpperBounds = fromWildcardType.getUpperBounds();
        Type[] toUpperBounds = toWildcardType.getUpperBounds();
        Type[] fromLowerBounds = fromWildcardType.getLowerBounds();
        Type[] toLowerBounds = toWildcardType.getLowerBounds();
        checkArgument(
            fromUpperBounds.length == toUpperBounds.length
                && fromLowerBounds.length == toLowerBounds.length,
            "Incompatible type: %s vs. %s",
            fromWildcardType,
            to);
        for (int i = 0; i < fromUpperBounds.length; i++) {
          populateTypeMappings(
              mappings,
              ConstraintType.UPPER_BOUND,
              fromUpperBounds[i],
              toUpperBounds[i]);
        }
        for (int i = 0; i < fromLowerBounds.length; i++) {
          populateTypeMappings(
              mappings,
              ConstraintType.LOWER_BOUND,
              fromLowerBounds[i],
              toLowerBounds[i]);
        }
      }

      @Override
      void visitParameterizedType(ParameterizedType fromParameterizedType) {
        if (to instanceof WildcardType) {
          return; // Okay to say Foo<A> is <?>
        }
        if (to instanceof Class && fromParameterizedType.getRawType().equals(Class.class)) {
          // from=Class<K extends Enum<K>>, to=java.util.concurrent.TimeUnit
          populateTypeMappings(
              mappings,
              ConstraintType.EXACT_TYPE,
              fromParameterizedType.getActualTypeArguments()[0],
              to);
          return;
        }
        ParameterizedType toParameterizedType;
        if (to instanceof Class) {
          // from=Comparable<? super T>, to=Integer
          Type supertype = getSupertype(to, (Class<?>) fromParameterizedType.getRawType());
          toParameterizedType = expectArgument(ParameterizedType.class, supertype);
        } else {
          toParameterizedType = expectArgument(ParameterizedType.class, to);
        }
        if (fromParameterizedType.getOwnerType() != null
            && toParameterizedType.getOwnerType() != null) {
          populateTypeMappings(
              mappings,
              ConstraintType.EXACT_TYPE,
              fromParameterizedType.getOwnerType(),
              toParameterizedType.getOwnerType());
        }
        checkArgument(
            ((Class<?>) fromParameterizedType.getRawType())
                .isAssignableFrom((Class<?>) toParameterizedType.getRawType()),
            "Inconsistent raw type: %s vs. %s",
            fromParameterizedType,
            to);
        Type[] fromArgs = fromParameterizedType.getActualTypeArguments();
        Type[] toArgs = toParameterizedType.getActualTypeArguments();
        checkArgument(
            fromArgs.length == toArgs.length,
            "%s not compatible with %s",
            fromParameterizedType,
            toParameterizedType);
        for (int i = 0; i < fromArgs.length; i++) {
          populateTypeMappings(
              mappings,
              ConstraintType.EXACT_TYPE,
              fromArgs[i],
              toArgs[i]);
        }
      }

      @Override
      void visitGenericArrayType(GenericArrayType fromArrayType) {
        if (to instanceof WildcardType) {
          return; // Okay to say A[] is <?>
        }
        Type componentType = Types.getComponentType(to);
        checkArgument(componentType != null, "%s is not an array type.", to);
        populateTypeMappings(
            mappings,
            ConstraintType.EXACT_TYPE,
            fromArrayType.getGenericComponentType(),
            componentType);
      }

      @Override
      void visitClass(Class<?> fromClass) {
        if (to instanceof WildcardType) {
          return; // Okay to say Foo is <?>
        }
        // Can't map from a raw class to anything other than itself or a wildcard.
        // You can't say "assuming String is Integer".
        // And we don't support "assuming String is T"; user has to say "assuming T is String".
        throw new IllegalArgumentException("No type mapping from " + fromClass + " to " + to);
      }
    }.visit(from);
  }

  /**
   * Resolves all type variables in {@code type} and all downstream types and returns a
   * corresponding type with type variables resolved.
   */
  public Type resolveType(Type type) {
    checkNotNull(type);
    if (type instanceof TypeVariable) {
      return typeTable.resolve((TypeVariable<?>) type);
    } else if (type instanceof ParameterizedType) {
      return resolveParameterizedType((ParameterizedType) type);
    } else if (type instanceof GenericArrayType) {
      return resolveGenericArrayType((GenericArrayType) type);
    } else if (type instanceof WildcardType) {
      return resolveWildcardType((WildcardType) type);
    } else {
      // if Class<?>, no resolution needed, we are done.
      return type;
    }
  }

  Type[] resolveTypesInPlace(Type[] types) {
    for (int i = 0; i < types.length; i++) {
      types[i] = resolveType(types[i]);
    }
    return types;
  }

  private Type[] resolveTypes(Type[] types) {
    Type[] result = new Type[types.length];
    for (int i = 0; i < types.length; i++) {
      result[i] = resolveType(types[i]);
    }
    return result;
  }

  private WildcardType resolveWildcardType(WildcardType type) {
    Type[] lowerBounds = type.getLowerBounds();
    Type[] upperBounds = type.getUpperBounds();
    return new Types.WildcardTypeImpl(
        flattenLowerBounds(resolveTypes(lowerBounds)),
        flattenUpperBounds(resolveTypes(upperBounds)));
  }

  private static Type[] flattenLowerBounds(Type[] lowerBounds) {
    Set<Type> flat = null;
    for (int i = 0; i < lowerBounds.length; i++) {
      Type type = lowerBounds[i];
      if (!(type instanceof WildcardType)) {
        if (flat != null) {
          flat.add(type);
        }
        continue;
      }
      WildcardType nested = (WildcardType) type;
      Type[] nestedLowerBounds = nested.getLowerBounds();
      Type[] nestedUpperBounds = nested.getUpperBounds();
      if (flat == null) {
        flat = new LinkedHashSet<>(Arrays.asList(lowerBounds).subList(0, i));
      }
      // ? super ? ---> ? super Object
      if (nestedLowerBounds.length == 0) {
        if ((nestedUpperBounds.length == 1 && nestedUpperBounds[0].equals(Object.class))
            || nestedUpperBounds.length == 0) {
          // TODO: Is this case possible?
          flat.add(Object.class);
          continue;
        }
      }
      // ? super ? super Number ---> ? super Number
      Collections.addAll(flat, nestedLowerBounds);
      // ? super ? extends Number ---> ? super Number
      for (Type nestedUpperBound : nestedUpperBounds) {
        if (!nestedUpperBound.equals(Object.class)) {
          flat.add(nestedUpperBound);
        }
      }
    }
    return (flat == null) ? lowerBounds : flat.toArray(new Type[0]);
  }

  private static Type[] flattenUpperBounds(Type[] upperBounds) {
    Set<Type> flat = null;
    for (int i = 0; i < upperBounds.length; i++) {
      Type type = upperBounds[i];
      if (!(type instanceof WildcardType)) {
        if (flat != null) {
          flat.add(type);
        }
        continue;
      }
      WildcardType nested = (WildcardType) type;
      if (flat == null) {
        flat = new LinkedHashSet<>(Arrays.asList(upperBounds).subList(0, i));
      }
      // ? extends ? super Number ---> ? extends Object
      // This is already implied, so no action is required.
      //
      // ? extends ? extends Number ---> ? extends Number
      Collections.addAll(flat, nested.getUpperBounds());
    }
    return (flat == null) ? upperBounds : flat.toArray(new Type[0]);
  }

  private Type resolveGenericArrayType(GenericArrayType type) {
    Type componentType = type.getGenericComponentType();
    Type resolvedComponentType = resolveType(componentType);
    return Types.newArrayType(resolvedComponentType);
  }

  private ParameterizedType resolveParameterizedType(ParameterizedType type) {
    Type owner = type.getOwnerType();
    Type resolvedOwner = (owner == null) ? null : resolveType(owner);
    Type resolvedRawType = resolveType(type.getRawType());

    Type[] args = type.getActualTypeArguments();
    Type[] resolvedArgs = resolveTypes(args);
    return Types.newParameterizedTypeWithOwner(
        resolvedOwner, (Class<?>) resolvedRawType, resolvedArgs);
  }

  private static <T> T expectArgument(Class<T> type, Object arg) {
    try {
      return type.cast(arg);
    } catch (ClassCastException e) {
      throw new IllegalArgumentException(arg + " is not a " + type.getSimpleName());
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static Type getSupertype(Type type, Class<?> superclass) {
    return TypeToken.of(type).getSupertype((Class) superclass).getType();
  }

  /** A TypeTable maintains mapping from {@link TypeVariable} to types. */
  private static class TypeTable {
    private final Constraints map;

    TypeTable() {
      this.map = new Constraints();
    }

    private TypeTable(Constraints map) {
      this.map = map;
    }

    /** Returns a new {@code TypeResolver} with {@code variable} mapping to {@code type}. */
    final TypeTable where(Constraints mappings) {
      return new TypeTable(Constraints.combine(map, mappings));
    }

    final Type resolve(final TypeVariable<?> var) {
      final TypeTable unguarded = this;
      TypeTable guarded =
          new TypeTable() {
            @Override
            public Type resolveInternal(TypeVariable<?> intermediateVar, TypeTable forDependent) {
              if (intermediateVar.getGenericDeclaration().equals(var.getGenericDeclaration())) {
                return intermediateVar;
              }
              return unguarded.resolveInternal(intermediateVar, forDependent);
            }
          };
      return resolveInternal(var, guarded);
    }

    /**
     * Resolves {@code var} using the encapsulated type mapping. If it maps to yet another
     * non-reified type or has bounds, {@code forDependants} is used to do further resolution, which
     * doesn't try to resolve any type variable on generic declarations that are already being
     * resolved.
     *
     * <p>Should only be called and overridden by {@link #resolve(TypeVariable)}.
     */
    Type resolveInternal(TypeVariable<?> var, TypeTable forDependants) {
      Type type = map.get(new TypeVariableKey(var));
      if (type == null) {
        Type[] bounds = var.getBounds();
        if (bounds.length == 0) {
          return var;
        }
        Type[] resolvedBounds = new TypeResolver(forDependants).resolveTypes(bounds);
        /*
         * We'd like to simply create our own TypeVariable with the newly resolved bounds. There's
         * just one problem: Starting with JDK 7u51, the JDK TypeVariable's equals() method doesn't
         * recognize instances of our TypeVariable implementation. This is a problem because users
         * compare TypeVariables from the JDK against TypeVariables returned by TypeResolver. To
         * work with all JDK versions, TypeResolver must return the appropriate TypeVariable
         * implementation in each of the three possible cases:
         *
         * 1. Prior to JDK 7u51, the JDK TypeVariable implementation interoperates with ours.
         * Therefore, we can always create our own TypeVariable.
         *
         * 2. Starting with JDK 7u51, the JDK TypeVariable implementations does not interoperate
         * with ours. Therefore, we have to be careful about whether we create our own TypeVariable:
         *
         * 2a. If the resolved types are identical to the original types, then we can return the
         * original, identical JDK TypeVariable. By doing so, we sidestep the problem entirely.
         *
         * 2b. If the resolved types are different from the original types, things are trickier. The
         * only way to get a TypeVariable instance for the resolved types is to create our own. The
         * created TypeVariable will not interoperate with any JDK TypeVariable. But this is OK: We
         * don't _want_ our new TypeVariable to be equal to the JDK TypeVariable because it has
         * _different bounds_ than the JDK TypeVariable. And it wouldn't make sense for our new
         * TypeVariable to be equal to any _other_ JDK TypeVariable, either, because any other JDK
         * TypeVariable must have a different declaration or name. The only TypeVariable that our
         * new TypeVariable _will_ be equal to is an equivalent TypeVariable that was also created
         * by us. And that equality is guaranteed to hold because it doesn't involve the JDK
         * TypeVariable implementation at all.
         */
        if (Types.NativeTypeVariableEquals.NATIVE_TYPE_VARIABLE_ONLY
            && Arrays.equals(bounds, resolvedBounds)) {
          return var;
        }
        return Types.newArtificialTypeVariable(
            var.getGenericDeclaration(), var.getName(), resolvedBounds);
      }
      // in case the type is yet another type variable.
      return new TypeResolver(forDependants).resolveType(type);
    }
  }

  private static final class TypeMappingIntrospector extends TypeVisitor {

    private final Map<TypeVariableKey, Type> mappings = new LinkedHashMap<>();

    /**
     * Returns type mappings using type parameters and type arguments found in the generic
     * superclass and the super interfaces of {@code contextClass}.
     */
    static Constraints getTypeMappings(Type contextType) {
      checkNotNull(contextType);
      TypeMappingIntrospector introspector = new TypeMappingIntrospector();
      introspector.visit(contextType);
      Constraints mappings = new Constraints();
      for (Map.Entry<TypeVariableKey, Type> entry : introspector.mappings.entrySet()) {
        mappings.add(
            entry.getKey(),
            entry.getValue(),
            ConstraintType.EXACT_TYPE);
      }
      return mappings;
    }

    @Override
    void visitClass(Class<?> clazz) {
      visit(clazz.getGenericSuperclass());
      visit(clazz.getGenericInterfaces());
    }

    @Override
    void visitParameterizedType(ParameterizedType parameterizedType) {
      Class<?> rawClass = (Class<?>) parameterizedType.getRawType();
      TypeVariable<?>[] vars = rawClass.getTypeParameters();
      Type[] typeArgs = parameterizedType.getActualTypeArguments();
      checkState(vars.length == typeArgs.length);
      for (int i = 0; i < vars.length; i++) {
        map(new TypeVariableKey(vars[i]), typeArgs[i]);
      }
      visit(rawClass);
      visit(parameterizedType.getOwnerType());
    }

    @Override
    void visitTypeVariable(TypeVariable<?> t) {
      visit(t.getBounds());
    }

    @Override
    void visitWildcardType(WildcardType t) {
      visit(t.getUpperBounds());
    }

    private void map(final TypeVariableKey var, final Type arg) {
      if (mappings.containsKey(var)) {
        // Mapping already established
        // This is possible when following both superClass -> enclosingClass
        // and enclosingclass -> superClass paths.
        // Since we follow the path of superclass first, enclosing second,
        // superclass mapping should take precedence.
        return;
      }
      // First, check whether var -> arg forms a cycle
      for (Type t = arg; t != null; t = mappings.get(TypeVariableKey.forLookup(t))) {
        if (var.equalsType(t)) {
          // cycle detected, remove the entire cycle from the mapping so that
          // each type variable resolves deterministically to itself.
          // Otherwise, a F -> T cycle will end up resolving both F and T
          // nondeterministically to either F or T.
          for (Type x = arg; x != null; x = mappings.remove(TypeVariableKey.forLookup(x))) {}
          return;
        }
      }
      mappings.put(var, arg);
    }
  }

  // This is needed when resolving types against a context with wildcards
  // For example:
  // class Holder<T> {
  //   void set(T data) {...}
  // }
  // Holder<List<?>> should *not* resolve the set() method to set(List<?> data).
  // Instead, it should create a capture of the wildcard so that set() rejects any List<T>.
  private static class WildcardCapturer {

    static final WildcardCapturer INSTANCE = new WildcardCapturer();

    private final AtomicInteger id;

    private WildcardCapturer() {
      this(new AtomicInteger());
    }

    private WildcardCapturer(AtomicInteger id) {
      this.id = id;
    }

    final Type capture(Type type) {
      checkNotNull(type);
      if (type instanceof Class) {
        return type;
      }
      if (type instanceof TypeVariable) {
        return type;
      }
      if (type instanceof GenericArrayType) {
        GenericArrayType arrayType = (GenericArrayType) type;
        return Types.newArrayType(
            notForTypeVariable().capture(arrayType.getGenericComponentType()));
      }
      if (type instanceof ParameterizedType) {
        ParameterizedType parameterizedType = (ParameterizedType) type;
        Class<?> rawType = (Class<?>) parameterizedType.getRawType();
        TypeVariable<?>[] typeVars = rawType.getTypeParameters();
        Type[] typeArgs = parameterizedType.getActualTypeArguments();
        for (int i = 0; i < typeArgs.length; i++) {
          typeArgs[i] = forTypeVariable(typeVars[i]).capture(typeArgs[i]);
        }
        return Types.newParameterizedTypeWithOwner(
            notForTypeVariable().captureNullable(parameterizedType.getOwnerType()),
            rawType,
            typeArgs);
      }
      if (type instanceof WildcardType) {
        WildcardType wildcardType = (WildcardType) type;
        Type[] lowerBounds = wildcardType.getLowerBounds();
        if (lowerBounds.length == 0) { // ? extends something changes to capture-of
          return captureAsTypeVariable(wildcardType.getUpperBounds());
        } else {
          // TODO(benyu): handle ? super T somehow.
          return type;
        }
      }
      throw new AssertionError("must have been one of the known types");
    }

    TypeVariable<?> captureAsTypeVariable(Type[] upperBounds) {
      String name =
          "capture#" + id.incrementAndGet() + "-of ? extends " + Joiner.on('&').join(upperBounds);
      return Types.newArtificialTypeVariable(WildcardCapturer.class, name, upperBounds);
    }

    private WildcardCapturer forTypeVariable(final TypeVariable<?> typeParam) {
      return new WildcardCapturer(id) {
        @Override
        TypeVariable<?> captureAsTypeVariable(Type[] upperBounds) {
          Set<Type> combined = new LinkedHashSet<>(asList(upperBounds));
          // Since this is an artifically generated type variable, we don't bother checking
          // subtyping between declared type bound and actual type bound. So it's possible that we
          // may generate something like <capture#1-of ? extends Foo&SubFoo>.
          // Checking subtype between declared and actual type bounds
          // adds recursive isSubtypeOf() call and feels complicated.
          // There is no contract one way or another as long as isSubtypeOf() works as expected.
          combined.addAll(asList(typeParam.getBounds()));
          if (combined.size() > 1) { // Object is implicit and only useful if it's the only bound.
            combined.remove(Object.class);
          }
          return super.captureAsTypeVariable(combined.toArray(new Type[0]));
        }
      };
    }

    private WildcardCapturer notForTypeVariable() {
      return new WildcardCapturer(id);
    }

    private Type captureNullable(@Nullable Type type) {
      if (type == null) {
        return null;
      }
      return capture(type);
    }
  }

  /**
   * Wraps around {@code TypeVariable<?>} to ensure that any two type variables are equal as long as
   * they are declared by the same {@link java.lang.reflect.GenericDeclaration} and have the same
   * name, even if their bounds differ.
   *
   * <p>While resolving a type variable from a {@code var -> type} map, we don't care whether the
   * type variable's bound has been partially resolved. As long as the type variable "identity"
   * matches.
   *
   * <p>On the other hand, if for example we are resolving {@code List<A extends B>} to {@code
   * List<A extends String>}, we need to compare that {@code <A extends B>} is unequal to {@code <A
   * extends String>} in order to decide to use the transformed type instead of the original type.
   */
  private static final class TypeVariableKey {
    private final TypeVariable<?> var;

    TypeVariableKey(TypeVariable<?> var) {
      this.var = checkNotNull(var);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(var.getGenericDeclaration(), var.getName());
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof TypeVariableKey) {
        TypeVariableKey that = (TypeVariableKey) obj;
        return equalsTypeVariable(that.var);
      } else {
        return false;
      }
    }

    @Override
    public String toString() {
      return var.toString();
    }

    /** Wraps {@code t} in a {@code TypeVariableKey} if it's a type variable. */
    static TypeVariableKey forLookup(Type t) {
      if (t instanceof TypeVariable) {
        return new TypeVariableKey((TypeVariable<?>) t);
      } else {
        return null;
      }
    }

    /**
     * Returns true if {@code type} is a {@code TypeVariable} with the same name and declared by the
     * same {@code GenericDeclaration}.
     */
    boolean equalsType(Type type) {
      if (type instanceof TypeVariable) {
        return equalsTypeVariable((TypeVariable<?>) type);
      } else {
        return false;
      }
    }

    private boolean equalsTypeVariable(TypeVariable<?> that) {
      return var.getGenericDeclaration().equals(that.getGenericDeclaration())
          && var.getName().equals(that.getName());
    }
  }

  private enum ConstraintType { EXACT_TYPE, RELATED_TYPE, LOWER_BOUND, UPPER_BOUND }

  private static final class Constraint {
    private final TypeVariableKey key;
    private @Nullable Type exactType;
    private @Nullable Type relatedType;
    private @Nullable Type lowerBound;
    private @Nullable Set<Type> upperBounds;

    Constraint(TypeVariableKey key) {
      this.key = checkNotNull(key);
    }

    Type get() {
      if (exactType != null) {
        return exactType;
      }
      if (upperBounds != null) {
        return mergeUpperBounds(upperBounds);
      }
      if (lowerBound != null) {
        return lowerBound;
      }
      if (relatedType != null) {
        return Object.class;
      }
      throw new IllegalStateException();
    }

    void add(Constraint other) {
      checkNotNull(other);
      if (other.exactType != null) {
        addExactType(other.exactType);
      }
      if (other.relatedType != null) {
        addRelatedType(other.relatedType);
      }
      if (other.lowerBound != null) {
        addLowerBound(other.lowerBound);
      }
      if (other.upperBounds != null) {
        other.upperBounds.forEach(this::addUpperBound);
      }
    }

    void add(Type type, ConstraintType constraintType) {
      checkNotNull(type);
      checkNotNull(constraintType);
      switch (constraintType) {
        case EXACT_TYPE:
          addExactType(type);
          return;
        case RELATED_TYPE:
          addRelatedType(type);
          return;
        case LOWER_BOUND:
          addLowerBound(type);
          return;
        case UPPER_BOUND:
          addUpperBound(type);
          return;
      }
      throw new AssertionError("Unknown constraint type " + constraintType);
    }

    private void addExactType(Type type) {
      checkNotNull(type);
      checkNotMappedToSelf(type);
      checkExactTypeAndRelatedType(type, relatedType);
      checkExactTypeAndLowerBound(type, lowerBound);
      checkExactTypeAndUpperBounds(type, upperBounds);
      if (exactType == null) {
        exactType = type;
        return;
      }
      checkArgument(
          exactType.equals(type),
          "Type %s must be exactly %s, so it cannot be %s",
          key,
          exactType.getTypeName(),
          type.getTypeName());
    }

    private void addRelatedType(Type type) {
      checkNotNull(type);
      checkNotMappedToSelf(type);
      checkExactTypeAndRelatedType(exactType, type);
      checkRelatedTypeAndLowerBound(type, lowerBound);
      checkRelatedTypeAndUpperBounds(type, upperBounds);
      if (relatedType == null || TypeToken.of(type).isSubtypeOf(relatedType)) {
        relatedType = type;
        return;
      }
      checkArgument(
          TypeToken.of(relatedType).isSubtypeOf(type),
          "Type %s must be related to %s, so it cannot also be related to %s",
          key,
          relatedType.getTypeName(),
          type.getTypeName());
    }

    private void addLowerBound(Type type) {
      checkNotNull(type);
      checkNotMappedToSelf(type);
      checkExactTypeAndLowerBound(exactType, type);
      checkRelatedTypeAndLowerBound(relatedType, type);
      checkLowerBoundAndUpperBounds(type, upperBounds);
      if (lowerBound == null || TypeToken.of(type).isSubtypeOf(lowerBound)) {
        lowerBound = type;
        return;
      }
      checkArgument(
          TypeToken.of(lowerBound).isSubtypeOf(type),
          "Type %s must be a subtype of %s, so it cannot also be a subtype of %s",
          key,
          lowerBound.getTypeName(),
          type.getTypeName());
    }

    private void addUpperBound(Type type) {
      checkNotNull(type);
      checkNotMappedToSelf(type);
      // No need to re-check the other upper bounds.
      ImmutableSet<Type> newUpperBounds = ImmutableSet.of(type);
      checkExactTypeAndUpperBounds(exactType, newUpperBounds);
      checkRelatedTypeAndUpperBounds(relatedType, newUpperBounds);
      checkLowerBoundAndUpperBounds(lowerBound, newUpperBounds);
      if (upperBounds == null) {
        upperBounds = new LinkedHashSet<>();
      }
      upperBounds.add(type);
    }

    private void checkNotMappedToSelf(Type type) {
      checkArgument(
          !key.equalsType(type),
          "Type variable %s bound to itself",
          key);
    }

    private void checkExactTypeAndRelatedType(@Nullable Type exactType, @Nullable Type relatedType) {
      if (exactType == null || relatedType == null) {
        return;
      }
      checkArgument(
          TypeToken.of(relatedType).isSubtypeOf(exactType)
              || TypeToken.of(exactType).isSubtypeOf(relatedType),
          "No type can satisfy the constraints of %s, "
              + "which must be %s and related to %s",
          key,
          exactType.getTypeName(),
          relatedType.getTypeName());
    }

    private void checkExactTypeAndLowerBound(@Nullable Type exactType, @Nullable Type lowerBound) {
      if (exactType == null || lowerBound == null) {
        return;
      }
      checkArgument(
          TypeToken.of(exactType).isSubtypeOf(lowerBound),
          "No type can satisfy the constraints of %s, "
              + "which must be %s and a subtype of %s",
          key,
          exactType.getTypeName(),
          lowerBound.getTypeName());
    }

    private void checkExactTypeAndUpperBounds(@Nullable Type exactType, @Nullable Set<Type> upperBounds) {
      if (exactType == null || upperBounds == null) {
        return;
      }
      for (Type upperBound : upperBounds) {
        checkArgument(
            TypeToken.of(exactType).isSupertypeOf(upperBound),
            "No type can satisfy the constraints of %s, "
                + "which must be %s and a supertype of %s",
            key,
            exactType.getTypeName(),
            upperBound.getTypeName());
      }
    }

    private void checkRelatedTypeAndLowerBound(@Nullable Type relatedType, @Nullable Type lowerBound) {
      if (relatedType == null || lowerBound == null) {
        return;
      }
      checkArgument(
          TypeToken.of(relatedType).isSubtypeOf(lowerBound)
              || TypeToken.of(lowerBound).isSubtypeOf(relatedType),
          "No type can satisfy the constraints of %s, "
              + "which must be related to %s and a subtype of %s",
          key,
          relatedType.getTypeName(),
          lowerBound.getTypeName());
    }

    private void checkRelatedTypeAndUpperBounds(@Nullable Type relatedType, @Nullable Set<Type> upperBounds) {
      if (relatedType == null || upperBounds == null) {
        return;
      }
      for (Type upperBound : upperBounds) {
        checkArgument(
            TypeToken.of(relatedType).isSubtypeOf(upperBound)
                || TypeToken.of(upperBound).isSubtypeOf(relatedType),
            "No type can satisfy the constraints of %s, "
                + "which must be related to %s and a supertype of %s",
            key,
            relatedType.getTypeName(),
            upperBound.getTypeName());
      }
    }

    private void checkLowerBoundAndUpperBounds(@Nullable Type lowerBound, @Nullable Set<Type> upperBounds) {
      if (lowerBound == null || upperBounds == null) {
        return;
      }
      for (Type upperBound : upperBounds) {
        checkArgument(
            TypeToken.of(lowerBound).isSupertypeOf(upperBound),
            "No type can satisfy the constraints of %s, "
                + "which must be a subtype of %s and a supertype of %s",
            key,
            lowerBound.getTypeName(),
            upperBound.getTypeName());
      }
    }

    private static Type mergeUpperBounds(Set<Type> upperBounds) {
      checkNotNull(upperBounds);
      if (upperBounds.size() == 1) {
        return upperBounds.iterator().next();
      }
      Iterator<Type> iterator = upperBounds.iterator();
      Set<Type> supertypes = new LinkedHashSet<>(supertypesOf(iterator.next()));
      while (iterator.hasNext()) {
        supertypes.retainAll(supertypesOf(iterator.next()));
      }
      if (supertypes.isEmpty()) {
        // Shouldn't they all share Object at least?
        throw new IllegalArgumentException("No common supertype in bounds " + upperBounds);
      }
      // Remove redundant supertypes.
      for (Type a : new LinkedHashSet<>(supertypes)) {
        TypeToken<?> aToken = TypeToken.of(a);
        supertypes.removeIf(b -> !b.equals(a) && aToken.isSubtypeOf(b));
      }
      if (supertypes.isEmpty()) {
        return Object.class;
      }
      if (supertypes.size() == 1) {
        return supertypes.iterator().next();
      }
      return new Types.WildcardTypeImpl(
          new Type[0],
          supertypes.toArray(new Type[0]));
    }

    private static Set<Type> supertypesOf(Type type) {
      Set<Type> supertypes = new LinkedHashSet<>();
      for (TypeToken<?> supertype : TypeToken.of(type).getTypes()) {
        supertypes.add(supertype.getType());
        //
        // We also need to inspect the raw types.  Suppose that our bounds
        // contain String and Integer.  String is Comparable<String>, and
        // Integer is Comparable<Integer>.  They don't share either one of those
        // parameterized types, but they do share Comparable<?>.
        //
        Class<?> rawType = supertype.getRawType();
        TypeVariable<?>[] typeParameters = rawType.getTypeParameters();
        if (typeParameters.length == 0) {
          supertypes.add(rawType);
        } else {
          Type[] typeArguments = new Type[typeParameters.length];
          for (int i = 0; i < typeParameters.length; i++) {
            typeArguments[i] =
                new Types.WildcardTypeImpl(
                    new Type[0],
                    typeParameters[i].getBounds());
          }
          ParameterizedType parameterizedType =
              Types.newParameterizedType(rawType, typeArguments);
          supertypes.add(parameterizedType);
        }
      }
      return supertypes;
    }
  }

  private static final class Constraints {
    private final Set<Seen> seen = new LinkedHashSet<>();
    private final Map<TypeVariableKey, Constraint> constraints = new LinkedHashMap<>();

    @Nullable Type get(TypeVariableKey key) {
      checkNotNull(key);
      Constraint constraint = constraints.get(key);
      return (constraint == null) ? null : constraint.get();
    }

    boolean add(TypeVariableKey key, Type type, ConstraintType constraintType) {
      checkNotNull(key);
      checkNotNull(type);
      checkNotNull(constraintType);
      if (!seen.add(new Seen(key, type, constraintType))) {
        return false;
      }
      constraints.computeIfAbsent(key, Constraint::new).add(type, constraintType);
      return true;
    }

    private void addAll(Constraints other) {
      checkNotNull(other);
      seen.addAll(other.seen);
      other.constraints.forEach(
          (key, constraint) ->
              constraints.merge(
                  key,
                  constraint,
                  (c1, c2) -> { c1.add(c2); return c1; }));
    }

    static Constraints combine(Constraints a, Constraints b) {
      checkNotNull(a);
      checkNotNull(b);
      Constraints sum = new Constraints();
      sum.addAll(a);
      sum.addAll(b);
      return sum;
    }

    private static final class Seen {
      private final TypeVariableKey key;
      private final Type type;
      private final ConstraintType constraintType;

      Seen(TypeVariableKey key, Type type, ConstraintType constraintType) {
        this.key = checkNotNull(key);
        this.type = checkNotNull(type);
        this.constraintType = checkNotNull(constraintType);
      }

      @Override
      public boolean equals(@Nullable Object object) {
        if (object instanceof Seen) {
          Seen that = (Seen) object;
          return this.key.equals(that.key)
              && this.type.equals(that.type)
              && this.constraintType.equals(that.constraintType);
        } else {
          return false;
        }
      }

      @Override
      public int hashCode() {
        return Objects.hashCode(key, type, constraintType);
      }
    }
  }

  private static ConstraintType constraintTypeForBound(ConstraintType constraintType) {
    switch (constraintType) {
      case EXACT_TYPE:
        // key=B
        // type=EXACT_TYPE
        // to=Integer
        // B == Integer
        //
        // boundVar=A
        // B extends A
        // -----> Integer extends A
        // -----> Integer is a subtype of A
        //
        // same logic as UPPER_BOUND case
        //
        // (fallthrough)
        //
      case UPPER_BOUND:
        // key=U
        // type=UPPER_BOUND
        // to=Integer
        // "Integer is something that extends U"
        // "Integer is a subtype of U"
        //
        // boundVar=T
        // "U extends T"
        // ----> "Integer is a subtype of T"
        //
        return ConstraintType.UPPER_BOUND;
      case LOWER_BOUND:
        // key=U
        // type=LOWER_BOUND
        // to=Number
        // "Number is something that is a supertype of U"
        // "Number is a supertype of U"
        // "U extends Number"
        //
        // boundVar=T
        // "U extends T"
        // "U is a subtype of T"
        // "T is a supertype of U"
        // anything we can conclude about T w.r.t Number?
        // it can't be totally unrelated to Number, right?
        // it has to be either a supertype of Number or a subtype of Number?
        //   T=Integer works, T=Object works?, T=String doesn't work, right?
        //         yes              yes          right
        // so T is RELATED to Number
        //
        return ConstraintType.RELATED_TYPE;
      case RELATED_TYPE:
        // TODO: Is this outcome possible?
        //
        // It seems like we can't conclude anything about the bound in this
        // situation.
        //
        // key=A
        // type=RELATED_TYPE
        // to=Number
        // "A extends Number or Number extends A"
        //
        // boundVar=B
        // B extends A or A extends B
        // ^ don't think it's possible to express this in the type system
        //
        // Could B be String?
        // yes, if A is Object (Number extends A), and B extends A
        // so B could have nothing to do with Number.
        //
        throw new IllegalArgumentException();
    }
    throw new AssertionError("Unknown constraint type " + constraintType);
  }
}
