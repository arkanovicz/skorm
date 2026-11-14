package importer.model

import com.republicate.skorm.*
import kotlinx.datetime.*
import com.republicate.kson.Json


// attribute registrations for client, called from initialize(): the read-only database's, then the mutable one's when it exists
internal actual fun RepublicateDatabase.registerAttributes() {
    // forward foreign key attribute
    RepublicateDatabase.republicate.entity("decision").instanceAttributes.rowAttribute<net.arkad.model.ArkadDatabase.ArkadSchema.Message>("message", setOf("message"), net.arkad.model.ArkadDatabase.ArkadSchema.Message)
    // forward foreign key attribute
    RepublicateDatabase.republicate.entity("decision").instanceAttributes.nullableRowAttribute<net.arkad.model.ArkadDatabase.ArkadSchema.Message>("second", setOf("second"), net.arkad.model.ArkadDatabase.ArkadSchema.Message)
    // forward foreign key attribute
    RepublicateDatabase.republicate.entity("ballot").instanceAttributes.rowAttribute<RepublicateDatabase.RepublicateSchema.Decision>("decision", setOf("decision_id"), RepublicateDatabase.RepublicateSchema.Decision)
    // reverse foreign key attribute
    RepublicateDatabase.republicate.entity("decision").instanceAttributes.rowSetAttribute<RepublicateDatabase.RepublicateSchema.Ballot>("ballots", setOf("id"), RepublicateDatabase.RepublicateSchema.Ballot)
    // left to right n-n join attribute
    RepublicateDatabase.republicate.entity("tag").instanceAttributes.rowSetAttribute<net.arkad.model.ArkadDatabase.ArkadSchema.Room>("rooms", setOf("id"), net.arkad.model.ArkadDatabase.ArkadSchema.Room)

    // attribute Decision.thread
    RepublicateDatabase.republicate.entity("decision").instanceAttributes.rowSetAttribute<net.arkad.model.ArkadDatabase.ArkadSchema.Message>("thread", setOf("message"), net.arkad.model.ArkadDatabase.ArkadSchema.Message::new)

    // attribute Decision.subject
    RepublicateDatabase.republicate.entity("decision").instanceAttributes.rowAttribute<Subject>("subject", setOf("id","message"), ::Subject)
    if (!MutableRepublicateDatabase.isInstantiated()) return
    // forward foreign key attribute
    MutableRepublicateDatabase.republicate.entity("decision").instanceAttributes.rowAttribute<net.arkad.model.MutableArkadDatabase.MutableArkadSchema.MutableMessage>("message", setOf("message"), net.arkad.model.MutableArkadDatabase.MutableArkadSchema.MutableMessage)
    // forward foreign key attribute
    MutableRepublicateDatabase.republicate.entity("decision").instanceAttributes.nullableRowAttribute<net.arkad.model.MutableArkadDatabase.MutableArkadSchema.MutableMessage>("second", setOf("second"), net.arkad.model.MutableArkadDatabase.MutableArkadSchema.MutableMessage)
    // forward foreign key attribute
    MutableRepublicateDatabase.republicate.entity("ballot").instanceAttributes.rowAttribute<MutableRepublicateDatabase.MutableRepublicateSchema.MutableDecision>("decision", setOf("decision_id"), MutableRepublicateDatabase.MutableRepublicateSchema.MutableDecision)
    // reverse foreign key attribute
    MutableRepublicateDatabase.republicate.entity("decision").instanceAttributes.rowSetAttribute<MutableRepublicateDatabase.MutableRepublicateSchema.MutableBallot>("ballots", setOf("id"), MutableRepublicateDatabase.MutableRepublicateSchema.MutableBallot)
    // left to right n-n join attribute
    MutableRepublicateDatabase.republicate.entity("tag").instanceAttributes.rowSetAttribute<net.arkad.model.MutableArkadDatabase.MutableArkadSchema.MutableRoom>("rooms", setOf("id"), net.arkad.model.MutableArkadDatabase.MutableArkadSchema.MutableRoom)

    // attribute Decision.thread
    MutableRepublicateDatabase.republicate.entity("decision").instanceAttributes.rowSetAttribute<net.arkad.model.MutableArkadDatabase.MutableArkadSchema.MutableMessage>("thread", setOf("message"), net.arkad.model.MutableArkadDatabase.MutableArkadSchema.MutableMessage::new)

    // attribute Decision.subject
    MutableRepublicateDatabase.republicate.entity("decision").instanceAttributes.rowAttribute<Subject>("subject", setOf("id","message"), ::Subject)
}
