package shapes.model

import kotlinx.datetime.*
import com.republicate.skorm.*
import com.republicate.kson.Json


// runtime model attributes declaration for client: the read-only database's, then the mutable one's when it exists
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
    if (!MutableShapesDatabase.isInstantiated()) return

    // attribute main.personCount
    MutableShapesDatabase.main.scalarAttribute<Int>("personCount", setOf())

    // attribute main.oldestBirth
    MutableShapesDatabase.main.scalarAttribute<LocalDate?>("oldestBirth", setOf())

    // attribute main.summary
    MutableShapesDatabase.main.scalarAttribute<Json.Object>("summary", setOf())

    // attribute main.everybody
    MutableShapesDatabase.main.rowSetAttribute<MutableShapesDatabase.MutableMainSchema.MutablePerson>("everybody", setOf(), MutableShapesDatabase.MutableMainSchema.MutablePerson::new)

    // attribute Person.mainAddress
    MutableShapesDatabase.main.entity("person").instanceAttributes.nullableInstanceAttribute<MutableShapesDatabase.MutableMainSchema.MutableAddress>("mainAddress", setOf("person_id"), MutableShapesDatabase.MutableMainSchema.MutableAddress::new)

    // attribute Person.forget
    MutableShapesDatabase.main.entity("person").instanceAttributes.mutationAttribute("forget", setOf("person_id"))

    // attribute Person.counts
    MutableShapesDatabase.main.entity("person").instanceAttributes.rowAttribute<Counts>("counts", setOf("person_id"), ::Counts)

    // attribute Person.cities
    MutableShapesDatabase.main.entity("person").instanceAttributes.scalarAttribute<String>("cities", setOf("person_id"))
}
