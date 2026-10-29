package ai.petologic.skills.security.analysis

import ai.petologic.skills.security.models.SecurityFinding

/** Pure detector boundary shared by the Android product and inert benchmark runner. */
fun interface SecurityAnalyzer<T> { fun analyze(input:T):SecurityFinding }

object SecurityAnalyzers {
 val url:SecurityAnalyzer<String> = SecurityAnalyzer(UrlAnalyzer::scanUrl)
 val message:SecurityAnalyzer<String> = SecurityAnalyzer(MessageAnalyzer::scanMessage)
 val app:SecurityAnalyzer<AppAssessment> = SecurityAnalyzer { AppRiskEngine.assess(it.subject,it.signals) }
}

data class AppAssessment(val subject:String,val signals:AppSignals)
