package com.jiafang.order

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import android.widget.Toast

enum class OrderStatus { PENDING, COMPLETED }
class DbConverters { @TypeConverter fun fromStatus(s:OrderStatus)=s.name; @TypeConverter fun toStatus(s:String)=OrderStatus.valueOf(s) }
@Entity(indices=[Index(value=["phone"]),Index(value=["status"]),Index(value=["orderTime"]),Index(value=["createdAt"])]) data class Order(@PrimaryKey(autoGenerate=true) val id:Long=0,val phone:String,val orderTime:Long,val pickupTime:Long?=null,val status:OrderStatus=OrderStatus.PENDING,val remark:String="",val createdAt:Long=System.currentTimeMillis(),val paperImagePath:String="",val sourceImagePath:String="",val extraImagePath:String="")
@Entity(foreignKeys=[ForeignKey(entity=Order::class,parentColumns=["id"],childColumns=["orderId"],onDelete=ForeignKey.CASCADE)],indices=[Index("orderId")]) data class OrderItem(@PrimaryKey(autoGenerate=true) val id:Long=0,val orderId:Long,val productType:String,val fabricNumber:String="",val fabricSize:String="",val cottonQuality:String="",val cottonPrice:Double=0.0,val cottonWeight:Int=0,val unitPrice:Double=0.0,val cottonFabricNumber:String="",val cottonFabricSize:String="",val cottonFabricPrice:Double=0.0,val quantity:Int=1,val itemRemark:String="")
@Entity data class Inventory(@PrimaryKey(autoGenerate=true) val id:Long=0,val name:String,val quantity:Int=0,val unit:String="件",val remark:String="",val updatedAt:Long=System.currentTimeMillis())
data class OrderWithItems(@Embedded val order:Order,@Relation(parentColumn="id",entityColumn="orderId") val items:List<OrderItem>)
@Dao interface OrderDao{@Transaction @Query("SELECT * FROM `Order` ORDER BY orderTime DESC") fun observe():Flow<List<OrderWithItems>>;@Query("SELECT * FROM `Order` WHERE status = :status AND pickupTime IS NOT NULL AND pickupTime < :before") suspend fun expired(status:OrderStatus,before:Long):List<Order>;@Query("SELECT * FROM `Order` WHERE status = :status") suspend fun byStatus(status:OrderStatus):List<Order>;@Insert suspend fun insert(o:Order):Long;@Insert suspend fun insertItems(i:List<OrderItem>);@Update suspend fun update(o:Order);@Query("UPDATE `Order` SET status = :status, pickupTime = :pickupTime WHERE id = :id") suspend fun complete(id:Long,status:OrderStatus,pickupTime:Long);@Query("UPDATE `Order` SET remark = :remark, extraImagePath = :extraImagePath WHERE id = :id") suspend fun updateDetails(id:Long,remark:String,extraImagePath:String);@Query("UPDATE `Order` SET paperImagePath = '', sourceImagePath = '', extraImagePath = '' WHERE id = :id") suspend fun clearImages(id:Long);@Delete suspend fun delete(o:Order)}
@Dao interface InventoryDao{@Query("SELECT * FROM Inventory ORDER BY name") fun observe():Flow<List<Inventory>>}
@Database(entities=[Order::class,OrderItem::class,Inventory::class],version=5,exportSchema=false) @TypeConverters(DbConverters::class) abstract class AppDb:RoomDatabase(){abstract fun orders():OrderDao;abstract fun inventory():InventoryDao;companion object{@Volatile private var instance:AppDb?=null;private val M12=object:Migration(1,2){override fun migrate(db:SupportSQLiteDatabase){db.execSQL("ALTER TABLE OrderItem ADD COLUMN cottonWeight INTEGER NOT NULL DEFAULT 0");db.execSQL("ALTER TABLE OrderItem ADD COLUMN unitPrice REAL NOT NULL DEFAULT 0.0");db.execSQL("ALTER TABLE OrderItem ADD COLUMN cottonFabricNumber TEXT NOT NULL DEFAULT ''");db.execSQL("ALTER TABLE OrderItem ADD COLUMN cottonFabricSize TEXT NOT NULL DEFAULT ''");db.execSQL("ALTER TABLE OrderItem ADD COLUMN cottonFabricPrice REAL NOT NULL DEFAULT 0.0")}};private val M23=object:Migration(2,3){override fun migrate(db:SupportSQLiteDatabase){db.execSQL("ALTER TABLE `Order` ADD COLUMN paperImagePath TEXT NOT NULL DEFAULT ''");db.execSQL("ALTER TABLE `Order` ADD COLUMN sourceImagePath TEXT NOT NULL DEFAULT ''")}};private val M34=object:Migration(3,4){override fun migrate(db:SupportSQLiteDatabase){db.execSQL("ALTER TABLE `Order` ADD COLUMN extraImagePath TEXT NOT NULL DEFAULT ''")}};private val M45=object:Migration(4,5){override fun migrate(db:SupportSQLiteDatabase){db.execSQL("CREATE INDEX IF NOT EXISTS index_Order_phone ON `Order`(phone)");db.execSQL("CREATE INDEX IF NOT EXISTS index_Order_status ON `Order`(status)");db.execSQL("CREATE INDEX IF NOT EXISTS index_Order_orderTime ON `Order`(orderTime)");db.execSQL("CREATE INDEX IF NOT EXISTS index_Order_createdAt ON `Order`(createdAt)")}};fun get(c:Context)=instance?:synchronized(this){instance?:Room.databaseBuilder(c,AppDb::class.java,"jiafang.db").addMigrations(M12,M23,M34,M45).build().also{instance=it}}}}
data class DetectedOrder(val phone:String,val imagePath:String,val sourcePath:String)
private fun removeOrderFiles(o:Order){listOf(o.paperImagePath,o.sourceImagePath,o.extraImagePath).filter{it.isNotBlank()}.distinct().forEach{File(it).delete()}}
class MainVm(private val db:AppDb):ViewModel(){val orders=db.orders().observe().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());init{cleanupExpired()};fun createBatch(list:List<DetectedOrder>)=viewModelScope.launch(Dispatchers.IO){list.forEach{db.orders().insert(Order(phone=it.phone,orderTime=System.currentTimeMillis(),paperImagePath=it.imagePath,sourceImagePath=it.sourcePath));db.orders().insertItems(emptyList())}};fun complete(o:Order)=viewModelScope.launch(Dispatchers.IO){removeOrderFiles(o);db.orders().complete(o.id,OrderStatus.COMPLETED,System.currentTimeMillis());db.orders().clearImages(o.id)};fun updateDetails(o:Order,remark:String,extraImagePath:String)=viewModelScope.launch(Dispatchers.IO){db.orders().updateDetails(o.id,remark,extraImagePath)};fun delete(o:Order)=viewModelScope.launch(Dispatchers.IO){removeOrderFiles(o);db.orders().delete(o)};private fun cleanupExpired()=viewModelScope.launch(Dispatchers.IO){val before=System.currentTimeMillis()-15L*24*60*60*1000;db.orders().expired(OrderStatus.COMPLETED,before).forEach{removeOrderFiles(it);db.orders().delete(it)}}}
class VmFactory(private val db:AppDb):ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainVm::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainVm(db) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
private val dateFmt=SimpleDateFormat("MM-dd HH:mm",Locale.getDefault())
private fun newPhotoFile(c:Context,p:String)=File(File(c.filesDir,"orders").apply{mkdirs()},"${p}_${System.currentTimeMillis()}.jpg")
private fun saveBitmap(b:Bitmap,f:File){FileOutputStream(f).use{b.compress(Bitmap.CompressFormat.JPEG,92,it)}}

private val GlassInk = Color(0xFF222327)
private val GlassSurface = Color.White.copy(alpha = 0.92f)
private val GlassSurfaceStrong = Color.White.copy(alpha = 0.98f)
private val GlassBorder = Color(0xFFE1E3E7)
private val GlassMuted = Color(0xFF6F7279)
private val GlassPurple = Color(0xFF6750A4)
private val GlassGreen = Color(0xFF2E9D68)
private val GlassRed = Color(0xFFD84A62)
private val GlassShape = RoundedCornerShape(20.dp)
private val GlassBackground = Brush.verticalGradient(listOf(Color(0xFFFAFAF8), Color(0xFFF2F3F5)))
private val GlassTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 57.sp),
    displayMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 45.sp),
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 36.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 32.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 23.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 18.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 17.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 15.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
)

private val GlassColors = lightColorScheme(
    primary = GlassPurple,
    onPrimary = Color.White,
    secondary = Color(0xFF7B6BAF),
    onSecondary = Color.White,
    background = Color.Transparent,
    onBackground = GlassInk,
    surface = GlassSurface,
    onSurface = GlassInk,
    surfaceVariant = GlassSurfaceStrong,
    onSurfaceVariant = GlassMuted,
    outline = GlassBorder,
    error = GlassRed,
    onError = Color.White
)

@Composable
private fun JiaFangGlassTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = GlassColors, typography = GlassTypography, content = content)
}

@Composable
private fun GlassPage(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(GlassBackground), content = content)
}

@Composable
private fun GlassPanel(
    modifier: Modifier = Modifier,
    strong: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .background(if (strong) GlassSurfaceStrong else GlassSurface, GlassShape)
            .border(1.dp, GlassBorder, GlassShape)
            .padding(18.dp),
        content = content
    )
}

@Composable
private fun StaggeredAppear(index: Int, content: @Composable () -> Unit) {
    content()
}

@Composable
private fun PressScaleBox(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier.clickable(enabled = enabled, onClick = onClick),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: MainVm) {
    val context = LocalContext.current
    val preferences = remember { context.applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    val orders by vm.orders.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<OrderWithItems?>(null) }
    var detected by remember { mutableStateOf<List<DetectedOrder>?>(null) }
    var query by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<Uri?>(null) }
    var lastBack by remember { mutableLongStateOf(0L) }
    var fontScale by remember {
        mutableFloatStateOf(preferences.getFloat("font_scale", 1f).coerceIn(1f, 1.5f))
    }
    var cameraDenied by remember { mutableStateOf(false) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) pending?.let { analyzePage(context, it) { result -> detected = result } }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            cameraDenied = false
            val file = newPhotoFile(context, "page")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            pending = uri
            camera.launch(uri)
        } else cameraDenied = true
    }
    BackHandler {
        when {
            detail != null -> detail = null
            detected != null -> detected = null
            tab == 1 && query.isNotBlank() -> query = ""
            tab == 1 -> tab = 0
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBack < 2000L) (context as? ComponentActivity)?.finish()
                else { lastBack = now; Toast.makeText(context, "再按一次退出软件", Toast.LENGTH_SHORT).show() }
            }
        }
    }
    val navColors = NavigationBarItemDefaults.colors(
        selectedIconColor = GlassPurple,
        selectedTextColor = GlassPurple,
        indicatorColor = Color(0xFFEDE8F7),
        unselectedIconColor = GlassMuted,
        unselectedTextColor = GlassMuted
    )
    GlassPage {
        val showNav = detail == null && detected == null
        if (showNav) Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                    NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Default.CameraAlt, null) }, label = { Text("拍照") }, colors = navColors)
                    NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.AutoMirrored.Filled.ReceiptLong, null) }, label = { Text("订单") }, colors = navColors)
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    0 -> ScanPage(
                        fontScale = fontScale,
                        onScale = { fontScale = it },
                        onScaleFinished = { preferences.edit().putFloat("font_scale", fontScale).apply() },
                        cameraDenied = cameraDenied,
                        onRequestPermission = { permission.launch(Manifest.permission.CAMERA) }
                    )
                    else -> OrdersPage(orders, query, { query = it }, vm, fontScale) { if (it.order.status == OrderStatus.PENDING) detail = it }
                }
            }
        } else when {
            detail != null -> OrderDetail(detail!!, vm, fontScale, onCompleted = { query = "" }) { detail = null }
            detected != null -> RecognitionPage(detected!!, vm) { detected = null }
        }
    }
}
@Composable
fun ScanPage(
    fontScale: Float,
    onScale: (Float) -> Unit,
    onScaleFinished: () -> Unit,
    cameraDenied: Boolean,
    onRequestPermission: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val horizontalPadding = (configuration.screenWidthDp.dp * 0.055f).coerceIn(16.dp, 28.dp)
    val cameraHeight = (configuration.screenHeightDp.dp * 0.30f).coerceIn(220.dp, 340.dp)
    Column(Modifier.fillMaxSize().padding(horizontal = horizontalPadding, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        StaggeredAppear(0) {
            Column(Modifier.fillMaxWidth()) {
                Text("账本提取器", style = MaterialTheme.typography.headlineLarge)
                Text("把纸质账本留在镜头里", color = GlassMuted, style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (cameraDenied) {
            Spacer(Modifier.height(14.dp))
            StaggeredAppear(1) {
                GlassPanel(Modifier.fillMaxWidth(), strong = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, null, tint = Color(0xFFFFD38A))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("需要相机权限", style = MaterialTheme.typography.titleMedium)
                            Text("授权后即可继续拍摄账本", color = GlassMuted)
                        }
                        TextButton(onClick = onRequestPermission) { Text("重新授权") }
                    }
                }
            }
        }
        Spacer(Modifier.height(if (cameraDenied) 18.dp else 34.dp))
        StaggeredAppear(2) {
            PressScaleBox(
                Modifier.fillMaxWidth().height(cameraHeight)
                    .background(GlassSurfaceStrong, GlassShape)
                    .border(1.dp, GlassBorder, GlassShape),
                onClick = onRequestPermission
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(142.dp)
                            .background(Brush.linearGradient(listOf(Color(0xFFF0ECF8), Color(0xFFE6E0F2))), RoundedCornerShape(40.dp))
                            .border(1.dp, GlassBorder, RoundedCornerShape(40.dp)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.PhotoCamera, "拍摄账本", Modifier.size(76.dp), tint = GlassPurple) }
                }
            }
        }
        Spacer(Modifier.height(42.dp))
        StaggeredAppear(3) {
            GlassPanel(Modifier.fillMaxWidth(), strong = true) {
                Text("字体大小", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
                Slider(
                    value = fontScale,
                    onValueChange = onScale,
                    onValueChangeFinished = onScaleFinished,
                    valueRange = 1f..1.5f,
                    colors = SliderDefaults.colors(
                        thumbColor = GlassPurple,
                        activeTrackColor = GlassPurple,
                        inactiveTrackColor = Color(0xFFD9DADD)
                    )
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("默认", color = GlassMuted); Text("大", color = GlassMuted)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text("@原来是王某", color = GlassMuted, style = MaterialTheme.typography.bodyMedium)
    }
}
@Composable
fun RecognitionPage(items: List<DetectedOrder>, vm: MainVm, onDone: () -> Unit) {
    val source = items.firstOrNull()?.sourcePath.orEmpty()
    val phones = remember { mutableStateListOf<String>().apply { addAll(items.map { it.phone }); if (isEmpty()) add("") } }
    val selected = remember { mutableStateListOf<Boolean>().apply { repeat(phones.size) { add(true) } } }
    val requesters = remember { mutableStateListOf<FocusRequester>().apply { repeat(phones.size) { add(FocusRequester()) } } }
    val listState = rememberLazyListState()
    var focusIndex by remember { mutableStateOf<Int?>(0) }
    LaunchedEffect(focusIndex, phones.size) {
        val index = focusIndex ?: return@LaunchedEffect
        listState.scrollToItem(index)
        repeat(4) {
            withFrameNanos { }
            val focused = requesters.getOrNull(index)?.let { requester ->
                runCatching { requester.requestFocus() }.isSuccess
            } == true
            if (focused) {
                focusIndex = null
                return@LaunchedEffect
            }
            delay(40)
        }
    }
    fun addPhone() { phones.add(""); selected.add(true); requesters.add(FocusRequester()); focusIndex = phones.lastIndex }
    val fullRecognized = false
    val hasInput = phones.any { it.isNotBlank() }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(start = 18.dp, top = 12.dp, end = 18.dp, bottom = 18.dp)
    ) {
        StaggeredAppear(0) {
            GlassPanel(Modifier.fillMaxWidth(), strong = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (hasInput) Icons.Default.Edit else Icons.AutoMirrored.Filled.ReceiptLong, null, Modifier.size(32.dp), tint = GlassPurple)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(if (hasInput) "已填写单号" else "请输入单号", style = MaterialTheme.typography.titleLarge)
                        Text(if (hasInput) "确认后生成订单" else "支持数字、姓名或自定义内容", color = GlassMuted)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(phones.indices.toList()) { i ->
                GlassPanel(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = selected[i],
                            onCheckedChange = { selected[i] = it },
                            colors = CheckboxDefaults.colors(checkedColor = GlassPurple, checkmarkColor = Color.White, uncheckedColor = GlassMuted)
                        )
                        Text((i + 1).toString() + ". ", fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = phones[i],
                            onValueChange = { phones[i] = it.take(40) },
                            modifier = Modifier.fillMaxWidth().focusRequester(requesters[i]),
                            singleLine = true,
                            placeholder = { Text("请输入单号", color = GlassMuted) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { addPhone() }),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = GlassInk,
                                unfocusedTextColor = GlassInk,
                                focusedBorderColor = GlassPurple,
                                unfocusedBorderColor = GlassBorder,
                                cursorColor = GlassPurple
                            )
                        )
                    }
                }
            }
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    FilledIconButton(
                        onClick = { addPhone() },
                        modifier = Modifier.size(56.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFEDE8F7), contentColor = GlassPurple)
                    ) { Icon(Icons.Default.Add, "添加单号", modifier = Modifier.size(30.dp)) }
                }
            }
        }
        Button(
            onClick = { val save = phones.indices.filter { selected[it] && phones[it].isNotBlank() }.map { DetectedOrder(phones[it].trim(), source, source) }; if (save.isNotEmpty()) { vm.createBatch(save); onDone() } },
            enabled = phones.any { it.isNotBlank() },
            modifier = Modifier.fillMaxWidth().height(68.dp),
            shape = GlassShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF6848A7), disabledContainerColor = GlassSurface, disabledContentColor = GlassMuted)
        ) { Text("确认生成订单（" + phones.count { it.isNotBlank() } + "）", style = MaterialTheme.typography.titleMedium) }
    }
}
@Composable
fun OrdersPage(
    data: List<OrderWithItems>,
    query: String,
    onQuery: (String) -> Unit,
    vm: MainVm,
    onOpen: (OrderWithItems) -> Unit
) {
    var pickupTarget by remember { mutableStateOf<Order?>(null) }
    var deleteTarget by remember { mutableStateOf<Order?>(null) }
    var pendingExpanded by remember { mutableStateOf(true) }
    var completedExpanded by remember { mutableStateOf(false) }
    var gesturesEnabled by remember { mutableStateOf(false) }
    var swipeResetGeneration by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { delay(450); gesturesEnabled = true }
    val filtered = remember(data, query) { data.filter { query.isBlank() || it.order.phone.contains(query, ignoreCase = true) } }
    val pending = remember(filtered) { filtered.filter { it.order.status == OrderStatus.PENDING }.sortedBy { it.order.orderTime } }
    val completed = remember(filtered) { filtered.filter { it.order.status == OrderStatus.COMPLETED }.sortedByDescending { it.order.pickupTime ?: it.order.orderTime } }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            query, { onQuery(it.take(40)) }, Modifier.fillMaxWidth().padding(vertical = 14.dp),
            placeholder = { Text("输入单号搜索订单", color = GlassMuted) },
            leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = GlassShape,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = GlassInk, unfocusedTextColor = GlassInk,
                focusedBorderColor = GlassPurple, unfocusedBorderColor = GlassBorder,
                focusedContainerColor = GlassSurface, unfocusedContainerColor = GlassSurface, cursorColor = GlassPurple,
                focusedLeadingIconColor = GlassPurple, unfocusedLeadingIconColor = GlassMuted
            )
        )
        if (filtered.isNotEmpty()) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                if (pending.isNotEmpty()) {
                    item { GlassSectionHeader("待取货", pending.size, pendingExpanded) { pendingExpanded = !pendingExpanded } }
                    if (pendingExpanded) items(pending, key = { it.order.id }) { row ->
                        key(swipeResetGeneration) {
                            val state = rememberSwipeToDismissBoxState()
                            LaunchedEffect(state.currentValue) {
                                if (state.currentValue == SwipeToDismissBoxValue.EndToStart) {
                                    pickupTarget = row.order
                                    swipeResetGeneration++
                                }
                            }
                            SwipeToDismissBox(
                                state = state, enableDismissFromStartToEnd = false, gesturesEnabled = gesturesEnabled,
                                backgroundContent = {
                                    val active = state.targetValue == SwipeToDismissBoxValue.EndToStart
                                    Box(Modifier.fillMaxSize().background(if(active) GlassGreen.copy(alpha = .28f) else Color.Transparent, GlassShape), contentAlignment = Alignment.CenterEnd) {
                                        if(active) Icon(Icons.Default.CheckCircle, "取货", tint = GlassGreen, modifier = Modifier.padding(end = 22.dp))
                                    }
                                },
                                content = { OrderCard(row, onOpen) }
                            )
                        }
                    }
                }
                if (completed.isNotEmpty()) {
                    item { GlassSectionHeader("已取货", completed.size, completedExpanded) { completedExpanded = !completedExpanded } }
                    if (completedExpanded) items(completed, key = { it.order.id }) { row ->
                        key(swipeResetGeneration) {
                            val state = rememberSwipeToDismissBoxState()
                            LaunchedEffect(state.currentValue) {
                                if (state.currentValue == SwipeToDismissBoxValue.StartToEnd) {
                                    deleteTarget = row.order
                                    swipeResetGeneration++
                                }
                            }
                            SwipeToDismissBox(
                                state = state, enableDismissFromStartToEnd = true, enableDismissFromEndToStart = false, gesturesEnabled = gesturesEnabled,
                                backgroundContent = {
                                    val active = state.targetValue == SwipeToDismissBoxValue.StartToEnd
                                    Box(Modifier.fillMaxSize().background(if(active) GlassRed.copy(alpha = .28f) else Color.Transparent, GlassShape), contentAlignment = Alignment.CenterStart) {
                                        if(active) Icon(Icons.Default.Delete, "删除", tint = GlassRed, modifier = Modifier.padding(start = 22.dp))
                                    }
                                },
                                content = { OrderCard(row, onOpen) }
                            )
                        }
                    }
                }
            }
        }
    }
    pickupTarget?.let { order ->
        AlertDialog(
            onDismissRequest = { pickupTarget = null }, title = { Text("确认取货") },
            text = { Text("确认后将自动清理订单图片，只保留订单卡记录。") },
            confirmButton = { Button(onClick = { vm.complete(order); onQuery(""); pickupTarget = null }, colors = ButtonDefaults.buttonColors(containerColor = GlassPurple, contentColor = Color.White)) { Text("确认") } },
            dismissButton = { TextButton(onClick = { pickupTarget = null }) { Text("取消", color = GlassPurple) } },
            containerColor = Color.White, titleContentColor = GlassInk, textContentColor = GlassMuted, shape = GlassShape
        )
    }
    deleteTarget?.let { order ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null }, title = { Text("删除已取货订单") },
            text = { Text("确定删除这条订单记录吗？删除后无法恢复。") },
            confirmButton = { Button(onClick = { vm.delete(order); deleteTarget = null }, colors = ButtonDefaults.buttonColors(containerColor = GlassRed)) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消", color = GlassPurple) } },
            containerColor = Color.White, titleContentColor = GlassInk, textContentColor = GlassMuted, shape = GlassShape
        )
    }
}

@Composable
private fun GlassSectionHeader(title: String, count: Int, expanded: Boolean, onClick: () -> Unit) {
    PressScaleBox(Modifier.fillMaxWidth().background(GlassSurfaceStrong, GlassShape).border(1.dp, GlassBorder, GlassShape), onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 15.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("$title（$count）", style = MaterialTheme.typography.titleMedium, color = GlassInk)
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, "展开或折叠", tint = GlassInk)
        }
    }
}

@Composable
fun OrderCard(row: OrderWithItems, onOpen: (OrderWithItems) -> Unit) {
    val pending = row.order.status == OrderStatus.PENDING
    PressScaleBox(
        Modifier.fillMaxWidth().background(GlassSurface, GlassShape).border(1.dp, GlassBorder, GlassShape),
        enabled = pending, onClick = { onOpen(row) }
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    row.order.phone,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFF111216),
                    fontWeight = FontWeight.ExtraBold
                )
                Text(if (pending) "创建时间" else "取货时间", color = GlassMuted, style = MaterialTheme.typography.labelLarge)
                Text(dateFmt.format(Date(if (pending) row.order.orderTime else (row.order.pickupTime ?: row.order.orderTime))), color = GlassMuted, maxLines = 1, softWrap = false)
            }
            Icon(Icons.Default.Circle, null, modifier = Modifier.padding(start = 10.dp).size(13.dp), tint = if (pending) GlassGreen else GlassRed)
        }
    }
}
@Composable
fun OrderDetail(row: OrderWithItems, vm: MainVm, onCompleted: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var zoomPath by remember { mutableStateOf<String?>(null) }
    var remark by remember { mutableStateOf(row.order.remark) }
    var extraPath by remember { mutableStateOf(row.order.extraImagePath) }
    var pendingExtra by remember { mutableStateOf<File?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) pendingExtra?.let { extraPath = it.absolutePath; vm.updateDetails(row.order, remark, extraPath) } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) { val file = newPhotoFile(context, "extra_${row.order.id}"); pendingExtra = file; camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)) } }
    Scaffold(containerColor = Color.Transparent, bottomBar = { Surface(color = GlassSurfaceStrong, tonalElevation = 0.dp, modifier = Modifier.fillMaxWidth()) { Button(onClick = { vm.complete(row.order); onCompleted(); onBack() }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).navigationBarsPadding().height(58.dp), shape = GlassShape) { Text("标记已取货", style = MaterialTheme.typography.titleMedium) } } }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }; Text("订单详情", style = MaterialTheme.typography.headlineSmall) }
            LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                item { StaggeredAppear(0) { GlassPanel(Modifier.fillMaxWidth(), strong = true) {
                    Text("单号", color = GlassMuted); Text(row.order.phone, style = MaterialTheme.typography.headlineSmall)
                    Text("订单状态：待取货", color = GlassGreen)
                    Text("创建时间："); Text(dateFmt.format(Date(row.order.orderTime)), maxLines = 1, softWrap = false)
                    rememberThumbnail(row.order.paperImagePath)?.let { bmp -> Text("点击图片放大查看", color = GlassMuted); Image(bmp.asImageBitmap(), "订单图片", Modifier.fillMaxWidth().clickable { zoomPath = row.order.paperImagePath }, contentScale = ContentScale.FillWidth) }
                } } }
                item { StaggeredAppear(1) { GlassPanel(Modifier.fillMaxWidth()) {
                    Text("备注与附加图片", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(value = remark, onValueChange = { remark = it.take(500) }, modifier = Modifier.fillMaxWidth(), label = { Text("自定义备注") }, minLines = 3, shape = RoundedCornerShape(16.dp)); Button(onClick = { permission.launch(Manifest.permission.CAMERA) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF6848A7))) { Icon(Icons.Default.AddAPhoto, "添加图片"); Spacer(Modifier.width(8.dp)); Text(if (extraPath.isBlank()) "添加图片" else "重新拍摄图片") }
                    rememberThumbnail(extraPath)?.let { bmp -> Image(bmp.asImageBitmap(), "附加图片", Modifier.fillMaxWidth().clickable { zoomPath = extraPath }, contentScale = ContentScale.FillWidth) }
                    Button(onClick = { vm.updateDetails(row.order, remark, extraPath); Toast.makeText(context, "备注已保存", Toast.LENGTH_SHORT).show() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEDE8F7), contentColor = GlassPurple)) { Text("保存备注") }
                } } }
            }
        }
    }
    zoomPath?.let { path -> FullResolutionImageDialog(path) { zoomPath = null } }
}
@Composable fun ZoomImageDialog(bitmap:Bitmap,onDismiss:()->Unit){var scale by remember{mutableFloatStateOf(1f)};var offsetX by remember{mutableFloatStateOf(0f)};var offsetY by remember{mutableFloatStateOf(0f)};val state=rememberTransformableState{zoomChange,panChange,_->scale=(scale*zoomChange).coerceIn(1f,5f);offsetX+=panChange.x;offsetY+=panChange.y};Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)){Surface(Modifier.fillMaxSize(),color=Color.Black){Box(Modifier.fillMaxSize()){Image(bitmap.asImageBitmap(),"订单图片",Modifier.fillMaxSize().graphicsLayer(scaleX=scale,scaleY=scale,translationX=offsetX,translationY=offsetY).transformable(state),contentScale=ContentScale.Fit);IconButton(onClick=onDismiss,modifier=Modifier.align(Alignment.TopEnd).padding(12.dp)){Icon(Icons.Default.Close,"关闭",tint=Color.White)}}}}}

private fun loadSampledImage(path: String, maxDimension: Int): Bitmap? {
    if (path.isBlank()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
        sampleSize *= 2
    }
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    })
}

@Composable
private fun rememberThumbnail(path: String): Bitmap? {
    val image by produceState<Bitmap?>(initialValue = null, key1 = path) {
        value = withContext(Dispatchers.IO) { loadSampledImage(path, 900) }
    }
    return image
}

@Composable
private fun FullResolutionImageDialog(path: String, onDismiss: () -> Unit) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = path) {
        value = withContext(Dispatchers.IO) { loadSampledImage(path, 4096) }
    }
    if (bitmap == null) {
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = Color.Black) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                    IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                        Icon(Icons.Default.Close, "关闭", tint = Color.White)
                    }
                }
            }
        }
    } else {
        ZoomImageDialog(bitmap!!, onDismiss)
    }
}
private fun analyzePage(context:Context,uri:Uri,onResult:(List<DetectedOrder>)->Unit){
    val source=File(context.filesDir,"orders").listFiles()?.filter{it.name.startsWith("page_")}?.maxByOrNull{it.lastModified()}
    Handler(Looper.getMainLooper()).post { onResult(listOf(DetectedOrder("",source?.absolutePath.orEmpty(),source?.absolutePath.orEmpty()))) }
}
@Composable fun ScaledContent(scale:Float,content: @Composable () -> Unit){val t=MaterialTheme.typography;val scaled=remember(t,scale){t.copy(displayLarge=t.displayLarge.copy(fontSize=t.displayLarge.fontSize*scale),displayMedium=t.displayMedium.copy(fontSize=t.displayMedium.fontSize*scale),displaySmall=t.displaySmall.copy(fontSize=t.displaySmall.fontSize*scale),headlineLarge=t.headlineLarge.copy(fontSize=t.headlineLarge.fontSize*scale),headlineMedium=t.headlineMedium.copy(fontSize=t.headlineMedium.fontSize*scale),headlineSmall=t.headlineSmall.copy(fontSize=t.headlineSmall.fontSize*scale),titleLarge=t.titleLarge.copy(fontSize=t.titleLarge.fontSize*scale),titleMedium=t.titleMedium.copy(fontSize=t.titleMedium.fontSize*scale),titleSmall=t.titleSmall.copy(fontSize=t.titleSmall.fontSize*scale),bodyLarge=t.bodyLarge.copy(fontSize=t.bodyLarge.fontSize*scale),bodyMedium=t.bodyMedium.copy(fontSize=t.bodyMedium.fontSize*scale),bodySmall=t.bodySmall.copy(fontSize=t.bodySmall.fontSize*scale),labelLarge=t.labelLarge.copy(fontSize=t.labelLarge.fontSize*scale),labelMedium=t.labelMedium.copy(fontSize=t.labelMedium.fontSize*scale),labelSmall=t.labelSmall.copy(fontSize=t.labelSmall.fontSize*scale))};MaterialTheme(typography=scaled,content=content)}
@Composable fun OrdersPage(data:List<OrderWithItems>,query:String,onQuery:(String)->Unit,vm:MainVm,fontScale:Float,onOpen:(OrderWithItems)->Unit){ScaledContent(fontScale){OrdersPage(data,query,onQuery,vm,onOpen)}}
@Composable fun OrderDetail(row:OrderWithItems,vm:MainVm,fontScale:Float,onCompleted:()->Unit,onBack:()->Unit){ScaledContent(fontScale){OrderDetail(row,vm,onCompleted,onBack)}}
class MainActivity:ComponentActivity(){override fun onCreate(b:Bundle?){super.onCreate(b);WindowCompat.setDecorFitsSystemWindows(window,true);WindowCompat.getInsetsController(window,window.decorView).apply{isAppearanceLightStatusBars=true;isAppearanceLightNavigationBars=true};val db=AppDb.get(this);setContent{JiaFangGlassTheme{val vm:MainVm=viewModel(factory=VmFactory(db));App(vm)}}}}

