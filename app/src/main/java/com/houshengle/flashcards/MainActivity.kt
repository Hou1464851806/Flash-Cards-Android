package com.houshengle.flashcards

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.houshengle.flashcards.data.Card
import com.houshengle.flashcards.data.CardDao
import com.houshengle.flashcards.data.CardView
import com.houshengle.flashcards.data.DataBase
import com.houshengle.flashcards.data.Group
import com.houshengle.flashcards.data.GroupDao
import com.houshengle.flashcards.ui.theme.FlashCardsTheme
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db =
            Room.databaseBuilder(applicationContext, DataBase::class.java, "cards-database").build()
        Log.println(Log.INFO, "数据库路径", getDatabasePath("cards-database").absolutePath)

        val cardDao = db.cardDao()
        val groupDao = db.groupDao()

        runBlocking {
            groupDao.getGroupByName("默认")?.id ?: groupDao.insert(Group(name = "默认"))
        }

        setContent {
            FlashCardsTheme {
                App(cardDao = cardDao, groupDao = groupDao)
            }
        }

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1
            )
        }

        setupWorkManager(this, cardDao)
    }

}

fun setupWorkManager(context: Context, cardDao: CardDao) {
    val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED)
        .setRequiresCharging(false).build()
    var cardsNumToReview: Int
    runBlocking {
        cardsNumToReview = cardDao.getCardsNumberWithDueTimeBefore(getTodayDueTime(23, 59, 59))
    }
    val delayTime = calculateInitialDelay(context)
    Log.i("应用", cardsNumToReview.toString())
    val inputData = workDataOf(ReviewWorker.PARAM_1 to cardsNumToReview.toString())
    val workRequest = OneTimeWorkRequestBuilder<ReviewWorker>().setInitialDelay(
        delayTime, TimeUnit.MILLISECONDS
    ).setConstraints(constraints).addTag("Worker").setInputData(inputData).build()
    WorkManager.getInstance(context)
        .enqueueUniqueWork("Review", ExistingWorkPolicy.REPLACE, workRequest)
}

fun calculateInitialDelay(context: Context): Long {
    val sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE)
    val notifyTimeHour = sharedPreferences.getInt("notify_time_hour", 8)
    val notifyTimeMinute = sharedPreferences.getInt("notify_time_minute", 0)

    val targetTime = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, notifyTimeHour) // 设置提醒时间
        set(Calendar.MINUTE, notifyTimeMinute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    Log.i(
        "设定时间", SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss,SSS", Locale.getDefault()
        ).format(
            Date(targetTime)
        )
    )
    val currentTime = System.currentTimeMillis()
    return if (targetTime > currentTime) {
        targetTime - currentTime
    } else {
        targetTime + TimeUnit.DAYS.toMillis(1) - currentTime
    }
}

@Composable
fun App(modifier: Modifier = Modifier, cardDao: CardDao, groupDao: GroupDao) {
    val navController = rememberNavController()

    Scaffold(bottomBar = {
        BottomBar(
            modifier = modifier, navController = navController
        )
    }) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Review.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Review.route) {
                ReviewScreen(
                    modifier = modifier, cardDao = cardDao, groupDao = groupDao
                )
            }
            composable(Screen.Add.route) {
                AddScreen(
                    modifier = modifier, cardDao = cardDao, groupDao = groupDao
                )
            }
            composable(Screen.Cards.route) {
                CardsScreen(
                    modifier = modifier, cardDao = cardDao, groupDao = groupDao
                )
            }
            composable(Screen.Groups.route) {
                GroupsScreen(
                    modifier = modifier, cardDao = cardDao, groupDao = groupDao
                )
            }
            composable(Screen.Options.route) { OptionsScreen() }
        }
    }
}

sealed class Screen(val route: String) {
    data object Review : Screen("review")
    data object Add : Screen("add")
    data object Cards : Screen("cards")
    data object Groups : Screen("groups")
    data object Options : Screen("options")
}

fun getTodayDueTime(hour: Int, minute: Int, second: Int): Long {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, hour)
    calendar.set(Calendar.MINUTE, minute)
    calendar.set(Calendar.SECOND, second)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

@Composable
fun ReviewScreen(modifier: Modifier = Modifier, cardDao: CardDao, groupDao: GroupDao) {
    val context = LocalContext.current

    val todayDueTime = getTodayDueTime(23, 59, 59)

    val cardView = CardView(cardDao)

    val reviewCards = remember { mutableStateListOf<Card>() }

    val displayCards = remember { mutableStateListOf<Card>() }

    var currentCard by remember {
        mutableStateOf<Card?>(null)
    }

    var showAnswer by remember {
        mutableStateOf(false)
    }

    var selectedGroup by remember { mutableStateOf("所有") }
    var selectedGroupId: Int
    var expanded by remember { mutableStateOf(false) }
    val groups = remember { mutableStateListOf<String>() }

    var isShowTime by remember {
        mutableStateOf(false)
    }

    val sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE)
    val notifyTimeHour = sharedPreferences.getInt("notify_time_hour", 8)
    val notifyTimeMinute = sharedPreferences.getInt("notify_time_minute", 0)

    LaunchedEffect(Unit) {
        val dbGroups = groupDao.getAllGroups()
        groups.addAll(dbGroups.map { it.name })
    }

    LaunchedEffect(Unit) {
        cardView.getAllCardsWithDueTimeBefore(todayDueTime) { cards ->
            reviewCards.clear()
            reviewCards.addAll(cards)
        }
    }


    if (isShowTime) {
        var displayHour by remember {
            mutableStateOf(notifyTimeHour.toString())
        }
        var displayMinute by remember {
            mutableStateOf(notifyTimeMinute.toString())
        }
        AlertDialog(onDismissRequest = { isShowTime = false },
            title = { Text("设置时间") },
            text = {
                Column {
                    OutlinedTextField(value = displayHour,
                        onValueChange = { displayHour = it },
                        label = {
                            Text(
                                text = "小时"
                            )
                        })
                    OutlinedTextField(value = displayMinute,
                        onValueChange = { displayMinute = it },
                        label = {
                            Text(
                                text = "分钟"
                            )
                        })
                }
            },
            confirmButton = {
                Button(onClick = {
                    val editor = sharedPreferences.edit()
                    editor.putInt("notify_time_hour", displayHour.toInt())
                    editor.putInt("notify_time_minute", displayMinute.toInt())
                    editor.apply()
                    setupWorkManager(context, cardDao)
                    isShowTime = false
                }) {
                    Text("确认")
                }
            },
            dismissButton = {
                Button(onClick = {
                    isShowTime = false
                }) {
                    Text("取消")
                }
            })
    }
    //Log.i("复习截止时间", todayDueTime.toString())

    if (reviewCards.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "今天没有需要复习的卡片", fontSize = TextUnit(25.0F, TextUnitType.Sp))
        }
    } else {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            if (selectedGroup != "所有") {
                runBlocking {
                    selectedGroupId = groupDao.getGroupByName(selectedGroup)!!.id
                }
                displayCards.clear()
                displayCards.addAll((reviewCards).filter { it.groupId == selectedGroupId })
            } else {
                displayCards.clear()
                displayCards.addAll(reviewCards)
            }

            if (!showAnswer) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier
                        //.align(Alignment.Start)
                        .padding(horizontal = 20.dp)
                        .clickable { expanded = true }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                selectedGroup, modifier = Modifier, fontSize = 20.sp
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null
                            )
                            DropdownMenu(modifier = Modifier.wrapContentSize(align = Alignment.Center),
                                expanded = expanded,
                                onDismissRequest = { expanded = false }) {
                                DropdownMenuItem(onClick = {
                                    selectedGroup = "所有"
                                    expanded = false
                                }, text = { Text(text = "所有", fontSize = 18.sp) })
                                groups.forEach { group ->
                                    DropdownMenuItem(onClick = {
                                        selectedGroup = group
                                        expanded = false
                                    }, text = { Text(text = group, fontSize = 18.sp) })
                                }
                            }
                        }
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(text = "提醒复习时间")
                        Text(text = String.format(
                            Locale.getDefault(), "%02d: %02d", notifyTimeHour, notifyTimeMinute
                        ), modifier = Modifier.clickable { isShowTime = true })
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(text = "${displayCards.count { it.interval == 0 }} 新卡片")
                        Text(text = "${displayCards.count { it.interval != 0 }} 待复习")
                    }
                }

                if (displayCards.isEmpty()) {
                    Text(
                        text = "今天此卡组中没有需要复习的卡片",
                        fontSize = TextUnit(25.0F, TextUnitType.Sp),
                    )
                    Spacer(modifier = Modifier.padding(20.dp))
                } else {
                    currentCard = displayCards.first()
                    ElevatedCard(
                        Modifier.wrapContentSize(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onPrimary)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 200.dp, height = 300.dp)
                                .padding(5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = currentCard!!.front,
                                fontSize = TextUnit(5.0F, TextUnitType.Em)
                            )
                        }
                    }

                    TextButton(onClick = {
                        showAnswer = true
                    }) {
                        Text(
                            text = "显示答案",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = TextUnit(20.0F, TextUnitType.Sp)
                        )
                    }
                }
            } else {
                ElevatedCard(
                    Modifier
                        .wrapContentSize(align = Alignment.Center)
                        .heightIn(min = 300.dp)
                        .padding(15.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .padding(20.dp),
                        contentAlignment = Alignment.TopStart,
                    ) {
                        Text(
                            text = currentCard!!.front, fontSize = TextUnit(5.0F, TextUnitType.Em)
                        )
                    }
                    Divider(thickness = 1.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .padding(20.dp),
                        contentAlignment = Alignment.TopStart,
                    ) {
                        Text(
                            text = currentCard!!.back, fontSize = TextUnit(5.0F, TextUnitType.Em)
                        )
                    }
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(modifier = Modifier.padding(5.dp), onClick = {
                            cardView.reviewCard(currentCard!!, 0)
                            displayCards.removeFirst()
                            showAnswer = false
                            cardView.getAllCardsWithDueTimeBefore(todayDueTime) { cards ->
                                reviewCards.clear()
                                reviewCards.addAll(cards)
                            }
                        }) {
                            Text(
                                text = "非常困难", fontSize = TextUnit(15.0F, TextUnitType.Sp)
                            )
                        }
                        Button(modifier = Modifier.padding(5.dp), onClick = {
                            cardView.reviewCard(currentCard!!, 1)
                            displayCards.removeFirst()
                            showAnswer = false
                            cardView.getAllCardsWithDueTimeBefore(todayDueTime) { cards ->
                                reviewCards.clear()
                                reviewCards.addAll(cards)
                            }
                        }) {
                            Text(
                                text = "中等困难", fontSize = TextUnit(15.0F, TextUnitType.Sp)
                            )
                        }
                        Button(modifier = Modifier.padding(5.dp), onClick = {
                            cardView.reviewCard(currentCard!!, 2)
                            displayCards.removeFirst()
                            showAnswer = false
                            cardView.getAllCardsWithDueTimeBefore(todayDueTime) { cards ->
                                reviewCards.clear()
                                reviewCards.addAll(cards)
                            }
                        }) {
                            Text(
                                text = "一般困难", fontSize = TextUnit(15.0F, TextUnitType.Sp)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        FilledTonalButton(modifier = Modifier.padding(5.dp), onClick = {
                            cardView.reviewCard(currentCard!!, 3)
                            displayCards.removeFirst()
                            showAnswer = false
                            cardView.getAllCardsWithDueTimeBefore(todayDueTime) { cards ->
                                reviewCards.clear()
                                reviewCards.addAll(cards)
                            }
                        }) {
                            Text(
                                text = "一般容易", fontSize = TextUnit(15.0F, TextUnitType.Sp)
                            )
                        }
                        FilledTonalButton(modifier = Modifier.padding(5.dp), onClick = {
                            cardView.reviewCard(currentCard!!, 4)
                            displayCards.removeFirst()
                            showAnswer = false
                            cardView.getAllCardsWithDueTimeBefore(todayDueTime) { cards ->
                                reviewCards.clear()
                                reviewCards.addAll(cards)
                            }
                        }) {
                            Text(
                                text = "中等容易", fontSize = TextUnit(15.0F, TextUnitType.Sp)
                            )
                        }
                        FilledTonalButton(modifier = Modifier.padding(5.dp), onClick = {
                            cardView.reviewCard(currentCard!!, 5)
                            displayCards.removeFirst()
                            showAnswer = false
                            cardView.getAllCardsWithDueTimeBefore(todayDueTime) { cards ->
                                reviewCards.clear()
                                reviewCards.addAll(cards)
                            }
                        }) {
                            Text(
                                text = "非常容易", fontSize = TextUnit(15.0F, TextUnitType.Sp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddScreen(modifier: Modifier = Modifier, cardDao: CardDao, groupDao: GroupDao) {
    Column(modifier = modifier.fillMaxWidth()) {
        AddCard(modifier = modifier.fillMaxWidth(), cardDao, groupDao)
    }
}

@Composable
fun AddCard(modifier: Modifier = Modifier, cardDao: CardDao, groupDao: GroupDao) {
    val context = LocalContext.current

    var cardFrontTextFieldValue by remember { mutableStateOf(TextFieldValue()) }
    var cardBackTextFieldValue by remember { mutableStateOf(TextFieldValue()) }
    var isStar by remember {
        mutableStateOf(false)
    }

    var selectedGroup by remember { mutableStateOf("默认") }
    var expanded by remember { mutableStateOf(false) }
    var newGroupDialogVisible by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    val groups = remember { mutableStateListOf<String>() }

    val cardView = CardView(cardDao)

    LaunchedEffect(Unit) {
        val dbGroups = groupDao.getAllGroups()
        groups.addAll(dbGroups.map { it.name })
    }

    Row(
        modifier = modifier
            .wrapContentHeight()
            .fillMaxWidth()
            .padding(5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(modifier = Modifier.wrapContentSize(), onClick = {
            isStar = !isStar
        }) {
            Icon(
                modifier = Modifier.size(30.dp),
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = if (isStar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
            )
        }

        Box(
            modifier = Modifier.clickable { expanded = true }, contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    selectedGroup, fontSize = 20.sp
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown, contentDescription = null
                )
                DropdownMenu(modifier = Modifier.wrapContentSize(align = Alignment.Center),
                    expanded = expanded,
                    onDismissRequest = { expanded = false }) {
                    groups.forEach { group ->
                        DropdownMenuItem(onClick = {
                            selectedGroup = group
                            expanded = false
                        }, text = { Text(text = group, fontSize = 18.sp) })
                    }
                    Divider()
                    DropdownMenuItem(onClick = {
                        newGroupDialogVisible = true
                        expanded = false
                    }, text = { Text(text = "新建", fontSize = 18.sp) })
                }
            }
        }

        FilledTonalButton(modifier = Modifier.width(75.dp), onClick = {
            //检查变量合法性
            if (cardFrontTextFieldValue.text.isEmpty() or cardBackTextFieldValue.text.isEmpty()) {
                Toast.makeText(context, "卡面或者卡背为空，请检查", Toast.LENGTH_SHORT).show()
                return@FilledTonalButton
            }

            val currentTime = Calendar.getInstance().timeInMillis

            val groupId = runBlocking {
                groupDao.getGroupByName(selectedGroup)?.id
                    ?: groupDao.insert(Group(name = selectedGroup)).toInt()
            }

            //数据库插入
            cardView.insert(
                Card(
                    front = cardFrontTextFieldValue.text,
                    back = cardBackTextFieldValue.text,
                    createdTime = currentTime,
                    updatedTime = currentTime,
                    isStar = isStar,
                    planReviewTime = currentTime,
                    groupId = groupId,
                    groupName = selectedGroup
                )
            )
            Toast.makeText(context, "卡片添加成功", Toast.LENGTH_SHORT).show()
            //重置状态
            cardFrontTextFieldValue = TextFieldValue()
            cardBackTextFieldValue = TextFieldValue()
            isStar = false

        }) {
            Text(text = "完成", fontSize = TextUnit(3.0F, TextUnitType.Em))
        }
    }

    OutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .padding(5.dp),
        value = cardFrontTextFieldValue,
        onValueChange = { cardFrontTextFieldValue = it },
        singleLine = false,
        label = { Text(text = "卡片正面") },
        placeholder = {
            Text(
                text = "问题"
            )
        },
    )

    OutlinedTextField(modifier = Modifier
        .fillMaxWidth()
        .padding(5.dp)
        .fillMaxHeight(),
        value = cardBackTextFieldValue,
        onValueChange = { cardBackTextFieldValue = it },
        singleLine = false,
        label = { Text(text = "卡片背面") },
        placeholder = {
            Text(
                text = "答案..."
            )
        })

    if (newGroupDialogVisible) {
        AlertDialog(onDismissRequest = { newGroupDialogVisible = false },
            title = { Text("新建卡组") },
            text = {
                OutlinedTextField(value = newGroupName,
                    onValueChange = { newGroupName = it },
                    placeholder = { Text("卡组名字") })
            },
            confirmButton = {
                Button(onClick = {
                    if (newGroupName.isNotEmpty() && !groups.contains(newGroupName)) {
                        groups.add(newGroupName)
                        runBlocking {
                            groupDao.insert(Group(name = newGroupName))
                        }
                        selectedGroup = newGroupName
                    }
                    newGroupDialogVisible = false
                }) {
                    Text("确认")
                }
            },
            dismissButton = {
                Button(onClick = {
                    newGroupDialogVisible = false
                }) {
                    Text("取消")
                }
            })
    }

}

@Composable
fun CardsScreen(modifier: Modifier = Modifier, cardDao: CardDao, groupDao: GroupDao) {
    val cardView = CardView(cardDao)

    val cards by cardView.allCards.observeAsState(emptyList())

    val minOffset = -600f

    var selectedGroup by remember { mutableStateOf("所有") }
    var selectedGroupId: Int
    var expanded by remember { mutableStateOf(false) }
    val groups = remember { mutableStateListOf<String>() }

    var displayCards by remember {
        mutableStateOf<List<Card>>(emptyList())
    }

    LaunchedEffect(Unit) {
        val dbGroups = groupDao.getAllGroups()
        groups.addAll(dbGroups.map { it.name })
    }

    if (selectedGroup != "所有") {
        runBlocking {
            selectedGroupId = groupDao.getGroupByName(selectedGroup)!!.id
        }
        displayCards = cards.filter { it.groupId == selectedGroupId }
    } else {
        displayCards = cards
    }

    Column(modifier = modifier.fillMaxHeight()) {
        //SearchBar()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier
                //align(Alignment.Start)
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .clickable { expanded = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        selectedGroup, modifier = Modifier, fontSize = 20.sp
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown, contentDescription = null
                    )
                    DropdownMenu(modifier = Modifier.wrapContentSize(align = Alignment.Center),
                        expanded = expanded,
                        onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(onClick = {
                            selectedGroup = "所有"
                            expanded = false
                        }, text = { Text(text = "所有", fontSize = 18.sp) })
                        groups.forEach { group ->
                            DropdownMenuItem(onClick = {
                                selectedGroup = group
                                expanded = false
                            }, text = { Text(text = group, fontSize = 18.sp) })
                        }
                    }
                }
            }
            Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text(text = "${displayCards.count()} 张卡片")
            }
        }
        Column(
            modifier = modifier
                .fillMaxHeight()
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (displayCards.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "此卡组下没有卡片，请添加",
                        fontSize = TextUnit(25.0F, TextUnitType.Sp)
                    )
                }
            } else {
                LazyColumn(modifier = modifier.padding(vertical = 8.dp)) {
                    items(displayCards, key = { it.id }) { card ->

                        var dismissed by remember { mutableStateOf(false) }
                        var offsetX by remember { mutableFloatStateOf(0f) }
                        var isShowCardDetail by remember { mutableStateOf(false) }

                        if (!dismissed) {
                            Box(modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp, horizontal = 8.dp)
                                .clickable {
                                    isShowCardDetail = true
                                }
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures(onHorizontalDrag = { change, dragAmount ->
                                        offsetX = (offsetX + dragAmount).coerceIn(minOffset, 0f)
                                        //Log.i("偏移", offsetX.toString())
                                        if (offsetX == minOffset) {
                                            dismissed = true
                                            cardView.delete(card)
                                        }
                                    })
                                }) {
                                OutlinedCard(
                                    Modifier
                                        .offset { IntOffset(offsetX.toInt(), 0) }
                                        .wrapContentSize(align = Alignment.Center)
                                        .padding(horizontal = 15.dp, vertical = 4.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onPrimary)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .wrapContentHeight()
                                            .padding(20.dp),
                                        contentAlignment = Alignment.TopStart,
                                    ) {
                                        //设计布局
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            // 顶部行，包含文本和图标
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Start
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(
                                                        text = "最新编辑时间: ${
                                                            SimpleDateFormat(
                                                                "yyyy-MM-dd", Locale.getDefault()
                                                            ).format(
                                                                Date(card.updatedTime)
                                                            )
                                                        } ",
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Text(
                                                        text = "下次复习时间: ${
                                                            SimpleDateFormat(
                                                                "yyyy-MM-dd", Locale.getDefault()
                                                            ).format(
                                                                Date(card.planReviewTime)
                                                            )
                                                        } ",
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                            // 中间的文本行
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp)
                                            ) {
                                                Column {
                                                    Text(
                                                        text = card.front,
                                                        fontSize = 24.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Divider(
                                                        thickness = 2.dp,
                                                        modifier = Modifier.padding(vertical = 5.dp),
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Text(
                                                        text = card.back,
                                                        fontSize = 24.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                            // 底部行，包含星星图标
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Start
                                            ) {
                                                Text(
                                                    text = "已复习 ${card.totalReviewTimes} 次",
                                                    color = Color.Gray
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = if (card.interval == 0) "新卡片" else "复习中",
                                                    color = Color.Gray
                                                )
                                                Spacer(modifier = Modifier.width(110.dp))
                                                if (card.isStar) {
                                                    Icon(
                                                        modifier = Modifier,
                                                        imageVector = Icons.Default.Star,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                if (offsetX <= -100f) {
                                    Box(
                                        modifier = Modifier
                                            .padding(end = 10.dp)
                                            .align(Alignment.CenterEnd),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        FilledTonalIconButton(onClick = {
                                            dismissed = true
                                            cardView.delete(card)
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = null,
                                            )
                                        }
                                    }
                                }
                            }
                            if (isShowCardDetail) {
                                var updateCardFront by remember {
                                    mutableStateOf(card.front)
                                }
                                var updateCardBack by remember {
                                    mutableStateOf(card.back)
                                }
                                var updateCardIsStar by remember {
                                    mutableStateOf(card.isStar)
                                }
                                AlertDialog(onDismissRequest = { isShowCardDetail = false },
                                    title = { Text("编辑卡片") },
                                    text = {
                                        Column {
                                            OutlinedTextField(value = updateCardFront,
                                                onValueChange = { updateCardFront = it },
                                                label = {
                                                    Text(
                                                        text = "卡片正面"
                                                    )
                                                })
                                            OutlinedTextField(value = updateCardBack,
                                                onValueChange = { updateCardBack = it },
                                                label = {
                                                    Text(
                                                        text = "卡片背面"
                                                    )
                                                })
                                            IconButton(modifier = Modifier.align(Alignment.End),
                                                onClick = {
                                                    updateCardIsStar = !updateCardIsStar
                                                }) {
                                                Icon(
                                                    imageVector = Icons.Default.Star,
                                                    contentDescription = null,
                                                    tint = if (updateCardIsStar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                                                )
                                            }
                                        }
                                    },
                                    confirmButton = {
                                        Button(onClick = {
                                            card.front = updateCardFront
                                            card.back = updateCardBack
                                            card.isStar = updateCardIsStar
                                            cardView.updateCard(card)
                                            isShowCardDetail = false
                                        }) {
                                            Text("确认")
                                        }
                                    },
                                    dismissButton = {
                                        Button(onClick = {
                                            isShowCardDetail = false
                                        }) {
                                            Text("取消")
                                        }
                                    })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GroupsScreen(modifier: Modifier = Modifier, cardDao: CardDao, groupDao: GroupDao) {

    var newGroupDialogVisible by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }

    val groups = remember { mutableStateListOf<Group>() }

    LaunchedEffect(Unit) {
        val dbGroups = groupDao.getAllGroups()
        groups.clear()
        groups.addAll(dbGroups)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        IconButton(
            onClick = { newGroupDialogVisible = true },
            modifier = Modifier
                .align(Alignment.End)
                .padding(horizontal = 10.dp)
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
        }
        //SearchBar()
        LazyColumn(modifier = modifier.padding(vertical = 8.dp)) {
            items(groups) { group ->

                var dismissed by remember { mutableStateOf(false) }
                var offsetX by remember { mutableFloatStateOf(0f) }
                var isShowGroupDetail by remember { mutableStateOf(false) }

                if (!dismissed) {
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp, horizontal = 8.dp)
                        .clickable {
                            isShowGroupDetail = true
                        }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(onHorizontalDrag = { change, dragAmount ->
                                offsetX = (offsetX + dragAmount).coerceIn(-600f, 0f)
                                if (offsetX == -600f) {
                                    dismissed = true
                                    runBlocking {
                                        groupDao.delete(group)
                                        val dbGroups = groupDao.getAllGroups()
                                        groups.clear()
                                        groups.addAll(dbGroups)
                                    }
                                }
                            })
                        }) {
                        ElevatedCard(
                            Modifier
                                .offset { IntOffset(offsetX.toInt(), 0) }
                                .wrapContentSize(align = Alignment.Center)
                                .padding(horizontal = 15.dp, vertical = 4.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onPrimary)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                                    .padding(20.dp),
                                contentAlignment = Alignment.TopStart,
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {

                                    Text(
                                        text = group.name,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        if (offsetX <= -100f) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 10.dp)
                                    .align(Alignment.CenterEnd),
                                contentAlignment = Alignment.Center
                            ) {
                                FilledTonalIconButton(onClick = {
                                    dismissed = true
                                    runBlocking {
                                        groupDao.delete(group)
                                        val dbGroups = groupDao.getAllGroups()
                                        groups.clear()
                                        groups.addAll(dbGroups)
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                    )
                                }
                            }
                        }
                    }
                    if (isShowGroupDetail) {
                        var updateGroupName by remember {
                            mutableStateOf(group.name)
                        }
                        AlertDialog(onDismissRequest = { isShowGroupDetail = false },
                            title = { Text("编辑卡组") },
                            text = {
                                Column {
                                    OutlinedTextField(value = updateGroupName,
                                        onValueChange = { updateGroupName = it },
                                        label = {
                                            Text(
                                                text = "卡组名字"
                                            )
                                        })
                                }
                            },
                            confirmButton = {
                                Button(onClick = {
                                    group.name = updateGroupName
                                    runBlocking {
                                        groupDao.update(group)
                                        val dbGroups = groupDao.getAllGroups()
                                        groups.clear()
                                        groups.addAll(dbGroups)
                                    }
                                    isShowGroupDetail = false
                                }) {
                                    Text("确认")
                                }
                            },
                            dismissButton = {
                                Button(onClick = {
                                    isShowGroupDetail = false
                                }) {
                                    Text("取消")
                                }
                            })
                    }
                }
            }
        }
    }
    if (newGroupDialogVisible) {
        AlertDialog(onDismissRequest = { newGroupDialogVisible = false },
            title = { Text("新建卡组") },
            text = {
                OutlinedTextField(value = newGroupName,
                    onValueChange = { newGroupName = it },
                    placeholder = { Text("卡组名字") })
            },
            confirmButton = {
                Button(onClick = {
                    var newGroup: Group?
                    runBlocking {
                        newGroup = groupDao.getGroupByName(newGroupName)
                    }
                    if (newGroupName.isNotEmpty() && newGroup == null) {
                        runBlocking {
                            groupDao.insert(Group(name = newGroupName))
                            val dbGroups = groupDao.getAllGroups()
                            groups.clear()
                            groups.addAll(dbGroups)
                        }
                    }
                    newGroupDialogVisible = false
                }) {
                    Text("确认")
                }
            },
            dismissButton = {
                Button(onClick = {
                    newGroupDialogVisible = false
                }) {
                    Text("取消")
                }
            })
    }
}

@Composable
fun OptionsScreen() {
    var notifyTimeString by remember {
        mutableStateOf("")
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Text(text = "计划")
        OutlinedTextField(value = notifyTimeString,
            onValueChange = { notifyTimeString = it },
            label = {
                Text(
                    text = "提醒复习时间"
                )
            },
            placeholder = {},
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun SearchBar(modifier: Modifier = Modifier, placeholder: String = "搜索") {
    var queryString by remember {
        mutableStateOf("")
    }
    TextField(
        placeholder = { Text(text = placeholder) },
        value = queryString,
        onValueChange = { queryString = it },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
    )
}

@Composable
fun BottomBar(modifier: Modifier = Modifier, navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(modifier = modifier) {
        NavigationBarItem(selected = currentRoute == Screen.Review.route,
            onClick = { navController.navigate(Screen.Review.route) },
            icon = { Icon(imageVector = Icons.Default.DateRange, contentDescription = null) },
            label = { Text(text = "复习") })
        NavigationBarItem(selected = currentRoute == Screen.Add.route,
            onClick = { navController.navigate(Screen.Add.route) },
            icon = { Icon(imageVector = Icons.Default.Add, contentDescription = null) },
            label = { Text(text = "添加") })
        NavigationBarItem(selected = currentRoute == Screen.Cards.route,
            onClick = { navController.navigate(Screen.Cards.route) },
            icon = { Icon(imageVector = Icons.Default.List, contentDescription = null) },
            label = { Text(text = "卡片") })
        NavigationBarItem(selected = currentRoute == Screen.Groups.route,
            onClick = { navController.navigate(Screen.Groups.route) },
            icon = { Icon(imageVector = Icons.Default.Menu, contentDescription = null) },
            label = { Text(text = "卡组") })
//        NavigationBarItem(selected = currentRoute == Screen.Options.route,
//            onClick = { navController.navigate(Screen.Options.route) },
//            icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = null) },
//            label = { Text(text = "设置") })
    }

}
