package com.jhkj.videoplayer.compose_pages.pages


import android.net.Uri
import android.text.TextUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.gson.Gson
import com.jhkj.videoplayer.compose_pages.models.ConnInfo
import com.jhkj.videoplayer.compose_pages.viewmodel.ConnInfoVm
import com.jhkj.videoplayer.compose_pages.widgets.CustomIcon
import com.jhkj.videoplayer.compose_pages.widgets.RouterName
import com.jhkj.videoplayer.R
import com.jhkj.videoplayer.compose_pages.viewmodel.WebdavViewModel
import com.jhkj.videoplayer.compose_pages.viewmodel.rememberToast
import com.jhkj.videoplayer.theme.popupBg
import com.jhkj.videoplayer.theme.textThird
import com.jhkj.videoplayer.theme.transBg
import com.jhkj.videoplayer.utils.GsonUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@Composable
fun ConnectionsScreen(
    navController: NavController, viewModel: ConnInfoVm = viewModel(),
    davModel: WebdavViewModel = viewModel()
) {
    val connInfos by viewModel.allConn.collectAsState(initial = emptyList())
    var fold by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableIntStateOf(-1) }


    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CustomIcon(
                    modifier = Modifier.size(44.dp),
                    imageVector = ImageVector.vectorResource(id = R.drawable.portrait),
                    Color.Gray,
                    Color.Blue,
                ) {
                    navController.navigate(RouterName.AddConnection.name)
                }
                Text(
                    "连接",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.W600,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                CustomIcon(
                    modifier = Modifier.size(44.dp),
                    imageVector = Icons.Outlined.Bolt,
                    Color.Blue,
                    Color.Blue,
                ) {
                    navController.navigate(RouterName.AddConnection.name)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "连接",
                    fontSize = 14.sp,
                    color = textThird,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    fold = !fold
                }) {
                    Icon(
                        if (fold) Icons.Default.ExpandLess else
                            Icons.Default.ExpandMore, contentDescription = "Fold"
                    )
                }
            }

            if (!fold) {
                LazyColumn(
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                ) {
                    items(connInfos) { conn ->
                        ConnItem(conn = conn, onClick = {
                            expanded = true
                        }, expanded, {
                            expanded = false
                        }, navController, viewModel, davModel)
                    }
                }
            }
        }

    }
}


@Composable
fun ConnItem(
    conn: ConnInfo, onClick: () -> Unit, expanded: Boolean,
    onDismiss: () -> Unit, navController: NavController, viewModel: ConnInfoVm,
    davModel: WebdavViewModel
) {
    val img = connTypeList.find { conn.connType == it.identity.ordinal }!!
    val coroutineScope = rememberCoroutineScope()
    val showToast = rememberToast()

    val displayName = if (TextUtils.isEmpty(conn.displayName)) {
        "${conn.domain}: ${conn.port}"
    } else {
        conn.displayName
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                true,
                onClick = {
                    // 点击时进行连接连通性测试
                    coroutineScope.launch(Dispatchers.IO) {
                        val isValid = davModel.checkUrl(conn)
                        withContext(Dispatchers.Main) {
                            if (!isValid) {
                                showToast("连接不可用")
                            } else {
                                val str = Uri.encode(GsonUtils.objToJson(conn))
                                // 使用 navController 的 navigate 方法直接传递对象
                                navController.navigate("${RouterName.WebdavEntry.name}/$str")
                            }
                        }
                    }
                }),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = img.icon),
            contentDescription = "icon",
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = displayName, modifier = Modifier.weight(1f))
        Box {
            IconButton(onClick = {
                onClick.invoke()
            }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More")
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = onDismiss,
                modifier = Modifier
                    .width(IntrinsicSize.Min)
                    .wrapContentSize(Alignment.TopStart)
                    .shadow(elevation = 2.dp)
                    .border(
                        border = BorderStroke(1.dp, color = transBg),
                        shape = RoundedCornerShape(2.dp)
                    )
                    .background(color = popupBg)
            ) {
                DropdownMenuItem({ Text("编辑") }, trailingIcon = {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit"
                    )
                }, onClick = {
                    onDismiss.invoke()
                    coroutineScope.launch {
                        val str = GsonUtils.objToJson(conn)
                        // 使用 navController 的 navigate 方法直接传递对象
                        navController.navigate("${RouterName.EditConnection.name}/$str/false/${ConnType.WEBDAV.ordinal}")
                    }
                })
                DropdownMenuItem({ Text("删除") }, trailingIcon = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Edit"
                    )
                }, onClick = {
                    onDismiss.invoke()
                    viewModel.deleteConn(conn)
                })
            }
        }
    }

}