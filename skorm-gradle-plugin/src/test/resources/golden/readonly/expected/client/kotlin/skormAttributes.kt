package shapes.model

import com.republicate.skorm.*
import kotlinx.datetime.*
import com.republicate.kson.Json


// attribute registrations for client, called from initialize(): the read-only database's, then the mutable one's when it exists
internal actual fun ShapesDatabase.registerAttributes() {
    // forward foreign key attribute
    ShapesDatabase.main.entity("friendship").instanceAttributes.rowAttribute<ShapesDatabase.MainSchema.Person>("a", setOf("a_id"), ShapesDatabase.MainSchema.Person)
    // reverse foreign key attribute
    ShapesDatabase.main.entity("person").instanceAttributes.rowSetAttribute<ShapesDatabase.MainSchema.Friendship>("aFriendships", setOf("person_id"), ShapesDatabase.MainSchema.Friendship)
    // forward foreign key attribute
    ShapesDatabase.main.entity("friendship").instanceAttributes.rowAttribute<ShapesDatabase.MainSchema.Person>("b", setOf("b_id"), ShapesDatabase.MainSchema.Person)
    // reverse foreign key attribute
    ShapesDatabase.main.entity("person").instanceAttributes.rowSetAttribute<ShapesDatabase.MainSchema.Friendship>("bFriendships", setOf("person_id"), ShapesDatabase.MainSchema.Friendship)
    // forward foreign key attribute
    ShapesDatabase.main.entity("gift").instanceAttributes.rowAttribute<ShapesDatabase.MainSchema.Friendship>("friendship", setOf("a_id","b_id"), ShapesDatabase.MainSchema.Friendship)
    // reverse foreign key attribute
    ShapesDatabase.main.entity("friendship").instanceAttributes.rowSetAttribute<ShapesDatabase.MainSchema.Gift>("gifts", setOf("a_id","b_id"), ShapesDatabase.MainSchema.Gift)
    // forward foreign key attribute
    ShapesDatabase.main.entity("address").instanceAttributes.rowAttribute<ShapesDatabase.MainSchema.Person>("owner", setOf("owner"), ShapesDatabase.MainSchema.Person)
    // reverse foreign key attribute
    ShapesDatabase.main.entity("person").instanceAttributes.rowSetAttribute<ShapesDatabase.MainSchema.Address>("ownerAddresses", setOf("person_id"), ShapesDatabase.MainSchema.Address)
    // forward foreign key attribute
    ShapesDatabase.main.entity("address").instanceAttributes.nullableRowAttribute<ShapesDatabase.MainSchema.Person>("backup", setOf("backup"), ShapesDatabase.MainSchema.Person)
    // forward foreign key attribute
    ShapesDatabase.main.entity("address").instanceAttributes.rowAttribute<ShapesDatabase.MainSchema.Country>("country", setOf("code"), ShapesDatabase.MainSchema.Country)
    // reverse foreign key attribute
    ShapesDatabase.main.entity("country").instanceAttributes.rowSetAttribute<ShapesDatabase.MainSchema.Address>("addresses", setOf("code"), ShapesDatabase.MainSchema.Address)
    // left to right n-n join attribute
    ShapesDatabase.main.entity("person").instanceAttributes.rowSetAttribute<ShapesDatabase.MainSchema.Address>("addresses", setOf("person_id"), ShapesDatabase.MainSchema.Address)
    // right to left n-n join attribute
    ShapesDatabase.main.entity("address").instanceAttributes.rowSetAttribute<ShapesDatabase.MainSchema.Person>("persons", setOf("address_id"), ShapesDatabase.MainSchema.Person)
    // forward foreign key attribute
    ShapesDatabase.other.entity("badge").instanceAttributes.rowAttribute<ShapesDatabase.MainSchema.Person>("person", setOf("person_id"), ShapesDatabase.MainSchema.Person)
    // reverse foreign key attribute
    ShapesDatabase.main.entity("person").instanceAttributes.rowSetAttribute<ShapesDatabase.OtherSchema.Badge>("badges", setOf("person_id"), ShapesDatabase.OtherSchema.Badge)

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
