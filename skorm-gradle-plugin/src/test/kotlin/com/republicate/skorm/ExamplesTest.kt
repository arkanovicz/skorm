package com.republicate.skorm

import org.gradle.testkit.runner.GradleRunner
import org.junit.Test
import java.io.File

class ExamplesTest {
    @Test
    fun bookshelfTest() {
        val projectDir = File("../examples")
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withDebug(true)
            // .withArguments(":bookshelf:build")
            .withArguments(":build")
            .forwardOutput()
            .build()
    }
}
