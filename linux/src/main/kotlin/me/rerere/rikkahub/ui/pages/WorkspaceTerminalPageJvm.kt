package me.rerere.rikkahub.ui.pages.extensions.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import com.termux.view.DesktopTerminalView
import com.termux.view.TerminalView
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.theme.ColorMode
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 工作区终端页的桌面端版本：Scaffold、标签栏、关闭确认、深色终端面这些结构照抄上游
 * （WorkspaceTerminalPage.kt，Android 那份用 AndroidView 挂 Termux 的 TerminalView），
 * 只把渲染换成 [DesktopTerminalView]。
 */
@Composable
fun WorkspaceTerminalPage(id: String) {
    val vm: WorkspaceDetailVM = koinViewModel(parameters = { parametersOf(id) })
    val state by vm.state.collectAsStateWithLifecycle()
    val sessionManager: WorkspaceTerminalSessionManager = koinInject()
    val context = LocalContext.current
    val workspace = state.workspace
    val root = workspace?.root
    val terminalStateFlow = remember(root, sessionManager) {
        root?.let(sessionManager::observeWorkspace)
    }
    val terminalState = terminalStateFlow?.collectAsStateWithLifecycle(WorkspaceTerminalTabsState())?.value
        ?: WorkspaceTerminalTabsState()

    var pendingCloseTabId by remember(root) { mutableStateOf<Long?>(null) }

    LaunchedEffect(root) {
        // 上游 Manager 以 hasRootfs 决定就绪状态；桌面端跑宿主机 shell，先放一个探针文件让它判真
        workspace?.let {
            ensureDesktopRootfsProbe(context, it.root)
            sessionManager.ensureSession(it)
        }
    }

    RikkahubTheme(colorMode = ColorMode.DARK) {
        Scaffold { innerPadding ->
            WorkspaceTerminalContent(
                root = root,
                state = terminalState,
                contentPadding = innerPadding,
                onCreateTab = { workspace?.let(sessionManager::createTab) },
                onSelectTab = { tabId -> root?.let { sessionManager.selectTab(it, tabId) } },
                onCloseTab = { tabId -> pendingCloseTabId = tabId },
            )
        }

        val pendingCloseTab = terminalState.tabs.firstOrNull { it.id == pendingCloseTabId }
        if (pendingCloseTab != null) {
            AlertDialog(
                onDismissRequest = { pendingCloseTabId = null },
                title = {
                    Text(stringResource(R.string.workspace_terminal_close_confirm_title, pendingCloseTab.number))
                },
                text = { Text(stringResource(R.string.workspace_terminal_close_confirm_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        root?.let { sessionManager.closeTab(it, pendingCloseTab.id) }
                        pendingCloseTabId = null
                    }) { Text(stringResource(R.string.workspace_terminal_close)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingCloseTabId = null }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun WorkspaceTerminalContent(
    root: String?,
    state: WorkspaceTerminalTabsState,
    contentPadding: PaddingValues,
    onCreateTab: () -> Unit,
    onSelectTab: (Long) -> Unit,
    onCloseTab: (Long) -> Unit,
) {
    val selectedIndex = state.tabs.indexOfFirst { it.id == state.selectedTabId }.coerceAtLeast(0)
    val selectedTab = state.tabs.getOrNull(selectedIndex)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .background(Color.Black),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(MaterialTheme.colorScheme.surface),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton()
            Box(modifier = Modifier.weight(1f)) {
                if (selectedTab != null) {
                    // Android 用的是 SecondaryScrollableTabRow，JB 这一版没有 secondary 变体，
                    // 用同尺寸的 ScrollableTabRow 顶位，标签内容与关闭按钮完全一致。
                    ScrollableTabRow(
                        selectedTabIndex = selectedIndex,
                        modifier = Modifier.fillMaxWidth(),
                        edgePadding = 0.dp,
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        state.tabs.forEach { tab ->
                            val tabDescription = stringResource(R.string.workspace_terminal_tab, tab.number)
                            val tabTitle = tab.title ?: tabDescription
                            val isSelected = selectedTab.id == tab.id
                            Tab(
                                selected = isSelected,
                                onClick = { onSelectTab(tab.id) },
                                modifier = Modifier
                                    .height(48.dp)
                                    .widthIn(min = 160.dp, max = 240.dp)
                                    .semantics { contentDescription = tabTitle },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = tabTitle,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = if (isSelected) TextOverflow.Clip else TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                    val closeDescription = stringResource(
                                        R.string.workspace_terminal_close_tab,
                                        tab.number,
                                    )
                                    IconButton(
                                        onClick = { onCloseTab(tab.id) },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .semantics { contentDescription = closeDescription },
                                    ) { Text(text = "×", fontSize = 18.sp) }
                                }
                            }
                        }
                    }
                }
            }
            val newTabDescription = stringResource(R.string.workspace_terminal_new_tab)
            IconButton(
                onClick = onCreateTab,
                enabled = root != null && !state.isCreating,
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = newTabDescription },
            ) { Text(text = "+", fontSize = 24.sp) }
        }

        if (selectedTab != null) {
            val view = remember(selectedTab.id) { TerminalView().apply { attachSession(selectedTab.session) } }
            DesktopTerminalView(
                view = view,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = when {
                        root == null || state.isCreating ||
                            state.readiness == WorkspaceTerminalReadiness.Loading -> {
                            stringResource(R.string.workspace_terminal_loading)
                        }

                        else -> stringResource(R.string.workspace_terminal_no_tabs)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
