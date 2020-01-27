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
import com.google.common.collect.Maps;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
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
    Constraints constraints = new Constraints();
    constraints.set(formal, ConstraintKind.SUPERTYPE_OF, actual);
    return where(constraints);
  }

  // TODO: Figure out if this method is really necessary.
  // TODO: If this method is necessary, come up with a better name.
  TypeResolver whereTypeVariablesMayBeWildcards(Type formal, Type actual) {
    checkNotNull(formal);
    checkNotNull(actual);
    Constraints constraints = new Constraints(true);
    constraints.set(formal, ConstraintKind.SUPERTYPE_OF, actual);
    return where(constraints);
  }

  /** Returns a new {@code TypeResolver} with additional constraints. */
  private TypeResolver where(Constraints constraints) {
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

  private WildcardType resolveWildcardType(WildcardType type) {
    Type[] lowerBounds = type.getLowerBounds();
    Type[] upperBounds = type.getUpperBounds();
    return new Types.WildcardTypeImpl(
        flattenWildcardLowerBounds(resolveTypes(lowerBounds)),
        flattenWildcardUpperBounds(resolveTypes(upperBounds)));
  }

  // TODO: Explain why flattening wildcards is necessary.
  private static boolean FLATTEN_WILDCARDS = true;

  private static Type[] flattenWildcardLowerBounds(Type[] lowerBounds) {
    if (!FLATTEN_WILDCARDS) {
      return lowerBounds;
    }
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
          System.out.println("In this case we didn't know was possible!!!");
          flat.add(Object.class);
          continue;
        }
      }
      // ? super ? super Number ---> ? super Number
      flat.addAll(Arrays.asList(nestedLowerBounds));

      // ? super ? extends Number ---> ? super Number
      for (Type nestedUpperBound : nestedUpperBounds) {
        if (!nestedUpperBound.equals(Object.class)) {
          flat.add(nestedUpperBound);
        }
      }
    }
    return (flat == null) ? lowerBounds : flat.toArray(new Type[0]);
  }

  private static Type[] flattenWildcardUpperBounds(Type[] upperBounds) {
    if (!FLATTEN_WILDCARDS) {
      return upperBounds;
    }
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
      flat.addAll(Arrays.asList(nested.getUpperBounds()));
    }
    return (flat == null) ? upperBounds : flat.toArray(new Type[0]);
  }

  /** A TypeTable maintains mapping from {@link TypeVariable} to types. */
  private static class TypeTable {
    private final Constraints constraints;

    TypeTable() {
      this.constraints = new Constraints();
    }

    private TypeTable(Constraints constraints) {
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
        constraints.set(entry.getKey(), ConstraintKind.EQUAL_TO, entry.getValue());
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
    // TODO: Come up with a better name for this.
    RELATED_TO,

    /** The variable's type must be a subtype of the specified type. */
    SUBTYPE_OF,

    /** The variable's type must be a supertype of the specified type. */
    SUPERTYPE_OF
  }

  /** Constraints for one {@link TypeVariable}. */
  private static final class Constraint {
    private final TypeVariableKey key;
    private @Nullable Type equalTo;
    private @Nullable Set<Type> relatedTo;
    private @Nullable Set<Type> subtypeOf;
    private @Nullable Set<Type> supertypeOf;

    Constraint(TypeVariableKey key) {
      this.key = checkNotNull(key);
    }

    Type get() {
      if (equalTo != null) {
        return equalTo;
      }
      if (supertypeOf != null) {
        return mergeSupertypeOf(supertypeOf);
      }
      if (subtypeOf != null) {
        return mergeSubtypeOf(subtypeOf);
      }
      if (relatedTo != null) {
        return Object.class;
      }
      throw new IllegalStateException();
    }

    void set(Constraint that) {
      checkNotNull(that);
      if (that.equalTo != null) {
        setEqualTo(that.equalTo);
      }
      if (that.relatedTo != null) {
        that.relatedTo.forEach(this::setRelatedTo);
      }
      if (that.subtypeOf != null) {
        that.subtypeOf.forEach(this::setSubtypeOf);
      }
      if (that.supertypeOf != null) {
        that.supertypeOf.forEach(this::setSupertypeOf);
      }
    }

    void set(ConstraintKind kind, Type type) {
      checkNotNull(kind);
      checkNotNull(type);
      switch (kind) {
        case EQUAL_TO:
          setEqualTo(type);
          return;
        case RELATED_TO:
          setRelatedTo(type);
          return;
        case SUBTYPE_OF:
          setSubtypeOf(type);
          return;
        case SUPERTYPE_OF:
          setSupertypeOf(type);
          return;
      }
      throw new AssertionError("Unknown constraint kind " + kind);
    }

    void setEqualTo(Type type) {
      checkNotNull(type);
      checkNotMappedToSelf(type);
      checkEqualToAndRelatedTo(type, relatedTo);
      checkEqualToAndSubtypeOf(type, subtypeOf);
      checkEqualToAndSupertypeOf(type, supertypeOf);
      if (equalTo == null) {
        equalTo = type;
        return;
      }
      checkArgument(
          equalTo.equals(type),
          "Type %s must be exactly %s, so it cannot be %s",
          key,
          equalTo.getTypeName(),
          type.getTypeName());
    }

    void setRelatedTo(Type type) {
      checkNotNull(type);
      checkNotMappedToSelf(type);
      // No need to re-check the other bounds.
      ImmutableSet<Type> newRelatedTo = ImmutableSet.of(type);
      checkEqualToAndRelatedTo(equalTo, newRelatedTo);
      checkRelatedToAndSubtypeOf(newRelatedTo, subtypeOf);
      checkRelatedToAndSupertypeOf(newRelatedTo, supertypeOf);
      if (relatedTo != null) {
        for (Type existing : relatedTo) {
          // TODO: Write a test that throws here.
          checkPossibleToSubtypeBoth(existing, type);
        }
      }
      if (relatedTo == null) {
        relatedTo = new LinkedHashSet<>();
      }
      relatedTo.add(type);
    }

    void setSubtypeOf(Type type) {
      checkNotNull(type);
      checkNotMappedToSelf(type);
      // No need to re-check the other bounds.
      ImmutableSet<Type> newSubtypeOf = ImmutableSet.of(type);
      checkEqualToAndSubtypeOf(equalTo, newSubtypeOf);
      checkRelatedToAndSubtypeOf(relatedTo, newSubtypeOf);
      checkSubtypeOfAndSupertypeOf(newSubtypeOf, supertypeOf);
      if (subtypeOf != null) {
        for (Type existing : subtypeOf) {
          checkPossibleToSubtypeBoth(existing, type);
        }
      }
      if (subtypeOf == null) {
        subtypeOf = new LinkedHashSet<>();
      }
      subtypeOf.add(type);
    }

    void setSupertypeOf(Type type) {
      checkNotNull(type);
      checkNotMappedToSelf(type);
      // No need to re-check the other bounds.
      ImmutableSet<Type> newSupertypeOf = ImmutableSet.of(type);
      checkEqualToAndSupertypeOf(equalTo, newSupertypeOf);
      checkRelatedToAndSupertypeOf(relatedTo, newSupertypeOf);
      checkSubtypeOfAndSupertypeOf(subtypeOf, newSupertypeOf);
      if (supertypeOf == null) {
        supertypeOf = new LinkedHashSet<>();
      }
      supertypeOf.add(type);
    }

    private void checkNotMappedToSelf(Type type) {
      checkArgument(
          !key.equalsType(type),
          "Type variable %s bound to itself",
          key);
    }

    private void checkPossibleToSubtypeBoth(Type a, Type b) {
      checkNotNull(a);
      checkNotNull(b);
      checkArgument(
          isPossibleToSubtypeBoth(a, b),
          "Type %s must be a subtype of %s, so it cannot also be a subtype of %s",
          key,
          a.getTypeName(),
          b.getTypeName());
    }

    private void checkEqualToAndRelatedTo(
        @Nullable Type equalTo,
        @Nullable Set<Type> relatedTo) {
      if (equalTo == null || relatedTo == null) {
        return;
      }
      for (Type type : relatedTo) {
        checkPossibleToSubtypeBoth(type, equalTo);
      }
    }

    private void checkEqualToAndSubtypeOf(
        @Nullable Type equalTo,
        @Nullable Set<Type> subtypeOf) {
      if (equalTo == null || subtypeOf == null) {
        return;
      }
      for (Type type : subtypeOf) {
        checkArgument(
            TypeToken.of(equalTo).isSubtypeOf(type),
            "No type can satisfy the constraints of %s, "
                + "which must be equal to %s and a subtype of %s",
            key,
            equalTo.getTypeName(),
            type.getTypeName());
      }
    }

    private void checkEqualToAndSupertypeOf(
        @Nullable Type equalTo,
        @Nullable Set<Type> supertypeOf) {
      if (equalTo == null || supertypeOf == null) {
        return;
      }
      for (Type type : supertypeOf) {
        checkArgument(
            TypeToken.of(equalTo).isSupertypeOf(type),
            "No type can satisfy the constraints of %s, "
                + "which must be equal to %s and a supertype of %s",
            key,
            equalTo.getTypeName(),
            type.getTypeName());
      }
    }

    private void checkRelatedToAndSubtypeOf(
        @Nullable Set<Type> relatedTo,
        @Nullable Set<Type> subtypeOf) {
      if (relatedTo == null || subtypeOf == null) {
        return;
      }
      for (Type a : relatedTo) {
        for (Type b : subtypeOf) {
          checkPossibleToSubtypeBoth(a, b);
        }
      }
    }

    private void checkRelatedToAndSupertypeOf(
        @Nullable Set<Type> relatedTo,
        @Nullable Set<Type> supertypeOf) {
      if (relatedTo == null || supertypeOf == null) {
        return;
      }
      for (Type a : relatedTo) {
        for (Type b : supertypeOf) {
          checkPossibleToSubtypeBoth(a, b);
        }
      }
    }

    private void checkSubtypeOfAndSupertypeOf(
        @Nullable Set<Type> subtypeOf,
        @Nullable Set<Type> supertypeOf) {
      if (subtypeOf == null || supertypeOf == null) {
        return;
      }
      for (Type a : subtypeOf) {
        for (Type b : supertypeOf) {
          checkArgument(
              TypeToken.of(a).isSupertypeOf(b),
              "No type can satisfy the constraints of %s, "
                  + "which must be a subtype of %s and a supertype of %s",
              key,
              a.getTypeName(),
              b.getTypeName());
        }
      }
    }

    private static Type mergeSubtypeOf(Set<Type> types) {
      checkNotNull(types);
      checkArgument(!types.isEmpty());
      if (types.size() == 1) {
        return types.iterator().next();
      }
      // Remove redundant types.
      Set<Type> canonical = new LinkedHashSet<>(types);
      for (Type a : types) {
        TypeToken<?> aToken = TypeToken.of(a);
        canonical.removeIf(b -> !b.equals(a) && aToken.isSubtypeOf(b));
      }
      if (canonical.isEmpty()) {
        // This is impossible, right?  As long as the caller didn't pass us an empty set?
        throw new IllegalArgumentException("No common subtype in bounds " + types);
      }
      if (canonical.size() == 1) {
        return canonical.iterator().next();
      }
      return newIntersectionType(canonical);
    }

    private static Type mergeSupertypeOf(Set<Type> types) {
      checkNotNull(types);
      checkArgument(!types.isEmpty());
      if (types.size() == 1) {
        return types.iterator().next();
      }
      Iterator<Type> iterator = types.iterator();
      Set<Type> supertypes = new LinkedHashSet<>(getSupertypes(iterator.next()));
      while (iterator.hasNext()) {
        supertypes.retainAll(getSupertypes(iterator.next()));
      }
      if (supertypes.isEmpty()) {
        // Shouldn't they all share Object at least?
        throw new IllegalArgumentException("No common supertype in bounds " + types);
      }
      // Remove redundant supertypes.
      for (Type a : new LinkedHashSet<>(supertypes)) {
        supertypes.removeIf(b -> !b.equals(a) && TypeToken.of(a).isSubtypeOf(b));
      }
      if (supertypes.isEmpty()) {
        return Object.class;
      }
      if (supertypes.size() == 1) {
        return supertypes.iterator().next();
      }
      return newIntersectionType(supertypes);
    }

    private static Set<Type> getSupertypes(Type type) {
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

    // TODO: Explain why we use a wildcard.
    // TODO: Explain why using a wildcard here means we have to flatten wildcards elsewhere.
    private static Type newIntersectionType(Set<Type> types) {
      checkNotNull(types);
      checkArgument(types.size() > 1);
      return new Types.WildcardTypeImpl(
          new Type[0],
          types.toArray(new Type[0]));
    }
  }

  /** Constraints for many {@link TypeVariable}s. */
  private static final class Constraints {
    private final Set<Seen> seen = new LinkedHashSet<>();
    private final Map<TypeVariableKey, Constraint> constraints = new LinkedHashMap<>();
    private final boolean typeVarParamsMayEqualWildcards;

    Constraints() {
      this.typeVarParamsMayEqualWildcards = false;
    }

    Constraints(boolean typeVarParamsMayEqualWildcards) {
      this.typeVarParamsMayEqualWildcards = typeVarParamsMayEqualWildcards;
    }

    @Nullable Type get(TypeVariableKey key) {
      checkNotNull(key);
      Constraint constraint = constraints.get(key);
      return (constraint == null) ? null : constraint.get();
    }

    // TODO: Separate this method into a ConstraintsBuilder class.
    // TODO: Rename this method and the methods it calls to use some prefix other than "set".
    void set(Type from, ConstraintKind kind, Type to) {
      //System.out.println("set " + from + " " + kind + " " + to);
      checkNotNull(from);
      checkNotNull(kind);
      checkNotNull(to);
      if (from.equals(to)) {
        return;
      }
      if (!seen.add(new Seen(from, kind, to))) {
        return;
      }
      if (from instanceof TypeVariable) {
        setTypeVariable((TypeVariable<?>) from, kind, to);
      } else if (from instanceof WildcardType) {
        setWildcardType((WildcardType) from, kind, to);
      } else if (from instanceof ParameterizedType) {
        setParameterizedType((ParameterizedType) from, kind, to);
      } else if (from instanceof GenericArrayType) {
        setGenericArrayType((GenericArrayType) from, kind, to);
      } else if (from instanceof Class) {
        setClass((Class<?>) from, kind, to);
      } else {
        throw new AssertionError("Unknown type: " + from);
      }
    }

    private void setTypeVariable(TypeVariable<?> from, ConstraintKind kind, Type to) {
      TypeVariableKey key = new TypeVariableKey(from);
      set(key, kind, to);
      for (Type bound : from.getBounds()) {
        if (bound instanceof Class) {
          // The Object.class bound is redundant, and it causes problems
          // when binding T[] to int[].
          if (!bound.equals(Object.class)) {
            set(key, ConstraintKind.SUBTYPE_OF, bound);
          }
        } else {
          ConstraintKind kindForBound = constraintKindForBound(kind);
          if (bound instanceof TypeVariable || !kindForBound.equals(ConstraintKind.RELATED_TO)) {
            set(bound, kindForBound, to);
          }
        }
      }
    }

    private void setWildcardType(WildcardType from, ConstraintKind kind, Type to) {
      if (!(to instanceof WildcardType)) {
        for (Type fromLowerBound : from.getLowerBounds()) {
          if (!(fromLowerBound instanceof Class)) {
            set(fromLowerBound, ConstraintKind.SUBTYPE_OF, to);
          }
        }
        for (Type fromUpperBound : from.getUpperBounds()) {
          if (!(fromUpperBound instanceof Class)) {
            set(fromUpperBound, ConstraintKind.SUPERTYPE_OF, to);
          }
        }
        return;
      }
      WildcardType toWildcardType = (WildcardType) to;
      Type[] fromUpperBounds = from.getUpperBounds();
      Type[] toUpperBounds = toWildcardType.getUpperBounds();
      Type[] fromLowerBounds = from.getLowerBounds();
      Type[] toLowerBounds = toWildcardType.getLowerBounds();
      checkArgument(
          fromUpperBounds.length == toUpperBounds.length
              && fromLowerBounds.length == toLowerBounds.length,
          "Incompatible type: %s vs. %s",
          from,
          to);
      for (int i = 0; i < fromLowerBounds.length; i++) {
        set(fromLowerBounds[i], ConstraintKind.SUBTYPE_OF, toLowerBounds[i]);
      }
      for (int i = 0; i < fromUpperBounds.length; i++) {
        set(fromUpperBounds[i], ConstraintKind.SUPERTYPE_OF, toUpperBounds[i]);
      }
    }

    private void setParameterizedType(ParameterizedType from, ConstraintKind kind, Type to) {
      if (kind.equals(ConstraintKind.SUBTYPE_OF)) {
        // TODO: Write a test that enters this block, or figure out why that's impossible.
        System.out.println(from + " " + kind + " " + to);
      }
      if (to instanceof WildcardType) {
        WildcardType toWildcardType = (WildcardType) to;
        Type[] toLowerBounds = toWildcardType.getLowerBounds();
        Type[] toUpperBounds = toWildcardType.getUpperBounds();
        for (Type toLowerBound : toLowerBounds) {
          set(from, ConstraintKind.SUPERTYPE_OF, toLowerBound);
        }
        for (Type toUpperBound : toUpperBounds) {
          if (!toUpperBound.equals(Object.class)) {
            set(from, ConstraintKind.SUBTYPE_OF, toUpperBound);
          }
        }
        return; // Okay to say Foo<A> is <?>
      }
      if (to instanceof Class && from.getRawType().equals(Class.class)) {
        // from=Class<K extends Enum<K>>, to=java.util.concurrent.TimeUnit
        set(from.getActualTypeArguments()[0], ConstraintKind.EQUAL_TO, to);
        return;
      }
      ParameterizedType toParameterizedType;
      if (to instanceof Class) {
        // from=Comparable<? super T>, to=Integer
        toParameterizedType = getParameterizedSupertype((Class<?>) to, from);
      } else {
        checkArgument(
            to instanceof ParameterizedType,
            "%s is not a parameterized type",
            to.getTypeName());
        toParameterizedType = (ParameterizedType) to;
      }
      Class<?> fromRawType = (Class<?>) from.getRawType();
      Class<?> toRawType = (Class<?>) toParameterizedType.getRawType();
      if (kind.equals(ConstraintKind.EQUAL_TO)) {
        checkArgument(
            fromRawType.equals(toRawType),
            "Inconsistent raw type: %s vs. %s",
            from.getTypeName(),
            toParameterizedType.getTypeName());
      } else {
        checkArgument(
            fromRawType.isAssignableFrom(toRawType),
            "Inconsistent raw type: %s vs. %s",
            from.getTypeName(),
            toParameterizedType.getTypeName());
      }
      Type[] fromArgs = from.getActualTypeArguments();
      Type[] toArgs = toParameterizedType.getActualTypeArguments();
      checkArgument(
          fromArgs.length == toArgs.length,
          "%s not compatible with %s",
          from,
          toParameterizedType);
      Type fromOwnerType = from.getOwnerType();
      Type toOwnerType = toParameterizedType.getOwnerType();
      if (fromOwnerType != null && toOwnerType != null) {
        set(fromOwnerType, kind, toOwnerType);
      }
      for (int i = 0; i < fromArgs.length; i++) {
        Type fromArg = fromArgs[i];
        Type toArg = toArgs[i];
        if (fromArg instanceof TypeVariable) {
          checkArgument(
              !(toArg instanceof WildcardType)
                  || !kind.equals(ConstraintKind.EQUAL_TO)
                  || typeVarParamsMayEqualWildcards,
              "%s not compatible with %s because parameter %s at index %s "
                  + "is not compatible with argument %s",
              from.getTypeName(),
              to.getTypeName(),
              fromArg.getTypeName(),
              i,
              toArg.getTypeName());
        }
        if (fromArg instanceof WildcardType) {
          checkArgument(
              toArg instanceof WildcardType
                  || !kind.equals(ConstraintKind.EQUAL_TO),
              "%s not compatible with %s because parameter %s at index %s "
                  + "is not compatible with argument %s",
              from.getTypeName(),
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
              from.getTypeName(),
              to.getTypeName(),
              fromArg.getTypeName(),
              i,
              toArg.getTypeName());
        }
        set(fromArg, ConstraintKind.EQUAL_TO, toArg);
      }
    }

    private void setGenericArrayType(GenericArrayType from, ConstraintKind kind, Type to) {
      if (to instanceof WildcardType) {
        WildcardType toWildcardType = (WildcardType) to;
        Type[] toLowerBounds = toWildcardType.getLowerBounds();
        Type[] toUpperBounds = toWildcardType.getUpperBounds();
        for (Type toLowerBound : toLowerBounds) {
          set(from, ConstraintKind.SUPERTYPE_OF, toLowerBound);
        }
        for (Type toUpperBound : toUpperBounds) {
          if (!toUpperBound.equals(Object.class)) {
            set(from, ConstraintKind.SUBTYPE_OF, toUpperBound);
          }
        }
        return; // Okay to say A[] is <?>
      }
      Type componentType = Types.getComponentType(to);
      checkArgument(componentType != null, "%s is not an array type.", to);
      set(from.getGenericComponentType(), kind, componentType);
    }

    private void setClass(Class<?> from, ConstraintKind kind, Type to) {
      if (to instanceof WildcardType) {
        WildcardType toWildcardType = (WildcardType) to;
        Type[] toLowerBounds = toWildcardType.getLowerBounds();
        Type[] toUpperBounds = toWildcardType.getUpperBounds();
        for (Type toLowerBound : toLowerBounds) {
          checkArgument(
              isPossibleToSubtypeBoth(from, toLowerBound),
              "Type %s is incompatible with lower bound %s of wildcard type %s",
              from.getTypeName(),
              toLowerBound.getTypeName(),
              to.getTypeName());
        }
        for (Type toUpperBound : toUpperBounds) {
          checkArgument(
              TypeToken.of(from).isSubtypeOf(toUpperBound),
              "Type %s is incompatible with upper bound %s of wildcard type %s",
              from.getTypeName(),
              toUpperBound.getTypeName(),
              to.getTypeName());
        }
        return; // Okay to say Foo is <?>
      }
      // Can't map from a raw class to anything other than itself or a wildcard.
      // You can't say "assuming String is Integer".
      // And we don't support "assuming String is T"; user has to say "assuming T is String".
      throw new IllegalArgumentException("No type mapping from " + from + " to " + to);
    }

    void set(TypeVariableKey key, ConstraintKind kind, Type type) {
      checkNotNull(key);
      checkNotNull(kind);
      checkNotNull(type);
      constraints.computeIfAbsent(key, Constraint::new).set(kind, type);
    }

    private void set(Constraints that) {
      checkNotNull(that);
      this.seen.addAll(that.seen);
      that.constraints.forEach(
          (key, thatConstraint) -> {
            Constraint thisConstraint = this.constraints.get(key);
            if (thisConstraint == null) {
              thisConstraint = new Constraint(key);
              this.constraints.put(key, thisConstraint);
            }
            thisConstraint.set(thatConstraint);
          });
    }

    static Constraints combine(Constraints a, Constraints b) {
      checkNotNull(a);
      checkNotNull(b);
      Constraints sum = new Constraints();
      sum.set(a);
      sum.set(b);
      return sum;
    }

    private static final class Seen {
      private final Type from;
      private final ConstraintKind kind;
      private final Type to;

      Seen(Type from, ConstraintKind kind, Type to) {
        this.from = checkNotNull(from);
        this.kind = checkNotNull(kind);
        this.to = checkNotNull(to);
      }

      @Override
      public boolean equals(@Nullable Object object) {
        if (object instanceof Seen) {
          Seen that = (Seen) object;
          return this.from.equals(that.from)
              && this.kind.equals(that.kind)
              && this.to.equals(that.to);
        } else {
          return false;
        }
      }

      @Override
      public int hashCode() {
        return Objects.hashCode(from, kind, to);
      }
    }
  }

  private static ParameterizedType getParameterizedSupertype(
      Class<?> subtype, ParameterizedType supertype) {
    checkNotNull(subtype);
    checkNotNull(supertype);
    // Avoid TypeToken.of(subtype).getSupertype((Class) supertype.getRawType())
    // in order to avoid an uncomfortable circular dependency between TypeToken
    // and TypeResolver.
    Class<?> targetClass = (Class<?>) supertype.getRawType();
    Set<Type> seen = new HashSet<>();
    Deque<Type> todo = new ArrayDeque<>();
    todo.add(subtype);
    while (!todo.isEmpty()) {
      Type type = todo.pop();
      if (!seen.add(type)) {
        continue;
      }
      Class<?> classOfType;
      if (type instanceof Class) {
        classOfType = (Class<?>) type;
      } else if (type instanceof ParameterizedType) {
        ParameterizedType parameterizedType = (ParameterizedType) type;
        classOfType = (Class<?>) parameterizedType.getRawType();
        if (classOfType.equals(targetClass)) {
          return parameterizedType;
        }
      } else {
        continue;
      }
      Type superclassOfType = classOfType.getGenericSuperclass();
      if (superclassOfType != null) {
        todo.add(superclassOfType);
      }
      todo.addAll(Arrays.asList(classOfType.getGenericInterfaces()));
    }
    throw new IllegalArgumentException(
        supertype.getTypeName() + " is not a supertype of " + subtype.getTypeName());
  }

  private static boolean isPossibleToSubtypeBoth(Type a, Type b) {
    checkNotNull(a);
    checkNotNull(b);
    return (a instanceof Class && ((Class<?>) a).isInterface())
        || (b instanceof Class && ((Class<?>) b).isInterface())
        || TypeToken.of(a).isSubtypeOf(b)
        || TypeToken.of(b).isSubtypeOf(a);
  }

  private static ConstraintKind constraintKindForBound(ConstraintKind kind) {
    switch (kind) {
      case EQUAL_TO:
        // key=B
        // kind=EQUAL_TO
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
      case SUPERTYPE_OF:
        // key=U
        // kind=SUPERTYPE_OF
        // to=Integer
        // "Integer is something that extends U"
        // "Integer is a subtype of U"
        //
        // boundVar=T
        // "U extends T"
        // ----> "Integer is a subtype of T"
        //
        return ConstraintKind.SUPERTYPE_OF;
      case SUBTYPE_OF:
        // key=U
        // kind=SUBTYPE_OF
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
        // (fallthrough)
        //
      case RELATED_TO:
        //
        // key=A
        // kind=RELATED_TYPE
        // to=Number
        // "A extends Number or Number extends A"
        //
        // boundVar=B
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
}
