package ai.petologic.paladino

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Skills and tools are separate toggles: a skill makes a capability visible, tools register for real. */
@Composable internal fun SkillsCard(){
 val context=androidx.compose.ui.platform.LocalContext.current
 var revision by remember{mutableIntStateOf(0)}
 Surface(color=Panel,shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,Gold.copy(alpha=.6f))){
  Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
   Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
    Icon(Icons.Outlined.AutoAwesome,null,tint=Gold)
    Text(tr("Skills"),style=MaterialTheme.typography.titleMedium)
   }
   Text(tr("Skills teach Paladino what it can do. Auto wakes a skill only when a message needs it."),style=MaterialTheme.typography.bodyMedium,color=Muted)
   key(revision){
    for(skill in ai.petologic.paladino.skills.SkillRegistry.definitions){
     val state=ai.petologic.paladino.skills.SkillRegistry.states(context)[skill.id]?:ai.petologic.paladino.skills.SkillState.OFF
     Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)){
      Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
       Column(Modifier.weight(1f)){
        Text(tr(skill.name),style=MaterialTheme.typography.titleSmall)
        Text(tr(skill.description),style=MaterialTheme.typography.bodySmall,color=Muted)
       }
       Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){
        listOf(ai.petologic.paladino.skills.SkillState.OFF to "Off",
         ai.petologic.paladino.skills.SkillState.AUTO to "Auto",
         ai.petologic.paladino.skills.SkillState.PINNED to "Always").forEach{(value,label)->
         FilterChip(selected=state==value,onClick={
          ai.petologic.paladino.skills.SkillRegistry.setSkillState(context,skill.id,value);revision++
         },label={Text(tr(label),fontSize=11.sp)})
        }
       }
      }
      if(state!=ai.petologic.paladino.skills.SkillState.OFF){
       val toolStates=skill.tools.map{it to ai.petologic.paladino.skills.SkillRegistry.toolEnabled(context,skill,it)}
       for((spec,enabled) in toolStates){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
         Text("· "+tr(spec.label),style=MaterialTheme.typography.bodySmall,color=Muted)
         Switch(checked=enabled,onCheckedChange={on->
          ai.petologic.paladino.skills.SkillRegistry.setToolEnabled(context,skill.id,spec.id,on);revision++
         },enabled=true)
        }
       }
       val enabledCount=toolStates.count{it.second}
       val estimate=skill.promptStub.length/4+enabledCount*15
       Text(tr("Idle ~0 tokens · active ~%1\$s tokens · %2\$s tools exposed",estimate.toString(),enabledCount.toString()),
        style=MaterialTheme.typography.bodySmall,color=Gold)
      }
     }
    }
   }
  }
 }
}
