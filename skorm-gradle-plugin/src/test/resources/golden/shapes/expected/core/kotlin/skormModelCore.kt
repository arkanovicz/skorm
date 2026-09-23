package shapes.model

import kotlinx.datetime.*
import com.republicate.skorm.core.*
import com.republicate.kson.Json


// runtime model attributes declaration for core
fun shapes.model.ShapesDatabase.initRuntimeModel() {

    // attribute main.personCount
    ShapesDatabase.main.scalarAttribute<Int>("personCount", """
        SELECT count(*) FROM person;""".trimIndent())


    // attribute main.oldestBirth
    ShapesDatabase.main.scalarAttribute<LocalDate?>("oldestBirth", """
        SELECT min(born) FROM person;""".trimIndent())


    // attribute main.summary
    ShapesDatabase.main.rowAttribute<Json.MutableObject>("summary", """
        SELECT count(*) AS persons FROM person;""".trimIndent())


    // attribute main.everybody
    ShapesDatabase.main.rowSetAttribute<ShapesDatabase.MainSchema.Person>("everybody", """
        SELECT * FROM person;""".trimIndent(), ShapesDatabase.MainSchema.Person::new)


    // attribute Person.mainAddress
    ShapesDatabase.main.entity("person").instanceAttributes.nullableRowAttribute<ShapesDatabase.MainSchema.Address>("mainAddress", """
        SELECT * FROM address WHERE owner = {person_id} LIMIT 1;""".trimIndent(), ShapesDatabase.MainSchema.Address::new)


    // attribute Person.forget
    ShapesDatabase.main.entity("person").instanceAttributes.mutationAttribute("forget", """
        DELETE FROM address WHERE owner = {person_id};
DELETE FROM person WHERE person_id = {person_id};""".trimIndent())


    // attribute Person.counts
    ShapesDatabase.main.entity("person").instanceAttributes.rowAttribute<Counts>("counts", """
        SELECT (SELECT count(*) FROM address WHERE owner = {person_id}) addresses,
             (SELECT count(*) FROM friendship WHERE a_id = {person_id}) friends;""".trimIndent(), ::Counts)


    // attribute Person.cities
    ShapesDatabase.main.entity("person").instanceAttributes.rowSetAttribute<Json.MutableObject>("cities", """
        SELECT city FROM address WHERE owner = {person_id};""".trimIndent())

}
