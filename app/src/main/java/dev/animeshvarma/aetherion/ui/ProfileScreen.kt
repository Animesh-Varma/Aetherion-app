package dev.animeshvarma.aetherion.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.animeshvarma.aetherion.MainViewModel
import dev.animeshvarma.aetherion.ProfileUiState
import dev.animeshvarma.aetherion.network.models.HighlightReelItem
import dev.animeshvarma.aetherion.network.models.MediaFeedItem
import dev.animeshvarma.aetherion.network.models.OwnProfileData
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(viewModel: MainViewModel) {
    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    var showSecretsSheet by remember { mutableStateOf(false) }
    var selectedPost by remember { mutableStateOf<MediaFeedItem?>(null) }
    val sheetState = rememberModalBottomSheetState()

    Scaffold(
        topBar = {
            val titleText = when (val s = profileState) {
                is ProfileUiState.Loaded -> s.profile.username
                else -> viewModel.deviceFingerprint.selfUsername.ifBlank { "Profile" }
            }
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if ((profileState as? ProfileUiState.Loaded)?.profile?.isPrivate == true) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Private Account",
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(end = 4.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if ((profileState as? ProfileUiState.Loaded)?.profile?.isVerified == true) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Verified",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(2.dp)
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showSecretsSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Saved Device Secrets"
                        )
                    }
                    IconButton(onClick = { viewModel.refreshOwnProfile() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Profile"
                        )
                    }
                    IconButton(onClick = { viewModel.logout() }) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Logout"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val s = profileState) {
                ProfileUiState.Loading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Fetching profile sequence…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is ProfileUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = s.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.refreshOwnProfile() }) {
                            Text("Retry")
                        }
                    }
                }

                is ProfileUiState.Loaded -> {
                    InstagramMaterial3ProfileContent(
                        profile = s.profile,
                        viewModel = viewModel,
                        onOpenSecrets = { showSecretsSheet = true },
                        onPostClick = { selectedPost = it }
                    )
                }
            }
        }
    }

    if (showSecretsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSecretsSheet = false },
            sheetState = sheetState
        ) {
            SavedSecretsBottomSheet(
                secrets = viewModel.deviceFingerprint.getAllSavedSecretsSummary()
            )
        }
    }

    selectedPost?.let { post ->
        PostDetailDialog(
            post = post,
            viewModel = viewModel,
            onDismiss = { selectedPost = null }
        )
    }
}

@Composable
private fun InstagramMaterial3ProfileContent(
    profile: OwnProfileData,
    viewModel: MainViewModel,
    onOpenSecrets: () -> Unit,
    onPostClick: (MediaFeedItem) -> Unit
) {
    var selectedSubTab by remember { mutableIntStateOf(0) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Profile Info Header
        item(span = { GridItemSpan(3) }) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Avatar + Stats Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NetworkImage(
                            url = profile.avatarUrl,
                            viewModel = viewModel,
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape
                                )
                        )

                        Spacer(Modifier.width(16.dp))

                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ProfileStatColumn(count = profile.mediaCount, label = "posts")
                            ProfileStatColumn(count = profile.followerCount, label = "followers")
                            ProfileStatColumn(count = profile.followingCount, label = "following")
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Full Name + Pronouns
                    if (profile.fullName.isNotBlank() || profile.pronouns.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (profile.fullName.isNotBlank()) {
                                Text(
                                    text = profile.fullName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (profile.pronouns.isNotEmpty()) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = profile.pronouns.joinToString("/"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Category
                    if (!profile.category.isNullOrBlank()) {
                        Text(
                            text = profile.category,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    // Biography
                    if (profile.biography.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = profile.biography,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Links
                    val links = profile.bioLinks.mapNotNull { it.url?.takeIf { u -> u.isNotBlank() } }
                        .ifEmpty { listOfNotNull(profile.externalUrl?.takeIf { it.isNotBlank() }) }

                    if (links.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        links.forEach { linkUrl ->
                            Text(
                                text = linkUrl,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Meta Chips
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AssistChip(
                            onClick = onOpenSecrets,
                            label = {
                                Text(
                                    if (profile.is2faEnabled) "2FA Active" else "Trusted Session",
                                    fontSize = 11.sp
                                )
                            }
                        )
                        if (profile.textFeedCount > 0) {
                            AssistChip(
                                onClick = {},
                                label = { Text("Threads: ${profile.textFeedCount}", fontSize = 11.sp) }
                            )
                        }
                        AssistChip(
                            onClick = onOpenSecrets,
                            label = { Text("ID: ${profile.userId}", fontSize = 11.sp) }
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Profile Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { viewModel.refreshOwnProfile() },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text("Sync Profile", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = onOpenSecrets,
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text("Saved Secrets", maxLines = 1)
                        }
                    }
                }

                // Story Highlights Tray (edge-to-edge scroll)
                if (profile.highlights.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(profile.highlights) { reel ->
                            HighlightBubble(reel = reel, viewModel = viewModel)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // Full-width TabRow without artificial horizontal insets
                TabRow(selectedTabIndex = selectedSubTab) {
                    Tab(
                        selected = selectedSubTab == 0,
                        onClick = { selectedSubTab = 0 },
                        text = { Text("Grid (${profile.posts.size})") }
                    )
                    Tab(
                        selected = selectedSubTab == 1,
                        onClick = { selectedSubTab = 1 },
                        text = { Text("Steps (${profile.executedSteps.size})") }
                    )
                }
            }
        }

        // SubTab 0: Media Grid
        if (selectedSubTab == 0) {
            if (profile.posts.isEmpty()) {
                item(span = { GridItemSpan(3) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No Posts Yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Posts shared by @${profile.username} will appear here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(profile.posts, key = { it.id ?: it.pk?.toString() ?: it.hashCode() }) { post ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable { onPostClick(post) }
                    ) {
                        NetworkImage(
                            url = post.thumbnailUrl,
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (post.media_type == 8 || post.media_type == 2) {
                            Surface(
                                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                            ) {
                                Text(
                                    text = if (post.media_type == 8) "CAROUSEL" else "REEL",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.inverseOnSurface,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // SubTab 1: Replay Steps
            item(span = { GridItemSpan(3) }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = profile.executedFlowName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    profile.executedSteps.forEach { step ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        ) {
                            Text(
                                text = step,
                                modifier = Modifier.padding(12.dp),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileStatColumn(count: Int, label: String) {
    val formatted = remember(count) {
        NumberFormat.getInstance(Locale.US).format(count)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = formatted,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HighlightBubble(reel: HighlightReelItem, viewModel: MainViewModel) {
    val coverUrl = reel.cover_media?.cropped_image_version?.url
        ?: reel.cover_media?.full_image_version?.url.orEmpty()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(68.dp)
    ) {
        NetworkImage(
            url = coverUrl,
            viewModel = viewModel,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .border(
                    width = 1.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape
                )
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = reel.title ?: "Highlight",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun NetworkImage(
    url: String,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(url) {
        if (url.isNotBlank()) {
            bitmap = viewModel.loadBitmap(url)
        }
    }

    val loaded = bitmap
    if (loaded != null) {
        Image(
            bitmap = loaded.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun SavedSecretsBottomSheet(secrets: Map<String, String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Device & Server Secrets",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Persisted tokens, headers, and cryptographic device secrets",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        secrets.forEach { (key, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = key,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(0.45f)
                )
                Text(
                    text = value,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(0.55f),
                    textAlign = TextAlign.End
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun PostDetailDialog(
    post: MediaFeedItem,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = {
            Text(
                text = "Post ${post.code?.let { "($it)" } ?: ""}",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column {
                NetworkImage(
                    url = post.thumbnailUrl,
                    viewModel = viewModel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(MaterialTheme.shapes.medium)
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "${post.like_count ?: 0} likes",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${post.comment_count ?: 0} comments",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (!post.caption?.text.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = post.caption?.text.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    )
}