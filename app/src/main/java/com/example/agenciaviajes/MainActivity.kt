package com.example.agenciaviajes

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class Vehicle(val id: Long, val name: String, val type: String, val seats: Int)
data class Trip(val id: Long, val title: String, val departure: String, val returnAt: String, val vehicleId: Long, val occupied: Set<Int>)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { TravelApp(this) } }
    }
}

private fun loadVehicles(ctx: Context): List<Vehicle> {
    val a = ctx.getSharedPreferences("agency", 0).getString("vehicles", "[]") ?: "[]"
    val arr = JSONArray(a)
    return (0 until arr.length()).map { i -> arr.getJSONObject(i).let { Vehicle(it.getLong("id"), it.getString("name"), it.getString("type"), it.getInt("seats")) } }
}
private fun saveVehicles(ctx: Context, list: List<Vehicle>) {
    val arr = JSONArray()
    list.forEach { arr.put(JSONObject().put("id",it.id).put("name",it.name).put("type",it.type).put("seats",it.seats)) }
    ctx.getSharedPreferences("agency",0).edit().putString("vehicles",arr.toString()).apply()
}
private fun loadTrips(ctx: Context): List<Trip> {
    val arr = JSONArray(ctx.getSharedPreferences("agency",0).getString("trips","[]") ?: "[]")
    return (0 until arr.length()).map { i -> arr.getJSONObject(i).let { o ->
        val seats=o.getJSONArray("occupied")
        Trip(o.getLong("id"),o.getString("title"),o.getString("departure"),o.getString("returnAt"),o.getLong("vehicleId"),(0 until seats.length()).map { seats.getInt(it) }.toSet())
    } }
}
private fun saveTrips(ctx: Context, list: List<Trip>) {
    val arr=JSONArray()
    list.forEach { t ->
        val seats=JSONArray(); t.occupied.sorted().forEach { seats.put(it) }
        arr.put(JSONObject().put("id",t.id).put("title",t.title).put("departure",t.departure).put("returnAt",t.returnAt).put("vehicleId",t.vehicleId).put("occupied",seats))
    }
    ctx.getSharedPreferences("agency",0).edit().putString("trips",arr.toString()).apply()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelApp(ctx: Context) {
    var vehicles by remember { mutableStateOf(loadVehicles(ctx)) }
    var trips by remember { mutableStateOf(loadTrips(ctx)) }
    var tab by remember { mutableStateOf(0) }
    var selectedTripId by remember { mutableStateOf<Long?>(null) }
    var vehicleName by remember { mutableStateOf("") }
    var vehicleType by remember { mutableStateOf("Camión") }
    var vehicleSeats by remember { mutableStateOf("40") }
    var tripTitle by remember { mutableStateOf("") }
    var departureDate by remember { mutableStateOf("") }
    var departureTime by remember { mutableStateOf("08:00") }
    var returnDate by remember { mutableStateOf("") }
    var returnTime by remember { mutableStateOf("18:00") }
    var selectedVehicleId by remember { mutableStateOf<Long?>(null) }
    var message by remember { mutableStateOf("") }
    val context = LocalContext.current
    val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    fun chooseDate(onPicked: (String) -> Unit) {
        val c=Calendar.getInstance()
        DatePickerDialog(context,{ _,y,m,d -> onPicked(String.format(Locale.getDefault(),"%04d-%02d-%02d",y,m+1,d)) },c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show()
    }
    fun chooseTime(onPicked: (String) -> Unit) {
        val c=Calendar.getInstance()
        TimePickerDialog(context,{ _,h,m -> onPicked(String.format(Locale.getDefault(),"%02d:%02d",h,m)) },c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE),true).show()
    }
    val activeTrip = trips.firstOrNull { it.id == selectedTripId }
    Scaffold(topBar={ TopAppBar(title={ Text("Agencia de viajes") }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TabRow(selectedTabIndex=tab) {
                Tab(tab==0,onClick={tab=0},text={Text("Viajes")})
                Tab(tab==1,onClick={tab=1},text={Text("Transportes")})
            }
            if (tab==1) {
                Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("Registrar medio de transporte", style=MaterialTheme.typography.titleLarge)
                    OutlinedTextField(vehicleName,{vehicleName=it},label={Text("Nombre o identificador")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                    var typeExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded=typeExpanded,onExpandedChange={typeExpanded=it}) {
                        OutlinedTextField(vehicleType,{vehicleType=it},readOnly=true,label={Text("Tipo de transporte")},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded=typeExpanded)},modifier=Modifier.menuAnchor().fillMaxWidth())
                        ExposedDropdownMenu(expanded=typeExpanded,onDismissRequest={typeExpanded=false}) {
                            listOf("Camión","Van","Automóvil","Avión","Otro").forEach { opt -> DropdownMenuItem(text={Text(opt)},onClick={vehicleType=opt;typeExpanded=false}) }
                        }
                    }
                    OutlinedTextField(vehicleSeats,{vehicleSeats=it.filter(Char::isDigit)},label={Text("Cantidad de lugares")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                    Button(onClick={
                        val n=vehicleSeats.toIntOrNull() ?: 0
                        if(vehicleName.isBlank() || n<1 || n>300) message="Captura nombre y entre 1 y 300 lugares."
                        else { vehicles=vehicles+Vehicle(System.currentTimeMillis(),vehicleName.trim(),vehicleType,n); saveVehicles(ctx,vehicles); vehicleName=""; vehicleSeats="40"; message="Transporte registrado." }
                    },modifier=Modifier.fillMaxWidth()) { Text("Guardar transporte") }
                    if(message.isNotBlank()) Text(message)
                    Divider()
                    Text("Transportes registrados",style=MaterialTheme.typography.titleMedium)
                    if(vehicles.isEmpty()) Text("Todavía no hay transportes.")
                    vehicles.forEach { v -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text(v.name,fontWeight=FontWeight.Bold); Text("${v.type} · ${v.seats} lugares") } } }
                }
            } else {
                Column(Modifier.padding(horizontal=12.dp).fillMaxSize()) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text("Registrar viaje",style=MaterialTheme.typography.titleLarge)
                        OutlinedTextField(tripTitle,{tripTitle=it},label={Text("Título del viaje")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                            OutlinedButton(onClick={chooseDate{departureDate=it}},modifier=Modifier.weight(1f)){Text(if(departureDate.isBlank()) "Día de salida" else departureDate)}
                            OutlinedButton(onClick={chooseTime{departureTime=it}},modifier=Modifier.weight(1f)){Text("Hora $departureTime")}
                        }
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                            OutlinedButton(onClick={chooseDate{returnDate=it}},modifier=Modifier.weight(1f)){Text(if(returnDate.isBlank()) "Día de regreso" else returnDate)}
                            OutlinedButton(onClick={chooseTime{returnTime=it},modifier=Modifier.weight(1f)){Text("Hora $returnTime")}
                        }
                        var vehicleExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expanded=vehicleExpanded,onExpandedChange={vehicleExpanded=it}) {
                            val selected=vehicles.firstOrNull{it.id==selectedVehicleId}
                            OutlinedTextField(selected?.let{"${it.name} (${it.seats} lugares)"} ?: "",{ },readOnly=true,label={Text("Medio de transporte")},placeholder={Text("Selecciona transporte")},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded=vehicleExpanded)},modifier=Modifier.menuAnchor().fillMaxWidth())
                            ExposedDropdownMenu(expanded=vehicleExpanded,onDismissRequest={vehicleExpanded=false}) {
                                vehicles.forEach { v -> DropdownMenuItem(text={Text("${v.name} · ${v.type} · ${v.seats} lugares")},onClick={selectedVehicleId=v.id;vehicleExpanded=false}) }
                            }
                        }
                        Button(onClick={
                            val v=vehicles.firstOrNull{it.id==selectedVehicleId}
                            if(tripTitle.isBlank() || departureDate.isBlank() || returnDate.isBlank() || v==null) message="Completa título, fechas y transporte."
                            else if("$returnDate $returnTime" < "$departureDate $departureTime") message="El regreso no puede ser anterior a la salida."
                            else {
                                val t=Trip(System.currentTimeMillis(),tripTitle.trim(),"$departureDate $departureTime","$returnDate $returnTime",v.id, emptySet())
                                trips=trips+t; saveTrips(ctx,trips); selectedTripId=t.id
                                tripTitle=""; departureDate=""; returnDate=""; message="Viaje registrado."
                            }
                        },modifier=Modifier.fillMaxWidth()){Text("Guardar viaje")}
                        if(message.isNotBlank()) Text(message)
                        Divider()
                        Text("Viajes registrados",style=MaterialTheme.typography.titleMedium)
                        if(trips.isEmpty()) Text("Aún no hay viajes. Registra uno arriba.")
                        trips.forEach { t ->
                            val v=vehicles.firstOrNull{it.id==t.vehicleId}
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                    Text(t.title,fontWeight=FontWeight.Bold)
                                    Text("Salida: ${t.departure}  ·  Regreso: ${t.returnAt}")
                                    Text("Transporte: ${v?.name ?: "No disponible"}")
                                    Row(verticalAlignment=Alignment.CenterVertically) {
                                        Text("${t.occupied.size}/${v?.seats ?: 0} lugares ocupados",Modifier.weight(1f))
                                        TextButton(onClick={selectedTripId=if(selectedTripId==t.id)null else t.id}) { Text(if(selectedTripId==t.id) "Ocultar asientos" else "Ver asientos") }
                                    }
                                }
                            }
                            if(selectedTripId==t.id && v!=null) {
                                Text("Selecciona las casillas de los lugares ocupados",style=MaterialTheme.typography.titleMedium)
                                Text("Marcada = ocupado · Sin marcar = disponible",style=MaterialTheme.typography.bodySmall)
                                val cols=4
                                (1..v.seats).chunked(cols).forEach { group ->
                                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                                        group.forEach { seat ->
                                            Row(Modifier.weight(1f).toggleable(value=seat in t.occupied,onValueChange={ checked ->
                                                val updated=t.copy(occupied=if(checked)t.occupied+seat else t.occupied-seat)
                                                trips=trips.map{if(it.id==t.id)updated else it}; saveTrips(ctx,trips)
                                            }).padding(vertical=3.dp),verticalAlignment=Alignment.CenterVertically) {
                                                Checkbox(checked=seat in t.occupied,onCheckedChange={ checked ->
                                                    val updated=t.copy(occupied=if(checked)t.occupied+seat else t.occupied-seat)
                                                    trips=trips.map{if(it.id==t.id)updated else it}; saveTrips(ctx,trips)
                                                })
                                                Text(seat.toString())
                                            }
                                        }
                                        repeat(cols-group.size){Spacer(Modifier.weight(1f))}
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
