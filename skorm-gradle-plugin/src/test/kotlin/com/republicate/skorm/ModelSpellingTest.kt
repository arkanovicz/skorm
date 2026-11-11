package com.republicate.skorm

import com.republicate.kddl.SearchPathResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * A model is spelled as its author likes, kddl writes its SQL in snake: whatever the spelling, the generated code
 * names the same columns and the same classes.
 */
class ModelSpellingTest {

    @TempDir
    lateinit var dir: File

    private val camel = """
        database myShop {
          schema frontDesk {
            enum orderState(open, closed)
            table customerAccount {
              *id serial
              firstName varchar(50)
              state orderState
              taste enum('sweet', 'sour')
            }
            table deliveryNote {
              *id serial
              shippedAt timestamp?
              backupAccount -> customerAccount?
            }
            table productTag {
              *id serial
              label varchar(20)
            }
            table person {
              *id serial
              lastName varchar(50)
            }
            table vip : person {
              loyaltyPoints integer
            }
            deliveryNote *-- customerAccount     // both keyed `id`: kddl names the column itself
            customerAccount *-* productTag       // and both columns of the join table
          }
        }
    """.trimIndent()

    private val snake = camel
        .replace("myShop", "my_shop").replace("frontDesk", "front_desk").replace("orderState", "order_state")
        .replace("customerAccount", "customer_account").replace("firstName", "first_name")
        .replace("deliveryNote", "delivery_note").replace("shippedAt", "shipped_at")
        .replace("backupAccount", "backup_account").replace("productTag", "product_tag")
        .replace("lastName", "last_name").replace("loyaltyPoints", "loyalty_points")

    // SQL is written against the database, in snake; a receiver or a type is the Kotlin class
    private val attributes = """
        database myShop {
          schema frontDesk {
            attr CustomerAccount.notesCount: Int =
              SELECT count(*) FROM delivery_note WHERE customer_account_id = {id};
            attr CustomerAccount.greeting(salutation: String): String =
              SELECT {salutation} || ' ' || {first_name};
            attr DeliveryNote.sameDay: DeliveryNote* =
              SELECT * FROM delivery_note WHERE shipped_at = {shipped_at};
          }
        }
    """.trimIndent()

    private fun generate(name: String, model: String): Map<String, String> {
        // one file name for both: the shipped files are named after it
        val source = dir.resolve("$name/shop.kddl").apply { parentFile.mkdirs(); writeText(model) }
        val ksql = dir.resolve("$name/shop.ksql").apply { writeText(attributes) }
        val out = dir.resolve("$name/out")
        generateInto(out, source, ksql, "shop.model", "postgresql")
        // the creation script opens with a comment quoting the database as written, the shipped kddl schema is the author's
        return out.filesByPath().filterKeys { !it.startsWith("common/resources/${SearchPathResolver.ARTIFACT_DIR}/") }
            .mapValues { it.value.readLines().filterNot { line -> line.startsWith("-- database ") }.joinToString("\n") }
    }

    @Test
    fun `a model written in camel generates what the same model written in snake does`() {
        val fromCamel = generate("camel", camel)
        val fromSnake = generate("snake", snake)
        assertEquals(fromSnake.keys, fromCamel.keys)
        for ((path, text) in fromSnake) assertEquals(text, fromCamel.getValue(path), path)

        // and what that is: snake in SQL, camel in Kotlin
        val objects = fromCamel.getValue("common/kotlin/skormObjects.kt")
        val registrations = fromCamel.getValue("core/kotlin/skormAttributes.kt")
        fun assertContains(text: String, part: String) = assertTrue(part in text) { "expected `$part` in:\n$text" }
        assertContains(objects, "val firstName: String")
        assertContains(objects, "Field(\"customerAccountId\"")
        assertContains(objects, "Schema(\"front_desk\"")
        assertContains(registrations, "FROM front_desk.delivery_note WHERE delivery_note.customer_account_id = {id}")
        assertContains(registrations, "join_table.product_tag_id WHERE join_table.customer_account_id = {id}")
        assertContains(registrations, "entity(\"customerAccount\").instanceAttributes.scalarAttribute<Int>(\"notesCount\"")
    }
}
