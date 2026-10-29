package ai.petologic.skills.security.guard

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName="findings")
data class FindingRow(@PrimaryKey val id:String,val createdAt:Long,val json:String)

@Entity(tableName="security_knowledge")
data class KnowledgeRow(@PrimaryKey val id:String,val revision:Long,val packVersion:Long,val kind:String,
 val language:String?,val canonicalText:String,val source:String,val sourceVersion:String,val trust:String,
 val validUntil:Long,val evidenceRefsJson:String,val limitationsJson:String,val contentHash:String)

@Entity(tableName="security_vectors",primaryKeys=["namespace","ownerId","ownerRevision","modelId","modelRevision"],
 indices=[Index("namespace"),Index("ownerId"),Index("expiresAt")])
data class SecurityVectorRow(val namespace:String,val ownerId:String,val ownerRevision:Long,val modelId:String,
 val modelRevision:String,val contentHash:String,val dimension:Int,val vector:ByteArray,val createdAt:Long,val expiresAt:Long)

@Entity(tableName="security_index_outbox",primaryKeys=["namespace","ownerId"],indices=[Index("createdAt")])
data class SecurityIndexRow(val namespace:String,val ownerId:String,val ownerRevision:Long,val operation:String,
 val createdAt:Long=System.currentTimeMillis(),val attempts:Int=0)

@Entity(tableName="threat_edges",primaryKeys=["fromId","toId","relation"],indices=[Index("fromId"),Index("toId")])
data class ThreatEdgeRow(val fromId:String,val toId:String,val relation:String,val packVersion:Long)

@Dao interface SecurityDao {
 @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insert(row:FindingRow)
 @Query("SELECT * FROM findings ORDER BY createdAt DESC LIMIT 100") fun observe():Flow<List<FindingRow>>
 @Query("SELECT * FROM findings WHERE id=:id") suspend fun finding(id:String):FindingRow?
 @Query("DELETE FROM findings WHERE id=:id") suspend fun deleteFinding(id:String)
 @Query("SELECT id FROM findings WHERE createdAt < :cutoff") suspend fun expiredFindingIds(cutoff:Long):List<String>
 @Query("DELETE FROM findings WHERE createdAt < :cutoff") suspend fun expireFindings(cutoff:Long)
 @Query("DELETE FROM findings") suspend fun clearFindings()

 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putKnowledge(rows:List<KnowledgeRow>)
 @Query("SELECT * FROM security_knowledge WHERE id=:id") suspend fun knowledge(id:String):KnowledgeRow?
 @Query("SELECT id FROM security_knowledge") suspend fun knowledgeIds():List<String>
 @Query("SELECT * FROM security_knowledge WHERE validUntil>:now AND (:kind IS NULL OR kind=:kind) ORDER BY id LIMIT :limit") suspend fun knowledgeCandidates(now:Long,kind:String?,limit:Int):List<KnowledgeRow>
 @Query("SELECT id FROM security_knowledge WHERE packVersion!=:packVersion") suspend fun obsoleteKnowledgeIds(packVersion:Long):List<String>
 @Query("DELETE FROM security_knowledge WHERE packVersion!=:packVersion") suspend fun deleteObsoleteKnowledge(packVersion:Long)
 @Query("DELETE FROM security_knowledge") suspend fun clearKnowledge()
 @Query("SELECT COUNT(*) FROM security_knowledge WHERE packVersion=:packVersion") suspend fun knowledgeCount(packVersion:Long):Int

 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putVector(row:SecurityVectorRow)
 @Query("SELECT * FROM security_vectors WHERE namespace=:namespace AND ownerId=:ownerId AND ownerRevision=:revision AND modelId=:modelId AND modelRevision=:modelRevision") suspend fun vector(namespace:String,ownerId:String,revision:Long,modelId:String,modelRevision:String):SecurityVectorRow?
 @Query("SELECT * FROM security_vectors WHERE namespace=:namespace AND expiresAt>:now") suspend fun vectors(namespace:String,now:Long):List<SecurityVectorRow>
 @Query("DELETE FROM security_vectors WHERE namespace=:namespace AND ownerId=:ownerId") suspend fun deleteVectors(namespace:String,ownerId:String)
 @Query("DELETE FROM security_vectors WHERE namespace=:namespace AND ownerId IN (:ownerIds)") suspend fun deleteVectors(namespace:String,ownerIds:List<String>)
 @Query("DELETE FROM security_vectors WHERE namespace=:namespace") suspend fun deleteVectors(namespace:String)
 @Query("DELETE FROM security_vectors WHERE expiresAt<=:now") suspend fun expireVectors(now:Long)
 @Query("SELECT DISTINCT ownerId FROM security_vectors WHERE namespace=:namespace") suspend fun vectorOwnerIds(namespace:String):List<String>
 @Query("DELETE FROM security_vectors") suspend fun clearVectors()
 @Query("SELECT COUNT(*) FROM security_vectors WHERE namespace=:namespace AND modelId=:modelId AND modelRevision=:modelRevision AND expiresAt>:now") suspend fun vectorCount(namespace:String,modelId:String,modelRevision:String,now:Long):Int

 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun queue(row:SecurityIndexRow)
 @Query("SELECT * FROM security_index_outbox ORDER BY createdAt LIMIT :limit") suspend fun pending(limit:Int=100):List<SecurityIndexRow>
 @Query("DELETE FROM security_index_outbox WHERE namespace=:namespace AND ownerId=:ownerId") suspend fun indexDone(namespace:String,ownerId:String)
 @Query("UPDATE security_index_outbox SET attempts=attempts+1 WHERE namespace=:namespace AND ownerId=:ownerId") suspend fun indexFailed(namespace:String,ownerId:String)
 @Query("DELETE FROM security_index_outbox WHERE namespace=:namespace AND ownerId IN (:ownerIds)") suspend fun deleteQueued(namespace:String,ownerIds:List<String>)
 @Query("DELETE FROM security_index_outbox WHERE namespace=:namespace") suspend fun deleteQueued(namespace:String)
 @Query("DELETE FROM security_index_outbox") suspend fun clearQueue()

 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putEdges(rows:List<ThreatEdgeRow>)
 @Query("DELETE FROM threat_edges WHERE packVersion!=:packVersion") suspend fun deleteObsoleteEdges(packVersion:Long)
 @Query("DELETE FROM threat_edges") suspend fun clearEdges()
 @Query("SELECT * FROM threat_edges WHERE fromId=:id OR toId=:id LIMIT :limit") suspend fun edges(id:String,limit:Int=20):List<ThreatEdgeRow>
}

@Database(entities=[FindingRow::class,KnowledgeRow::class,SecurityVectorRow::class,SecurityIndexRow::class,ThreatEdgeRow::class],version=2,exportSchema=true)
abstract class SecurityDatabase:RoomDatabase(){abstract fun dao():SecurityDao}
