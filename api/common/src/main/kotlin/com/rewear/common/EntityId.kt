package com.rewear.common

import java.util.UUID

interface EntityId {
    val value: UUID
}

fun newId(): UUID = UUID.randomUUID()
