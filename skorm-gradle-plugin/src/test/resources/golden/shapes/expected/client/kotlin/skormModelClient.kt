package shapes.model

import kotlinx.datetime.*
import com.republicate.skorm.*
import com.republicate.kson.Json


// runtime model attributes declaration for client
fun shapes.model.ShapesDatabase.initRuntimeModel() {

    // attribute main.personCount
    ShapesDatabase.main.scalarAttribute<Int>("personCount", setOf())

    // attribute main.oldestBirth
    ShapesDatabase.main.scalarAttribute<LocalDate?>("oldestBirth", setOf())

    // attribute main.summary
    ShapesDatabase.main.scalarAttribute<Json.Object>("summary", setOf())

    // attribute main.everybody
    ShapesDatabase.main.rowSetAttribute<ShapesDatabase.MainSchema.Person>("everybody", setOf(), ShapesDatabase.MainSchema.Person::new)

    // attribute Person.mainAddress
    ShapesDatabase.main.entity("person").instanceAttributes.nullableInstanceAttribute<ShapesDatabase.MainSchema.Address>("mainAddress", setOf("person_id"), ShapesDatabase.MainSchema.Address::new)

    // attribute Person.counts
    ShapesDatabase.main.entity("person").instanceAttributes.rowAttribute<Counts>("counts", setOf("person_id"), ::Counts)

    // attribute Person.cities
    ShapesDatabase.main.entity("person").instanceAttributes.scalarAttribute<String>("cities", setOf("person_id"))
}
