package ai.petologic.paladino.skills

import android.content.Context
import ai.petologic.skills.security.ToolResultEnvelope
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build

/** Reads real battery/network state and formats it through the pure formatters. Read-only. */
object DeviceStatusReader{
 fun query(context:Context,argument:String):String=runCatching{
  when(parseDeviceQuery(argument).subject){
   "battery"->{
    val bm=context.getSystemService(BatteryManager::class.java)
    DeviceStatusFormat.battery(
     bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?:-1,
     bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)?:BatteryManager.BATTERY_STATUS_UNKNOWN)
   }
   "connectivity"->{
    val connectivity=context.getSystemService(ConnectivityManager::class.java)
    val network=connectivity?.activeNetwork
    if(network==null)DeviceStatusFormat.connectivity(active=false,wifi=false,cellular=false,metered=false,vpn=false)
    else{
     val caps=connectivity.getNetworkCapabilities(network)
     if(caps==null)DeviceStatusFormat.connectivity(active=true,wifi=false,cellular=false,metered=false,vpn=false)
     else DeviceStatusFormat.connectivity(active=true,wifi=caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
      cellular=caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
      metered=!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
      vpn=caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
    }
   }
   "device"->DeviceStatusFormat.phone(Build.MANUFACTURER,Build.MODEL,Build.VERSION.RELEASE,
    android.os.Build.VERSION.SECURITY_PATCH?:"unknown")
   else->ToolResultEnvelope.error("unsupported_command","device_query subjects: battery, connectivity, device")
  }
 }.getOrElse{ToolResultEnvelope.error("invalid_command",it.message?:"Could not read the command.")}
}
