package io.github.perforators

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This is an internal condition API " +
        "that should not be used from outside of condition."
)
annotation class InternalConditionApi
