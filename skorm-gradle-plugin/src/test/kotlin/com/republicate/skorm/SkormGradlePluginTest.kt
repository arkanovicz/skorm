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
        project.pluginManager.apply("com.republicate.skorm.skorm-gradle-plugin")
        assert(project.tasks.getByName("SkormCodeGen") is CodeGenTask)
    }

    @Test
    fun `extension templateExampleConfig is created correctly`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.republicate.skorm.skorm-gradle-plugin")

        assertNotNull(project.extensions.getByName("SkormCodeGenConfig"))
    }

    @Test
    fun `parameters are passed correctly from extension to task`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.republicate.skorm.skorm-gradle-plugin")
        val aFile = File(project.projectDir, "example.kt")
        (project.extensions.getByName("SkormCodeGenConfig") as CodeGenParams).apply {
            source.set("src/test/resources/model.kddl")
            destPackage.set("com.republicate.skorm.example")
            destFile.set(aFile)
        }

        val task = project.tasks.getByName("SkormCodeGen") as CodeGenTask

        assertEquals("src/test/resources/model.kddl", task.source.get())
        assertEquals("com.republicate.skorm.example", task.destPackage.get())
        assertEquals(aFile, task.destFile.get().asFile)
    }
}
