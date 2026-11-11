package importer.model

import com.republicate.skorm.core.*
import com.republicate.skorm.underlying
import net.arkad.model.createMissingSchemas
import kotlinx.datetime.*
import com.republicate.kson.Json


// attribute registrations for core, called from initialize(): the read-only database's, then the mutable one's when it exists
internal actual fun RepublicateDatabase.registerAttributes() {
    // forward foreign key attribute
    RepublicateDatabase.republicate.entity("decision").instanceAttributes.rowAttribute<net.arkad.model.ArkadDatabase.ArkadSchema.Message>("message", "SELECT * FROM arkad.message WHERE message.id = {message};", net.arkad.model.ArkadDatabase.ArkadSchema.Message)
    // forward foreign key attribute
    RepublicateDatabase.republicate.entity("decision").instanceAttributes.nullableRowAttribute<net.arkad.model.ArkadDatabase.ArkadSchema.Message>("second", "SELECT * FROM arkad.message WHERE message.id = {second};", net.arkad.model.ArkadDatabase.ArkadSchema.Message)
    // forward foreign key attribute
    RepublicateDatabase.republicate.entity("ballot").instanceAttributes.rowAttribute<RepublicateDatabase.RepublicateSchema.Decision>("decision", "SELECT * FROM republicate.decision WHERE decision.id = {decision_id};", RepublicateDatabase.RepublicateSchema.Decision)
    // reverse foreign key attribute
    RepublicateDatabase.republicate.entity("decision").instanceAttributes.rowSetAttribute<RepublicateDatabase.RepublicateSchema.Ballot>("ballots", "SELECT * FROM republicate.ballot WHERE ballot.decision_id = {id};", RepublicateDatabase.RepublicateSchema.Ballot)

    // attribute Decision.thread
    RepublicateDatabase.republicate.entity("decision").instanceAttributes.rowSetAttribute<net.arkad.model.ArkadDatabase.ArkadSchema.Message>("thread", """
        SELECT * FROM arkad.message WHERE id >= {message};""".trimIndent(), net.arkad.model.ArkadDatabase.ArkadSchema.Message::new)

    // attribute Decision.subject
    RepublicateDatabase.republicate.entity("decision").instanceAttributes.rowAttribute<Subject>("subject", """
        SELECT m.*, (SELECT count(*) FROM republicate.ballot WHERE decision_id = {id}) votes
      FROM arkad.message m WHERE m.id = {message};""".trimIndent(), ::Subject)
    if (!MutableRepublicateDatabase.isInstantiated()) return
    // forward foreign key attribute
    MutableRepublicateDatabase.republicate.entity("decision").instanceAttributes.rowAttribute<net.arkad.model.MutableArkadDatabase.MutableArkadSchema.MutableMessage>("message", "SELECT * FROM arkad.message WHERE message.id = {message};", net.arkad.model.MutableArkadDatabase.MutableArkadSchema.MutableMessage)
    // forward foreign key attribute
    MutableRepublicateDatabase.republicate.entity("decision").instanceAttributes.nullableRowAttribute<net.arkad.model.MutableArkadDatabase.MutableArkadSchema.MutableMessage>("second", "SELECT * FROM arkad.message WHERE message.id = {second};", net.arkad.model.MutableArkadDatabase.MutableArkadSchema.MutableMessage)
    // forward foreign key attribute
    MutableRepublicateDatabase.republicate.entity("ballot").instanceAttributes.rowAttribute<MutableRepublicateDatabase.MutableRepublicateSchema.MutableDecision>("decision", "SELECT * FROM republicate.decision WHERE decision.id = {decision_id};", MutableRepublicateDatabase.MutableRepublicateSchema.MutableDecision)
    // reverse foreign key attribute
    MutableRepublicateDatabase.republicate.entity("decision").instanceAttributes.rowSetAttribute<MutableRepublicateDatabase.MutableRepublicateSchema.MutableBallot>("ballots", "SELECT * FROM republicate.ballot WHERE ballot.decision_id = {id};", MutableRepublicateDatabase.MutableRepublicateSchema.MutableBallot)

    // attribute Decision.thread
    MutableRepublicateDatabase.republicate.entity("decision").instanceAttributes.rowSetAttribute<net.arkad.model.MutableArkadDatabase.MutableArkadSchema.MutableMessage>("thread", """
        SELECT * FROM arkad.message WHERE id >= {message};""".trimIndent(), net.arkad.model.MutableArkadDatabase.MutableArkadSchema.MutableMessage::new)

    // attribute Decision.subject
    MutableRepublicateDatabase.republicate.entity("decision").instanceAttributes.rowAttribute<Subject>("subject", """
        SELECT m.*, (SELECT count(*) FROM republicate.ballot WHERE decision_id = {id}) votes
      FROM arkad.message m WHERE m.id = {message};""".trimIndent(), ::Subject)
}

/**
 * Creates the schemas of this kddl schema when the database has none of them, those of its imports first:
 * true when anything was created. For an initialized database.
 */
suspend fun MutableRepublicateDatabase.createMissingSchemas(): Boolean {
    val arkadCreated = arkad.createMissingSchemas() // first: this one's tables reference its own
    val script = { MutableRepublicateDatabase::class.java.getResource("/META-INF/skorm/model.sql")!!.readText() }
    return (processor.underlying as CoreProcessor).createMissingSchemas(listOf("republicate"), script) || arkadCreated
}
