package org.elly.core.errors

sealed class DomainException(msg: String): RuntimeException(msg)
class MissingConsent(msg: String): DomainException(msg)
class MissingBankLink(msg: String): DomainException(msg)
class ReserveNotFound(msg: String): DomainException(msg)
