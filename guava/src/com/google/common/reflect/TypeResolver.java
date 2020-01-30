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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
    return new TypeResolver().where(TypeMappingIntrospector.getConstraints(contextType));
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
    return new TypeResolver().where(TypeMappingIntrospector.getConstraints(invariantContext));
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
    checkNotNull(formal);
    checkNotNull(actual);
    return where(Constraints.fromTypeArgument(formal, actual));
  }

  // TODO: Figure out if this method is really necessary.
  TypeResolver withTypeDeclaration(Type formal, Type actual) {
    checkNotNull(formal);
    checkNotNull(actual);
    return where(Constraints.fromTypeDeclaration(formal, actual));
  }

  private TypeResolver where(Constraints constraints) {
    checkNotNull(constraints);
    return new TypeResolver(typeTable.where(constraints));
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
    private final Constraints constraints;

    TypeTable() {
      this.constraints = new Constraints();
    }

    TypeTable(Constraints constraints) {
      this.constraints = constraints;
    }

    /** Returns a new {@code TypeTable} with additional constraints. */
    final TypeTable where(Constraints moreConstraints) {
      return new TypeTable(Constraints.combine(constraints, moreConstraints));
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
      Type type = constraints.get(new TypeVariableKey(var));
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
    static Constraints getConstraints(Type contextType) {
      checkNotNull(contextType);
      TypeMappingIntrospector introspector = new TypeMappingIntrospector();
      introspector.visit(contextType);
      Constraints constraints = new Constraints();
      for (Map.Entry<TypeVariableKey, Type> entry : introspector.mappings.entrySet()) {
        constraints.put(entry.getKey(), ConstraintKind.EQUAL_TO, entry.getValue());
      }
      return constraints;
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

  /** A kind of constraint imposed on a {@link TypeVariable}. */
  private enum ConstraintKind {
    /** The variable's type must be equal to the specified type. */
    EQUAL_TO,

    /**
     * The variable's type must be related to the specified type.  Two types are
     * related when it is possible for a third type to subtype both of those two
     * types.
     */
    // TODO: Come up with a better name for this.  NOT_DISJOINT?
    RELATED_TO,

    /** The variable's type must be a subtype of the specified type. */
    SUBTYPE_OF,

    /** The variable's type must be a supertype of the specified type. */
    SUPERTYPE_OF
  }

  /** Constraints for one {@link TypeVariable}. */
  private static final class Constraint {
    private final TypeVariableKey key;
    private @Nullable Type equalToType;
    private @Nullable Set<Type> relatedToTypes;
    private @Nullable Set<Type> subtypeOfTypes;
    private @Nullable Set<Type> supertypeOfTypes;

    @Override
    public String toString() {
      return MoreObjects
          .toStringHelper(this)
          .omitNullValues()
          .add("key", key + " (from " + key.var.getGenericDeclaration() + ")")
          .add("equalToType", equalToType)
          .add("relatedToTypes", relatedToTypes)
          .add("subtypeOfTypes", subtypeOfTypes)
          .add("supertypeOfTypes", supertypeOfTypes)
          .toString();
    }

    Constraint(TypeVariableKey key) {
      this.key = checkNotNull(key);
    }

    /** Deduces the type of this type variable from its constraints. */
    Type get(Set<Type> recursiveTypes) {
      if (equalToType != null) {
        return equalToType;
      }
      if (supertypeOfTypes != null) {
        return leastUpperBound(supertypeOfTypes, recursiveTypes);
      }
      if (subtypeOfTypes != null) {
        return greatestLowerBound(subtypeOfTypes);
      }
      if (relatedToTypes != null) {
        return Object.class;
      }
      // This should never occur because whenever we create an instance, we immediately call one of
      // its setters, meaning that at least one of these fields will be non-null.
      throw new IllegalStateException();
    }

    /**
     * Adds the constraint that this type variable is equal to the specified type.
     *
     * @throws IllegalArgumentException if this new constraint is incompatible with existing
     *         constraints
     */
    void setEqualTo(Type type) {
      checkNotNull(type);
      checkNotMappedToSelf(type);
      checkEqualToAndRelatedTo(type, relatedToTypes);
      checkEqualToAndSubtypeOf(type, subtypeOfTypes);
      checkEqualToAndSupertypeOf(type, supertypeOfTypes);
      if (equalToType == null) {
        equalToType = type;
        return;
      }
      checkArgument(
          equalToType.equals(type),
          "Type %s must be exactly %s, so it cannot be %s",
          key,
          equalToType.getTypeName(),
          type.getTypeName());
    }

    /**
     * Adds the constraint that this type variable is related to the specified type.  Two types are
     * related when it is possible to subtype both types.
     *
     * @throws IllegalArgumentException if this new constraint is incompatible with existing
     *         constraints
     */
    void setRelatedTo(Type type) {
      checkNotNull(type);
      checkNotMappedToSelf(type);
      // No need to re-check the other bounds.
      ImmutableSet<Type> newRelatedTo = ImmutableSet.of(type);
      checkEqualToAndRelatedTo(equalToType, newRelatedTo);
      checkRelatedToAndSubtypeOf(newRelatedTo, subtypeOfTypes);
      checkRelatedToAndSupertypeOf(newRelatedTo, supertypeOfTypes);
      if (relatedToTypes != null) {
        for (Type existing : relatedToTypes) {
          // TODO: Write a test that throws here.
          checkArgument(
              isPossibleToSubtypeBoth(existing, type),
              "Type %s must be related to %s, so it cannot be related to %s",
              key,
              existing.getTypeName(),
              type.getTypeName());
        }
      }
      if (relatedToTypes == null) {
        relatedToTypes = new LinkedHashSet<>();
      }
      relatedToTypes.add(type);
    }

    /**
     * Adds the constraint that this type variable is a subtype of the specified type.
     *
     * @throws IllegalArgumentException if this new constraint is incompatible with existing
     *         constraints
     */
    void setSubtypeOf(Type type) {
      checkNotNull(type);
      checkNotMappedToSelf(type);
      // No need to re-check the other bounds.
      ImmutableSet<Type> newSubtypeOf = ImmutableSet.of(type);
      checkEqualToAndSubtypeOf(equalToType, newSubtypeOf);
      checkRelatedToAndSubtypeOf(relatedToTypes, newSubtypeOf);
      checkSubtypeOfAndSupertypeOf(newSubtypeOf, supertypeOfTypes);
      if (subtypeOfTypes != null) {
        for (Type existing : subtypeOfTypes) {
          checkArgument(
              isPossibleToSubtypeBoth(existing, type),
              "Type %s must be a subtype of%s, so it cannot be a subtype of %s",
              key,
              existing.getTypeName(),
              type.getTypeName());
        }
      }
      if (subtypeOfTypes == null) {
        subtypeOfTypes = new LinkedHashSet<>();
      }
      subtypeOfTypes.add(type);
    }

    /**
     * Adds the constraint that this type variable is a supertype of the specified type.
     *
     * @throws IllegalArgumentException if this new constraint is incompatible with existing
     *         constraints
     */
    void setSupertypeOf(Type type) {
      checkNotNull(type);
      checkNotMappedToSelf(type);
      // No need to re-check the other bounds.
      ImmutableSet<Type> newSupertypeOf = ImmutableSet.of(type);
      checkEqualToAndSupertypeOf(equalToType, newSupertypeOf);
      checkRelatedToAndSupertypeOf(relatedToTypes, newSupertypeOf);
      checkSubtypeOfAndSupertypeOf(subtypeOfTypes, newSupertypeOf);
      if (supertypeOfTypes == null) {
        supertypeOfTypes = new LinkedHashSet<>();
      }
      supertypeOfTypes.add(type);
    }

    /**
     * Adds the specified constraints to this type variable.
     *
     * @throws IllegalArgumentException if the new constraints are incompatible with the existing
     *         constraints
     */
    void setAll(Constraint that) {
      checkNotNull(that);
      if (that.equalToType != null) {
        setEqualTo(that.equalToType);
      }
      if (that.relatedToTypes != null) {
        for (Type type : that.relatedToTypes) {
          setRelatedTo(type);
        }
      }
      if (that.subtypeOfTypes != null) {
        for (Type type : that.subtypeOfTypes) {
          setSubtypeOf(type);
        }
      }
      if (that.supertypeOfTypes != null) {
        for (Type type : that.supertypeOfTypes) {
          setSupertypeOf(type);
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
     *         {@code equalToType} and related to all of {@code relatedToTypes}
     */
    private void checkEqualToAndRelatedTo(
        @Nullable Type equalToType,
        @Nullable Set<Type> relatedToTypes) {
      if (equalToType == null || relatedToTypes == null) {
        return;
      }
      for (Type type : relatedToTypes) {
        checkArgument(
            isPossibleToSubtypeBoth(equalToType, type),
            "No type can satisfy the constraints of %s, "
                + "which must be equal to %s and related to %s",
            key,
            equalToType.getTypeName(),
            type.getTypeName());
      }
    }

    /**
     * @throws IllegalArgumentException if it is impossible for any type variable to be equal to
     *         {@code equalToType} and a subtype of all of {@code subtypeOfTypes}
     */
    private void checkEqualToAndSubtypeOf(
        @Nullable Type equalToType,
        @Nullable Set<Type> subtypeOfTypes) {
      if (equalToType == null || subtypeOfTypes == null) {
        return;
      }
      for (Type type : subtypeOfTypes) {
        checkArgument(
            isSubtype(equalToType, type),
            "No type can satisfy the constraints of %s, "
                + "which must be equal to %s and a subtype of %s",
            key,
            equalToType.getTypeName(),
            type.getTypeName());
      }
    }

    /**
     * @throws IllegalArgumentException if it is impossible for any type variable to be equal to
     *         {@code equalToType} and a supertype of all of {@code supertypeOfTypes}
     */
    private void checkEqualToAndSupertypeOf(
        @Nullable Type equalToType,
        @Nullable Set<Type> supertypeOfTypes) {
      if (equalToType == null || supertypeOfTypes == null) {
        return;
      }
      for (Type type : supertypeOfTypes) {
        checkArgument(
            isSubtype(type, equalToType),
            "No type can satisfy the constraints of %s, "
                + "which must be equal to %s and a supertype of %s",
            key,
            equalToType.getTypeName(),
            type.getTypeName());
      }
    }

    /**
     * @throws IllegalArgumentException if it is impossible for any type variable to be related to
     *         all of {@code relatedToType} and a subtype of all of {@code subtypeOfTypes}
     */
    private void checkRelatedToAndSubtypeOf(
        @Nullable Set<Type> relatedToType,
        @Nullable Set<Type> subtypeOfTypes) {
      if (relatedToType == null || subtypeOfTypes == null) {
        return;
      }
      for (Type a : relatedToType) {
        for (Type b : subtypeOfTypes) {
          checkArgument(
              isPossibleToSubtypeBoth(a, b),
              "No type can satisfy the constraints of %s, "
                  + "which must be related to %s and a subtype of %s",
              key,
              a.getTypeName(),
              b.getTypeName());
        }
      }
    }

    /**
     * @throws IllegalArgumentException if it is impossible for any type variable to be related to
     *         all of {@code relatedToTypes} and a supertype of of all of {@code supertypeOfTypes}
     */
    private void checkRelatedToAndSupertypeOf(
        @Nullable Set<Type> relatedToTypes,
        @Nullable Set<Type> supertypeOfTypes) {
      if (relatedToTypes == null || supertypeOfTypes == null) {
        return;
      }
      for (Type a : relatedToTypes) {
        for (Type b : supertypeOfTypes) {
          checkArgument(
              isPossibleToSubtypeBoth(a, b),
              "No type can satisfy the constraints of %s, "
                  + "which must be related to %s and a supertype of %s",
              key,
              a.getTypeName(),
              b.getTypeName());
        }
      }
    }

    /**
     * @throws IllegalArgumentException if it is impossible for any type variable to be a subtype of
     *         all of {@code subtypeOfTypes} and a supertype of of all of {@code supertypeOfTypes}
     */
    private void checkSubtypeOfAndSupertypeOf(
        @Nullable Set<Type> subtypeOfTypes,
        @Nullable Set<Type> supertypeOfTypes) {
      if (subtypeOfTypes == null || supertypeOfTypes == null) {
        return;
      }
      for (Type a : subtypeOfTypes) {
        for (Type b : supertypeOfTypes) {
          checkArgument(
              isSubtype(b, a),
              "No type can satisfy the constraints of %s, "
                  + "which must be a subtype of %s and a supertype of %s",
              key,
              a.getTypeName(),
              b.getTypeName());
        }
      }
    }

    /**
     * Returns a type that is a subtype of all of the specified types.
     *
     * @throws IllegalArgumentException if the set is empty
     */
    private static Type greatestLowerBound(Set<Type> types) {
      checkNotNull(types);
      checkArgument(!types.isEmpty());
      if (types.size() == 1) {
        return types.iterator().next();
      }
      // Remove redundant types.
      Set<Type> canonicalTypes = new LinkedHashSet<>(types);
      for (Type a : types) {
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
     * Returns a type that is a supertype of all of the specified types.
     *
     * <p>See <a href="https://docs.oracle.com/javase/specs/jls/se13/html/jls-4.html#jls-4.10.4"
     * >Least Upper Bound</a>.
     *
     * @param recursiveTypes when this method is invoked recursively, a set of supertypes that must
     *        not be resolved in order to avoid infinite recursion
     * @throws IllegalArgumentException if the set is empty
     */
    private static Type leastUpperBound(Set<Type> types, Set<Type> recursiveTypes) {
      checkNotNull(types);
      checkArgument(!types.isEmpty());
      if (types.size() == 1) {
        return types.iterator().next();
      }
      Iterator<Type> iterator = types.iterator();
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
      ConstraintsBuilder builder = new ConstraintsBuilder();
      builder.isForLeastUpperBound = true;
      for (Type genericType : canonicalGenericTypes) {
        if (genericType instanceof Class) {
          continue;
        }
        if (recursiveTypes.contains(genericType)) {
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
            builder.visit(genericType, ConstraintKind.SUPERTYPE_OF, nonRecursiveType);
          } else {
            // TODO: Write a test that reaches this point or figure out why that's impossible.
            throw new AssertionError("didn't plan for this");
          }
          continue;
        }
        for (Type type : types) {
          builder.visit(genericType, ConstraintKind.SUPERTYPE_OF, type);
        }
      }
      Constraints constraints = builder.constraints;
      constraints.recursiveTypes.addAll(recursiveTypes);
      constraints.recursiveTypes.addAll(canonicalGenericTypes);
      TypeResolver resolver = new TypeResolver().where(constraints);
      return resolver.resolveType(genericIntersectionType);
    }
  }

  /** Constraints for many {@link TypeVariable}s. */
  private static final class Constraints {
    private final Map<TypeVariableKey, Constraint> keyToConstraint = new LinkedHashMap<>();
    private final Set<Type> recursiveTypes = new LinkedHashSet<>();

    @Override
    public String toString() {
      return keyToConstraint.values().toString();
    }

    /**
     * Deduces the type of the specified type variable from its constraints.  Returns {@code null}
     * if nothing is known about the type variable.
     */
    @Nullable Type get(TypeVariableKey key) {
      checkNotNull(key);
      Constraint constraint = keyToConstraint.get(key);
      return (constraint == null) ? null : constraint.get(recursiveTypes);
    }

    /**
     * Adds the specified constraint to this instance.
     *
     * @param key the type variable
     * @param kind the relationship between the type variable and the specified type
     * @param type the type to associated with the type variable
     */
    void put(TypeVariableKey key, ConstraintKind kind, Type type) {
      checkNotNull(key);
      checkNotNull(kind);
      checkNotNull(type);
      Constraint thisConstraint = this.keyToConstraint.get(key);
      if (thisConstraint == null) {
        thisConstraint = new Constraint(key);
        this.keyToConstraint.put(key, thisConstraint);
      }
      switch (kind) {
        case EQUAL_TO:
          thisConstraint.setEqualTo(type);
          return;
        case RELATED_TO:
          thisConstraint.setRelatedTo(type);
          return;
        case SUBTYPE_OF:
          thisConstraint.setSubtypeOf(type);
          return;
        case SUPERTYPE_OF:
          thisConstraint.setSupertypeOf(type);
          return;
      }
      throw new AssertionError("Unknown constraint kind " + kind);
    }

    /** Adds all of the specified constraints to this instance. */
    private void putAll(Constraints that) {
      checkNotNull(that);
      for (Map.Entry<TypeVariableKey, Constraint> entry : that.keyToConstraint.entrySet()) {
        TypeVariableKey key = entry.getKey();
        Constraint thatConstraint = entry.getValue();
        Constraint thisConstraint = this.keyToConstraint.get(key);
        if (thisConstraint == null) {
          thisConstraint = new Constraint(key);
          this.keyToConstraint.put(key, thisConstraint);
        }
        thisConstraint.setAll(thatConstraint);
      }
      this.recursiveTypes.addAll(that.recursiveTypes);
    }

    /**
     * Returns the combination of the specified constraints.
     *
     * @throws IllegalArgumentException if the specified constraints are incompatible with each
     *         other
     */
    static Constraints combine(Constraints a, Constraints b) {
      checkNotNull(a);
      checkNotNull(b);
      Constraints combined = new Constraints();
      combined.putAll(a);
      combined.putAll(b);
      return combined;
    }

    /**
     * Returns the constraints that result from supplying an argument of type {@code actual} in a
     * position that expects {@code formal}.  For example, {@code actual} may be a method argument
     * type while {@code formal} is the declared method parameter type.
     *
     * @throws IllegalArgumentException if the type argument is invalid
     */
    static Constraints fromTypeArgument(Type formal, Type actual) {
      checkNotNull(formal);
      checkNotNull(actual);
      ConstraintsBuilder builder = new ConstraintsBuilder();
      builder.visit(formal, ConstraintKind.SUPERTYPE_OF, actual);
      return builder.constraints;
    }

    /**
     * Returns the constraints that result from declaring {@code actual} as a subtype of {@code
     * formal}.  For example, {@code actual} may be the actual type of a variable containing an
     * instance of a class while {@code formal} is the declared generic type of that class.
     *
     * @throws IllegalArgumentException if the type declaration is invalid
     */
    static Constraints fromTypeDeclaration(Type formal, Type actual) {
      checkNotNull(formal);
      checkNotNull(actual);
      ConstraintsBuilder builder = new ConstraintsBuilder();
      builder.isForTypeDeclaration = true;
      builder.visit(formal, ConstraintKind.SUPERTYPE_OF, actual);
      return builder.constraints;
    }
  }

  private static final class ConstraintsBuilder {
    final Constraints constraints = new Constraints();
    private final Set<Seen> seen = new LinkedHashSet<>();
    boolean isForTypeDeclaration;
    boolean isForLeastUpperBound;

    void visit(Type formal, ConstraintKind kind, Type actual) {
      //System.out.println("set " + formal + " " + kind + " " + actual);
      checkNotNull(formal);
      checkNotNull(kind);
      checkNotNull(actual);
      if (formal.equals(actual)) {
        return;
      }
      if (!seen.add(new Seen(formal, kind, actual))) {
        return;
      }
      if (formal instanceof TypeVariable) {
        visitTypeVariable((TypeVariable<?>) formal, kind, actual);
      } else if (formal instanceof WildcardType) {
        visitWildcardType((WildcardType) formal, kind, actual);
      } else if (formal instanceof ParameterizedType) {
        visitParameterizedType((ParameterizedType) formal, kind, actual);
      } else if (formal instanceof GenericArrayType) {
        visitGenericArrayType((GenericArrayType) formal, kind, actual);
      } else if (formal instanceof Class) {
        visitClass((Class<?>) formal, kind, actual);
      } else {
        throw new AssertionError("Unknown type: " + formal);
      }
    }

    private void visitTypeVariable(TypeVariable<?> formal, ConstraintKind kind, Type actual) {
      TypeVariableKey key = new TypeVariableKey(formal);
      constraints.put(key, kind, actual);
      for (Type formalBound : formal.getBounds()) {
        if (formalBound instanceof Class) {
          // The Object.class bound is redundant, and it causes problems
          // when binding T[] to int[].
          if (!formalBound.equals(Object.class)) {
            constraints.put(key, ConstraintKind.SUBTYPE_OF, formalBound);
          }
        } else {
          ConstraintKind kindForBound = constraintKindForBound(kind);
          if (formalBound instanceof TypeVariable
              || !kindForBound.equals(ConstraintKind.RELATED_TO)) {
            visit(formalBound, kindForBound, actual);
          }
        }
      }
    }

    private void visitWildcardType(WildcardType formal, ConstraintKind kind, Type actual) {
      if (!(actual instanceof WildcardType)) {
        for (Type formalLowerBound : formal.getLowerBounds()) {
          if (!(formalLowerBound instanceof Class)) {
            visit(formalLowerBound, ConstraintKind.SUBTYPE_OF, actual);
          }
        }
        for (Type formalUpperBound : formal.getUpperBounds()) {
          if (!(formalUpperBound instanceof Class)) {
            visit(formalUpperBound, ConstraintKind.SUPERTYPE_OF, actual);
          }
        }
        return;
      }
      WildcardType actualWildcardType = (WildcardType) actual;
      Type[] formalUpperBounds = formal.getUpperBounds();
      Type[] actualUpperBounds = actualWildcardType.getUpperBounds();
      Type[] formalLowerBounds = formal.getLowerBounds();
      Type[] actualLowerBounds = actualWildcardType.getLowerBounds();
      checkArgument(
          formalUpperBounds.length == actualUpperBounds.length
              && formalLowerBounds.length == actualLowerBounds.length,
          "Incompatible type: %s vs. %s",
          formal,
          actual);
      for (int i = 0; i < formalLowerBounds.length; i++) {
        visit(formalLowerBounds[i], ConstraintKind.SUBTYPE_OF, actualLowerBounds[i]);
      }
      for (int i = 0; i < formalUpperBounds.length; i++) {
        visit(formalUpperBounds[i], ConstraintKind.SUPERTYPE_OF, actualUpperBounds[i]);
      }
    }

    private void visitParameterizedType(ParameterizedType formal, ConstraintKind kind, Type actual) {
      if (actual instanceof WildcardType) {
        WildcardType actualWildcardType = (WildcardType) actual;
        Type[] actualLowerBounds = actualWildcardType.getLowerBounds();
        Type[] actualUpperBounds = actualWildcardType.getUpperBounds();
        for (Type actualLowerBound : actualLowerBounds) {
          visit(formal, ConstraintKind.SUPERTYPE_OF, actualLowerBound);
        }
        for (Type actualUpperBound : actualUpperBounds) {
          if (!actualUpperBound.equals(Object.class)) {
            visit(formal, ConstraintKind.SUBTYPE_OF, actualUpperBound);
          }
        }
        return; // Okay to say Foo<A> is <?>
      }
      if (actual instanceof Class && formal.getRawType().equals(Class.class)) {
        // formal=Class<K extends Enum<K>>, actual=TimeUnit
        visit(formal.getActualTypeArguments()[0], ConstraintKind.EQUAL_TO, actual);
        return;
      }
      ParameterizedType actualParameterizedType;
      if (actual instanceof Class) {
        // formal=Comparable<? super T>, actual=Integer
        actualParameterizedType = getParameterizedSupertype((Class<?>) actual, formal);
      } else {
        checkArgument(
            actual instanceof ParameterizedType,
            "%s is not a parameterized type",
            actual.getTypeName());
        actualParameterizedType = (ParameterizedType) actual;
      }
      Class<?> formalRawType = (Class<?>) formal.getRawType();
      Class<?> actualRawType = (Class<?>) actualParameterizedType.getRawType();
      if (kind.equals(ConstraintKind.EQUAL_TO)) {
        checkArgument(
            formalRawType.equals(actualRawType),
            "Inconsistent raw type: %s vs. %s",
            formal.getTypeName(),
            actualParameterizedType.getTypeName());
      } else {
        checkArgument(
            formalRawType.isAssignableFrom(actualRawType),
            "Inconsistent raw type: %s vs. %s",
            formal.getTypeName(),
            actualParameterizedType.getTypeName());
      }
      Type[] formalArguments = formal.getActualTypeArguments();
      Type[] actualArguments = actualParameterizedType.getActualTypeArguments();
      checkArgument(
          formalArguments.length == actualArguments.length,
          "%s not compatible with %s",
          formal,
          actualParameterizedType);
      Type formalOwnerType = formal.getOwnerType();
      Type actualOwnerType = actualParameterizedType.getOwnerType();
      if (formalOwnerType != null && actualOwnerType != null) {
        visit(formalOwnerType, kind, actualOwnerType);
      }
      for (int i = 0; i < formalArguments.length; i++) {
        Type formalArgument = formalArguments[i];
        Type actualArgument = actualArguments[i];
        if (formalArgument instanceof TypeVariable) {
          checkArgument(
              !(actualArgument instanceof WildcardType)
                  || !kind.equals(ConstraintKind.EQUAL_TO)
                  || isForTypeDeclaration,
              "%s not compatible with %s because parameter %s at index %s "
                  + "is not compatible with argument %s",
              formal.getTypeName(),
              actual.getTypeName(),
              formalArgument.getTypeName(),
              i,
              actualArgument.getTypeName());
        }
        if (formalArgument instanceof WildcardType) {
          checkArgument(
              actualArgument instanceof WildcardType
                  || !kind.equals(ConstraintKind.EQUAL_TO),
              "%s not compatible with %s because parameter %s at index %s "
                  + "is not compatible with argument %s",
              formal.getTypeName(),
              actual.getTypeName(),
              formalArgument.getTypeName(),
              i,
              actualArgument.getTypeName());
        }
        if (actualArgument instanceof WildcardType) {
          checkArgument(
              formalArgument instanceof Class
                  || formalArgument instanceof WildcardType
                  || formalArgument instanceof TypeVariable,
              "%s not compatible with %s because parameter %s at index %s "
                  + "is not compatible with argument capture of %s",
              formal.getTypeName(),
              actual.getTypeName(),
              formalArgument.getTypeName(),
              i,
              actualArgument.getTypeName());
        }
        visit(
            formalArgument,
            isForLeastUpperBound ? ConstraintKind.SUPERTYPE_OF : ConstraintKind.EQUAL_TO,
            actualArgument);
      }
    }

    private void visitGenericArrayType(GenericArrayType formal, ConstraintKind kind, Type actual) {
      if (actual instanceof WildcardType) {
        WildcardType actualWildcardType = (WildcardType) actual;
        Type[] actualLowerBounds = actualWildcardType.getLowerBounds();
        Type[] actualUpperBounds = actualWildcardType.getUpperBounds();
        for (Type actualLowerBound : actualLowerBounds) {
          visit(formal, ConstraintKind.SUPERTYPE_OF, actualLowerBound);
        }
        for (Type actualUpperBound : actualUpperBounds) {
          if (!actualUpperBound.equals(Object.class)) {
            visit(formal, ConstraintKind.SUBTYPE_OF, actualUpperBound);
          }
        }
        return; // Okay to say A[] is <?>
      }
      Type actualComponentType = Types.getComponentType(actual);
      checkArgument(actualComponentType != null, "%s is not an array type.", actual);
      visit(formal.getGenericComponentType(), kind, actualComponentType);
    }

    private void visitClass(Class<?> formal, ConstraintKind kind, Type actual) {
      if (actual instanceof WildcardType) {
        WildcardType actualWildcardType = (WildcardType) actual;
        Type[] actualLowerBounds = actualWildcardType.getLowerBounds();
        Type[] actualUpperBounds = actualWildcardType.getUpperBounds();
        for (Type actualLowerBound : actualLowerBounds) {
          checkArgument(
              isPossibleToSubtypeBoth(formal, actualLowerBound),
              "Type %s is incompatible with lower bound %s of wildcard type %s",
              formal.getTypeName(),
              actualLowerBound.getTypeName(),
              actual.getTypeName());
        }
        for (Type actualUpperBound : actualUpperBounds) {
          checkArgument(
              isSubtype(formal, actualUpperBound),
              "Type %s is incompatible with upper bound %s of wildcard type %s",
              formal.getTypeName(),
              actualUpperBound.getTypeName(),
              actual.getTypeName());
        }
        return; // Okay to say Foo is <?>
      }
      // Can't map from a raw class to anything other than itself or a wildcard.
      // You can't say "assuming String is Integer".
      // And we don't support "assuming String is T"; user has to say "assuming T is String".
      throw new IllegalArgumentException("No type mapping from " + formal + " to " + actual);
    }

    private static ConstraintKind constraintKindForBound(ConstraintKind kind) {
      switch (kind) {
        case EQUAL_TO:
          // formal=B
          // kind=EQUAL_TO
          // actual=Integer
          // B == Integer
          //
          // formalBound=A
          // B extends A
          // -----> Integer extends A
          // -----> Integer is a subtype of A
          //
          // same logic as UPPER_BOUND case
          //
          // (fallthrough)
          //
        case SUPERTYPE_OF:
          // formal=U
          // kind=SUPERTYPE_OF
          // actual=Integer
          // "Integer is something that extends U"
          // "Integer is a subtype of U"
          //
          // formalBound=T
          // "U extends T"
          // ----> "Integer is a subtype of T"
          //
          return ConstraintKind.SUPERTYPE_OF;
        case SUBTYPE_OF:
          // formal=U
          // kind=SUBTYPE_OF
          // actual=Number
          // "Number is something that is a supertype of U"
          // "Number is a supertype of U"
          // "U extends Number"
          //
          // formalBound=T
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
          // (fallthrough)
          //
        case RELATED_TO:
          //
          // formal=A
          // kind=RELATED_TYPE
          // actual=Number
          // "A extends Number or Number extends A"
          //
          // formalBound=B
          // A extends B
          //
          // Could B be String?
          // no
          // could B be Object?
          // yes
          // could B be Integer?
          // yes
          //
          return ConstraintKind.RELATED_TO;
      }
      throw new AssertionError("Unknown constraint kind " + kind);
    }

    private static final class Seen {
      private final Type formal;
      private final ConstraintKind kind;
      private final Type actual;

      Seen(Type formal, ConstraintKind kind, Type actual) {
        this.formal = checkNotNull(formal);
        this.kind = checkNotNull(kind);
        this.actual = checkNotNull(actual);
      }

      @Override
      public boolean equals(@Nullable Object object) {
        if (object instanceof Seen) {
          Seen that = (Seen) object;
          return this.formal.equals(that.formal)
              && this.kind.equals(that.kind)
              && this.actual.equals(that.actual);
        } else {
          return false;
        }
      }

      @Override
      public int hashCode() {
        return Objects.hashCode(formal, kind, actual);
      }
    }
  }

  /**
   * Returns a type representing the intersection of all of the specified types.  If there is only
   * one type in the provided set, then that type is returned.
   *
   * @throws IllegalArgumentException if {@code types} is empty
   */
  private static Type newIntersectionType(Set<Type> types) {
    checkNotNull(types);
    checkArgument(!types.isEmpty());
    if (types.size() == 1) {
      return types.iterator().next();
    }
    Type[] bounds = types.toArray(new Type[0]);
    return new Types.WildcardTypeImpl(bounds, bounds);
  }

  /**
   * Returns {@code true} if it is possible for any type to be a subtype of both {@code a} and
   * {@code b}.
   */
  private static boolean isPossibleToSubtypeBoth(Type a, Type b) {
    checkNotNull(a);
    checkNotNull(b);
    return (a instanceof Class && ((Class<?>) a).isInterface())
        || (b instanceof Class && ((Class<?>) b).isInterface())
        || isSubtype(a, b)
        || isSubtype(b, a);
  }

  /**
   * Returns the parameterized supertype of {@code subtype} having the same raw class as {@code
   * supertype}.
   *
   * @throws IllegalArgumentException if no such supertype exists
   */
  private static ParameterizedType getParameterizedSupertype(
      Class<?> subtype, ParameterizedType supertype) {
    checkNotNull(subtype);
    checkNotNull(supertype);
    Class<?> rawTypeToMatch = (Class<?>) supertype.getRawType();
    AtomicReference<ParameterizedType> resultHolder = new AtomicReference<>();
    new TypeVisitor() {
      @Override
      void visitClass(Class<?> t) {
        visit(t.getGenericSuperclass());
        visit(t.getGenericInterfaces());
      }

      @Override
      void visitParameterizedType(ParameterizedType t) {
        Class<?> rawType = (Class<?>) t.getRawType();
        if (rawType.equals(rawTypeToMatch)) {
          resultHolder.set(t);
          return;
        }
        visit(rawType);
      }
    }.visit(subtype);
    ParameterizedType result = resultHolder.get();
    checkArgument(
        result != null,
        "% is not a supertype of %s",
        supertype, subtype);
    return result;
  }

  /**
   * Returns the generic type declarations of the specified type, including the generic type
   * declarations of its supertypes and interfaces.
   */
  private static Set<Type> getGenericSupertypes(Type type) {
    checkNotNull(type);
    Set<Type> genericSupertypes = new LinkedHashSet<>();
    for (Class<?> rawType : getRawTypes(type)) {
      Type genericType = getGenericType(rawType);
      genericSupertypes.add(genericType);
    }
    Type componentType = Types.getComponentType(type);
    if (componentType != null && !isPrimitive(componentType)) {
      genericSupertypes.add(GenericArrayTypeHolder.GENERIC_ARRAY_TYPE);
    }
    return genericSupertypes;
  }

  private static final class GenericArrayTypeHolder {
    private static <E> E[] genericArray() { throw new UnsupportedOperationException(); }
    static final Type GENERIC_ARRAY_TYPE;
    static {
      Method method;
      try {
        method = GenericArrayTypeHolder.class.getDeclaredMethod("genericArray");
      } catch (NoSuchMethodException e) {
        throw new LinkageError("", e);
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
