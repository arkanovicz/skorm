package com.republicate.skorm

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

class SkormGradlePluginTest {

    @Test
    fun `plugin is applied correctly to the project`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("skorm-gradle-plugin")
        assert(project.tasks.getByName("skormCodeGeneration") is CodeGenTask)
    }

    @Test
    fun `extension templateExampleConfig is created correctly`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("skorm-gradle-plugin")

        assertNotNull(project.extensions.getByName("skorm"))
    }

    @Test
    fun `parameters are passed correctly from extension to task`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("skorm-gradle-plugin")
        val aFile = File(project.projectDir, "example.kt")
        (project.extensions.getByName("skorm") as CodeGenParams).apply {
            source.set("src/test/resources/model.kddl")
            destPackage.set("com.republicate.skorm.example")
            destFile.set(aFile)
        }

        val task = project.tasks.getByName("skormCodeGeneration") as CodeGenTask

        assertEquals("src/test/resources/model.kddl", task.source.get())
        assertEquals("com.republicate.skorm.example", task.destPackage.get())
        assertEquals(aFile, task.destFile.get().asFile)
    }
}
