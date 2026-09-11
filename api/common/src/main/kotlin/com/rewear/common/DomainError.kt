package com.rewear.common

sealed interface DomainError {
    val message: String

    data class NotFound(override val message: String) : DomainError

    data class Conflict(override val message: String) : DomainError

    data class Validation(override val message: String) : DomainError
}
