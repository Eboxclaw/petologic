package ai.petologic.paladino.skills

import ai.petologic.skills.security.ToolResultEnvelope

/** Device domain (plan 12): one read tool over battery, connection and phone identity. */
object DeviceSkill {
 const val ID="device"
 val definition=SkillDefinition(
  id=ID,
  name="Device",
  description="Battery, connection and phone status.",
  routerTerms=listOf("battery","bateria","charging","carregando","wifi","wi-fi","internet","connection","conexao","rede","sinal"),
  promptStub="DEVICE SKILL: device_query reports battery, connection and phone status. It only reads; it never changes settings.\n",
  tools=listOf(SkillToolSpec("device_query","Battery, connection and status")),
  defaultState=SkillState.AUTO
 )
}

/** argument grammar: battery · connectivity · device */
data class DeviceQuery(val subject:String)

fun parseDeviceQuery(argument:String):DeviceQuery{
 val subject=argument.trim().lowercase()
 require(subject in setOf("battery","connectivity","device")){"Unknown device query '$subject'. Supported: battery, connectivity, device"}
 return DeviceQuery(subject)
}

/** Pure formatters; Android supplies the raw values. */
object DeviceStatusFormat{
 fun battery(level:Int,status:Int):String=if(level<0||level>100)
  ToolResultEnvelope.error("unavailable","Battery level is unavailable right now.")
 else{
  val charging=status==android.os.BatteryManager.BATTERY_STATUS_CHARGING||status==android.os.BatteryManager.BATTERY_STATUS_FULL
  ToolResultEnvelope.ok("device.query","Battery at $level%${if(charging)" and charging" else ""}.")
 }

 fun connectivity(active:Boolean,wifi:Boolean,cellular:Boolean,metered:Boolean,vpn:Boolean):String{
  if(!active)return ToolResultEnvelope.ok("device.query","No active network right now.")
  val via=when{wifi->"Wi-Fi";cellular->"mobile data";else->"another transport"}
  return ToolResultEnvelope.ok("device.query","Online via $via${if(metered)" (metered)" else ""}${if(vpn)"; a VPN is active" else ""}.")
 }

 fun phone(manufacturer:String,model:String,release:String,securityPatch:String):String=
  ToolResultEnvelope.ok("device.query","$manufacturer $model · Android $release · security patch $securityPatch")
}
