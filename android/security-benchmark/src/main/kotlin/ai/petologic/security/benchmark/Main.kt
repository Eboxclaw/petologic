package ai.petologic.security.benchmark

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

fun main(rawArgs:Array<String>){
 val args=rawArgs.toList().chunked(2).associate{pair->require(pair.size==2){"Arguments must be --name value pairs."};pair[0] to pair[1]}
 val corpusArg=requireNotNull(args["--corpus"]){"--corpus is required"}
 val registry=File(requireNotNull(args["--source-registry"]){"--source-registry is required"}).readBytes()
 val output=File(requireNotNull(args["--output-dir"]){"--output-dir is required"}).apply{mkdirs()}
 val mode=BenchmarkMode.valueOf((args["--mode"]?:"release").uppercase())
 val raw=if(corpusArg=="-")System.`in`.readBytes() else File(corpusArg).readBytes()
 val validated=CorpusValidator.parseAndValidate(raw,registry,mode)
 val report=BenchmarkRunner.run(validated,mode,args["--git-revision"]?:System.getenv("GITHUB_SHA")?:"unknown",
  args["--model-hash"]?:"not-used",args["--device-fingerprint"]?:"JVM-${System.getProperty("os.name")}-${System.getProperty("os.arch")}")
 val json=Json{prettyPrint=true;encodeDefaults=true}
 File(output,"report.json").writeText(json.encodeToString(report)+"\n")
 File(output,"report.md").writeText(markdown(report))
 println("Safe benchmark: ${if(report.passed)"PASS" else "FAIL"}; report=${output.resolve("report.json")}")
 check(report.passed){"Safe benchmark release gates failed."}
}

private fun markdown(report:BenchmarkReport)=buildString{
 appendLine("# Paladino safe efficacy benchmark")
 appendLine();appendLine(report.disclaimer);appendLine()
 appendLine("- Result: **${if(report.passed)"PASS" else "FAIL"}**")
 appendLine("- Mode: `${report.mode.name.lowercase()}`")
 appendLine("- Corpus: `${report.corpusVersion}` (`${report.corpusSha256}`)")
 appendLine("- Git revision: `${report.gitRevision}`")
 appendLine("- Model hash: `${report.modelHash}`")
 appendLine("- Device: `${report.deviceFingerprint}`")
 appendLine();appendLine("## Gates");appendLine()
 report.gates.forEach{appendLine("- ${if(it.passed)"PASS" else "FAIL"} — ${it.name}: ${it.actual} (${it.requirement})")}
 appendLine();appendLine("No raw inputs are included in this report. Case identifiers are SHA-256 digests.")
}
