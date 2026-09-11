package com.rewear.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture
import com.lemonappdev.konsist.api.architecture.Layer
import kotlin.test.Test

class ModuleBoundaryTest {

    private val common = Layer("common", "com.rewear.common..")
    private val users = Layer("users", "com.rewear.users..")
    private val catalog = Layer("catalog", "com.rewear.catalog..")
    private val app = Layer("app", "com.rewear.app..")

    @Test
    fun `given the module graph when checked then dependencies flow one way with no cycles`() {
        Konsist.scopeFromProject().assertArchitecture {
            common.dependsOnNothing()
            users.dependsOn(common)
            catalog.dependsOn(users, common)
            app.dependsOn(users, catalog, common)
        }
    }

    @Test
    fun `given the users leaf when checked then it does not depend on catalog or app`() {
        Konsist.scopeFromProject().assertArchitecture {
            users.doesNotDependOn(catalog, app)
        }
    }
}
