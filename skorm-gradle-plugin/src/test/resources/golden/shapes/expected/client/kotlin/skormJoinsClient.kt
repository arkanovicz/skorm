package shapes.model

import com.republicate.skorm.*


// join attributes declaration for client
fun ShapesDatabase.initJoins() {
    // forward foreign key attribute
    main.entity("friendship").instanceAttributes.rowAttribute<ShapesDatabase.MainSchema.Person>("a", setOf("a_id"), ShapesDatabase.MainSchema.Person)
    // reverse foreign key attribute
    main.entity("person").instanceAttributes.rowSetAttribute<ShapesDatabase.MainSchema.Friendship>("aFriendships", setOf("person_id"), ShapesDatabase.MainSchema.Friendship)
    // forward foreign key attribute
    main.entity("friendship").instanceAttributes.rowAttribute<ShapesDatabase.MainSchema.Person>("b", setOf("b_id"), ShapesDatabase.MainSchema.Person)
    // reverse foreign key attribute
    main.entity("person").instanceAttributes.rowSetAttribute<ShapesDatabase.MainSchema.Friendship>("bFriendships", setOf("person_id"), ShapesDatabase.MainSchema.Friendship)
    // forward foreign key attribute
    main.entity("gift").instanceAttributes.rowAttribute<ShapesDatabase.MainSchema.Friendship>("friendship", setOf("a_id","b_id"), ShapesDatabase.MainSchema.Friendship)
    // reverse foreign key attribute
    main.entity("friendship").instanceAttributes.rowSetAttribute<ShapesDatabase.MainSchema.Gift>("gifts", setOf("a_id","b_id"), ShapesDatabase.MainSchema.Gift)
    // forward foreign key attribute
    main.entity("address").instanceAttributes.rowAttribute<ShapesDatabase.MainSchema.Person>("owner", setOf("owner"), ShapesDatabase.MainSchema.Person)
    // reverse foreign key attribute
    main.entity("person").instanceAttributes.rowSetAttribute<ShapesDatabase.MainSchema.Address>("ownerAddresses", setOf("person_id"), ShapesDatabase.MainSchema.Address)
    // forward foreign key attribute
    main.entity("address").instanceAttributes.nullableRowAttribute<ShapesDatabase.MainSchema.Person>("backup", setOf("backup"), ShapesDatabase.MainSchema.Person)
    // forward foreign key attribute
    main.entity("address").instanceAttributes.rowAttribute<ShapesDatabase.MainSchema.Country>("code", setOf("code"), ShapesDatabase.MainSchema.Country)
    // reverse foreign key attribute
    main.entity("country").instanceAttributes.rowSetAttribute<ShapesDatabase.MainSchema.Address>("addresses", setOf("code"), ShapesDatabase.MainSchema.Address)
    // left to right n-n join attribute
    main.entity("person").instanceAttributes.rowSetAttribute<ShapesDatabase.MainSchema.Address>("addresses", setOf("person_id"), ShapesDatabase.MainSchema.Address)
    // right to left n-n join attribute
    main.entity("address").instanceAttributes.rowSetAttribute<ShapesDatabase.MainSchema.Person>("persons", setOf("address_id"), ShapesDatabase.MainSchema.Person)
    // forward foreign key attribute
    other.entity("badge").instanceAttributes.rowAttribute<ShapesDatabase.OtherSchema.Person>("person", setOf("person_id"), ShapesDatabase.OtherSchema.Person)
    // reverse foreign key attribute
    other.entity("person").instanceAttributes.rowSetAttribute<ShapesDatabase.OtherSchema.Badge>("badges", setOf("person_id"), ShapesDatabase.OtherSchema.Badge)
}
