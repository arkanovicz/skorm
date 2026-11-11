package com.republicate.skorm

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File

class ExamplesTest {
    @Test
    fun bookshelfTest() = run(":bookshelf:jvmTest")

    /** a kddl schema importing the bookshelf's */
    @Test
    fun clubTest() = run(":club:test")

    private fun run(task: String) {
        val projectDir = File("../examples")
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(task)
            .forwardOutput()
            .build()
        val outcome = result.task(task)?.outcome
        Assertions.assertNotNull(outcome)
        Assertions.assertTrue(setOf(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE, TaskOutcome.FROM_CACHE).contains(outcome))
    }
}
