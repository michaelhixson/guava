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
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
    return where(TypeMappings.fromTypeDeclaration(formal, actual));
  }

  /** Returns a new {@code TypeResolver} with additional mappings. */
  private TypeResolver where(TypeMappings mappings) {
    return new TypeResolver(typeTable.where(mappings));
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
    return new Types.WildcardTypeImpl(resolveTypes(lowerBounds), resolveTypes(upperBounds));
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

  /** A TypeTable maintains mapping from {@link TypeVariable} to types. */
  private static class TypeTable {
    private final TypeMappings mappings;

    TypeTable() {
      this.mappings = new TypeMappings();
    }

    TypeTable(TypeMappings mappings) {
      this.mappings = mappings;
    }

    /** Returns a new {@code TypeTable} with additional mappings. */
    final TypeTable where(TypeMappings moreMappings) {
      return new TypeTable(TypeMappings.combine(mappings, moreMappings));
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
      Type type = mappings.resolve(new TypeVariableKey(var));
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

    private final Map<TypeVariableKey, Type> mappings = Maps.newHashMap();

    /**
     * Returns type mappings using type parameters and type arguments found in the generic
     * superclass and the super interfaces of {@code contextClass}.
     */
    static TypeMappings getTypeMappings(Type contextType) {
      checkNotNull(contextType);
      TypeMappingIntrospector introspector = new TypeMappingIntrospector();
      introspector.visit(contextType);
      TypeMappings mappings = new TypeMappings();
      for (Map.Entry<TypeVariableKey, Type> entry : introspector.mappings.entrySet()) {
        mappings.put(entry.getKey(), entry.getValue(), TypeRelationship.EQUAL);
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

  /** A relationship between two types. */
  private enum TypeRelationship {
    /** That type is equal to this type. */
    EQUAL,

    /**
     * That type {@linkplain #intersecting(Type, Type) intersects} this type.
     */
    INTERSECTING,

    /** That type is a supertype of this type. */
    SUPERTYPE,

    /** That type is a subtype of this type. */
    SUBTYPE
  }

  /** Constraints for one {@link TypeVariable}. */
  private static final class TypeVariableConstraints {
    private final TypeVariableKey key;
    private @Nullable Type exactType;
    private @Nullable Set<Type> intersectingTypes;
    private @Nullable Set<Type> supertypes;
    private @Nullable Set<Type> subtypes;

    @Override
    public String toString() {
      return MoreObjects
          .toStringHelper(this)
          .omitNullValues()
          .add("key", key)
          .add("exactType", exactType)
          .add("intersectingTypes", intersectingTypes)
          .add("supertypes", supertypes)
          .add("subtypes", subtypes)
          .toString();
    }

    TypeVariableConstraints(TypeVariableKey key) {
      this.key = checkNotNull(key);
    }

    /**
     * Resolves the type of this type variable given its constraints.
     */
    Type resolve(TypeMappings mappings) {
      checkNotNull(mappings);
      if (exactType != null) {
        return exactType;
      }
      if (subtypes != null) {
        return leastUpperBound(mappings);
      }
      if (supertypes != null) {
        return greatestLowerBound();
      }
      return Object.class;
    }

    /**
     * Adds the constraint that the specified type is equal to this type variable's type.
     *
     * @return {@code true} if this instance was modified as a result of the call
     * @throws IllegalArgumentException if this new constraint is incompatible with existing
     *         constraints
     */
    boolean setExactType(Type exactType) {
      checkNotNull(exactType);
      checkNotMappedToSelf(exactType);
      checkExactTypeAndIntersectingTypes(exactType, intersectingTypes);
      checkExactTypeAndSupertypes(exactType, supertypes);
      checkExactTypeAndSubtypes(exactType, subtypes);
      if (this.exactType == null) {
        this.exactType = exactType;
        return true;
      }
      checkArgument(
          this.exactType.equals(exactType),
          "Type %s must be exactly %s, so it cannot be %s",
          key,
          this.exactType.getTypeName(),
          exactType.getTypeName());
      return false;
    }

    /**
     * Adds the constraint that the specified type {@linkplain #intersecting(Type, Type)} intersects}
     * this type variable's type.
     *
     * @return {@code true} if this instance was modified as a result of the call
     * @throws IllegalArgumentException if this new constraint is incompatible with existing
     *         constraints
     */
    boolean addIntersectingType(Type intersectingType) {
      checkNotNull(intersectingType);
      checkNotMappedToSelf(intersectingType);
      // No need to re-check the other bounds.
      ImmutableSet<Type> newIntersectingTypes = ImmutableSet.of(intersectingType);
      checkExactTypeAndIntersectingTypes(exactType, newIntersectingTypes);
      checkIntersectingTypesAndSupertypes(newIntersectingTypes, supertypes);
      checkIntersectingTypesAndSubtypes(newIntersectingTypes, subtypes);
      if (intersectingTypes != null) {
        for (Type existingIntersectingType : intersectingTypes) {
          // TODO: Write a test that throws here.
          checkArgument(
              intersecting(existingIntersectingType, intersectingType),
              "Type %s must intersect %s, so it cannot intersect %s",
              key,
              existingIntersectingType.getTypeName(),
              intersectingType.getTypeName());
        }
      }
      if (intersectingTypes == null) {
        intersectingTypes = new LinkedHashSet<>();
      }
      return intersectingTypes.add(intersectingType);
    }

    /**
     * Adds the constraint that the specified type is a supertype of this type variable's type.
     *
     * @return {@code true} if this instance was modified as a result of the call
     * @throws IllegalArgumentException if this new constraint is incompatible with existing
     *         constraints
     */
    boolean addSupertype(Type supertype) {
      checkNotNull(supertype);
      checkNotMappedToSelf(supertype);
      // No need to re-check the other bounds.
      ImmutableSet<Type> newSupertypes = ImmutableSet.of(supertype);
      checkExactTypeAndSupertypes(exactType, newSupertypes);
      checkIntersectingTypesAndSupertypes(intersectingTypes, newSupertypes);
      checkSupertypesAndSubtypes(newSupertypes, subtypes);
      if (supertypes != null) {
        for (Type existingSupertype : supertypes) {
          checkArgument(
              intersecting(existingSupertype, supertype),
              "Type %s must be a subtype of %s, so it cannot be a subtype of %s",
              key,
              existingSupertype.getTypeName(),
              supertype.getTypeName());
        }
      }
      if (supertypes == null) {
        supertypes = new LinkedHashSet<>();
      }
      return supertypes.add(supertype);
    }

    /**
     * Adds the constraint that the specified type is a subtype of this type variable's type.
     *
     * @return {@code true} if this instance was modified as a result of the call
     * @throws IllegalArgumentException if this new constraint is incompatible with existing
     *         constraints
     */
    boolean addSubtype(Type subtype) {
      checkNotNull(subtype);
      checkNotMappedToSelf(subtype);
      // No need to re-check the other bounds.
      ImmutableSet<Type> newSubtypes = ImmutableSet.of(subtype);
      checkExactTypeAndSubtypes(exactType, newSubtypes);
      checkIntersectingTypesAndSubtypes(intersectingTypes, newSubtypes);
      checkSupertypesAndSubtypes(supertypes, newSubtypes);
      if (subtypes == null) {
        subtypes = new LinkedHashSet<>();
      }
      return subtypes.add(subtype);
    }

    /**
     * Adds the specified constraints to this instance.
     *
     * @throws IllegalArgumentException if the new constraints are incompatible with the existing
     *         constraints
     */
    void addAll(TypeVariableConstraints constraints) {
      checkNotNull(constraints);
      if (constraints.exactType != null) {
        setExactType(constraints.exactType);
      }
      if (constraints.intersectingTypes != null) {
        for (Type intersectingType : constraints.intersectingTypes) {
          addIntersectingType(intersectingType);
        }
      }
      if (constraints.supertypes != null) {
        for (Type supertype : constraints.supertypes) {
          addSupertype(supertype);
        }
      }
      if (constraints.subtypes != null) {
        for (Type subtype : constraints.subtypes) {
          addSubtype(subtype);
        }
      }
    }

    /**
     * @throws IllegalArgumentException if the specified type is this type variable
     */
    private void checkNotMappedToSelf(Type type) {
      checkArgument(
          !key.equalsType(type),
          "Type variable %s bound to itself",
          key);
    }

    /**
     * @throws IllegalArgumentException if it is impossible for any type variable to be equal to
     *         {@code exactType} and to intersect all of {@code intersectingTypes}
     */
    private void checkExactTypeAndIntersectingTypes(
        @Nullable Type exactType,
        @Nullable Set<Type> intersectingTypes) {
      if (exactType == null || intersectingTypes == null) {
        return;
      }
      for (Type intersectingType : intersectingTypes) {
        checkArgument(
            intersecting(exactType, intersectingType),
            "No type can satisfy the constraints of %s, "
                + "which must be equal to %s and intersect %s",
            key,
            exactType.getTypeName(),
            intersectingType.getTypeName());
      }
    }

    /**
     * @throws IllegalArgumentException if it is impossible for any type variable to be equal to
     *         {@code exactType} and to be a subtype of all of {@code supertypes}
     */
    private void checkExactTypeAndSupertypes(
        @Nullable Type exactType,
        @Nullable Set<Type> supertypes) {
      if (exactType == null || supertypes == null) {
        return;
      }
      for (Type supertype : supertypes) {
        checkArgument(
            isSubtype(exactType, supertype),
            "No type can satisfy the constraints of %s, "
                + "which must be equal to %s and a subtype of %s",
            key,
            exactType.getTypeName(),
            supertype.getTypeName());
      }
    }

    /**
     * @throws IllegalArgumentException if it is impossible for any type variable to be equal to
     *         {@code exactType} and to be a supertype of all of {@code subtypes}
     */
    private void checkExactTypeAndSubtypes(
        @Nullable Type exactType,
        @Nullable Set<Type> subtypes) {
      if (exactType == null || subtypes == null) {
        return;
      }
      for (Type subtype : subtypes) {
        checkArgument(
            isSubtype(subtype, exactType),
            "No type can satisfy the constraints of %s, "
                + "which must be equal to %s and a supertype of %s",
            key,
            exactType.getTypeName(),
            subtype.getTypeName());
      }
    }

    /**
     * @throws IllegalArgumentException if it is impossible for any type variable to intersect all
     *         of {@code intersectingTypes} and to be a subtype of all of {@code supertypes}
     */
    private void checkIntersectingTypesAndSupertypes(
        @Nullable Set<Type> intersectingTypes,
        @Nullable Set<Type> supertypes) {
      if (intersectingTypes == null || supertypes == null) {
        return;
      }
      for (Type intersectingType : intersectingTypes) {
        for (Type supertype : supertypes) {
          checkArgument(
              intersecting(intersectingType, supertype),
              "No type can satisfy the constraints of %s, "
                  + "which must intersect %s and be a subtype of %s",
              key,
              intersectingType.getTypeName(),
              supertype.getTypeName());
        }
      }
    }

    /**
     * @throws IllegalArgumentException if it is impossible for any type variable to intersect all
     *         of {@code intersectingTypes} and to be a supertype of of all of {@code subtypes}
     */
    private void checkIntersectingTypesAndSubtypes(
        @Nullable Set<Type> intersectingTypes,
        @Nullable Set<Type> subtypes) {
      if (intersectingTypes == null || subtypes == null) {
        return;
      }
      for (Type intersectingType : intersectingTypes) {
        for (Type subtype : subtypes) {
          checkArgument(
              intersecting(intersectingType, subtype),
              "No type can satisfy the constraints of %s, "
                  + "which must intersect %s and be a supertype of %s",
              key,
              intersectingType.getTypeName(),
              subtype.getTypeName());
        }
      }
    }

    /**
     * @throws IllegalArgumentException if it is impossible for any type variable to be a subtype of
     *         all of {@code supertypes} and a supertype of all of {@code subtypes}
     */
    private void checkSupertypesAndSubtypes(
        @Nullable Set<Type> supertypes,
        @Nullable Set<Type> subtypes) {
      if (supertypes == null || subtypes == null) {
        return;
      }
      for (Type supertype : supertypes) {
        for (Type subtype : subtypes) {
          checkArgument(
              isSubtype(subtype, supertype),
              //intersecting(subtype, supertype),
              "No type can satisfy the constraints of %s, "
                  + "which must be a subtype of %s and a supertype of %s",
              key,
              supertype.getTypeName(),
              subtype.getTypeName());
        }
      }
    }

    /**
     * Returns a type that is a subtype of all {@link #supertypes}.
     *
     * @throws IllegalStateException if this type variable has no known supertypes
     */
    private Type greatestLowerBound() {
      checkState(supertypes != null && !supertypes.isEmpty());
      if (supertypes.size() == 1) {
        return supertypes.iterator().next();
      }
      // Remove redundant types.
      Set<Type> canonicalTypes = new LinkedHashSet<>(supertypes);
      for (Type a : supertypes) {
        for (Iterator<Type> remover = canonicalTypes.iterator(); remover.hasNext();) {
          Type b = remover.next();
          if (!b.equals(a) && isSubtype(a, b)) {
            remover.remove();
          }
        }
      }
      return newIntersectionType(canonicalTypes);
    }

    /**
     * Returns a type that is a supertype of all {@link #subtypes}.
     *
     * <p>See <a href="https://docs.oracle.com/javase/specs/jls/se13/html/jls-4.html#jls-4.10.4"
     * >Least Upper Bound</a>.
     *
     * @throws IllegalStateException if this type variable has no known subtypes
     */
    private Type leastUpperBound(TypeMappings mappings) {
      checkNotNull(mappings);
      checkState(subtypes != null && !subtypes.isEmpty());
      if (subtypes.size() == 1) {
        return subtypes.iterator().next();
      }
      Iterator<Type> iterator = subtypes.iterator();
      Set<Type> genericSupertypes = new LinkedHashSet<>(getGenericSupertypes(iterator.next()));
      while (iterator.hasNext()) {
        genericSupertypes.retainAll(getGenericSupertypes(iterator.next()));
      }
      if (genericSupertypes.isEmpty()) {
        // Interfaces don't have Object as a supertype.
        return Object.class;
      }
      // Remove redundant supertypes.
      Set<Type> canonicalGenericTypes = new LinkedHashSet<>(genericSupertypes);
      for (Type a : genericSupertypes) {
        for (Iterator<Type> remover = canonicalGenericTypes.iterator(); remover.hasNext();) {
          Type b = remover.next();
          if (!b.equals(a)) {
            Class<?> aRaw = getRawType(a);
            Class<?> bRaw = getRawType(b);
            if (bRaw.isAssignableFrom(aRaw)) {
              remover.remove();
            }
          }
        }
      }
      Type genericIntersectionType = newIntersectionType(canonicalGenericTypes);
      TypeMappingsBuilder builder = new TypeMappingsBuilder();
      builder.isForLeastUpperBound = true;
      for (Type genericType : canonicalGenericTypes) {
        if (genericType instanceof Class) {
          continue;
        }
        if (mappings.lubTypes.contains(genericType)) {
          // We've already seen this generic type and we're already trying to
          // resolve it.  It's probably Comparable<T> and we're probably going
          // to end up with some type that looks like
          // "Foo & Comparable<? extends Foo & Comparable<?>>".
          // This logic here is responsible for inserting that last
          // "Comparable<?>" with a "?" for its type argument.
          //
          // TODO: It's not clear this shouldn't result in an infinite type.
          //       From the JLS on "least upper bound" (lub) computation:
          //         "It is possible that the lub() function yields an
          //         infinite type. This is permissible, and a compiler for
          //         the Java programming language must recognize such
          //         situations and represent them appropriately using cyclic
          //         data structures."
          if (genericType instanceof ParameterizedType) {
            Type ownerType = ((ParameterizedType) genericType).getOwnerType();
            Class<?> rawType = (Class<?>) ((ParameterizedType) genericType).getRawType();
            TypeVariable<?>[] typeParameters = rawType.getTypeParameters();
            Type[] typeArguments = new Type[typeParameters.length];
            for (int i = 0; i < typeParameters.length; i++) {
              typeArguments[i] =
                  new Types.WildcardTypeImpl(
                      new Type[0],
                      typeParameters[i].getBounds());
            }
            ParameterizedType nonRecursiveType =
                Types.newParameterizedTypeWithOwner(ownerType, rawType, typeArguments);
            builder.populateTypeMappings(genericType, nonRecursiveType, TypeRelationship.SUBTYPE);
          } else {
            // TODO: Write a test that reaches this point or figure out why that's impossible.
            throw new AssertionError("didn't plan for this");
          }
          continue;
        }
        for (Type type : subtypes) {
          builder.populateTypeMappings(genericType, type, TypeRelationship.SUBTYPE);
        }
      }
      TypeMappings newMappings = builder.mappings;
      newMappings.lubTypes.addAll(mappings.lubTypes);
      newMappings.lubTypes.addAll(canonicalGenericTypes);
      TypeResolver resolver = new TypeResolver().where(newMappings);
      return resolver.resolveType(genericIntersectionType);
    }
  }

  /**
   * Mappings from {@link TypeVariable}s to {@linkplain TypeVariableConstraints constraints} for
   * those type variables.
   */
  private static final class TypeMappings {
    private final Map<TypeVariableKey, TypeVariableConstraints> map = new HashMap<>();

    /**
     * Used to prevent infinite recursion in least upper bound (lub) computation.
     */
    private final Set<Type> lubTypes = new HashSet<>();

    @Override
    public String toString() {
      return map.values().toString();
    }

    /**
     * Resolves the type of the specified type variable.  Returns {@code null} if nothing is known
     * about the type variable.
     */
    @Nullable Type resolve(TypeVariableKey key) {
      checkNotNull(key);
      TypeVariableConstraints constraints = map.get(key);
      return (constraints == null) ? null : constraints.resolve(this);
    }

    /**
     * Adds the specified mapping to this instance.
     *
     * @param key the type variable
     * @param value the type to associate with the type variable
     * @param relationship the relationship between the type variable and the specified type
     * @return {@code true} if this instance was modified as a result of the call
     */
    boolean put(TypeVariableKey key, Type value, TypeRelationship relationship) {
      checkNotNull(key);
      checkNotNull(value);
      checkNotNull(relationship);
      //System.out.println("put " + key + " " + value + " " + relationship);
      TypeVariableConstraints constraints = map.get(key);
      if (constraints == null) {
        constraints = new TypeVariableConstraints(key);
        map.put(key, constraints);
      }
      switch (relationship) {
        case EQUAL:
          return constraints.setExactType(value);
        case INTERSECTING:
          return constraints.addIntersectingType(value);
        case SUPERTYPE:
          return constraints.addSupertype(value);
        case SUBTYPE:
          return constraints.addSubtype(value);
      }
      throw new AssertionError("Unknown type relationship: " + relationship);
    }

    /** Adds all of the specified mappings to this instance. */
    private void putAll(TypeMappings that) {
      checkNotNull(that);
      for (Map.Entry<TypeVariableKey, TypeVariableConstraints> entry : that.map.entrySet()) {
        TypeVariableKey key = entry.getKey();
        TypeVariableConstraints thoseConstraints = entry.getValue();
        TypeVariableConstraints theseConstraints = this.map.get(key);
        if (theseConstraints == null) {
          theseConstraints = new TypeVariableConstraints(key);
          this.map.put(key, theseConstraints);
        }
        theseConstraints.addAll(thoseConstraints);
      }
      this.lubTypes.addAll(that.lubTypes);
    }

    /**
     * Returns the combination of the specified mappings.
     *
     * @throws IllegalArgumentException if the specified mappings are incompatible with each other
     */
    static TypeMappings combine(TypeMappings a, TypeMappings b) {
      checkNotNull(a);
      checkNotNull(b);
      TypeMappings combined = new TypeMappings();
      combined.putAll(a);
      combined.putAll(b);
      return combined;
    }

    /**
     * Returns the mappings that result from declaring {@code actual} as a subtype of {@code
     * formal}.  For example, {@code actual} may be the actual type of a variable referring to an
     * instance of a class while {@code formal} is the declared generic type of that class.
     *
     * @throws IllegalArgumentException if the type declaration is invalid
     */
    static TypeMappings fromTypeDeclaration(Type formal, Type actual) {
      checkNotNull(formal);
      checkNotNull(actual);
      TypeMappingsBuilder builder = new TypeMappingsBuilder();
      builder.populateTypeMappings(formal, actual, TypeRelationship.SUBTYPE);
      return builder.mappings;
    }
  }

  private static final class TypeMappingsBuilder {
    final TypeMappings mappings = new TypeMappings();
    boolean isForLeastUpperBound;

    void populateTypeMappings(Type from, Type to, TypeRelationship relationship) {
      //System.out.println("set " + from.getTypeName() + " " + to.getTypeName() + " " + relationship);
      checkNotNull(from);
      checkNotNull(to);
      checkNotNull(relationship);
      if (from.equals(to)) {
        return;
      }
      if (from instanceof TypeVariable) {
        visitTypeVariable((TypeVariable<?>) from, to, relationship);
      } else if (from instanceof WildcardType) {
        visitWildcardType((WildcardType) from, to, relationship);
      } else if (from instanceof ParameterizedType) {
        visitParameterizedType((ParameterizedType) from, to, relationship);
      } else if (from instanceof GenericArrayType) {
        visitGenericArrayType((GenericArrayType) from, to, relationship);
      } else if (from instanceof Class) {
        visitClass((Class<?>) from, to, relationship);
      } else {
        throw new AssertionError("Unknown type: " + from);
      }
    }

    private void visitTypeVariable(
        TypeVariable<?> typeVariable, Type to, TypeRelationship relationship) {
      TypeVariableKey key = new TypeVariableKey(typeVariable);
      if (!mappings.put(key, to, relationship)) {
        return;
      }
      for (Type bound : typeVariable.getBounds()) {
        if (bound instanceof Class) {
          // The Object.class bound is redundant, and it causes problems when binding T[] to int[].
          if (!bound.equals(Object.class)) {
            mappings.put(key, bound, TypeRelationship.SUPERTYPE);
          }
        } else {
          TypeRelationship boundRelationship = relationshipForBound(relationship);
          if (bound instanceof TypeVariable
              || !boundRelationship.equals(TypeRelationship.INTERSECTING)) {
            populateTypeMappings(bound, to, boundRelationship);
          }
        }
      }
    }

    private void visitWildcardType(
        WildcardType fromWildcardType, Type to, TypeRelationship relationship) {
      Type[] fromLowerBounds = fromWildcardType.getLowerBounds();
      Type[] fromUpperBounds = fromWildcardType.getUpperBounds();
      if (!(to instanceof WildcardType)) {
        for (Type fromLowerBound : fromLowerBounds) {
          if (!(fromLowerBound instanceof Class)) {
            populateTypeMappings(fromLowerBound, to, TypeRelationship.SUPERTYPE);
          }
        }
        for (Type fromUpperBound : fromUpperBounds) {
          if (!(fromUpperBound instanceof Class)) {
            populateTypeMappings(fromUpperBound, to, TypeRelationship.SUBTYPE);
          }
        }
        return;
      }
      WildcardType toWildcardType = (WildcardType) to;
      Type[] toLowerBounds = toWildcardType.getLowerBounds();
      Type[] toUpperBounds = toWildcardType.getUpperBounds();
      if (fromLowerBounds.length != toLowerBounds.length
          || fromUpperBounds.length != toUpperBounds.length) {
        for (Type fromLowerBound : fromLowerBounds) {
          populateTypeMappings(fromLowerBound, to, TypeRelationship.SUPERTYPE);
        }
        for (Type fromUpperBound : fromUpperBounds) {
          populateTypeMappings(fromUpperBound, to, TypeRelationship.SUBTYPE);
        }
        return;
      }
      for (Type fromLowerBound : fromLowerBounds) {
        for (Type toLowerBound : toLowerBounds) {
          populateTypeMappings(fromLowerBound, toLowerBound, TypeRelationship.SUPERTYPE);
        }
      }
      for (Type fromUpperBound : fromUpperBounds) {
        for (Type toUpperBound : toUpperBounds) {
          populateTypeMappings(fromUpperBound, toUpperBound, TypeRelationship.SUBTYPE);
        }
      }
    }

    private void visitParameterizedType(
        ParameterizedType fromParameterizedType, Type to, TypeRelationship relationship) {
      if (to instanceof WildcardType) {
        WildcardType toWildcardType = (WildcardType) to;
        for (Type toLowerBound : toWildcardType.getLowerBounds()) {
          populateTypeMappings(fromParameterizedType, toLowerBound, TypeRelationship.SUBTYPE);
        }
        for (Type toUpperBound : toWildcardType.getUpperBounds()) {
          if (!toUpperBound.equals(Object.class)) {
            populateTypeMappings(fromParameterizedType, toUpperBound, TypeRelationship.SUPERTYPE);
          }
        }
        return; // Okay to say Foo<A> is <?>
      }
      if (relationship.equals(TypeRelationship.EQUAL)) {
        checkArgument(
            to instanceof ParameterizedType,
            "%s is not a parameterized type",
            to.getTypeName());
        ParameterizedType toParameterizedType = (ParameterizedType) to;
        Class<?> formalRawType = (Class<?>) fromParameterizedType.getRawType();
        Class<?> actualRawType = (Class<?>) toParameterizedType.getRawType();
        checkArgument(
            formalRawType.equals(actualRawType),
            "Inconsistent raw type: %s vs. %s",
            fromParameterizedType.getTypeName(),
            toParameterizedType.getTypeName());
      }
      ParameterizedType toParameterizedType = getParameterizedSupertype(to, fromParameterizedType);
      Type[] fromArgs = fromParameterizedType.getActualTypeArguments();
      Type[] toArgs = toParameterizedType.getActualTypeArguments();
      checkArgument(
          fromArgs.length == toArgs.length,
          "%s not compatible with %s",
          fromParameterizedType,
          toParameterizedType);
      Type fromOwnerType = fromParameterizedType.getOwnerType();
      Type toOwnerType = toParameterizedType.getOwnerType();
      if (fromOwnerType != null && toOwnerType != null) {
        // TODO: Add tests that exercise this behavior.
        populateTypeMappings(fromOwnerType, toOwnerType, relationship);
      }
      for (int i = 0; i < fromArgs.length; i++) {
        Type fromArg = fromArgs[i];
        Type toArg = toArgs[i];
        // FIXME: These two checks are incompatible with preexisting behavior such as this:
        //        https://github.com/google/guava/commit/7a3389afb9f97fe846c69e46d106ac1dbf59f51d
        //        But they seem important when resolving method type parameters from arguments.
        //        Add `boolean isForMethodArgument` flag to enable these checks,
        //        and set that flag to true when the caller is TypeToken.method(...)?
        if (fromArg instanceof WildcardType) {
          checkArgument(
              toArg instanceof WildcardType
                  || !relationship.equals(TypeRelationship.EQUAL),
              "%s not compatible with %s because parameter %s at index %s "
                  + "is not compatible with argument %s",
              fromParameterizedType.getTypeName(),
              to.getTypeName(),
              fromArg.getTypeName(),
              i,
              toArg.getTypeName());
        }
        if (toArg instanceof WildcardType) {
          checkArgument(
              fromArg instanceof Class
                  || fromArg instanceof WildcardType
                  || fromArg instanceof TypeVariable,
              "%s not compatible with %s because parameter %s at index %s "
                  + "is not compatible with argument capture of %s",
              fromParameterizedType.getTypeName(),
              to.getTypeName(),
              fromArg.getTypeName(),
              i,
              toArg.getTypeName());
        }
        TypeRelationship argRelationship =
            isForLeastUpperBound ? TypeRelationship.SUBTYPE : TypeRelationship.EQUAL;
        populateTypeMappings(fromArg, toArg, argRelationship);
      }
    }

    private void visitGenericArrayType(GenericArrayType fromArrayType, Type to, TypeRelationship relationship) {
      if (to instanceof WildcardType) {
        WildcardType toWildcardType = (WildcardType) to;
        for (Type toLowerBound : toWildcardType.getLowerBounds()) {
          populateTypeMappings(fromArrayType, toLowerBound, TypeRelationship.SUBTYPE);
        }
        for (Type toUpperBound : toWildcardType.getUpperBounds()) {
          if (!toUpperBound.equals(Object.class)) {
            populateTypeMappings(fromArrayType, toUpperBound, TypeRelationship.SUPERTYPE);
          }
        }
        return; // Okay to say A[] is <?>
      }
      Type componentType = Types.getComponentType(to);
      checkArgument(componentType != null, "%s is not an array type.", to);
      populateTypeMappings(fromArrayType.getGenericComponentType(), componentType, relationship);
    }

    private void visitClass(Class<?> fromClass, Type to, TypeRelationship relationship) {
      if (to instanceof WildcardType) {
        WildcardType toWildcardType = (WildcardType) to;
        for (Type toLowerBound : toWildcardType.getLowerBounds()) {
          checkArgument(
              intersecting(fromClass, toLowerBound),
              "Type %s is incompatible with lower bound %s of wildcard type %s",
              fromClass.getTypeName(),
              toLowerBound.getTypeName(),
              to.getTypeName());
        }
        for (Type toUpperBound : toWildcardType.getUpperBounds()) {
          checkArgument(
              isSubtype(fromClass, toUpperBound),
              "Type %s is incompatible with upper bound %s of wildcard type %s",
              fromClass.getTypeName(),
              toUpperBound.getTypeName(),
              to.getTypeName());
        }
        return; // Okay to say Foo is <?>
      }
      // Can't map from a raw class to anything other than itself or a wildcard.
      // You can't say "assuming String is Integer".
      // And we don't support "assuming String is T"; user has to say "assuming T is String".
      throw new IllegalArgumentException("No type mapping from " + fromClass + " to " + to);
    }

    private static TypeRelationship relationshipForBound(TypeRelationship relationship) {
      switch (relationship) {
        case EQUAL:
          // from=B
          // to=Integer
          // relationship=EQUAL
          // B == Integer
          //
          // formalBound=A
          // B extends A
          // -----> Integer extends A
          // -----> Integer is a subtype of A
          //
          // (fallthrough)
          //
        case SUBTYPE:
          // from=U
          // to=Integer
          // relationship=SUBTYPE
          // "Integer is something that extends U"
          // "Integer is a subtype of U"
          //
          // formalBound=T
          // "U extends T"
          // ----> "Integer is a subtype of T"
          //
          return TypeRelationship.SUBTYPE;
        case SUPERTYPE:
          // from=U
          // to=Number
          // relationship=SUPERTYPE
          // "Number is a supertype of U"
          // "U extends Number"
          //
          // bound=T
          // "U extends T"
          // "T is a supertype of U"
          // anything we can conclude about T w.r.t Number?
          // it can't be totally unrelated to Number, right?
          //   T=Integer works, T=Object works?, T=String doesn't work, right?
          //         yes              yes          right
          // so T intersects Number
          //
          // (fallthrough)
          //
        case INTERSECTING:
          //
          // from=A
          // to=Number
          // relationship=INTERSECTING
          // "a type may extend both A and Number"
          // "A intersects Number"
          //
          // bound=B
          // A extends B
          //
          // Could B be String?
          // no
          // could B be Object?
          // yes
          // could B be Integer?
          // yes
          //
          return TypeRelationship.INTERSECTING;
      }
      throw new AssertionError("Unknown type relationship: " + relationship);
    }
  }

  /**
   * Returns a type representing the intersection of all of the specified types.  If there is only
   * one type in the specified set, then that type is returned.
   *
   * @throws IllegalArgumentException if the specified set is empty
   */
  private static Type newIntersectionType(Set<Type> types) {
    checkNotNull(types);
    checkArgument(!types.isEmpty());
    if (types.size() == 1) {
      return types.iterator().next();
    }
    if (true) {
      // TODO: This is generally preferred, but it runs into issues with our wildcard-to-wildcard
      //       TypeMappingsBuilder implementation.  That implementation has other issues, so once we
      //       fix those, try re-enabling this block again and see if things work.
      return new Types.WildcardTypeImpl(new Type[0], types.toArray(new Type[0]));
    }
    Type[] bounds = types.toArray(new Type[0]);
    StringBuilder name = new StringBuilder(bounds[0].getTypeName());
    for (int i = 1; i < bounds.length; i++) {
      name.append(" & ").append(bounds[i].getTypeName());
    }
    return Types.newArtificialTypeVariable(
        TypeResolver.class,
        name.toString(),
        bounds);
  }

  /**
   * Returns {@code true} if {@code a} and {@code b} are intersecting types.  Two types are said to
   * intersect when it is possible for some type to be a subtype of both of those types.
   *
   * <p>"Intersecting" is the antonym of "disjoint".  Two types are intersecting when they are not
   * disjoint, and two types are disjoint when they are not intersecting.
   *
   * <p>Note that this algorithm does not consider the {@code final} keyword of classes or other
   * attributes that would prevent classes from being extended.  In that way, this algorithm errs on
   * the side of assuming that types are intersecting.
   */
  private static boolean intersecting(Type a, Type b) {
    checkNotNull(a);
    checkNotNull(b);
    return (a instanceof Class && ((Class<?>) a).isInterface())
        || (b instanceof Class && ((Class<?>) b).isInterface())
        || isSubtype(a, b)
        || isSubtype(b, a);
  }

  /**
   * Returns the parameterized supertype of {@code subtype} having the same raw type as {@code
   * supertype}.
   *
   * @throws IllegalArgumentException if no such supertype exists
   */
  private static ParameterizedType getParameterizedSupertype(
      Type subtype, ParameterizedType supertype) {
    checkNotNull(subtype);
    checkNotNull(supertype);
    for (Class<?> rawType : getRawTypes(subtype)) {
      if (rawType.equals(supertype.getRawType())) {
        Type genericType = getGenericType(rawType);
        return (ParameterizedType) covariantly(subtype).resolveType(genericType);
      }
    }
    throw new IllegalArgumentException(
        supertype.getTypeName() + " is not a supertype of " + subtype.getTypeName());
  }

  /**
   * Returns the generic type declarations of the specified type, including the generic type
   * declarations of its supertypes and interfaces.
   */
  private static Set<Type> getGenericSupertypes(Type type) {
    checkNotNull(type);
    ImmutableSet.Builder<Type> builder = ImmutableSet.builder();
    for (Class<?> rawType : getRawTypes(type)) {
      Type genericType = getGenericType(rawType);
      builder.add(genericType);
    }
    Type componentType = Types.getComponentType(type);
    if (componentType != null && !isPrimitive(componentType)) {
      builder.add(GenericArrayTypeHolder.GENERIC_ARRAY_TYPE);
    }
    return builder.build();
  }

  private static final class GenericArrayTypeHolder {
    private static <E> E[] genericArray() { throw new UnsupportedOperationException(); }
    static final Type GENERIC_ARRAY_TYPE;
    static {
      Method method;
      try {
        method = GenericArrayTypeHolder.class.getDeclaredMethod("genericArray");
      } catch (NoSuchMethodException e) {
        throw new LinkageError(e.getMessage(), e);
      }
      GENERIC_ARRAY_TYPE = method.getGenericReturnType();
    }
  }

  /** Returns {@code true} if the specified type is a primitive type. */
  private static boolean isPrimitive(Type type) {
    checkNotNull(type);
    return (type instanceof Class) && ((Class<?>) type).isPrimitive();
  }

  /** Returns the raw type of the specified type. */
  private static Class<?> getRawType(Type type) {
    checkNotNull(type);
    return getRawTypes(type).iterator().next();
  }

  /**
   * Returns the raw types of the specified type, including the raw types of its supertypes and
   * interfaces.
   */
  static Set<Class<?>> getRawTypes(Type type) {
    checkNotNull(type);
    final ImmutableSet.Builder<Class<?>> builder = ImmutableSet.builder();
    new TypeVisitor() {
      @Override
      void visitTypeVariable(TypeVariable<?> t) {
        visit(t.getBounds());
      }

      @Override
      void visitWildcardType(WildcardType t) {
        visit(t.getUpperBounds());
      }

      @Override
      void visitParameterizedType(ParameterizedType t) {
        visit(t.getRawType());
      }

      @Override
      void visitClass(Class<?> t) {
        builder.add(t);
        visit(t.getInterfaces());
        visit(t.getSuperclass());
      }

      @Override
      void visitGenericArrayType(GenericArrayType t) {
        visit(Types.getArrayClass(getRawType(t.getGenericComponentType())));
      }
    }.visit(type);
    return builder.build();
  }

  /** Returns the generic type declaration of the specified class. */
  private static Type getGenericType(Class<?> clazz) {
    checkNotNull(clazz);
    if (clazz.isArray()) {
      return Types.newArrayType(getGenericType(clazz.getComponentType()));
    }
    TypeVariable<?>[] typeParameters = clazz.getTypeParameters();
    Type ownerType =
        clazz.isMemberClass() && !Modifier.isStatic(clazz.getModifiers())
            ? getGenericType(clazz.getEnclosingClass())
            : null;
    if ((typeParameters.length > 0)
        || ((ownerType != null) && ownerType != clazz.getEnclosingClass())) {
      return Types.newParameterizedTypeWithOwner(ownerType, clazz, typeParameters);
    } else {
      return clazz;
    }
  }

  /** Returns {@code true} if {@code a} is a subtype of {@code b}. */
  private static boolean isSubtype(Type a, Type b) {
    checkNotNull(a);
    checkNotNull(b);
    // TODO: Avoid depending on TypeToken because TypeToken depends on TypeResolver.
    return TypeToken.of(a).isSubtypeOf(b);
  }
}
