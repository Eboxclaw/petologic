package ai.petologic.core

import java.time.Clock

/** Compares the complete persisted proposal, not only a caller-supplied digest. */
fun validateStoredApproval(p:ActionProposal, approvedHash:String, session:String,
 tool:String, argument:String, hash:String, expiresAt:Long, owner:String, status:String,
 clock:Clock=Clock.systemUTC(), allowCompleted:Boolean=false) {
 ApprovalPolicy(clock).validate(p,approvedHash)
 require(owner==session){"Action belongs to another session."}
 require(tool==p.tool && argument==p.argument && hash==approvedHash && expiresAt==p.expiresAt.toEpochMilli()){"Stored action changed."}
 require(status=="PENDING" || (allowCompleted && status=="COMPLETED")){"Action is no longer pending; uncertain operations are never replayed."}
}
