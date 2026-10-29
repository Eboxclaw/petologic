package ai.petologic.security.benchmark

object RetrievalScoring {
 fun summarize(passes:List<RetrievalPass>):List<RetrievalLocaleSummary> = passes.groupBy{it.locale}.map{(locale,values)->
  require(values.size==3){"Exactly three measured retrieval passes are required for $locale."}
  require(values.map{it.run}.toSet().size==3){"Retrieval pass numbers must be unique for $locale."}
  val recalls=values.map{it.recallAt5}.sorted()
  val median=recalls[1]
  val worst=values.maxOf{it.p95Millis}
  RetrievalLocaleSummary(locale,median,worst,median>=0.90,worst<=200)
 }.sortedBy{it.locale}
}
