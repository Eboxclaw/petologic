package ai.petologic.skills.security.guard

import android.content.Context
import androidx.appsearch.app.*
import androidx.appsearch.localstorage.LocalStorage
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Rebuildable exact vector index. Room remains canonical on every path. */
@androidx.annotation.OptIn(markerClass=[ExperimentalAppSearchApi::class])
class ThreatVectorIndex(context:Context,databaseName:String="paladino-security-vectors") {
 private val future=LocalStorage.createSearchSessionAsync(LocalStorage.SearchContext.Builder(context,databaseName).build())
 private val lock=Mutex()
 private var initialized=false
 private var supported=false
 private suspend fun session():AppSearchSession=lock.withLock {
  val session=future.await()
  if(!initialized){
   supported=session.features.isFeatureSupported(Features.SCHEMA_EMBEDDING_PROPERTY_CONFIG)
   if(supported){
    val schema=AppSearchSchema.Builder(SCHEMA)
     .addProperty(AppSearchSchema.StringPropertyConfig.Builder("kind").setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL).build())
     .addProperty(AppSearchSchema.StringPropertyConfig.Builder("source").setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL).build())
     .addProperty(AppSearchSchema.EmbeddingPropertyConfig.Builder("vector").setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_REQUIRED).setIndexingType(AppSearchSchema.EmbeddingPropertyConfig.INDEXING_TYPE_SIMILARITY).build())
     .build()
    session.setSchemaAsync(SetSchemaRequest.Builder().addSchemas(schema).setForceOverride(true).build()).await()
   }
   initialized=true
  }
  session
 }
 suspend fun available():Boolean {session();return supported}
 suspend fun put(namespace:String,id:String,kind:String,source:String,vector:FloatArray,modelSignature:String){
  val session=session();if(!supported)return
  val doc=GenericDocument.Builder<GenericDocument.Builder<*>>(namespace,id,SCHEMA)
   .setPropertyString("kind",kind).setPropertyString("source",source)
   .setPropertyEmbedding("vector",EmbeddingVector(vector,modelSignature)).build()
  check(session.putAsync(PutDocumentsRequest.Builder().addGenericDocuments(doc).build()).await().isSuccess){"Threat vector index update failed."}
 }
 suspend fun remove(namespace:String,id:String){
  val session=session();if(!supported)return
  session.removeAsync(RemoveByDocumentIdRequest.Builder(namespace).addIds(id).build()).await()
 }
 suspend fun search(namespace:String,query:FloatArray,modelSignature:String,limit:Int):List<Pair<String,Float>> {
  val session=session();if(!supported)return emptyList()
  val spec=SearchSpec.Builder().addFilterSchemas(SCHEMA).addFilterNamespaces(namespace)
   .setResultCountPerPage(limit).addEmbeddingParameters(EmbeddingVector(query,modelSignature))
   .setDefaultEmbeddingSearchMetricType(SearchSpec.EMBEDDING_SEARCH_METRIC_TYPE_COSINE)
   .setRetrieveEmbeddingMatchInfos(true).setListFilterQueryLanguageEnabled(true).build()
  val results=session.search("semanticSearch(getEmbeddingParameter(0), -1, 1, \"COSINE\")",spec)
  return try { results.nextPageAsync.await().mapNotNull { result->
   val score=result.matchInfos.mapNotNull{it.embeddingMatch?.semanticScore}.maxOrNull()?.toFloat() ?: return@mapNotNull null
   result.genericDocument.id to score
  }.sortedByDescending{it.second}.take(limit) } finally { results.close() }
 }
 companion object { private const val SCHEMA="SecurityVector" }
}
