//package com.jhkj.videoplayer.compose_pages.pages
//
//import android.annotation.SuppressLint
//import android.text.TextUtils
//import androidx.activity.compose.BackHandler
//import androidx.compose.foundation.Image
//import androidx.compose.foundation.clickable
//import androidx.compose.foundation.layout.Arrangement
//import androidx.compose.foundation.layout.Box
//import androidx.compose.foundation.layout.Column
//import androidx.compose.foundation.layout.FlowRow
//import androidx.compose.foundation.layout.PaddingValues
//import androidx.compose.foundation.layout.Row
//import androidx.compose.foundation.layout.Spacer
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.foundation.layout.fillMaxWidth
//import androidx.compose.foundation.layout.height
//import androidx.compose.foundation.layout.padding
//import androidx.compose.foundation.layout.size
//import androidx.compose.foundation.layout.statusBarsPadding
//import androidx.compose.foundation.layout.width
//import androidx.compose.foundation.lazy.grid.GridCells
//import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.outlined.ArrowBack
//import androidx.compose.material3.CircularProgressIndicator
//import androidx.compose.material3.Text
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.LaunchedEffect
//import androidx.compose.runtime.getValue
//import androidx.compose.runtime.mutableStateListOf
//import androidx.compose.runtime.mutableStateOf
//import androidx.compose.runtime.remember
//import androidx.compose.runtime.rememberCoroutineScope
//import androidx.compose.runtime.setValue
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.res.painterResource
//import androidx.compose.ui.text.font.FontWeight
//import androidx.compose.ui.text.style.TextAlign
//import androidx.compose.ui.text.style.TextOverflow
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import androidx.lifecycle.viewmodel.compose.viewModel
//import androidx.navigation.NavController
//import com.jhkj.gl_player.util.HttpDate
//import com.jhkj.videoplayer.R
//import com.jhkj.videoplayer.compose_pages.models.ConnInfo
//import com.jhkj.videoplayer.compose_pages.models.FileType
//import com.jhkj.videoplayer.compose_pages.models.ObservableStack
//import com.jhkj.videoplayer.viewmodels.WebdavViewModel
//import com.jhkj.videoplayer.compose_pages.widgets.CustomIcon
//import com.jhkj.videoplayer.theme.textPrimary
//import com.jhkj.videoplayer.theme.textThird
//import com.thegrizzlylabs.sardineandroid.DavResource
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.withContext
//import java.util.Date
//
//
//@SuppressLint("MutableCollectionMutableState")
//@Composable
//fun WebdavEntryScreen(
//    connDto: ConnInfo, navController: NavController,
//    davVm: WebdavViewModel = viewModel()
//) {
//    val context = LocalContext.current
//    val recursivePath = remember { ObservableStack<String>() }
//    val coroutineScope = rememberCoroutineScope()
//    var showDialog by remember { mutableStateOf(false) }
//    var resultMessage by remember { mutableStateOf<String?>(null) }
//    val curFiles = remember { mutableStateListOf<DavResource>() }
//
//    var displayName by remember { mutableStateOf("") }
//
//
//    fun relistDir(){
//        curFiles.clear()
//
//        var subPath = ""
//        recursivePath.items.forEachIndexed {idx,item ->
//            if(idx == 0) {
//                subPath += item
//            }else{
//                subPath += "$item/"
//            }
//        }
//        coroutineScope.launch(Dispatchers.IO) {
//            val files:List<DavResource>? = davVm.listPath(connDto,subPath)
//            val filterFiles = files?.filter { it.path != subPath }
//            withContext(Dispatchers.Main) {
//                curFiles.addAll(filterFiles ?: listOf())
//            }
//        }
//    }
//
//
//    BackHandler(enabled = true) {
//        // 处理返回逻辑
//        if(recursivePath.size == 1) {
//            navController.popBackStack()
//        }else{
//            recursivePath.pop()
//            relistDir()
//        }
//    }
//
//    LaunchedEffect(Unit) {
//        val initPath = connDto.path ?: ""
//        if(!TextUtils.isEmpty(initPath)){
//            val pathsArr = initPath.split("/")
//            recursivePath.push("/")  //根目录
//            pathsArr.forEach {
//                if(!TextUtils.isEmpty(it)) {
//                    recursivePath.push(it)
//                }
//            }
//        }else {
//            //初始化块
//            recursivePath.push("/")  //根目录
//        }
//        displayName = connDto.displayName
//        relistDir()
//    }
//
//    Column(modifier = Modifier.fillMaxSize()) {
//        Row(
//            modifier = Modifier
//                .fillMaxWidth()
//                .height(56.dp)
//                .statusBarsPadding(),
//            verticalAlignment = Alignment.CenterVertically
//        ) {
//            Spacer(modifier = Modifier.width(8.dp))
//            CustomIcon(
//                modifier = Modifier
//                    .size(44.dp)
//                    .padding(2.dp),
//                imageVector = Icons.Outlined.ArrowBack,
//                Color.Black,
//                Color.Gray,
//                onClick = {
//                    navController.popBackStack()
//                }
//            )
//            Text(
//                displayName,
//                fontSize = 18.sp, fontWeight = FontWeight.W600, textAlign = TextAlign.Center,
//                modifier = Modifier.weight(1f)
//            )
//
//            Spacer(modifier = Modifier.width(52.dp))
//        }
//        Column(
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(horizontal = 20.dp, vertical = 10.dp)
//        ) {
//
//            FlowRow(modifier = Modifier.fillMaxWidth(),
//                horizontalArrangement = Arrangement.spacedBy(4.dp),
//                verticalArrangement = Arrangement.spacedBy(6.dp),
//                itemVerticalAlignment = Alignment.CenterVertically,
//                maxItemsInEachRow = 7 // 可选：限制每行最大项目数
//            ){
//                PathRender("连接") { }
//                recursivePath.items.forEach { path ->
//                    PathRender(path){
//                        val topPath = recursivePath.peek() ?: "/"
//                        if(topPath != path){
//                            while(recursivePath.peek() != path) {
//                                recursivePath.pop()
//                            }
//                            relistDir()
//                        }
//                    }
//                }
//            }
//            Spacer(modifier = Modifier.height(10.dp))
//
//
//            if(curFiles.isEmpty()){
//                Box(
//                    modifier = Modifier
//                        .fillMaxSize(),
//                    contentAlignment = Alignment.Center
//                ) {
//                    CircularProgressIndicator(modifier = Modifier.size(80.dp))
//                }
//            }else {
//                LazyVerticalGrid(
//                    columns = GridCells.Fixed(3), // 2列网格
//                    modifier = Modifier.fillMaxSize(),
//                    contentPadding = PaddingValues(top = 10.dp, bottom = 10.dp),
//                    horizontalArrangement = Arrangement.spacedBy(5.dp),
//                    verticalArrangement = Arrangement.spacedBy(20.dp)
//                ) {
//                    items(curFiles.size) { idx ->
//                        GridItem(curFiles[idx]) { item ->
//                            //点击跳转到下一级目录
//                            if (item.isDirectory) {
//                                recursivePath.push(item.name)
//                                relistDir()
//                            } else {
//                                coroutineScope.launch(Dispatchers.Main) {
//                                    FileType.doFileOpenAction(context, item, connDto)
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//        }
//    }
//}
//
//@Composable
//private fun GridItem(item: DavResource,onClick:(DavResource)->Unit) {
//    val types = item.resourceTypes
//    val icon = if(item.isDirectory){
//        R.drawable.folder
//    }else{
//        FileType.getFileIdentity(item.name)
//    }
//    val size = FileType.calcSize(item.contentLength)
//    val modifierDate: Date = item.modified
//    val modifierTime = HttpDate.format(modifierDate)
//    Column(modifier = Modifier
//        .clickable(onClick = {
//            onClick.invoke(item)
//        }),
//        horizontalAlignment = Alignment.CenterHorizontally,
//        verticalArrangement = Arrangement.Center
//    ) {
//        Image(
//            painter = painterResource(id = icon),
//            contentDescription = "icon",
//            modifier = Modifier.size(width = 60.dp,height = 48.dp)
//        )
////            Icon(imageVector = item.icon, contentDescription = null, modifier = Modifier.size(48.dp))
//        Spacer(modifier = Modifier
//            .height(8.dp)
//            .padding(horizontal = 5.dp))
//        Text(text = item.name, fontSize = 14.sp,
//            color = textPrimary,
//            maxLines = 2, overflow = TextOverflow.Ellipsis,
//            textAlign = TextAlign.Center,
//            modifier = Modifier.fillMaxWidth())
//        Text(text = if(item.isDirectory) "" else size, fontSize = 12.sp,
//            maxLines = 2, overflow = TextOverflow.Ellipsis,
//            color = textThird,
//            textAlign = TextAlign.Center,
//            modifier = Modifier.fillMaxWidth())
//        Text(text = modifierTime, fontSize = 12.sp,
//            color = textThird,
//            maxLines = 2, overflow = TextOverflow.Ellipsis,
//            textAlign = TextAlign.Center,
//            modifier = Modifier.fillMaxWidth())
//    }
////    }
//}
//
//
//
//@Composable
//private fun PathRender(path: String, onClick: (String) -> Unit) {
//    Text(
//        "$path >", fontSize = 14.sp, color = textPrimary,
//        modifier = Modifier.clickable(true, onClick = {
//            onClick.invoke(path)
//        })
//    )
//}
