package ai.petologic.paladino.runtime

import ai.koog.agents.core.tools.ToolDescriptor
import kotlinx.serialization.json.*

/** Turns Koog descriptors into the single-string-argument schema the local prompt presents to LFM. */
object LfmToolDescriptorSchemer {
 fun definitions(tools:List<ToolDescriptor>):JsonArray=buildJsonArray{tools.forEach{descriptor->add(buildJsonObject{
  put("name",descriptor.name);put("description",descriptor.description)
  put("parameters",buildJsonObject{put("type","object");put("properties",buildJsonObject{put("argument",buildJsonObject{put("type","string")})});put("required",buildJsonArray{add("argument")})})
 })}}
}
