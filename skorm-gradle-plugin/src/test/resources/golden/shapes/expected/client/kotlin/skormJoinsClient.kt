package shapes.model

import com.republicate.skorm.*


// navigations declaration for client: the read-only database's, then the mutable one's when it exists
fun ShapesDatabase.initJoins() {
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
    if (!MutableShapesDatabase.isInstantiated()) return
    // forward foreign key attribute
    MutableShapesDatabase.main.entity("friendship").instanceAttributes.rowAttribute<MutableShapesDatabase.MutableMainSchema.MutablePerson>("a", setOf("a_id"), MutableShapesDatabase.MutableMainSchema.MutablePerson)
    // reverse foreign key attribute
    MutableShapesDatabase.main.entity("person").instanceAttributes.rowSetAttribute<MutableShapesDatabase.MutableMainSchema.MutableFriendship>("aFriendships", setOf("person_id"), MutableShapesDatabase.MutableMainSchema.MutableFriendship)
    // forward foreign key attribute
    MutableShapesDatabase.main.entity("friendship").instanceAttributes.rowAttribute<MutableShapesDatabase.MutableMainSchema.MutablePerson>("b", setOf("b_id"), MutableShapesDatabase.MutableMainSchema.MutablePerson)
    // reverse foreign key attribute
    MutableShapesDatabase.main.entity("person").instanceAttributes.rowSetAttribute<MutableShapesDatabase.MutableMainSchema.MutableFriendship>("bFriendships", setOf("person_id"), MutableShapesDatabase.MutableMainSchema.MutableFriendship)
    // forward foreign key attribute
    MutableShapesDatabase.main.entity("gift").instanceAttributes.rowAttribute<MutableShapesDatabase.MutableMainSchema.MutableFriendship>("friendship", setOf("a_id","b_id"), MutableShapesDatabase.MutableMainSchema.MutableFriendship)
    // reverse foreign key attribute
    MutableShapesDatabase.main.entity("friendship").instanceAttributes.rowSetAttribute<MutableShapesDatabase.MutableMainSchema.MutableGift>("gifts", setOf("a_id","b_id"), MutableShapesDatabase.MutableMainSchema.MutableGift)
    // forward foreign key attribute
    MutableShapesDatabase.main.entity("address").instanceAttributes.rowAttribute<MutableShapesDatabase.MutableMainSchema.MutablePerson>("owner", setOf("owner"), MutableShapesDatabase.MutableMainSchema.MutablePerson)
    // reverse foreign key attribute
    MutableShapesDatabase.main.entity("person").instanceAttributes.rowSetAttribute<MutableShapesDatabase.MutableMainSchema.MutableAddress>("ownerAddresses", setOf("person_id"), MutableShapesDatabase.MutableMainSchema.MutableAddress)
    // forward foreign key attribute
    MutableShapesDatabase.main.entity("address").instanceAttributes.nullableRowAttribute<MutableShapesDatabase.MutableMainSchema.MutablePerson>("backup", setOf("backup"), MutableShapesDatabase.MutableMainSchema.MutablePerson)
    // forward foreign key attribute
    MutableShapesDatabase.main.entity("address").instanceAttributes.rowAttribute<MutableShapesDatabase.MutableMainSchema.MutableCountry>("country", setOf("code"), MutableShapesDatabase.MutableMainSchema.MutableCountry)
    // reverse foreign key attribute
    MutableShapesDatabase.main.entity("country").instanceAttributes.rowSetAttribute<MutableShapesDatabase.MutableMainSchema.MutableAddress>("addresses", setOf("code"), MutableShapesDatabase.MutableMainSchema.MutableAddress)
    // left to right n-n join attribute
    MutableShapesDatabase.main.entity("person").instanceAttributes.rowSetAttribute<MutableShapesDatabase.MutableMainSchema.MutableAddress>("addresses", setOf("person_id"), MutableShapesDatabase.MutableMainSchema.MutableAddress)
    // right to left n-n join attribute
    MutableShapesDatabase.main.entity("address").instanceAttributes.rowSetAttribute<MutableShapesDatabase.MutableMainSchema.MutablePerson>("persons", setOf("address_id"), MutableShapesDatabase.MutableMainSchema.MutablePerson)
    // forward foreign key attribute
    MutableShapesDatabase.other.entity("badge").instanceAttributes.rowAttribute<MutableShapesDatabase.MutableMainSchema.MutablePerson>("person", setOf("person_id"), MutableShapesDatabase.MutableMainSchema.MutablePerson)
    // reverse foreign key attribute
    MutableShapesDatabase.main.entity("person").instanceAttributes.rowSetAttribute<MutableShapesDatabase.MutableOtherSchema.MutableBadge>("badges", setOf("person_id"), MutableShapesDatabase.MutableOtherSchema.MutableBadge)
}
