package com.republicate.skorm

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File

class ExamplesTest {
    @Test
    fun bookshelfTest() {
        val projectDir = File("../examples")
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withDebug(true)
            .withArguments(":build")
            .forwardOutput()
            .build()
        Assertions.assertEquals(TaskOutcome.SUCCESS, result.task(":build")?.outcome)
    }
}
