package com.jhkj.videoplayer.compose_pages.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.jhkj.videoplayer.compose_pages.models.ConnInfo
import com.jhkj.videoplayer.compose_pages.viewmodel.WebdavViewModel
import com.jhkj.videoplayer.compose_pages.widgets.CustomIcon
import com.jhkj.videoplayer.compose_pages.widgets.CustomTextField
import com.jhkj.videoplayer.theme.textPrimary
import com.jhkj.videoplayer.theme.transBg

class SaveState {
    private val _dataEdit = mutableStateOf(false)
    val isDataEdit: State<Boolean> = _dataEdit
}
@Composable
fun rememberSaveState() = remember { SaveState() }

@Composable
fun ConnectionEditScreen(connDto:ConnInfo?, isEdit:Boolean, navController: NavController,viewModel:WebdavViewModel = viewModel()) {
    val saveState = rememberSaveState()
    var protocolIndex by remember { mutableStateOf(0) }

    var displayName by remember { mutableStateOf("") }
    var fullURL by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var protocol by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().height(56.dp)
            .statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = Modifier.width(8.dp))
            CustomIcon(
                modifier = Modifier.size(44.dp).padding(2.dp),
                imageVector = Icons.Outlined.ArrowBack,
                Color.Black,
                Color.Gray,
                onClick = {
                    navController.popBackStack()
                }
            )
            Text(if(isEdit) "连接设置" else "新建连接",
                fontSize = 18.sp, fontWeight = FontWeight.W600, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f))

            CustomIcon(
                modifier = Modifier.size(44.dp).padding(2.dp),
                imageVector = Icons.Outlined.Save,
                if(saveState.isDataEdit.value) Color.Blue else Color.Gray,
                Color.Gray,
                onClick = {
                    navController.popBackStack()
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Column(modifier = Modifier.fillMaxSize()
            .padding(20.dp)) {

            Text("连接", fontSize = 14.sp, color = textPrimary)
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Box(modifier = Modifier.width(100.dp)) {
                    Text("显示名称", fontSize = 16.sp, color = textPrimary)
                }
                CustomTextField(
                    text = displayName,
                    onValueChange = { displayName = it },
                    placeholder = "可选",
                    modifier = Modifier.padding(start = 5.dp)
                        .background(color = transBg)

                )
            }

            Spacer(modifier = Modifier.height(15.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Box(modifier = Modifier.width(100.dp)) {
                    Text("主机URL", fontSize = 16.sp, color = textPrimary)
                }
                CustomTextField(
                    text = fullURL,
                    placeholder = "",
                    modifier = Modifier.padding(start = 5.dp)
                        .background(color = transBg),
                    isEditable = false,
                )
            }

            Spacer(modifier = Modifier.height(15.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Start) {
                Box(modifier = Modifier.width(100.dp)) {
                    Text("协议", fontSize = 16.sp, color = textPrimary)
                }
                Row{
                    Column(modifier = Modifier
                        .background(color = if(protocolIndex == 0) Color.Blue else Color.White , shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                        .clickable{
                            protocolIndex = 0
                            protocol = "HTTP"
                        }
                        .border(width = 1.dp, brush = SolidColor(Color.Blue), shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)),
                       ){
                        Text("HTTP", fontSize = 16.sp, color = if(protocolIndex == 0) Color.White else Color.Blue,
                            modifier = Modifier.padding(top=6.dp, bottom = 6.dp, start = 10.dp, end = 10.dp))
                    }
                    Box(modifier = Modifier
                        .background(color = if(protocolIndex == 1)  Color.Blue else Color.White, shape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                        .clickable{
                            protocolIndex = 1
                            protocol = "HTTPS"
                        }
                        .border(width = 1.dp, brush = SolidColor(Color.Blue), shape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)),
                        ){
                        Text("HTTPS", fontSize = 16.sp, color = if(protocolIndex == 1) Color.White else Color.Blue,
                            modifier = Modifier.padding(top=6.dp, bottom = 6.dp, start = 10.dp, end = 10.dp))
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
            }

            //第四行
            Spacer(modifier = Modifier.height(15.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Box(modifier = Modifier.width(100.dp)) {
                    Text("主机名称或IP", fontSize = 16.sp, color = textPrimary)
                }
                CustomTextField(
                    text = host,
                    onValueChange = {
                        host = it
                    },
                    placeholder = "",
                    modifier = Modifier.padding(start = 5.dp)
                        .background(color = transBg),
                    isEditable = false,
                )
            }
        }

    }
}



//4. 多行文本输入 - BasicTextField
/*
BasicTextField(
    value = longText,
    onValueChange = { longText = it },
    modifier = Modifier
        .fillMaxWidth()
        .height(200.dp)
        .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
        .padding(8.dp),
    decorationBox = { innerTextField ->
        Box(modifier = Modifier.padding(8.dp)) {
            if (longText.isEmpty()) {
                Text("Enter your text here", color = Color.Gray)
            }
            innerTextField()
        }
    }
)
 */