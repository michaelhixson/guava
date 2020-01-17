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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
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
    TypeVariableConstraints mappings = new TypeVariableConstraints();
    populateTypeMappings(
        mappings,
        TypeVariableConstraints.ConstraintType.EXACT_TYPE,
        checkNotNull(formal),
        checkNotNull(actual));
    return where(mappings);
  }

  /** Returns a new {@code TypeResolver} with {@code variable} mapping to {@code type}. */
  TypeResolver where(TypeVariableConstraints mappings) {
    return new TypeResolver(typeTable.where(mappings));
  }

  private static void populateTypeMappings(
      final TypeVariableConstraints mappings,
      final TypeVariableConstraints.ConstraintType constraintType,
      final Type from,
      final Type to) {
    if (from.equals(to)) {
      return;
    }
    new TypeVisitor() {
      @Override
      void visitTypeVariable(TypeVariable<?> typeVariable) {
        TypeVariableKey key = new TypeVariableKey(typeVariable);
        Type[] bounds = typeVariable.getBounds();
        if (bounds.length > 0 && !(bounds.length == 1 && bounds[0].equals(Object.class))) {
          //
          // Confirm that `to` can conform to the bounds of `typeVariable`.
          //
          // First, resolve any known type variables in the bounds, including
          // this type variable.  (This type variable may appear within its own
          // bounds, as in <T extends Comparable<? super T>>.)
          //
          TypeVariableConstraints map = new TypeVariableConstraints();
          map.addAll(mappings);
          map.add(key, to, constraintType);
          TypeTable typeTable = new TypeTable().where(map);
          TypeResolver resolver = new TypeResolver(typeTable);
          Type[] resolvedBounds = resolver.resolveTypes(bounds);
          //
          // If there are any remaining type variables in the bounds, then
          // resolve those type variables in the most permissive way possible.
          // For example, if one of the bounds is
          //
          //   List<? extends U>, where U extends Comparable<? super U>
          //
          // then resolve that bound to
          //
          //   List<? extends Comparable<?>>
          //
          Type[] mostPermissiveBounds = resolvePermissively(resolvedBounds, ImmutableSet.of());
          TypeToken<?> specifiedType = TypeToken.of(to).wrap();
          for (int i = 0; i < bounds.length; i++) {
            // TODO: Is this handling `to=?` correctly? (Unbounded wildcard type)
            checkArgument(
                specifiedType.isSubtypeOf(mostPermissiveBounds[i]),
                "Incompatible type for variable %s: "
                    + "specified type %s is not a subtype of bound %s, "
                    + "which was resolved using known type variable mappings to %s, "
                    + "which then had its remaining type variables resolved "
                    + "in the most permissive way possible to produce %s",
                typeVariable.getTypeName(),
                to.getTypeName(),
                bounds[i].getTypeName(),
                resolvedBounds[i].getTypeName(),
                mostPermissiveBounds[i].getTypeName());
          }
        }
        mappings.add(key, to, constraintType);
      }

      @Override
      void visitWildcardType(WildcardType fromWildcardType) {
        if (!(to instanceof WildcardType)) {
          for (Type fromUpperBound : fromWildcardType.getUpperBounds()) {
            if (!(fromUpperBound instanceof Class)) {
              populateTypeMappings(
                  mappings,
                  TypeVariableConstraints.ConstraintType.UPPER_BOUND,
                  fromUpperBound,
                  to);
            }
          }
          for (Type fromLowerBound : fromWildcardType.getLowerBounds()) {
            if (!(fromLowerBound instanceof Class)) {
              populateTypeMappings(
                  mappings,
                  TypeVariableConstraints.ConstraintType.LOWER_BOUND,
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
              TypeVariableConstraints.ConstraintType.UPPER_BOUND,
              fromUpperBounds[i],
              toUpperBounds[i]);
        }
        for (int i = 0; i < fromLowerBounds.length; i++) {
          populateTypeMappings(
              mappings,
              TypeVariableConstraints.ConstraintType.LOWER_BOUND,
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
          populateTypeMappings(
              mappings,
              TypeVariableConstraints.ConstraintType.EXACT_TYPE,
              fromParameterizedType.getActualTypeArguments()[0],
              to);
          return;
        }
        ParameterizedType toParameterizedType = expectArgument(ParameterizedType.class, to);
        if (fromParameterizedType.getOwnerType() != null
            && toParameterizedType.getOwnerType() != null) {
          populateTypeMappings(
              mappings,
              TypeVariableConstraints.ConstraintType.EXACT_TYPE,
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
              TypeVariableConstraints.ConstraintType.EXACT_TYPE,
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
            TypeVariableConstraints.ConstraintType.EXACT_TYPE,
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

  private static <T> T expectArgument(Class<T> type, Object arg) {
    try {
      return type.cast(arg);
    } catch (ClassCastException e) {
      throw new IllegalArgumentException(arg + " is not a " + type.getSimpleName());
    }
  }

  private static Type[] resolvePermissively(Type[] types, Set<TypeVariable<?>> seen) {
    Type[] result = new Type[types.length];
    for (int i = 0; i < types.length; i++) {
      result[i] = resolvePermissively(types[i], seen);
    }
    return result;
  }

  private static Type resolvePermissively(Type type, Set<TypeVariable<?>> seen) {
    if (type instanceof TypeVariable) {
      TypeVariable<?> typeVariable = (TypeVariable<?>) type;
      if (seen.contains(typeVariable)) {
        return new Types.WildcardTypeImpl(
            new Type[0],
            new Type[] { Object.class });
      }
      Type[] bounds = typeVariable.getBounds();
      Set<TypeVariable<?>> seenPlusThis =
          ImmutableSet.<TypeVariable<?>>builder()
              .addAll(seen)
              .add(typeVariable)
              .build();
      return new Types.WildcardTypeImpl(
          new Type[0],
          resolvePermissively(bounds, seenPlusThis));
    }
    if (type instanceof Class) {
      return type;
    }
    if (type instanceof WildcardType) {
      WildcardType wildcardType = (WildcardType) type;
      Type[] lowerBounds = filterTypes(wildcardType.getLowerBounds(), seen);
      Type[] upperBounds = filterTypes(wildcardType.getUpperBounds(), seen);
      if (lowerBounds.length == 0 && upperBounds.length == 0) {
        return new Types.WildcardTypeImpl(
            new Type[0],
            new Type[] { Object.class });
      }
      return new Types.WildcardTypeImpl(
          resolvePermissively(lowerBounds, seen),
          resolvePermissively(upperBounds, seen));
    }
    if (type instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType) type;
      Type ownerType = parameterizedType.getOwnerType();
      Class<?> rawType = (Class<?>) parameterizedType.getRawType();
      Type[] typeArguments = parameterizedType.getActualTypeArguments();
      return Types.newParameterizedTypeWithOwner(
          (ownerType == null) ? null : resolvePermissively(ownerType, seen),
          rawType,
          resolvePermissively(typeArguments, seen));
    }
    if (type instanceof GenericArrayType) {
      GenericArrayType arrayType = (GenericArrayType) type;
      Type componentType = arrayType.getGenericComponentType();
      return Types.newArrayType(resolvePermissively(componentType, seen));
    }
    return type;
  }

  private static Type[] filterTypes(Type[] types, Set<? extends Type> toRemove) {
    if (toRemove.isEmpty()) return types;
    List<Type> result = new ArrayList<>();
    for (Type type : types) {
      if (!toRemove.contains(type)) {
        result.add(type);
      }
    }
    return result.toArray(new Type[0]);
  }

  /** A TypeTable maintains mapping from {@link TypeVariable} to types. */
  private static class TypeTable {
    private final TypeVariableConstraints map;

    TypeTable() {
      this.map = new TypeVariableConstraints();
    }

    private TypeTable(TypeVariableConstraints map) {
      this.map = map;
    }

    /** Returns a new {@code TypeResolver} with {@code variable} mapping to {@code type}. */
    final TypeTable where(TypeVariableConstraints mappings) {
      TypeVariableConstraints combined = new TypeVariableConstraints();
      combined.addAll(map);
      combined.addAll(mappings);
      return new TypeTable(combined);
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
      TypeVariableKey key = new TypeVariableKey(var);
      Type type = map.get(key);
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
    static TypeVariableConstraints getTypeMappings(Type contextType) {
      checkNotNull(contextType);
      TypeMappingIntrospector introspector = new TypeMappingIntrospector();
      introspector.visit(contextType);
      TypeVariableConstraints mappings = new TypeVariableConstraints();
      for (Map.Entry<TypeVariableKey, Type> entry : introspector.mappings.entrySet()) {
        mappings.add(
            entry.getKey(),
            entry.getValue(),
            TypeVariableConstraints.ConstraintType.EXACT_TYPE);
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
  static final class TypeVariableKey {
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

  static final class TypeVariableConstraints {
    public enum ConstraintType { EXACT_TYPE, UPPER_BOUND, LOWER_BOUND }

    private final Map<TypeVariableKey, Type> exactTypes = new HashMap<>();
    private final Map<TypeVariableKey, Set<Type>> upperBounds = new HashMap<>();
    private final Map<TypeVariableKey, Set<Type>> lowerBounds = new HashMap<>();

    private static final boolean REPRESENT_INTERSECTION_TYPES_AS_TYPE_VARIABLES = true;

    public @Nullable Type get(TypeVariableKey key) {
      checkNotNull(key);
      final Type exactType = getExactType(key);
      final Type upperBound = getUpperBound(key);
      final Type lowerBound = getLowerBound(key);
      if (exactType == null && upperBound == null && lowerBound == null) {
        return null;
      }
      if (upperBound == null && lowerBound == null) {
        return exactType;
      }
      if (exactType == null && lowerBound == null) {
        return upperBound;
      }
      if (exactType == null && upperBound == null) {
        return lowerBound;
      }
      if (exactType == null) {
        checkArgument(
            TypeToken.of(lowerBound).isSupertypeOf(upperBound),
            "No type can satisfy the constraints of %s, "
                + "which must be a subtype of %s and a supertype of %s",
            key,
            lowerBound.getTypeName(),
            upperBound.getTypeName());
        return upperBound; // TODO: is this right?
      }
      if (upperBound == null) {
        checkArgument(
            TypeToken.of(exactType).isSubtypeOf(lowerBound),
            "No type can satisfy the constraints of %s, "
                + "which must be %s and a subtype of %s",
            key,
            exactType.getTypeName(),
            lowerBound.getTypeName());
        return exactType;
      }
      if (lowerBound == null) {
        checkArgument(
            TypeToken.of(exactType).isSupertypeOf(upperBound),
            "No type can satisfy the constraints of %s, "
                + "which must be %s and a supertype of %s",
            key,
            exactType.getTypeName(),
            upperBound.getTypeName());
        return exactType;
      }
      checkArgument(
          TypeToken.of(exactType).isSubtypeOf(lowerBound)
              && TypeToken.of(exactType).isSupertypeOf(upperBound)
              && TypeToken.of(lowerBound).isSupertypeOf(upperBound),
          "No type can satisfy the constraints of %s, "
              + "which must be %s, a subtype of %s, and a supertype of %s",
          key,
          exactType.getTypeName(),
          lowerBound.getTypeName(),
          upperBound.getTypeName());
      return exactType;
    }

    private @Nullable Type getExactType(TypeVariableKey key) {
      checkNotNull(key);
      return exactTypes.get(key);
    }

    private @Nullable Type getUpperBound(TypeVariableKey key) {
      checkNotNull(key);
      Set<Type> upper = upperBounds.get(key);
      if (upper == null) {
        return null;
      }
      if (upper.size() == 1) {
        return upper.iterator().next();
      }
      Iterator<Type> iterator = upper.iterator();
      Set<Type> supertypes = new LinkedHashSet<>(supertypesOf(iterator.next()));
      while (iterator.hasNext()) {
        supertypes.retainAll(supertypesOf(iterator.next()));
      }
      if (supertypes.isEmpty()) {
        // Shouldn't they all share Object at least?
        throw new IllegalArgumentException("No common supertype for " + key);
      }
      supertypes.remove(Object.class);
      if (supertypes.isEmpty()) {
        return Object.class;
      }
      if (supertypes.size() == 1) {
        return supertypes.iterator().next();
      }
      // TODO: Which approach is better?
      if (REPRESENT_INTERSECTION_TYPES_AS_TYPE_VARIABLES) {
        StringJoiner intersectionTypeName = new StringJoiner(" & ");
        for (Type supertype : supertypes) {
          intersectionTypeName.add(supertype.getTypeName());
        }
        return Types.newArtificialTypeVariable(
            TypeVariable.class,
            intersectionTypeName.toString(),
            supertypes.toArray(new Type[0]));
      } else {
        return new Types.WildcardTypeImpl(
            new Type[0],
            supertypes.toArray(new Type[0]));
      }
    }

    private @Nullable Type getLowerBound(TypeVariableKey key) {
      checkNotNull(key);
      Set<Type> lower = lowerBounds.get(key);
      if (lower == null) {
        return null;
      }
      Iterator<Type> iterator = lower.iterator();
      Type lowest = iterator.next();
      while (iterator.hasNext()) {
        Type next = iterator.next();
        if (TypeToken.of(lowest).isSubtypeOf(next)) {
          continue;
        }
        if (TypeToken.of(next).isSubtypeOf(lowest)) {
          lowest = next;
          continue;
        }
        throw new IllegalArgumentException(
            "No type that can conform to lower bounds " + lower);
      }
      return lowest;
    }

    public void add(TypeVariableKey key, Type type, ConstraintType context) {
      checkNotNull(key);
      checkNotNull(type);
      checkNotNull(context);
      switch (context) {
        case EXACT_TYPE:
          setExactType(key, type);
          return;
        case UPPER_BOUND:
          addUpperBound(key, type);
          return;
        case LOWER_BOUND:
          addLowerBound(key, type);
          return;
      }
      throw new AssertionError("Unknown context " + context);
    }

    public void addAll(TypeVariableConstraints other) {
      checkNotNull(other);
      for (Map.Entry<TypeVariableKey, Type> entry : other.exactTypes.entrySet()) {
        setExactType(entry.getKey(), entry.getValue());
      }
      for (Map.Entry<TypeVariableKey, Set<Type>> entry : other.lowerBounds.entrySet()) {
        for (Type type : entry.getValue()) {
          addLowerBound(entry.getKey(), type);
        }
      }
      for (Map.Entry<TypeVariableKey, Set<Type>> entry : other.upperBounds.entrySet()) {
        for (Type type : entry.getValue()) {
          addUpperBound(entry.getKey(), type);
        }
      }
    }

    private void setExactType(TypeVariableKey key, Type type) {
      checkNotNull(key);
      checkNotNull(type);
      checkNotMappedToSelf(key, type);
      Type oldType = exactTypes.get(key);
      checkArgument(
          (oldType == null) || oldType.equals(type),
          "Type %s must be exactly %s, so it cannot be %s",
          key,
          (oldType == null) ? "null" : oldType.getTypeName(),
          type.getTypeName());
      exactTypes.put(key, type);
    }

    private void addUpperBound(TypeVariableKey key, Type type) {
      checkNotNull(key);
      checkNotNull(type);
      checkNotMappedToSelf(key, type);
      Set<Type> existing = upperBounds.get(key);
      if (existing == null) {
        existing = new LinkedHashSet<>();
        upperBounds.put(key, existing);
      }
      existing.add(type);
      // TODO: Converge the upper bounds right away?
    }

    private void addLowerBound(TypeVariableKey key, Type type) {
      checkNotNull(key);
      checkNotNull(type);
      checkNotMappedToSelf(key, type);
      Set<Type> existing = lowerBounds.get(key);
      if (existing == null) {
        existing = new LinkedHashSet<>();
        lowerBounds.put(key, existing);
      }
      existing.add(type);
      // TODO: Converge the lower bounds right away?
    }

    private static void checkNotMappedToSelf(TypeVariableKey key, Type type) {
      checkArgument(
          !key.equalsType(type),
          "Type variable %s bound to itself",
          key);
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
}
