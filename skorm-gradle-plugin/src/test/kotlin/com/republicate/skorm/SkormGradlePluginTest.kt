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
        assert(project.tasks.getByName(GEN_TASK_NAME) is GenerateSkormCodeTask)
    }

    @Test
    fun `extension is created correctly`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.republicate.skorm")

        Assertions.assertNotNull(project.extensions.getByName("skorm"))
    }

    @Test
    fun `parameters are passed correctly from extension to task`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.republicate.skorm")
        val outDir = File(project.projectDir, "out")
        (project.extensions.getByName("skorm") as SkormParams).apply {
            datasource.set("src/test/resources/model.kddl")
            destPackage.set("com.republicate.skorm.example")
            outputDirectory.set(outDir)
            client.set(true)
        }

        val task = project.tasks.getByName(GEN_TASK_NAME) as GenerateSkormCodeTask

        Assertions.assertEquals("src/test/resources/model.kddl", task.datasource.get())
        Assertions.assertEquals("com.republicate.skorm.example", task.destPackage.get())
        Assertions.assertEquals(outDir, task.outputDirectory.get().asFile)
        // a declared value forces, whatever the project's targets say
        Assertions.assertEquals(true, task.client.get())
    }

    @Test
    fun `core and client are left to derivation when not declared`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.republicate.skorm")
        val task = project.tasks.getByName(GEN_TASK_NAME) as GenerateSkormCodeTask
        Assertions.assertNull(task.core.orNull)
        Assertions.assertNull(task.client.orNull)
    }
}
