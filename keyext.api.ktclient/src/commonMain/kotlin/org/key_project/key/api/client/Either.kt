package org.key_project.key.api.client

/**
 * Represents a value of one of two possible types, A or B.
 * Conventionally, Left is used for error/failure cases and Right for success cases.
 * 
 * @author Alexander Weigl 
 * @version 1 (22.06.26)
 */
sealed class Either<out A, out B> {

    /** Returns true if this is a Left, false otherwise. */
    abstract fun isLeft(): Boolean

    /** Returns true if this is a Right, false otherwise. */
    abstract fun isRight(): Boolean

    /** Returns the value if this is a Left, throws NoSuchElementException if this is a Right. */
    abstract fun getLeft(): A

    /** Returns the value if this is a Right, throws NoSuchElementException if this is a Left. */
    abstract fun getRight(): B

    /** Returns the value if this is a Left, or the default value if this is a Right. */
    abstract fun getLeftOrNull(): A?

    /** Returns the value if this is a Right, or the default value if this is a Left. */
    abstract fun getRightOrNull(): B?

    abstract fun getLeftOrElse(default: @UnsafeVariance A): A
    abstract fun getRightOrElse(default: @UnsafeVariance B): B

    /**
     * Applies [fnL] if this is a Left, or [fnR] if this is a Right.
     * This is the canonical way to extract a value of type C from an Either.
     */
    abstract fun <C> fold(fnL: (A) -> C, fnR: (B) -> C): C

    /**
     * Maps the Right value using the given function.
     * If this is a Left, returns the same Left unchanged.
     */
    abstract fun <C> map(fn: (B) -> C): Either<A, C>

    /**
     * Maps the Left value using the given function.
     * If this is a Right, returns the same Right unchanged.
     */
    abstract fun <C> mapLeft(fn: (A) -> C): Either<C, B>

    /**
     * Flatmaps the Right value using the given function.
     * If this is a Left, returns the same Left unchanged.
     */
    abstract fun <C> flatMap(fn: (B) -> Either<@UnsafeVariance A, C>): Either<A, C>

    /**
     * Returns this Either if it's a Left, or the result of calling [default] if it's a Right.
     */
    abstract fun leftOrElse(default: () -> @UnsafeVariance A): A

    /**
     * Returns this Either if it's a Right, or the result of calling [default] if it's a Left.
     */
    abstract fun rightOrElse(default: () -> @UnsafeVariance B): B

    companion object {
        /** Creates a Left containing the given value. */
        fun <A, B> left(value: A): Either<A, B> = Left(value)

        /** Creates a Right containing the given value. */
        fun <A, B> right(value: B): Either<A, B> = Right(value)
    }
}

/**
 * Represents the Left side of an Either.
 */
class Left<out A, out B>(private val value: A) : Either<A, B>() {
    override fun isLeft(): Boolean = true
    override fun isRight(): Boolean = false
    override fun getLeft(): A = value
    override fun getRight(): B = throw NoSuchElementException("Cannot get Right value from Left")
    override fun getLeftOrNull() = value
    override fun getRightOrNull() = null
    override fun getLeftOrElse(default: @UnsafeVariance A): A = value
    override fun getRightOrElse(default: @UnsafeVariance B): B = default

    override fun <C> fold(fnL: (A) -> C, fnR: (B) -> C): C = fnL(value)

    override fun <C> map(fn: (B) -> C): Either<A, C> = Either.left(value)
    override fun <C> mapLeft(fn: (A) -> C): Either<C, B> = Either.left(fn(value))
    override fun <C> flatMap(fn: (B) -> Either<@UnsafeVariance A, C>): Either<A, C> = Either.left(value)

    override fun leftOrElse(default: () -> @UnsafeVariance A): A = value
    override fun rightOrElse(default: () -> @UnsafeVariance B): B = default()

    override fun equals(other: Any?): Boolean = other is Left<*, *> && other.value == value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = "Left($value)"
}

/**
 * Represents the Right side of an Either.
 */
class Right<out A, out B>(private val value: B) : Either<A, B>() {
    override fun isLeft(): Boolean = false
    override fun isRight(): Boolean = true
    override fun getLeft(): A = throw NoSuchElementException("Cannot get Left value from Right")
    override fun getRight(): B = value
    override fun getLeftOrElse(default: @UnsafeVariance A): A = default
    override fun getRightOrElse(default: @UnsafeVariance B): B = value

    override fun getLeftOrNull(): A? = null
    override fun getRightOrNull(): B? = value

    override fun <C> fold(fnL: (A) -> C, fnR: (B) -> C): C = fnR(value)

    override fun <C> map(fn: (B) -> C): Either<A, C> = Right(fn(value))
    override fun <C> mapLeft(fn: (A) -> C): Either<C, B> = Right(value)
    override fun <C> flatMap(fn: (B) -> Either<@UnsafeVariance A, C>): Either<A, C> = fn(value)

    override fun leftOrElse(default: () -> @UnsafeVariance A): A = default()
    override fun rightOrElse(default: () -> @UnsafeVariance B): B = value

    override fun equals(other: Any?): Boolean = other is Right<*, *> && other.value == value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = "Right($value)"
}