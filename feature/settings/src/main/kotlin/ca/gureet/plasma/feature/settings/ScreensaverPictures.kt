// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import ca.gureet.plasma.core.design.component.SheetOption
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** A place photos can be on: this TV, or a USB drive, by its name, and where it is. */
data class PhotoDrive(val name: String, val path: String)

/** What's in a folder on the TV: its folders, A to Z, and how many photos are in it alone. */
data class PhotoFolderList(val folders: List<String>, val photos: Int)

/**
 * Screensaver Pictures › Photos From › On This TV or a USB Drive (#170): the TV's own folders, as
 * the app finds them. Android asks for [permission] first, when there is one.
 */
interface PhotoFolders {
    /** The permission Android asks for before Plasma can see photos; null when it needn't. */
    val permission: String?

    /** This TV, and each USB drive that's in. */
    suspend fun drives(): List<PhotoDrive>

    /** What's in [path]. */
    suspend fun folders(path: String): PhotoFolderList

    /** The screensaver's photos come from [path], on [drive], from now on. */
    fun use(drive: PhotoDrive, path: String)

    /** The TV's storage, with Pictures and DCIM, for previews and tests. */
    object Sample : PhotoFolders {
        override val permission: String? = null

        override suspend fun drives() = listOf(PhotoDrive("This TV", "/storage/emulated/0"), PhotoDrive("USB Drive", "/storage/1A2B-3C4D"))

        override suspend fun folders(path: String) = if (path.endsWith("/0")) PhotoFolderList(listOf("DCIM", "Download", "Pictures"), 0) else PhotoFolderList(emptyList(), 128)

        override fun use(drive: PhotoDrive, path: String) = Unit
    }
}

/** Launcher & Home › Screensaver Pictures (#170): Plasma's, or the viewer's photos. */
@Composable
internal fun screensaverPicturesRow(values: SettingsValues, shown: String): SettingItem {
    val photos = values.index(KEY_SCREENSAVER_PICTURES, 0) == 1 && shown.isNotEmpty()
    return infoRow(
        "screensaver-pictures",
        stringResource(R.string.screensaver_pictures),
        stringResource(if (photos) R.string.your_photos else R.string.plasmas_pictures),
        PlasmaIcons.Photo,
        stringResource(R.string.screensaver_pictures_text),
        SettingAction.Open(SettingsPage.ScreensaverPictures),
    )
}

/**
 * Screensaver Pictures (#170): Plasma's Pictures or Your Photos, ticked; where the photos come
 * from (on this TV or a USB drive, or on the network); their order; and how long each picture
 * shows. Your Photos without a folder asks for one first.
 */
@Composable
internal fun screensaverPicturesPage(values: SettingsValues, shown: String, folders: PhotoFolders, network: NetworkFolderSetup): PageSpec {
    val title = stringResource(R.string.screensaver_pictures)
    val photos = values.index(KEY_SCREENSAVER_PICTURES, 0) == 1 && shown.isNotEmpty()
    val each = values.choice(KEY_SCREENSAVER_EACH, R.array.each_picture_options, 1)
    val order = values.choice(KEY_SCREENSAVER_ORDER, R.array.photo_order_options, 0)
    val onTv = stringResource(R.string.photos_on_tv)
    val onNetwork = stringResource(R.string.photos_on_network)
    // Choosing a folder chooses Your Photos with it.
    val choose = SettingAction.Menu(
        title = stringResource(R.string.choose_photos),
        subtitle = stringResource(R.string.choose_photos_text),
        options = listOf(
            MenuOption(onTv, sheet = SettingAction.Sheet { close -> PhotoFolderSheet(folders, onUsed = { values.choose(KEY_SCREENSAVER_PICTURES, 1) }, onClose = close) }),
            MenuOption(
                onNetwork,
                sheet = SettingAction.Sheet { close ->
                    NetworkFolderSheet(network, back = title, onClose = close, words = FolderSheetWords.Photos, onUsed = { values.choose(KEY_SCREENSAVER_PICTURES, 1) })
                },
            ),
        ),
    )
    val photosText = stringResource(R.string.your_photos_text)
    return compactPage(
        title,
        stringResource(R.string.launcher),
        listOf(
            SettingSection(
                null,
                listOf(
                    SettingItem(
                        id = "plasma",
                        label = stringResource(R.string.plasmas_pictures),
                        value = null,
                        detail = SettingDetail(PlasmaIcons.Moon, stringResource(R.string.plasmas_pictures), text = stringResource(R.string.plasmas_pictures_text)),
                        action = SettingAction.Run { values.choose(KEY_SCREENSAVER_PICTURES, 0) },
                        chevron = false,
                        checked = !photos,
                    ),
                    SettingItem(
                        id = "photos",
                        label = stringResource(R.string.your_photos),
                        value = null,
                        detail = SettingDetail(PlasmaIcons.Photo, stringResource(R.string.your_photos), text = photosText),
                        action = if (shown.isEmpty()) choose else SettingAction.Run { values.choose(KEY_SCREENSAVER_PICTURES, 1) },
                        chevron = shown.isEmpty(),
                        checked = photos,
                    ),
                ),
            ),
            SettingSection(
                stringResource(R.string.your_photos),
                listOf(
                    infoRow(
                        "photos-from",
                        stringResource(R.string.photos_from),
                        shown.ifEmpty { stringResource(R.string.choose_a_folder) },
                        PlasmaIcons.Folder,
                        stringResource(R.string.photos_from_text),
                        choose,
                    ),
                    choiceRow("order", stringResource(R.string.photo_order), order, PlasmaIcons.Stacked, stringResource(R.string.photo_order_text)),
                ),
                divider = true,
            ),
            SettingSection(
                null,
                listOf(choiceRow("each", stringResource(R.string.each_picture), each, PlasmaIcons.History, stringResource(R.string.each_picture_text))),
                divider = true,
            ),
        ),
    )
}

/** Where the folder sheet is: the drives, or a folder on one, with what's in it. */
private sealed interface PhotoStage {
    data object Asking : PhotoStage

    data object NotAllowed : PhotoStage

    data class Drives(val drives: List<PhotoDrive>) : PhotoStage

    data class Folder(val drive: PhotoDrive, val path: String, val list: PhotoFolderList) : PhotoStage {
        /** "This TV › Pictures › 2025". */
        val shown: String get() = (listOf(drive.name) + path.removePrefix(drive.path).split('/').filter { it.isNotEmpty() }).joinToString(" › ")
    }
}

/**
 * Photos From › On This TV or a USB Drive (#170): Android's permission to see photos first, then
 * the drives, then their folders with Use This Folder, as the network folder's sheet has them.
 * Back goes up a folder, or closes it from the drives.
 */
@Composable
internal fun PhotoFolderSheet(folders: PhotoFolders, onUsed: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf<PhotoStage>(PhotoStage.Asking) }
    val showDrives = {
        scope.launch {
            stage = PhotoStage.Drives(
                try {
                    folders.drives()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failed: Exception) {
                    emptyList()
                },
            )
        }
    }
    val open = { drive: PhotoDrive, path: String ->
        scope.launch {
            val list = try {
                folders.folders(path)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failed: Exception) {
                PhotoFolderList(emptyList(), 0)
            }
            stage = PhotoStage.Folder(drive, path, list)
        }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showDrives() else stage = PhotoStage.NotAllowed
    }
    val askIfNeeded = {
        val permission = folders.permission
        if (permission == null || context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) showDrives() else ask.launch(permission)
    }
    LaunchedEffect(Unit) { askIfNeeded() }
    val title = stringResource(R.string.photos_on_tv)
    // A fresh sheet for each step, so focus starts on its first choice.
    when (val now = stage) {
        PhotoStage.Asking -> Unit
        PhotoStage.NotAllowed -> key(now) {
            SignInSheet(
                title = title,
                text = stringResource(R.string.photos_permission_text),
                onClose = onClose,
                icon = PlasmaIcons.Photo,
                options = listOf(SheetOption(stringResource(R.string.ask_again), onClick = { askIfNeeded() }), SheetOption(stringResource(R.string.cancel), onClick = onClose)),
            )
        }
        is PhotoStage.Drives -> key(now) {
            SignInSheet(
                title = title,
                text = stringResource(R.string.photos_drives_text),
                onClose = onClose,
                icon = PlasmaIcons.Photo,
                options = now.drives.map { drive -> SheetOption(drive.name, onClick = { open(drive, drive.path) }, icon = PlasmaIcons.Drive) },
            )
        }
        is PhotoStage.Folder -> key(now) {
            val up = {
                if (now.path.trimEnd('/') == now.drive.path.trimEnd('/')) showDrives() else open(now.drive, now.path.trimEnd('/').substringBeforeLast('/'))
            }
            SignInSheet(
                title = now.shown,
                text = stringResource(R.string.photos_folder_text),
                onClose = onClose,
                onBack = { up() },
                icon = PlasmaIcons.Photo,
                options = listOf(
                    SheetOption(
                        stringResource(R.string.use_this_folder),
                        onClick = {
                            folders.use(now.drive, now.path)
                            onUsed()
                            onClose()
                        },
                        value = pluralStringResource(R.plurals.photos_in_folder, now.list.photos, now.list.photos).takeIf { now.list.photos > 0 },
                    ),
                ) +
                    listOfNotNull(SheetOption.Divider.takeIf { now.list.folders.isNotEmpty() }) +
                    now.list.folders.map { folder -> SheetOption(folder, onClick = { open(now.drive, "${now.path.trimEnd('/')}/$folder") }, icon = PlasmaIcons.Folder) },
            )
        }
    }
}
