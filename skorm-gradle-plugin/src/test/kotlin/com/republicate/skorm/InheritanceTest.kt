package com.republicate.skorm

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Generated inheritance, end to end: a consumer built with TestKit generates and compiles `Vip : Person`,
 * then its own test runs against a Testcontainers PostgreSQL — HyperSQL has no table inheritance.
 * Skipped only where there is no Docker at all; a Docker present but unreachable fails, loudly.
 */
class InheritanceTest {

    @TempDir
    lateinit var dir: File

    @Test
    fun `rows of a hierarchy come back as their kind, and a subtype answers to its parent's navigations`() {
        assumeTrue(System.getenv("SKORM_SKIP_PG_TESTS") == null, "SKORM_SKIP_PG_TESTS set")
        assumeTrue(System.getenv("DOCKER_HOST") != null || File("/var/run/docker.sock").exists(), "no Docker on this host")

        val skorm = File("..").canonicalFile.invariantSeparatorsPath
        dir.resolve("settings.gradle.kts").writeText("""
            rootProject.name = "inheritance"
            pluginManagement {
                repositories { gradlePluginPortal(); mavenCentral(); mavenLocal() }
                includeBuild("$skorm")
            }
            dependencyResolutionManagement { repositories { mavenCentral(); mavenLocal() } }
            includeBuild("$skorm")
        """.trimIndent())
        dir.resolve("src/main/model").mkdirs()
        dir.resolve("src/test/kotlin").mkdirs()
        dir.resolve("build.gradle.kts").writeText("""
            plugins {
                kotlin("jvm") version "2.4.0"
                id("com.republicate.skorm")
            }
            kotlin { jvmToolchain(21) }
            skorm {
                model.set(file("src/main/model/inh.kddl"))
                destPackage.set("inh.model")
                dialect.set("postgresql")
            }
            dependencies {
                implementation("com.republicate.skorm:skorm-common")
                implementation("com.republicate.skorm:skorm-core")
                implementation("com.republicate.skorm:skorm-jdbc")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
                implementation("io.github.oshai:kotlin-logging:8.0.4")
                runtimeOnly("org.postgresql:postgresql:42.7.4")
                runtimeOnly("org.slf4j:slf4j-simple:2.0.16")
                testImplementation(kotlin("test"))
                testImplementation("org.testcontainers:postgresql:1.20.4")
            }
            tasks.test {
                useJUnitPlatform()
                testLogging { exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
            }
        """.trimIndent())
        dir.resolve("src/main/model/inh.kddl").writeText("""
            database inh {
              schema main {
                table person { name varchar(50) }
                table vip : person { since date }
                table address {
                  city varchar(50)
                  owner -- person
                }
              }
            }
        """.trimIndent())
        dir.resolve("src/test/kotlin/InheritanceCheck.kt").writeText("""
            import com.republicate.kson.toJsonObject
            import com.republicate.skorm.core.CoreProcessor
            import com.republicate.skorm.core.mutationAttribute
            import com.republicate.skorm.jdbc.JdbcConnector
            import inh.model.*
            import kotlinx.coroutines.runBlocking
            import kotlinx.datetime.LocalDate
            import org.testcontainers.containers.PostgreSQLContainer
            import kotlin.test.Test
            import kotlin.test.assertEquals
            import kotlin.test.assertIs
            import kotlin.test.assertIsNot

            typealias Person = InhDatabase.MainSchema.Person
            typealias Vip = InhDatabase.MainSchema.Vip
            typealias Address = InhDatabase.MainSchema.Address
            typealias PersonKind = InhDatabase.MainSchema.PersonKind
            typealias MutablePerson = MutableInhDatabase.MutableMainSchema.MutablePerson
            typealias MutableVip = MutableInhDatabase.MutableMainSchema.MutableVip
            typealias MutableAddress = MutableInhDatabase.MutableMainSchema.MutableAddress

            class InheritanceCheck {
                @Test
                fun check() {
                    PostgreSQLContainer("postgres:16-alpine").use { pg ->
                        pg.start()
                        val db = MutableInhDatabase(CoreProcessor(JdbcConnector()))
                        db.configure(mapOf("core" to mapOf("jdbc" to mapOf(
                            "url" to pg.jdbcUrl, "login" to pg.username, "password" to pg.password))).toJsonObject())
                        db.initialize()
                        db.mutationAttribute("create", InheritanceCheck::class.java.getResource("/create-script.sql")!!.readText())
                        runBlocking {
                            db.perform("create")
                            val alice = MutablePerson.new().apply { name = "Alice" }.also { it.insert() }
                            val bob = MutableVip.new().apply { name = "Bob"; since = LocalDate(2020, 1, 1) }.also { it.insert() }
                            MutableAddress.new().apply { city = "Paris"; owner = bob.personId }.insert()

                            // browse through the mutable database: every row as its kind, a Vip complete with its own columns
                            val all = MutablePerson.browse().toList()
                            assertEquals(2, all.size)
                            val bobRow = all.single { it.name == "Bob" }
                            assertIs<MutableVip>(bobRow)
                            assertEquals(PersonKind.vip, bobRow.kind)
                            assertEquals(LocalDate(2020, 1, 1), bobRow.since)
                            assertIsNot<Vip>(all.single { it.name == "Alice" })
                            // a new row knows its kind, and so does its fetched twin
                            assertEquals(PersonKind.person, alice.kind)
                            assertEquals(PersonKind.vip, bob.kind)
                            assertEquals(PersonKind.person, MutablePerson.fetch(alice.personId)!!.kind)

                            // fetch, and a navigation to a hierarchy member
                            assertIs<MutableVip>(MutablePerson.fetch(bob.personId))
                            assertIs<MutableVip>(MutableAddress.browse().first().owner())

                            // an inherited navigation, asked of a Vip: registered on person, found from vip
                            assertEquals(listOf("Paris"), bob.addresses().map { it.city }.toList())

                            // the read-only sibling reads the same rows as read-only types: a Vip, but nothing mutable
                            assertIs<InhDatabase>(db.readOnly)
                            val readOnlyBob = Person.fetch(bob.personId)!!
                            assertIs<Vip>(readOnlyBob)
                            assertIsNot<com.republicate.skorm.MutableInstance>(readOnlyBob)
                            assertEquals(listOf("Paris"), readOnlyBob.addresses().map { it.city }.toList())
                            assertEquals(2, Person.browse().count())
                        }
                    }
                }
            }
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(dir)
            .withArguments(":test")
            .forwardOutput()
            .build()
        assertTrue(result.task(":test")?.outcome in setOf(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE))
    }
}
