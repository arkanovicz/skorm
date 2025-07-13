package com.republicate.skorm

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File

class SkormGradlePluginTest {

    @Test
    fun `plugin is applied correctly to the project`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.republicate.skorm")
        assert(project.tasks.getByName("generateSkormObjectsCode") is GenerateObjectsCodeTask)
    }

    @Test
    fun `extension templateExampleConfig is created correctly`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.republicate.skorm")

        Assertions.assertNotNull(project.extensions.getByName("skorm"))
    }

    @Test
    fun `parameters are passed correctly from extension to task`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.republicate.skorm")
        val aFile = File(project.projectDir, "example.kt")
        (project.extensions.getByName("skorm") as SkormParams).apply {
            datasource.set("src/test/resources/model.kddl")
            destPackage.set("com.republicate.skorm.example")
            destStructureFile.set(aFile)
        }

        val task = project.tasks.getByName("generateSkormObjectsCode") as GenerateObjectsCodeTask

        Assertions.assertEquals("src/test/resources/model.kddl", task.datasource.get())
        Assertions.assertEquals("com.republicate.skorm.example", task.destPackage.get())
        Assertions.assertEquals(aFile, task.destFile.get().asFile)
    }
}
