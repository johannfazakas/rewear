package com.rewear.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import kotlin.test.Test

class CatalogPackageBoundaryTest {

    private val subdomains = listOf("listings", "categories", "favorites")

    @Test
    fun `given a catalog subdomain when it imports a sibling then it never targets that sibling's internal package`() {
        Konsist.scopeFromProject()
            .files
            .filter { file -> file.packagee?.name?.startsWith("com.rewear.catalog.") == true }
            .assertFalse { file ->
                val owner = subdomains.firstOrNull { subdomain ->
                    file.packagee?.name?.startsWith("com.rewear.catalog.$subdomain") == true
                }
                file.imports.any { import ->
                    subdomains.any { subdomain ->
                        subdomain != owner && import.name.startsWith("com.rewear.catalog.$subdomain.internal")
                    }
                }
            }
    }

    @Test
    fun `given a class in an internal package when checked then it carries the internal modifier`() {
        Konsist.scopeFromProject()
            .classes()
            .filter { it.packagee?.name?.contains(".internal") == true }
            .assertTrue { it.hasInternalModifier }
    }
}
