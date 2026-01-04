//package com.jhkj.videoplayer.compose_pages.pages
//
//import androidx.compose.foundation.background
//import androidx.compose.foundation.border
//import androidx.compose.foundation.clickable
//import androidx.compose.foundation.layout.Arrangement
//import androidx.compose.foundation.layout.Box
//import androidx.compose.foundation.layout.Column
//import androidx.compose.foundation.layout.Row
//import androidx.compose.foundation.layout.Spacer
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.foundation.layout.fillMaxWidth
//import androidx.compose.foundation.layout.height
//import androidx.compose.foundation.layout.padding
//import androidx.compose.foundation.layout.size
//import androidx.compose.foundation.layout.statusBarsPadding
//import androidx.compose.foundation.layout.width
//import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.outlined.ArrowBack
//import androidx.compose.material.icons.outlined.Save
//import androidx.compose.material3.AlertDialog
//import androidx.compose.material3.Button
//import androidx.compose.material3.Text
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.LaunchedEffect
//import androidx.compose.runtime.State
//import androidx.compose.runtime.getValue
//import androidx.compose.runtime.mutableStateOf
//import androidx.compose.runtime.remember
//import androidx.compose.runtime.setValue
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.graphics.SolidColor
//import androidx.compose.ui.text.font.FontWeight
//import androidx.compose.ui.text.input.KeyboardType
//import androidx.compose.ui.text.style.TextAlign
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import androidx.lifecycle.viewmodel.compose.viewModel
//import androidx.navigation.NavController
//import com.jhkj.videoplayer.compose_pages.models.ConnInfo
//import com.jhkj.videoplayer.viewmodels.ConnInfoVm
//import com.jhkj.videoplayer.compose_pages.widgets.CustomIcon
//import com.jhkj.videoplayer.compose_pages.widgets.CustomTextField
//import com.jhkj.videoplayer.theme.textPrimary
//import com.jhkj.videoplayer.theme.transBg
//
//class SaveState {
//    val _dataEdit = mutableStateOf(false)
//    val isDataEdit: State<Boolean> = _dataEdit
//}
//@Composable
//fun rememberSaveState() = remember { SaveState() }
//
//
//
//@Composable
//fun ConnectionEditScreen(connDto:ConnInfo?, isEdit:Boolean, navController: NavController,
//                         connVm:ConnInfoVm = viewModel()) {
//    val saveState = rememberSaveState()
//    var protocolIndex by remember { mutableStateOf(0) }
//    var showDialog by remember { mutableStateOf(false) }
//    var saveResult by remember { mutableStateOf(false) }
//    var resultMessage by remember { mutableStateOf<String?>(null) }
//
//    var displayName by remember { mutableStateOf("") }
//    var fullURL by remember { mutableStateOf("") }
//    var host by remember { mutableStateOf("") }
//    var path by remember { mutableStateOf("") }
//    var port by remember { mutableStateOf("80") }
//    var protocol by remember { mutableStateOf("HTTP") }
//    var username by remember { mutableStateOf("") }
//    var password by remember { mutableStateOf("") }
//
//    fun connInfoInputChange(){
//        val a1 = displayName.isEmpty()
//        val a2 = host.isEmpty()
//        val a3 = path.isEmpty()
//        val a4 = port.isEmpty()
//        saveState._dataEdit.value = (!a1 || !a2 || !a3 || !a4)
//        if(!a2){
//            val lowercaseProtocol = protocol.lowercase()
//            if(!a3) {
//                val pathWithoutPrefix = path.removePrefix("/")
//                fullURL = "$lowercaseProtocol://$host:$port/$pathWithoutPrefix"
//            }else{
//                fullURL = "$lowercaseProtocol://$host:$port"
//            }
//        }
//    }
//
//    LaunchedEffect(Unit) {
//        if(connDto != null){
//            displayName = connDto.displayName
//            host = connDto.domain
//            path = connDto.path ?: ""
//            port = connDto.port.toString()
//            protocol = connDto.protocol
//            username = connDto.username
//            password = connDto.pass
//            connInfoInputChange()
//        }
//    }
//
//    fun saveBtnClick(onResult: (Boolean) -> Unit){
//        val dto = ConnInfo(connDto?.id ?: 0,
//            displayName,
//            host,
//            path,
//            protocol,
//            port.toInt(),
//            username,
//            password,
//            ConnType.WEBDAV.ordinal)
//        connVm.insertOrUpdateConn(dto,onResult)
//    }
//
//
//    Column(modifier = Modifier.fillMaxSize()) {
//        Row(modifier = Modifier.fillMaxWidth().height(56.dp)
//            .statusBarsPadding(),
//            verticalAlignment = Alignment.CenterVertically) {
//            Spacer(modifier = Modifier.width(8.dp))
//            CustomIcon(
//                modifier = Modifier.size(44.dp).padding(2.dp),
//                imageVector = Icons.Outlined.ArrowBack,
//                Color.Black,
//                Color.Gray,
//                onClick = {
//                    navController.popBackStack()
//                }
//            )
//            Text(if(isEdit) "连接设置" else "新建连接",
//                fontSize = 18.sp, fontWeight = FontWeight.W600, textAlign = TextAlign.Center,
//                modifier = Modifier.weight(1f))
//
//            CustomIcon(
//                modifier = Modifier.size(44.dp).padding(2.dp),
//                imageVector = Icons.Outlined.Save,
//                if(saveState.isDataEdit.value) Color.Blue else Color.Gray,
//                Color.Gray,
//                onClick = {
//                    if(saveState.isDataEdit.value) {
//                        saveBtnClick{ res ->
//                            saveResult = res
//                            showDialog = true
//                            resultMessage = if(res) "保存成功" else "保存失败"
//                        }
//                    }
//                }
//            )
//            Spacer(modifier = Modifier.width(8.dp))
//        }
//        Column(modifier = Modifier.fillMaxSize()
//            .padding(20.dp)) {
//
//            Text("连接", fontSize = 14.sp, color = textPrimary)
//            Spacer(modifier = Modifier.height(10.dp))
//            Row(verticalAlignment = Alignment.Bottom) {
//                Box(modifier = Modifier.width(100.dp)) {
//                    Text("显示名称", fontSize = 16.sp, color = textPrimary)
//                }
//                CustomTextField(
//                    text = displayName,
//                    onValueChange = { displayName = it },
//                    placeholder = "可选",
//                    modifier = Modifier.padding(start = 5.dp)
//                        .background(color = transBg)
//                )
//            }
//
//            Spacer(modifier = Modifier.height(15.dp))
//            Row(verticalAlignment = Alignment.Bottom) {
//                Box(modifier = Modifier.width(100.dp)) {
//                    Text("主机URL", fontSize = 16.sp, color = textPrimary)
//                }
//                CustomTextField(
//                    text = fullURL,
//                    placeholder = "",
//                    modifier = Modifier.padding(start = 5.dp)
//                        .background(color = transBg),
//                    isEditable = false,
//                )
//            }
//
//            Spacer(modifier = Modifier.height(15.dp))
//            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Start) {
//                Box(modifier = Modifier.width(100.dp)) {
//                    Text("协议", fontSize = 16.sp, color = textPrimary)
//                }
//                Row{
//                    Column(modifier = Modifier
//                        .background(color = if(protocolIndex == 0) Color.Blue else Color.White , shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
//                        .clickable{
//                            protocolIndex = 0
//                            protocol = "HTTP"
//                        }
//                        .border(width = 1.dp, brush = SolidColor(Color.Blue), shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)),
//                       ){
//                        Text("HTTP", fontSize = 16.sp, color = if(protocolIndex == 0) Color.White else Color.Blue,
//                            modifier = Modifier.padding(top=6.dp, bottom = 6.dp, start = 10.dp, end = 10.dp))
//                    }
//                    Box(modifier = Modifier
//                        .background(color = if(protocolIndex == 1)  Color.Blue else Color.White, shape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
//                        .clickable{
//                            protocolIndex = 1
//                            protocol = "HTTPS"
//                        }
//                        .border(width = 1.dp, brush = SolidColor(Color.Blue), shape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)),
//                        ){
//                        Text("HTTPS", fontSize = 16.sp, color = if(protocolIndex == 1) Color.White else Color.Blue,
//                            modifier = Modifier.padding(top=6.dp, bottom = 6.dp, start = 10.dp, end = 10.dp))
//                    }
//                }
//                Spacer(modifier = Modifier.weight(1f))
//            }
//
//            //第四行
//            Spacer(modifier = Modifier.height(15.dp))
//            Row(verticalAlignment = Alignment.Bottom) {
//                Box(modifier = Modifier.width(100.dp)) {
//                    Text("主机名称或IP", fontSize = 16.sp, color = textPrimary)
//                }
//                CustomTextField(
//                    text = host,
//                    onValueChange = {
//                        host = it
//                        connInfoInputChange()
//                    },
//                    placeholder = "",
//                    modifier = Modifier.padding(start = 5.dp)
//                        .background(color = transBg)
//                )
//            }
//
//            //第五行
//            Spacer(modifier = Modifier.height(15.dp))
//            Row(verticalAlignment = Alignment.Bottom) {
//                Box(modifier = Modifier.width(100.dp)) {
//                    Text("路径", fontSize = 16.sp, color = textPrimary)
//                }
//                CustomTextField(
//                    text = path,
//                    onValueChange = {
//                        path = it
//                        connInfoInputChange()
//                    },
//                    placeholder = "可选",
//                    modifier = Modifier.padding(start = 5.dp)
//                        .background(color = transBg),
//                )
//            }
//
//            //第六行
//            Spacer(modifier = Modifier.height(15.dp))
//            Row(verticalAlignment = Alignment.Bottom) {
//                Box(modifier = Modifier.width(100.dp)) {
//                    Text("端口", fontSize = 16.sp, color = textPrimary)
//                }
//                CustomTextField(
//                    text = port,
//                    onValueChange = {
//                        port = it
//                        connInfoInputChange()
//                    },
//                    placeholder = "可选",
//                    maxLength = 5,
//                    modifier = Modifier.padding(start = 5.dp)
//                        .background(color = transBg),
//                    keyboardType = KeyboardType.Number
//                )
//            }
//
//            // ==================================================
//            Spacer(modifier = Modifier.height(30.dp))
//            Text("认证", fontSize = 14.sp, color = textPrimary)
//            Spacer(modifier = Modifier.height(10.dp))
//            Row(verticalAlignment = Alignment.Bottom) {
//                Box(modifier = Modifier.width(100.dp)) {
//                    Text("用户名", fontSize = 16.sp, color = textPrimary)
//                }
//                CustomTextField(
//                    text = username,
//                    onValueChange = {
//                        username = it
//                    },
//                    placeholder = "可选",
//                    modifier = Modifier.padding(start = 5.dp)
//                        .background(color = transBg),
//                )
//            }
//
//            Spacer(modifier = Modifier.height(15.dp))
//            Row(verticalAlignment = Alignment.Bottom) {
//                Box(modifier = Modifier.width(100.dp)) {
//                    Text("密码", fontSize = 16.sp, color = textPrimary)
//                }
//                CustomTextField(
//                    text = password,
//                    onValueChange = {
//                        password = it
//                    },
//                    placeholder = "可选",
//                    modifier = Modifier.padding(start = 5.dp)
//                        .background(color = transBg),
//                    keyboardType = KeyboardType.Password
//                )
//            }
//        }
//
//
//        if (showDialog) {
//            AlertDialog(
//                onDismissRequest = { showDialog = false },
//                title = { Text("操作结果") },
//                text = { Text(resultMessage ?: "") },
//                confirmButton = { Button(onClick = {
//                    showDialog = false
//                    if(saveResult) {
//                        navController.popBackStack()
//                    }
//                    saveResult = false
//                }
//                ) { Text("确定") } }
//            )
//        }
//    }
//}
//
//
//
////4. 多行文本输入 - BasicTextField
///*
//BasicTextField(
//    value = longText,
//    onValueChange = { longText = it },
//    modifier = Modifier
//        .fillMaxWidth()
//        .height(200.dp)
//        .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
//        .padding(8.dp),
//    decorationBox = { innerTextField ->
//        Box(modifier = Modifier.padding(8.dp)) {
//            if (longText.isEmpty()) {
//                Text("Enter your text here", color = Color.Gray)
//            }
//            innerTextField()
//        }
//    }
//)
// */