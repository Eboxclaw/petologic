package ai.petologic.core

import kotlin.test.*
import java.time.*
class ApprovedActionTest {
 private val now=Instant.parse("2026-09-20T10:00:00Z")
 private val clock=Clock.fixed(now,ZoneOffset.UTC)
 private val p=ApprovalPolicy(clock).propose("security.action","bound-target")
 private fun validate(status:String="PENDING",owner:String="session",argument:String=p.argument,expires:Long=p.expiresAt.toEpochMilli())=
  validateStoredApproval(p,p.argumentHash,"session",p.tool,argument,p.argumentHash,expires,owner,status,clock)
 @Test fun validSecurityProposal(){validate()}
 @Test fun replayAndUnknownCannotDispatch(){listOf("REQUESTED","UNKNOWN","COMPLETED","CANCELLED").forEach{assertFailsWith<IllegalArgumentException>{validate(status=it)}}}
 @Test fun targetSessionAndExpiryTamperingRejected(){
  assertFailsWith<IllegalArgumentException>{validate(owner="other")}
  assertFailsWith<IllegalArgumentException>{validate(argument="different")}
  assertFailsWith<IllegalArgumentException>{validate(expires=p.expiresAt.plusSeconds(1).toEpochMilli())}
 }
 @Test fun expiredAndUnknownToolRejected(){
  assertFailsWith<IllegalArgumentException>{ApprovalPolicy(Clock.fixed(now.plusSeconds(301),ZoneOffset.UTC)).validate(p,p.argumentHash)}
  val unknown=p.copy(tool="shell.execute")
  assertFailsWith<IllegalArgumentException>{ApprovalPolicy(clock).validate(unknown,unknown.argumentHash)}
 }
 @Test fun modelKindsExcludeEmbeddings(){assertTrue(ModelCatalog.selectable().all{it.kind==ModelKind.GENERATIVE});assertEquals(ModelKind.EMBEDDING,ModelCatalog.get("lfm25-embedding-350m").kind)}
}
